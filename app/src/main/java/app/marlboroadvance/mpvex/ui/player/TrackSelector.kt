package app.marlboroadvance.mpvex.ui.player

import android.util.Log
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

/**
 * Handles automatic track selection based on user preferences.
 * 
 * **Audio Priority:**
 * 1. Preferred language (clean, no commentary)
 * 2. First available clean audio
 * 
 * **Subtitle Priority:**
 * 1. Default track (even without language)
 * 2. External subtitle file
 * 3. Preferred language
 * 4. First available track
 */
class TrackSelector(
  private val audioPreferences: AudioPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
) {
  companion object {
    private const val TAG = "TrackSelector"
  }

  private data class Track(
    val id: Int,
    val type: String,
    val lang: String,
    val title: String,
    val isDefault: Boolean,
    val forced: Boolean,
    val hearing: Boolean,
    val external: Boolean,
    val image: Boolean
  )

  suspend fun onFileLoaded(hasState: Boolean = false) {
    var attempts = 0
    val maxAttempts = 20
    
    while (attempts < maxAttempts) {
      val count = MPVLib.getPropertyInt("track-list/count") ?: 0
      if (count > 0) break
      delay(50)
      attempts++
    }

    val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
    if (trackCount == 0) return

    val tracks = readTracks(trackCount)

    if (!isVideoFile(tracks)) {
      Log.d(TAG, "Audio/Image file detected. Track selection disabled.")
      return
    }
  
    ensureAudioTrackSelected(tracks, hasState)
    ensureSubtitleTrackSelected(tracks, hasState)
  }

  private fun readTracks(count: Int): List<Track> {
    val list = mutableListOf<Track>()
    for (i in 0 until count) {
      val id = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
      val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue

      list.add(
        Track(
          id = id,
          type = type,
          lang = (MPVLib.getPropertyString("track-list/$i/lang") ?: "").lowercase(),
          title = (MPVLib.getPropertyString("track-list/$i/title") ?: "").lowercase(),
          isDefault = MPVLib.getPropertyBoolean("track-list/$i/default") ?: false,
          forced = MPVLib.getPropertyBoolean("track-list/$i/forced") ?: false,
          hearing = MPVLib.getPropertyBoolean("track-list/$i/hearing-impaired") ?: false,
          external = MPVLib.getPropertyBoolean("track-list/$i/external") ?: false,
          image = MPVLib.getPropertyBoolean("track-list/$i/image") ?: false
        )
      )
    }
    return list
  }

  private fun isVideoFile(tracks: List<Track>): Boolean {
    return tracks.any { it.type == "video" && !it.image }
  }

  // ==================================================
  // AUDIO SELECTION
  // ==================================================

  private suspend fun ensureAudioTrackSelected(tracks: List<Track>, hasState: Boolean) {
    try {
      val currentAid = MPVLib.getPropertyInt("aid")
      if (hasState && currentAid != null && currentAid > 0) return

      val preferredLangs = audioPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

      val ignoreKeywords = listOf("commentary", "description", "adh", "comment", "extra")
      val audioTracks = tracks.filter { it.type == "audio" }

      // Priority 1: Preferred clean audio
      if (preferredLangs.isNotEmpty()) {
        for (prefLang in preferredLangs) {
          for (track in audioTracks) {
            if (track.lang == prefLang || track.lang.startsWith(prefLang)) {
              if (ignoreKeywords.none { track.title.contains(it) }) {
                if (currentAid == track.id) {
                  Log.d(TAG, "Audio: Preferred language already active (id=${track.id}, lang=${track.lang})")
                } else {
                  Log.d(TAG, "Audio: Selecting preferred language (id=${track.id}, lang=${track.lang})")
                  MPVLib.setPropertyInt("aid", track.id)
                }
                return
              }
            }
          }
        }
      }

      // Priority 2: MPV default is fine
      if (currentAid != null && currentAid > 0) return

      // Priority 3: First clean audio track
      for (track in audioTracks) {
        if (ignoreKeywords.none { track.title.contains(it) }) {
          if (currentAid == track.id) {
            Log.d(TAG, "Audio: First clean track already active (id=${track.id})")
          } else {
            Log.d(TAG, "Audio: Selecting first clean track (id=${track.id})")
            MPVLib.setPropertyInt("aid", track.id)
          }
          return
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Audio selection failed", e)
    }
  }

  // ==================================================
  // SUBTITLE SELECTION
  // ==================================================

  private suspend fun ensureSubtitleTrackSelected(tracks: List<Track>, hasState: Boolean) {
    try {
      val currentSid = MPVLib.getPropertyInt("sid") ?: 0

      // Respect manual "Subtitles Off" state
      if (hasState && currentSid == 0) {
        Log.d(TAG, "Subtitle: User disabled subtitles. Respecting choice.")
        return
      }

      if (hasState && currentSid > 0) return

      val subTracks = tracks.filter { it.type == "sub" }

      // PRIORITY 1: Default track (even without language)
      val defaultTrack = subTracks.firstOrNull { it.isDefault }
      if (defaultTrack != null) {
        if (currentSid == defaultTrack.id) {
          Log.d(TAG, "Subtitle: Default track already active (id=${defaultTrack.id}, lang='${defaultTrack.lang}')")
        } else {
          Log.d(TAG, "Subtitle: Selecting default track (id=${defaultTrack.id}, lang='${defaultTrack.lang}')")
          MPVLib.setPropertyInt("sid", defaultTrack.id)
        }
        return
      }

      // PRIORITY 2: External subtitle file
      val externalTrack = subTracks.firstOrNull { it.external }
      if (externalTrack != null) {
        if (currentSid == externalTrack.id) {
          Log.d(TAG, "Subtitle: External track already active (id=${externalTrack.id})")
        } else {
          Log.d(TAG, "Subtitle: Selecting external track (id=${externalTrack.id})")
          MPVLib.setPropertyInt("sid", externalTrack.id)
        }
        return
      }

      // PRIORITY 3: User's preferred language
      var preferredLangs = subtitlesPreferences.preferredLanguages.get()
        .split(",")
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }

      if (preferredLangs.isEmpty()) {
        preferredLangs = (MPVLib.getPropertyString("slang") ?: "")
          .split(",")
          .map { it.trim().lowercase() }
          .filter { it.isNotEmpty() }
      }

      if (preferredLangs.isNotEmpty()) {
        for (prefLang in preferredLangs) {
          val matchingTrack = subTracks.firstOrNull { 
            it.lang == prefLang || it.lang.startsWith(prefLang) 
          }
          if (matchingTrack != null) {
            if (currentSid == matchingTrack.id) {
              Log.d(TAG, "Subtitle: Preferred language already active (id=${matchingTrack.id}, lang='${matchingTrack.lang}')")
            } else {
              Log.d(TAG, "Subtitle: Selecting preferred language (id=${matchingTrack.id}, lang='${matchingTrack.lang}')")
              MPVLib.setPropertyInt("sid", matchingTrack.id)
            }
            return
          }
        }
      }

      // PRIORITY 4: First available track
      val firstTrack = subTracks.firstOrNull()
      if (firstTrack != null) {
        if (currentSid == firstTrack.id) {
          Log.d(TAG, "Subtitle: First track already active (id=${firstTrack.id})")
        } else {
          Log.d(TAG, "Subtitle: Selecting first available track (id=${firstTrack.id})")
          MPVLib.setPropertyInt("sid", firstTrack.id)
        }
        return
      }

      Log.d(TAG, "Subtitle: No tracks available")

    } catch (e: Exception) {
      Log.e(TAG, "Subtitle selection failed", e)
    }
  }
}
