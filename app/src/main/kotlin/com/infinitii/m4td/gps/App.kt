package com.infinitii.m4td.gps

import android.app.Application
import android.content.Context

/**
 * Application entrypoint.
 *
 * DJI MSDK V5 requires `com.cySdkyc.clx.Helper.install(this)` to be called in
 * `attachBaseContext()` BEFORE any other SDK call. See:
 *   https://developer.dji.com/doc/mobile-sdk-tutorial/en/quick-start/user-project-caution.html
 *
 * This class also owns the long-lived [DjiSdkManager] singleton so the SDK callback
 * never holds a strong reference to an Activity/Fragment (memory-leak warning in the
 * official ISDKManager docs).
 */
class App : Application() {

    // Initialized lazily on first access (typically from MainActivity / StreamingService).
    val sdkManager: com.infinitii.m4td.gps.sdk.DjiSdkManager by lazy {
        com.infinitii.m4td.gps.sdk.DjiSdkManager(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        // REQUIRED before any SDK use in V5. Must happen in attachBaseContext.
        com.cySdkyc.clx.Helper.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // Kick off SDK init immediately so registration can proceed before the user
        // opens the main screen (registration needs internet on first run).
        sdkManager.start()
    }

    companion object {
        @JvmStatic
        fun get(app: Application): App = app as App
    }
}
