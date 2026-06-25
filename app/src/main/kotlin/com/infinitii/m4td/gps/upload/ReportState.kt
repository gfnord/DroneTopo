package com.infinitii.m4td.gps.upload

import com.infinitii.m4td.gps.data.CalTopoConfig
import com.infinitii.m4td.gps.data.LocationFix

/**
 * Snapshot of CalTopo reporter state for the UI. Mirrors the latest-wins semantics: there
 * is no queue — only the most recent report outcome matters.
 */
data class ReportState(
    val running: Boolean = false,
    val activeConfig: CalTopoConfig,
    val lastReportEpochMs: Long? = null,
    val lastHttpStatus: Int? = null,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastError: String? = null,
    val lastReportedFix: LocationFix? = null,
    val fixStale: Boolean = false,
) {
    val callSign: String get() = activeConfig.callSign
    val configIncomplete: Boolean
        get() = activeConfig.connectKey.isBlank() || activeConfig.deviceId.isBlank()
}
