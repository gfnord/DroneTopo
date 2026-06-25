package com.infinitii.m4td.gps.data

/**
 * CalTopo Team "Shared Locations" reporter config.
 *
 * Endpoint shape (GET, unsigned — the connect key in the path is the shared secret):
 *   {baseUrl}/api/v1/position/report/{connectKey}?id={deviceId}&lat=..&lng=..
 *
 * CalTopo composes the on-map call sign from the path connect key + id:
 *   callSign = "{connectKey}-{deviceId}"
 *
 * The connect key is a shared secret — store it via [SettingsStore] (SharedPreferences),
 * never in committed files.
 *
 * Only `id`, `lat`, `lng` are documented params. Altitude/heading/speed are not sent.
 */
data class CalTopoConfig(
    val baseUrl: String,
    val connectKey: String,
    val deviceId: String,
    val reportIntervalSeconds: Long,
    val skipInvalidFixes: Boolean,
) {
    val callSign: String get() = "$connectKey-$deviceId"

    companion object {
        const val MIN_INTERVAL_SECONDS = 1L
        const val MAX_INTERVAL_SECONDS = 3600L
    }
}
