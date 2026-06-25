package com.infinitii.m4td.gps.upload

import com.infinitii.m4td.gps.data.CalTopoConfig
import com.infinitii.m4td.gps.data.LocationFix
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale

/**
 * Reports the latest aircraft position to CalTopo's Team "Shared Locations" overlay via
 * the unsigned connect-key position endpoint.
 *
 * Endpoint shape:
 *   GET {baseUrl}/api/v1/position/report/{connectKey}?id={deviceId}&lat=..&lng=..
 *
 * - CalTopo composes the call sign as "{connectKey}-{deviceId}" — see [CalTopoConfig.callSign].
 * - Only `id`, `lat`, `lng` are documented params; nothing else is sent.
 * - CalTopo timestamps each report on receipt, so no client timestamp is included.
 * - Lat/lng are formatted with `Locale.US` to guarantee `.` decimal separators regardless
 *   of the controller's locale (comma decimals would silently corrupt the request).
 * - HTTP 200 = success; non-200 throws inside [Result] so the ticker counts it as failure.
 */
class CalTopoPositionReporter(
    private val client: OkHttpClient,
    private val config: CalTopoConfig,
) {
    fun report(fix: LocationFix): Result<Int> {
        val lat = String.format(Locale.US, "%.6f", fix.lat)
        val lng = String.format(Locale.US, "%.6f", fix.lon)

        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/position/report/${config.connectKey}")
            .addQueryParameter("id", config.deviceId)
            .addQueryParameter("lat", lat)
            .addQueryParameter("lng", lng)
            .build()

        val request = Request.Builder().url(url).get().build()

        return runCatching {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("CalTopo HTTP ${resp.code}")
                resp.code
            }
        }
    }
}
