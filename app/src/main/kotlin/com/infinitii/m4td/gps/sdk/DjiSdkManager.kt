package com.infinitii.m4td.gps.sdk

import android.content.Context
import com.infinitii.m4td.gps.domain.SdkPhase
import com.infinitii.m4td.gps.domain.SdkState
import dji.sdk.keyvalue.key.DJIKey
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.manager.KeyManager
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Wraps DJI MSDK V5 [SDKManager] init/register/connect lifecycle.
 *
 * Lifecycle: `init → INITIALIZE_COMPLETE → registerApp → onRegisterSuccess → onProductConnect`.
 *
 * CRITICAL: The [SDKManagerCallback] is held by this singleton (Application-scoped), never
 * by an Activity/Fragment — see ISDKManager docs for the memory-leak warning. The rest of
 * the app observes [state] (cold [StateFlow]) so UI/Service layers never touch DJI types.
 *
 * See: https://developer.dji.com/api-reference-v5/android-api/Components/SDKManager/DJISDKManager.html
 */
class DjiSdkManager(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(SdkState())
    val state: StateFlow<SdkState> = _state.asStateFlow()

    /** Lazily created so first access from [com.infinitii.m4td.gps.App] doesn't block. */
    val repository: AircraftLocationRepository by lazy { AircraftLocationRepository(this, context) }

    @Volatile private var started = false

    /** Idempotent — safe to call from [com.infinitii.m4td.gps.App.onCreate]. */
    fun start() {
        if (started) return
        started = true
        transition { copy(phase = SdkPhase.INITIALIZING) }

        SDKManager.getInstance().init(context.applicationContext, object : SDKManagerCallback {
            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                transition { copy(initProgress = totalProcess.coerceIn(0, 100)) }
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    transition { copy(phase = SdkPhase.REGISTERING) }
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onRegisterSuccess() {
                transition { copy(phase = SdkPhase.REGISTERED, lastError = null) }
            }

            override fun onRegisterFailure(error: IDJIError) {
                transition { copy(phase = SdkPhase.REGISTRATION_FAILED, lastError = formatError(error)) }
            }

            override fun onProductConnect(productId: Int) {
                transition {
                    copy(
                        aircraftConnected = true,
                        productId = productId,
                        phase = SdkPhase.READY,
                    )
                }
                scope.launch { repository.start() }
            }

            override fun onProductDisconnect(productId: Int) {
                transition {
                    copy(
                        aircraftConnected = false,
                        productId = null,
                        phase = if (phase == SdkPhase.READY) SdkPhase.REGISTERED else phase,
                    )
                }
                scope.launch { repository.stop() }
            }

            override fun onProductChanged(productId: Int) {}

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {}
        })
    }

    private fun formatError(error: IDJIError): String {
        val code = try { error.errorCode() } catch (_: Throwable) { "?" }
        val desc = try { error.description() } catch (_: Throwable) { "" }
        return if (desc.isNullOrBlank()) "code=$code" else "$code: $desc"
    }

    @Synchronized
    private fun transition(reduce: SdkState.() -> SdkState) {
        _state.update { reduce(it).copy(lastChangeEpochMs = System.currentTimeMillis()) }
    }

    internal fun <T> listenForKey(
        key: DJIKey<T>,
        holder: Any,
        listener: (oldValue: T?, newValue: T?) -> Unit,
    ) {
        KeyManager.getInstance().listen(key, holder) { o, n -> listener(o, n) }
    }

    internal fun cancelListen(holder: Any) {
        KeyManager.getInstance().cancelListen(holder)
    }
}
