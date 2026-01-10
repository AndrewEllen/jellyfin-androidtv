package org.jellyfin.androidtv.preference

import android.content.Context
import org.jellyfin.preference.store.SharedPreferenceStore
import org.jellyfin.preference.stringPreference

class ExternalServicesPreferences(context: Context) : SharedPreferenceStore(
    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
) {
    companion object {
        private const val SHARED_PREFERENCES_NAME = "external_services"

        val jellyseerrUrl = stringPreference("jellyseerr_url", "")
        val jellyseerrApiKey = stringPreference("jellyseerr_api_key", "")
        val radarrUrl = stringPreference("radarr_url", "")
        val radarrApiKey = stringPreference("radarr_api_key", "")
        val sonarrUrl = stringPreference("sonarr_url", "")
        val sonarrApiKey = stringPreference("sonarr_api_key", "")
    }
}
