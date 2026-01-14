package app.marlboroadvance.mpvex.ui.player

import android.util.Log
import app.marlboroadvance.mpvex.preferences.AudioPreferences
import app.marlboroadvance.mpvex.preferences.SubtitlesPreferences
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay

/**
 * Handles automatic track selection based on user preferences.
 *
 * Priority hierarchy for SUBTITLES (highest to lowest):
 * 1. User manual selection (saved state) - ALWAYS respected, never overridden
 * 2. Preferred language (from settings) - Applied only when no saved selection exists
 * 3. Default track (from container metadata) - Used when no preference and no saved state
 * 4. No selection (disabled) - Subtitles are optional
 *
 * Priority hierarchy for AUDIO (highest to lowest):
 * 1. User manual selection (saved state) - ALWAYS respected, never overridden
 * 2. Preferred language (from settings) - Applied only when no saved selection exists
 * 3. Default track (from container metadata) - Used as fallback
 * 4. First available track - Final fallback (audio is mandatory)
 *
 * This ensures:
 * - User choices are ALWAYS preserved across app restarts
 * - Audio tracks are ALWAYS selected (never silent playback)
 * - Subtitle default tracks are respected on first-time playback
 * - Preferred languages serve as defaults for first-time playback only
 */
class TrackSelector(
  private val audioPreferences: AudioPreferences,
  private val subtitlesPreferences: SubtitlesPreferences,
) {
  companion object {
    private const val TAG = "TrackSelector"
  }

  /**
   * Called after a file loads in MPV.
   * Ensures proper track selection based on preferences and saved state.
   *
   * @param savedState The saved playback state (contains user's previous track selections)
   */
  suspend fun onFileLoaded(savedState: PlaybackStateEntity? = null) {
      // Wait for MPV to finish demuxing and detecting tracks
      var attempts = 0
      val maxAttempts = 20
      
      while (attempts < maxAttempts) {
          val trackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
          if (trackCount > 0) break
          delay(50)
          attempts++
      }
  
      ensureAudioTrackSelected(savedState)
      ensureSubtitleTrackSelected(savedState)
  }
  
  private fun ensureAudioTrackSelected(savedState: PlaybackStateEntity?) {
      try {
          val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
          
          // If we have saved state with a valid track, restore it
          if (savedState != null && savedState.aid > 0) {
              // Verify the track still exists
              val trackExists = (0 until totalTrackCount).any { i ->
                  MPVLib.getPropertyString("track-list/$i/type") == "audio" &&
                  MPVLib.getPropertyInt("track-list/$i/id") == savedState.aid
              }
              
              if (trackExists) {
                  MPVLib.setPropertyInt("aid", savedState.aid)
                  Log.d(TAG, "Audio: Restored saved track (aid=${savedState.aid})")
                  return
              } else {
                  Log.w(TAG, "Audio: Saved track ${savedState.aid} no longer exists, applying preferences")
              }
          }
          
          // No saved state or track doesn't exist - clear any auto-selected track
          val currentAid = MPVLib.getPropertyInt("aid")
          if (currentAid != null && currentAid > 0) {
              Log.d(TAG, "Audio: Clearing auto-selected track to apply preferences")
              MPVLib.setPropertyString("aid", "no")
          }
  
          // Try preferred languages
          val preferredLangs = audioPreferences.preferredLanguages.get()
              .split(",")
              .map { it.trim() }
              .filter { it.isNotEmpty() }
  
          if (preferredLangs.isNotEmpty()) {
              for (i in 0 until totalTrackCount) {
                  if (MPVLib.getPropertyString("track-list/$i/type") != "audio") continue
                  
                  val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                  val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                  
                  for (preferredLang in preferredLangs) {
                      if (lang.equals(preferredLang, ignoreCase = true)) {
                          Log.d(TAG, "Audio: Selected preferred language '$lang' (aid=$trackId)")
                          MPVLib.setPropertyInt("aid", trackId)
                          return
                      }
                  }
              }
          }
  
          // Try default track
          for (i in 0 until totalTrackCount) {
              if (MPVLib.getPropertyString("track-list/$i/type") != "audio") continue
              
              val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
              val isDefault = MPVLib.getPropertyBoolean("track-list/$i/default") ?: false
              
              if (isDefault) {
                  Log.d(TAG, "Audio: Selected default track (aid=$trackId)")
                  MPVLib.setPropertyInt("aid", trackId)
                  return
              }
          }
  
          // Select first audio track (mandatory)
          for (i in 0 until totalTrackCount) {
              if (MPVLib.getPropertyString("track-list/$i/type") == "audio") {
                  val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                  Log.d(TAG, "Audio: Selected first available track (aid=$trackId)")
                  MPVLib.setPropertyInt("aid", trackId)
                  return
              }
          }
  
      } catch (e: Exception) {
          Log.e(TAG, "Error selecting audio track", e)
      }
  }
  
  private fun ensureSubtitleTrackSelected(savedState: PlaybackStateEntity?) {
      try {
          val totalTrackCount = MPVLib.getPropertyInt("track-list/count") ?: 0
          
          // If we have saved state, restore it (even if disabled)
          if (savedState != null) {
              if (savedState.sid > 0) {
                  // Verify the track still exists
                  val trackExists = (0 until totalTrackCount).any { i ->
                      MPVLib.getPropertyString("track-list/$i/type") == "sub" &&
                      MPVLib.getPropertyInt("track-list/$i/id") == savedState.sid
                  }
                  
                  if (trackExists) {
                      MPVLib.setPropertyInt("sid", savedState.sid)
                      Log.d(TAG, "Subtitles: Restored saved track (sid=${savedState.sid})")
                  } else {
                      Log.w(TAG, "Subtitles: Saved track ${savedState.sid} no longer exists")
                      MPVLib.setPropertyString("sid", "no")
                  }
              } else {
                  // User had subtitles disabled - respect that
                  Log.d(TAG, "Subtitles: Respecting saved disabled state")
                  MPVLib.setPropertyString("sid", "no")
              }
              return
          }
          
          // No saved state - clear any auto-selected track and apply preferences
          val currentSid = MPVLib.getPropertyInt("sid")
          if (currentSid != null && currentSid > 0) {
              Log.d(TAG, "Subtitles: Clearing auto-selected track to apply preferences")
              MPVLib.setPropertyString("sid", "no")
          }
  
          // Try preferred languages
          val preferredLangs = subtitlesPreferences.preferredLanguages.get()
              .split(",")
              .map { it.trim() }
              .filter { it.isNotEmpty() }
  
          if (preferredLangs.isNotEmpty()) {
              for (i in 0 until totalTrackCount) {
                  if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
                  
                  val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
                  val lang = MPVLib.getPropertyString("track-list/$i/lang") ?: ""
                  
                  for (preferredLang in preferredLangs) {
                      if (lang.equals(preferredLang, ignoreCase = true)) {
                          Log.d(TAG, "Subtitles: Selected preferred language '$lang' (sid=$trackId)")
                          MPVLib.setPropertyInt("sid", trackId)
                          return
                      }
                  }
              }
              Log.d(TAG, "Subtitles: No preferred language match, keeping disabled")
              return
          }
  
          // Try default track
          for (i in 0 until totalTrackCount) {
              if (MPVLib.getPropertyString("track-list/$i/type") != "sub") continue
              
              val trackId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
              val isDefault = MPVLib.getPropertyBoolean("track-list/$i/default") ?: false
              
              if (isDefault) {
                  Log.d(TAG, "Subtitles: Selected default track (sid=$trackId)")
                  MPVLib.setPropertyInt("sid", trackId)
                  return
              }
          }
          
          Log.d(TAG, "Subtitles: No default track, keeping disabled")
  
      } catch (e: Exception) {
          Log.e(TAG, "Error selecting subtitle track", e)
      }
  }
