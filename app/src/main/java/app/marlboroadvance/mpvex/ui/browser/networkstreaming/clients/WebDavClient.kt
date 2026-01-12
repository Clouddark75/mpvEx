package app.marlboroadvance.mpvex.ui.browser.networkstreaming.clients

import android.net.Uri
import android.util.Log
import app.marlboroadvance.mpvex.domain.network.NetworkConnection
import app.marlboroadvance.mpvex.domain.network.NetworkFile
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import com.thegrizzlylabs.sardineandroid.DavResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class WebDavClient(private val connection: NetworkConnection) : NetworkClient {
  companion object {
    private const val TAG = "WebDavClient"
  }

  private var sardine: Sardine? = null
  private var baseUrl: String = ""

  override suspend fun connect(): Result<Unit> =
    withContext(Dispatchers.IO) {
      try {
        val protocol = if (connection.port == 443) "https" else "http"
        baseUrl = "$protocol://${connection.host}:${connection.port}${connection.path}"

        val client = OkHttpSardine()
        if (!connection.isAnonymous) {
          client.setCredentials(connection.username, connection.password)
        }

        client.exists(baseUrl)

        sardine = client
        Result.success(Unit)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun disconnect() {
    withContext(Dispatchers.IO) {
      sardine = null
    }
  }

  override fun isConnected(): Boolean = sardine != null

  /**
   * Construye una URL completa a partir de un path
   * Si el path ya contiene el basePath de la conexión, no lo duplica
   */
  fun buildFullUrl(path: String): String {
    if (path.startsWith("http")) {
      return path
    }

    val cleanBaseUrl = baseUrl.trimEnd('/')
    val cleanBasePath = connection.path.trimEnd('/')
    
    // Si el path ya comienza con el basePath de la conexión, no lo agregues de nuevo
    val cleanPath = if (path.startsWith(cleanBasePath)) {
      path
    } else {
      // Si no tiene el basePath, agrégalo
      if (path.startsWith("/")) path else "/$path"
    }
    
    // Si el path ya incluye el basePath, construye directamente
    if (path.startsWith(cleanBasePath)) {
      val protocol = if (connection.port == 443) "https" else "http"
      return "$protocol://${connection.host}:${connection.port}$cleanPath"
    }
    
    return "$cleanBaseUrl$cleanPath"
  }

  override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        val url = buildFullUrl(path)

        val resources = client.list(url)

        val files =
          resources
            .drop(1)
            .map { resource: DavResource ->
              val resourceName = resource.name ?: ""
              
              // Construye el path relativo SIN duplicar el basePath
              val filePath = if (path.endsWith("/")) {
                "$path$resourceName"
              } else if (path.isEmpty() || path == "/") {
                "${connection.path.trimEnd('/')}/$resourceName"
              } else {
                "$path/$resourceName"
              }

              NetworkFile(
                name = resourceName,
                path = filePath,
                isDirectory = resource.isDirectory,
                size = resource.contentLength ?: 0,
                lastModified = resource.modified?.time ?: 0,
                mimeType = if (!resource.isDirectory) getMimeType(resourceName) else null,
              )
            }

        Result.success(files)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun getFileSize(path: String): Result<Long> =
    withContext(Dispatchers.IO) {
      try {
        val client = sardine ?: return@withContext Result.failure(Exception("Not connected"))

        val url = buildFullUrl(path)

        val resources = client.list(url, 0)
        if (resources.isNotEmpty() && !resources[0].isDirectory) {
          val size = resources[0].contentLength ?: -1L
          Result.success(size)
        } else {
          Result.failure(Exception("File not found or is a directory"))
        }
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileStream(path: String): Result<InputStream> =
    withContext(Dispatchers.IO) {
      try {
        val streamClient = OkHttpSardine()

        if (!connection.isAnonymous) {
          streamClient.setCredentials(connection.username, connection.password)
        }

        val url = buildFullUrl(path)

        val rawStream = streamClient.get(url)

        if (rawStream == null) {
          return@withContext Result.failure(Exception("Failed to open WebDAV stream"))
        }

        val wrappedStream = object : InputStream() {
          override fun read(): Int = rawStream.read()

          override fun read(b: ByteArray): Int = rawStream.read(b)

          override fun read(b: ByteArray, off: Int, len: Int): Int = rawStream.read(b, off, len)

          override fun available(): Int = rawStream.available()

          override fun close() {
            try {
              rawStream.close()
            } catch (e: Exception) {
              // Ignore
            }
          }
        }

        Result.success(wrappedStream)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  override suspend fun getFileUri(path: String): Result<Uri> =
    withContext(Dispatchers.IO) {
      try {
        val url = buildFullUrl(path)

        val uriString =
          if (connection.isAnonymous) {
            url
          } else {
            // Reconstruye con credenciales
            val protocol = if (connection.port == 443) "https" else "http"
            val cleanBasePath = connection.path.trimEnd('/')
            
            // Extrae solo la parte del path después del basePath
            val relativePath = if (path.startsWith(cleanBasePath)) {
              path.substring(cleanBasePath.length)
            } else if (path.startsWith("/")) {
              path
            } else {
              "/$path"
            }
            
            "$protocol://${connection.username}:${connection.password}@${connection.host}:${connection.port}$cleanBasePath$relativePath"
          }

        Result.success(Uri.parse(uriString))
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  private fun getMimeType(fileName: String): String? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
      "mp4", "m4v" -> "video/mp4"
      "mkv" -> "video/x-matroska"
      "avi" -> "video/x-msvideo"
      "mov" -> "video/quicktime"
      "wmv" -> "video/x-ms-wmv"
      "flv" -> "video/x-flv"
      "webm" -> "video/webm"
      "mpeg", "mpg" -> "video/mpeg"
      "3gp" -> "video/3gpp"
      "ts" -> "video/mp2t"
      else -> null
    }
  }
}
