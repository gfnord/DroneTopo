package com.infinitii.m4td.gps.data

import android.content.Context
import android.content.SharedPreferences

import com.infinitii.m4td.gps.BuildConfig

/**
 * Persists user-editable CalTopo reporter config in SharedPreferences. The connect key
 * is a shared secret and lives ONLY here — never in committed files.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CalTopoConfig = CalTopoConfig(
        baseUrl = prefs.getString(KEY_BASE_URL, BuildConfig.DEFAULT_CALTOPO_BASE_URL)
            ?: BuildConfig.DEFAULT_CALTOPO_BASE_URL,
        connectKey = prefs.getString(KEY_CONNECT_KEY, BuildConfig.DEFAULT_CALTOPO_CONNECT_KEY)
            ?: BuildConfig.DEFAULT_CALTOPO_CONNECT_KEY,
        deviceId = prefs.getString(KEY_DEVICE_ID, BuildConfig.DEFAULT_DEVICE_ID)
            ?: BuildConfig.DEFAULT_DEVICE_ID,
        reportIntervalSeconds = prefs.getLong(
            KEY_REPORT_INTERVAL_SECONDS,
            BuildConfig.DEFAULT_CALTOPO_REPORT_INTERVAL_SECONDS.toLong(),
        ).coerceIn(CalTopoConfig.MIN_INTERVAL_SECONDS, CalTopoConfig.MAX_INTERVAL_SECONDS),
        skipInvalidFixes = prefs.getBoolean(KEY_SKIP_INVALID, BuildConfig.DEFAULT_SKIP_INVALID_FIXES),
    )

    fun save(cfg: CalTopoConfig) {
        prefs.edit().apply {
            putString(KEY_BASE_URL, cfg.baseUrl)
            putString(KEY_CONNECT_KEY, cfg.connectKey)
            putString(KEY_DEVICE_ID, cfg.deviceId)
            putLong(KEY_REPORT_INTERVAL_SECONDS, cfg.reportIntervalSeconds)
            putBoolean(KEY_SKIP_INVALID, cfg.skipInvalidFixes)
        }.apply()
    }

    companion object {
        private const val PREFS_NAME = "caltopo_settings"
        const val KEY_BASE_URL              = "caltopo_base_url"
        const val KEY_CONNECT_KEY           = "caltopo_connect_key"
        const val KEY_DEVICE_ID             = "device_id"
        const val KEY_REPORT_INTERVAL_SECONDS = "report_interval_seconds"
        const val KEY_SKIP_INVALID          = "skip_invalid_fixes"
    }
}
