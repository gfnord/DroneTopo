package com.infinitii.m4td.gps.data

import java.time.Instant

/**
 * SDK-agnostic aircraft position fix. Keeps DJI types out of the rest of the app so
 * the reporter / UI / service stay testable in isolation.
 *
 * @param deviceId       the logical identifier sent to the API (defaults from config / settings).
 * @param lat            aircraft latitude, decimal degrees, WGS84.
 * @param lon            aircraft longitude, decimal degrees, WGS84.
 * @param altitudeMeters altitude in meters. See [AltitudeReference] — MSDK V5 typically
 *                       reports takeoff-relative altitude for `KeyAircraftLocation3D`;
 *                       the JSON payload labels this so the receiver knows what it is.
 * @param altitudeRef    which reference the altitude is measured from.
 * @param satelliteCount GPS satellites in fix, or null if unknown.
 * @param gpsSignalLevel DJI GPS signal level enum name (e.g. "LEVEL_5"), or null.
 * @param timestamp      ISO-8601 UTC capture time. Defaults to now.
 */
data class LocationFix(
    val deviceId: String,
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Double,
    val altitudeRef: AltitudeReference = AltitudeReference.TAKEOFF_RELATIVE,
    val satelliteCount: Int? = null,
    val gpsSignalLevel: String? = null,
    val timestamp: String = Instant.now().toString(),
) {
    /**
     * Heuristic invalid-fix filter. (0,0), NaN, infinities, or out-of-range values are rejected.
     * Used by [com.infinitii.m4td.gps.sdk.AircraftLocationRepository] when
     * `skipInvalidFixes` is enabled.
     */
    val isValid: Boolean
        get() {
            if (!lat.isFinite() || !lon.isFinite() || !altitudeMeters.isFinite()) return false
            if (lat == 0.0 && lon == 0.0) return false
            if (lat < -90.0 || lat > 90.0) return false
            if (lon < -180.0 || lon > 180.0) return false
            return true
        }
}

/** What the [LocationFix.altitudeMeters] value is referenced to. */
enum class AltitudeReference(val wireName: String) {
    /** Altitude above the aircraft's takeoff / home point (DJI default for location keys). */
    TAKEOFF_RELATIVE("takeoff_relative"),

    /** Altitude above mean sea level. */
    MSL("msl"),

    /** Altitude above the WGS84 ellipsoid. */
    ELLIPSOIDAL("ellipsoidal");
}
