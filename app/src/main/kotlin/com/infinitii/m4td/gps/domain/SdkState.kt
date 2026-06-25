package com.infinitii.m4td.gps.domain

/**
 * High-level phase of the DJI SDK lifecycle, exposed to the UI.
 *
 * Sequence under normal operation:
 *   IDLE → INITIALIZING → REGISTERING → REGISTERED → READY (aircraft connected)
 *
 * Failure paths:
 *   - REGISTERING → REGISTRATION_FAILED (bad/expired App Key, package-name mismatch,
 *     no internet on first run, etc.)
 *   - READY → REGISTERED (aircraft disconnected; still ready to reconnect)
 */
enum class SdkPhase {
    IDLE,
    INITIALIZING,
    REGISTERING,
    REGISTERED,
    REGISTRATION_FAILED,
    READY,
}

/**
 * Snapshot of SDK + aircraft connection state for the UI.
 *
 * @param phase            see [SdkPhase].
 * @param initProgress     0..100 SDK init progress, when known.
 * @param aircraftConnected true once DJI reports [SDKManagerCallback.onProductConnect].
 * @param productId        DJI product id, when connected.
 * @param modelName        DJI model display name, when known.
 * @param lastError        last error message (registration failure, etc.).
 * @param lastChangeEpochMs timestamp of the most recent state transition (for UI freshness).
 */
data class SdkState(
    val phase: SdkPhase = SdkPhase.IDLE,
    val initProgress: Int = 0,
    val aircraftConnected: Boolean = false,
    val productId: Int? = null,
    val modelName: String? = null,
    val lastError: String? = null,
    val lastChangeEpochMs: Long = System.currentTimeMillis(),
) {
    val canStream: Boolean get() = phase == SdkPhase.READY && aircraftConnected
}
