package br.com.nobrega.m4td.gps.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.nobrega.m4td.gps.App
import br.com.nobrega.m4td.gps.data.LocationFix
import br.com.nobrega.m4td.gps.domain.SdkState
import br.com.nobrega.m4td.gps.upload.ReportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Exposes SDK, location, and CalTopo reporter state to [MainActivity].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val sdkManager = getApplication<App>().sdkManager

    val sdkState: StateFlow<SdkState> = sdkManager.state
    val latestFix: StateFlow<LocationFix?> = sdkManager.repository.latestFix

    private val _reportState = MutableStateFlow<ReportState?>(null)
    val reportState: StateFlow<ReportState?> = _reportState.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    fun bindReportState(service: br.com.nobrega.m4td.gps.service.StreamingService) {
        _streaming.value = true
        viewModelScope.launch {
            service.state.collectLatest { _reportState.value = it }
        }
    }

    fun onServiceUnbound() {
        _streaming.value = false
        _reportState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        onServiceUnbound()
    }
}
