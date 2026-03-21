package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

class AdvancedPreferences(
  preferenceStore: PreferenceStore,
) {
  // Cambiado de URI a path directo
  val mpvConfStorageLocation = preferenceStore.getString("mpv_conf_storage_location")
  val mpvConf = preferenceStore.getString("mpv.conf")
  val inputConf = preferenceStore.getString("input.conf")
  val verboseLogging = preferenceStore.getBoolean("verbose_logging", BuildConfig.BUILD_TYPE != "release")
  val enabledStatisticsPage = preferenceStore.getInt("enabled_stats_page", 0)
  val enableRecentlyPlayed = preferenceStore.getBoolean("enable_recently_played", true)
}
