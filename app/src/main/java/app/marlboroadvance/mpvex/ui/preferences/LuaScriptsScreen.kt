package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
object LuaScriptsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val scope = rememberCoroutineScope()
    
    val mpvConfStorageLocation by preferences.mpvConfStorageLocation.collectAsState()
    val selectedScripts by preferences.selectedLuaScripts.collectAsState()
    val enableLuaScripts by preferences.enableLuaScripts.collectAsState()
    
    var availableScripts by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load scripts
    LaunchedEffect(mpvConfStorageLocation) {
      if (mpvConfStorageLocation.isBlank()) {
        isLoading = false
        return@LaunchedEffect
      }
      
      withContext(Dispatchers.IO) {
        val scripts = mutableListOf<String>()
        runCatching {
          val folder = File(mpvConfStorageLocation)
          
          if (folder.exists() && folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
              if (file.isFile && file.name.endsWith(".lua")) {
                scripts.add(file.name)
              }
            }
          }
        }.onFailure { e ->
          withContext(Dispatchers.Main) {
            Toast.makeText(
              context,
              "Error loading scripts: ${e.message}",
              Toast.LENGTH_LONG
            ).show()
          }
        }
        withContext(Dispatchers.Main) {
          availableScripts = scripts.sorted()
          isLoading = false
        }
      }
    }
    
    fun toggleScriptSelection(scriptName: String) {
      val newSelection = if (selectedScripts.contains(scriptName)) {
        selectedScripts - scriptName
      } else {
        selectedScripts + scriptName
      }
      preferences.selectedLuaScripts.set(newSelection)
    }
    
    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = "Lua Scripts",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
      floatingActionButton = {
        FloatingActionButton(
          onClick = {
            backStack.add(LuaScriptEditorScreen(scriptName = null))
          },
          containerColor = MaterialTheme.colorScheme.primary,
        ) {
          Icon(
            Icons.Default.Add,
            contentDescription = "Create new script",
            tint = MaterialTheme.colorScheme.onPrimary,
          )
        }
      },
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
      ) {
        when {
          isLoading -> {
            Text(
              text = "Loading scripts...",
              modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          availableScripts.isEmpty() -> {
            Column(
              modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "No Lua scripts found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Add .lua files to:\n$mpvConfStorageLocation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 8.dp),
              )
            }
          }
          else -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize()
            ) {
              items(availableScripts) { scriptName ->
                Column(
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .clickable { 
                        toggleScriptSelection(scriptName) 
                      }
                      .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Row(
                      modifier = Modifier.weight(1f),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Checkbox(
                        checked = selectedScripts.contains(scriptName),
                        onCheckedChange = { toggleScriptSelection(scriptName) },
                      )
                      Spacer(modifier = Modifier.width(12.dp))
                      Text(
                        text = scriptName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    
                    IconButton(
                      onClick = {
                        backStack.add(LuaScriptEditorScreen(scriptName = scriptName))
                      }
                    ) {
                      Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}
