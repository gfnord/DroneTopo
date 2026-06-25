package com.infinitii.m4td.gps.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infinitii.m4td.gps.R
import com.infinitii.m4td.gps.data.LocationFix
import com.infinitii.m4td.gps.databinding.ActivityMainBinding
import com.infinitii.m4td.gps.domain.SdkPhase
import com.infinitii.m4td.gps.domain.SdkState
import com.infinitii.m4td.gps.service.StreamingService
import com.infinitii.m4td.gps.upload.ReportState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    private var service: StreamingService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = (binder as? StreamingService.LocalBinder)?.service ?: return
            service = s
            vm.bindReportState(s)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            vm.onServiceUnbound()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.btnToggle.setOnClickListener { toggleStreaming() }

        observeSdk()
        observeLocation()
        observeReport()
    }

    override fun onResume() {
        super.onResume()
        service?.reloadConfig()
    }

    private fun toggleStreaming() {
        if (service != null) {
            unbindService(connection)
            service = null
            vm.onServiceUnbound()
            val stop = Intent(this, StreamingService::class.java).setAction(StreamingService.ACTION_STOP)
            startService(stop)
            refreshToggle()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
            }
            val intent = Intent(this, StreamingService::class.java).setAction(StreamingService.ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            bindService(Intent(this, StreamingService::class.java), connection, Context.BIND_AUTO_CREATE)
            refreshToggle()
        }
    }

    private fun refreshToggle() {
        binding.btnToggle.setText(
            if (service != null) R.string.btn_stop_streaming else R.string.btn_start_streaming
        )
    }

    private fun observeSdk() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.sdkState.collect { st -> renderSdk(st) }
            }
        }
    }

    private fun observeLocation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.latestFix.collect { fix -> renderLocation(fix) }
            }
        }
    }

    private fun observeReport() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.reportState.collect { st -> renderReport(st) }
            }
        }
    }

    private fun renderSdk(st: SdkState) {
        binding.tvSdkPhase.text = when (st.phase) {
            SdkPhase.IDLE -> "Idle"
            SdkPhase.INITIALIZING -> "Initializing… (${st.initProgress}%)"
            SdkPhase.REGISTERING -> "Registering…"
            SdkPhase.REGISTERED -> "Registered (no aircraft)"
            SdkPhase.REGISTRATION_FAILED -> "Registration failed"
            SdkPhase.READY -> "Ready"
        }
        binding.tvSdkError.apply {
            text = st.lastError
            visibility = if (st.lastError.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        binding.tvConnection.text = when {
            st.aircraftConnected && st.modelName != null ->
                "Connected (productId=${st.productId}, ${st.modelName})"
            st.aircraftConnected -> "Connected (productId=${st.productId})"
            else -> getString(R.string.empty_no_connection)
        }
        refreshToggle()
    }

    private fun renderLocation(fix: LocationFix?) {
        binding.tvLocation.text = if (fix == null) {
            getString(R.string.empty_no_fix)
        } else {
            val sats = fix.satelliteCount?.toString() ?: "?"
            val sig = fix.gpsSignalLevel ?: "?"
            "lat=${formatCoord(fix.lat)}, lon=${formatCoord(fix.lon)}  alt=${"%.1f".format(fix.altitudeMeters)}m (${fix.altitudeRef.wireName})  sats=$sats  sig=$sig"
        }
    }

    private fun renderReport(st: ReportState?) {
        if (st == null) {
            binding.tvCallSign.text = "—"
            binding.tvReport.text = "—"
            binding.tvReportError.visibility = View.GONE
            return
        }
        binding.tvCallSign.text = if (st.configIncomplete) {
            getString(R.string.report_no_config)
        } else {
            st.callSign
        }
        val lastTime = st.lastReportEpochMs?.let { ISO.format(Date(it)) } ?: "—"
        binding.tvReport.text = buildString {
            append("HTTP ${st.lastHttpStatus ?: "—"}")
            append(" · ok=${st.successCount} fail=${st.failureCount}")
            append(" · last=$lastTime")
        }
        binding.tvReportError.apply {
            text = if (st.consecutiveFailures > 0 && st.lastError != null) {
                "(${st.consecutiveFailures}x) ${st.lastError}"
            } else null
            visibility = if (text.isNullOrBlank()) View.GONE else View.VISIBLE
        }
    }

    private fun formatCoord(v: Double): String = "%.5f".format(v)

    override fun onDestroy() {
        super.onDestroy()
        if (service != null) {
            try { unbindService(connection) } catch (_: Exception) {}
            service = null
        }
    }

    companion object {
        private const val REQ_NOTIF = 0xA1
        private val ISO: SimpleDateFormat = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
