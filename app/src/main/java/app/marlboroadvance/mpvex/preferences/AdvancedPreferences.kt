package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

class AdvancedPreferences(
  preferenceStore: PreferenceStore,
) {
  // Cambiado de mpvConfStorageUri a mpvConfStorageLocation con path directo
  val mpvConfStorageLocation = preferenceStore.getString("mpv_conf_storage_location")
  val mpvConf = preferenceStore.getString("mpv.conf")
  val inputConf = preferenceStore.getString("input.conf")
  val verboseLogging = preferenceStore.getBoolean("verbose_logging", BuildConfig.BUILD_TYPE != "release")
  val enabledStatisticsPage = preferenceStore.getInt("enabled_stats_page", 0)
  val enableRecentlyPlayed = preferenceStore.getBoolean("enable_recently_played", true)
  val enableLuaScripts = preferenceStore.getBoolean("enable_lua_scripts", false)
  val selectedLuaScripts = preferenceStore.getStringSet("selected_lua_scripts", emptySet())
}
