package br.com.nobrega.m4td.gps.sdk

import android.content.Context
import br.com.nobrega.m4td.gps.data.AltitudeReference
import br.com.nobrega.m4td.gps.data.LocationFix
import br.com.nobrega.m4td.gps.data.SettingsStore
import dji.sdk.keyvalue.KeyTools
import dji.sdk.keyvalue.key.DJIKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.sdk.keyvalue.value.flightcontroller.GPSSignalLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the [dji.v5.manager.KeyManager] listeners for aircraft GPS keys and exposes the
 * latest fix as a [StateFlow] of [LocationFix]. All DJI types are isolated here.
 *
 * Subscribed when [DjiSdkManager] reports aircraft connect, cancelled on disconnect.
 * Invalid fixes ((0,0), NaN, out-of-range) are filtered when `skipInvalidFixes` is on.
 *
 * Altitude note: MSDK V5's `KeyAircraftLocation3D` reports altitude relative to the
 * takeoff / home point (verified against the 5.18.0 API reference). The emitted
 * [LocationFix.altitudeRef] is set accordingly so receivers know the reference.
 */
class AircraftLocationRepository(
    private val sdk: DjiSdkManager,
    context: Context,
) {
    private val appContext = context.applicationContext
    private val settings = SettingsStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _latestFix = MutableStateFlow<LocationFix?>(null)
    val latestFix: StateFlow<LocationFix?> = _latestFix.asStateFlow()

    private data class Raw(
        val loc: LocationCoordinate3D? = null,
        val sats: Int? = null,
        val signal: String? = null,
    )

    @Volatile private var raw: Raw = Raw()
    @Volatile private var listening = false
    private val holder = Any()

    @Volatile private var cfgCache: br.com.nobrega.m4td.gps.data.CalTopoConfig = settings.load()

    fun refreshConfig() { cfgCache = settings.load() }

    fun start() {
        if (listening) return
        listening = true
        refreshConfig()
        try {
            val locKey: DJIKey<LocationCoordinate3D> =
                KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D)
            sdk.listenForKey(locKey, holder) { _, new ->
                new?.let {
                    synchronized(this) { raw = raw.copy(loc = it) }
                    emitIfReady()
                }
            }

            val satsKey: DJIKey<Int> =
                KeyTools.createKey(FlightControllerKey.KeyGPSSatelliteCount)
            sdk.listenForKey(satsKey, holder) { _, new ->
                synchronized(this) { raw = raw.copy(sats = new) }
                emitIfReady()
            }

            val sigKey: DJIKey<GPSSignalLevel> =
                KeyTools.createKey(FlightControllerKey.KeyGPSSignalLevel)
            sdk.listenForKey(sigKey, holder) { _, new ->
                synchronized(this) { raw = raw.copy(signal = new?.name) }
                emitIfReady()
            }
        } catch (_: Throwable) {
            listening = false
        }
    }

    fun stop() {
        if (!listening) return
        try {
            sdk.cancelListen(holder)
        } catch (_: Throwable) {
            // KeyManager may already be torn down; best-effort.
        } finally {
            listening = false
            synchronized(this) { raw = Raw() }
            _latestFix.value = null
        }
    }

    @Synchronized
    private fun emitIfReady() {
        val r = raw
        val loc = r.loc ?: return
        val cfg = cfgCache
        val fix = LocationFix(
            deviceId = cfg.deviceId,
            lat = loc.latitude,
            lon = loc.longitude,
            altitudeMeters = loc.altitude,
            altitudeRef = AltitudeReference.TAKEOFF_RELATIVE,
            satelliteCount = r.sats,
            gpsSignalLevel = r.signal,
        )
        if (cfg.skipInvalidFixes && !fix.isValid) return
        _latestFix.value = fix
    }
}
