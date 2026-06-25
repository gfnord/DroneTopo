package br.com.nobrega.m4td.gps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import br.com.nobrega.m4td.gps.App
import br.com.nobrega.m4td.gps.R
import br.com.nobrega.m4td.gps.data.CalTopoConfig
import br.com.nobrega.m4td.gps.data.LocationFix
import br.com.nobrega.m4td.gps.data.SettingsStore
import br.com.nobrega.m4td.gps.sdk.AircraftLocationRepository
import br.com.nobrega.m4td.gps.ui.MainActivity
import br.com.nobrega.m4td.gps.upload.CalTopoPositionReporter
import br.com.nobrega.m4td.gps.upload.ReportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Foreground service that pipes the aircraft GPS stream into the CalTopo reporter.
 *
 * Latest-wins (no queue): a SAR map must not show stale drone positions, so each interval
 * tick sends the most recent valid fix; failed sends are dropped (the next tick sends the
 * then-current position rather than backfilling).
 */
class StreamingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: AircraftLocationRepository
    private lateinit var settings: SettingsStore

    @Volatile private var config: CalTopoConfig = CalTopoConfig(
        baseUrl = br.com.nobrega.m4td.gps.BuildConfig.DEFAULT_CALTOPO_BASE_URL,
        connectKey = br.com.nobrega.m4td.gps.BuildConfig.DEFAULT_CALTOPO_CONNECT_KEY,
        deviceId = br.com.nobrega.m4td.gps.BuildConfig.DEFAULT_DEVICE_ID,
        reportIntervalSeconds = br.com.nobrega.m4td.gps.BuildConfig.DEFAULT_CALTOPO_REPORT_INTERVAL_SECONDS.toLong(),
        skipInvalidFixes = br.com.nobrega.m4td.gps.BuildConfig.DEFAULT_SKIP_INVALID_FIXES,
    )
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    @Volatile private var latestFix: LocationFix? = null
    private var collectJob: Job? = null
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(ReportState(activeConfig = config))
    val state: StateFlow<ReportState> = _state.asStateFlow()

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { val service: StreamingService get() = this@StreamingService }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val app = application as App
        repository = app.sdkManager.repository
        settings = SettingsStore(applicationContext)
        config = settings.load()
        _state.update { it.copy(activeConfig = config) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
        }
        startForegroundSafely(buildNotificationText(getString(R.string.notif_text_idle)))
        startStreaming()
        return START_STICKY
    }

    fun reloadConfig() {
        val newCfg = settings.load()
        val intervalChanged = newCfg.reportIntervalSeconds != config.reportIntervalSeconds
        config = newCfg
        _state.update { it.copy(activeConfig = config) }
        repository.refreshConfig()
        if (intervalChanged && tickerJob != null) startTicker()
    }

    private fun startStreaming() {
        if (tickerJob != null) return
        collectJob = scope.launch {
            repository.latestFix.collect { fix -> latestFix = fix }
        }
        startTicker()
        _state.update { it.copy(running = true) }
        scope.launch {
            _state.collect { st -> updateNotification(st) }
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(config.reportIntervalSeconds * 1000L)
                tickOnce()
            }
        }
    }

    private fun staleThresholdMs(cfg: CalTopoConfig): Long =
        maxOf(cfg.reportIntervalSeconds * 3L, 10L) * 1000L

    private suspend fun tickOnce() {
        val cfg = config
        if (cfg.connectKey.isBlank() || cfg.deviceId.isBlank()) return
        val fix = latestFix ?: return
        if (cfg.skipInvalidFixes && !fix.isValid) return

        val ageMs = runCatching {
            Duration.between(Instant.parse(fix.timestamp), Instant.now()).toMillis()
        }.getOrDefault(0L)
        if (ageMs > staleThresholdMs(cfg)) {
            _state.update { it.copy(fixStale = true) }
            return
        }

        val reporter = CalTopoPositionReporter(client, cfg)
        val result = withContext(Dispatchers.IO) { reporter.report(fix) }
        result.fold(
            onSuccess = { code ->
                _state.update {
                    it.copy(
                        lastReportEpochMs = System.currentTimeMillis(),
                        lastHttpStatus = code,
                        successCount = it.successCount + 1,
                        consecutiveFailures = 0,
                        lastError = null,
                        lastReportedFix = fix,
                        fixStale = false,
                    )
                }
            },
            onFailure = { e ->
                _state.update {
                    it.copy(
                        lastHttpStatus = null,
                        failureCount = it.failureCount + 1,
                        consecutiveFailures = it.consecutiveFailures + 1,
                        lastError = e.message ?: e.javaClass.simpleName,
                        fixStale = false,
                    )
                }
            },
        )
    }

    private fun stopStreaming() {
        collectJob?.cancel(); collectJob = null
        tickerJob?.cancel(); tickerJob = null
        latestFix = null
        _state.update { it.copy(running = false, fixStale = false) }
    }

    override fun onDestroy() {
        stopStreaming()
        scope.cancel()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                getString(R.string.notif_channel_id),
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun startForegroundSafely(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(state: ReportState) {
        val text = when {
            state.configIncomplete -> getString(R.string.notif_text_no_config)
            state.fixStale -> getString(R.string.notif_text_stale)
            state.consecutiveFailures > 0 && state.lastError != null ->
                getString(R.string.notif_text_fail, state.lastError)
            state.lastHttpStatus != null && state.successCount > 0 ->
                getString(R.string.notif_text_active, state.callSign)
            else -> getString(R.string.notif_text_idle)
        }
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            .notify(NOTIF_ID, buildNotificationText(text))
    }

    private fun buildNotificationText(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, StreamingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.btn_stop_streaming), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "br.com.nobrega.m4td.gps.action.START"
        const val ACTION_STOP = "br.com.nobrega.m4td.gps.action.STOP"
        private const val NOTIF_ID = 0xC0DE
    }
}
