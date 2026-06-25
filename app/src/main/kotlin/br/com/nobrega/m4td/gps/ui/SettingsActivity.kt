package br.com.nobrega.m4td.gps.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import br.com.nobrega.m4td.gps.R
import br.com.nobrega.m4td.gps.data.CalTopoConfig
import br.com.nobrega.m4td.gps.data.SettingsStore

/**
 * CalTopo reporter settings. Backed by SharedPreferences via [SettingsStore].
 *
 * On any change the full [CalTopoConfig] is re-persisted; the live
 * [br.com.nobrega.m4td.gps.service.StreamingService] picks up the new config on its next
 * tick (the ticker reads `config` per-iteration) and on `onResume` via `reloadConfig()`.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar_settings))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private val store by lazy { SettingsStore(requireContext()) }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "caltopo_settings"
            setPreferencesFromResource(R.xml.preferences, rootKey)
            wireSummaries()
        }

        private fun wireSummaries() {
            val persist = Preference.OnPreferenceChangeListener { _, _ ->
                store.save(readForm())
                true
            }
            listOf(
                SettingsStore.KEY_BASE_URL,
                SettingsStore.KEY_CONNECT_KEY,
                SettingsStore.KEY_DEVICE_ID,
                SettingsStore.KEY_REPORT_INTERVAL_SECONDS,
                SettingsStore.KEY_SKIP_INVALID,
            ).forEach { key ->
                findPreference<Preference>(key)?.onPreferenceChangeListener = persist
            }
            updateSummaries(store.load())
        }

        private fun readForm(): CalTopoConfig {
            fun s(key: String): String =
                findPreference<EditTextPreference>(key)?.text ?: ""
            fun l(key: String, default: Long): Long =
                findPreference<EditTextPreference>(key)?.text?.toLongOrNull()?.coerceIn(
                    CalTopoConfig.MIN_INTERVAL_SECONDS, CalTopoConfig.MAX_INTERVAL_SECONDS,
                ) ?: default
            fun b(key: String, default: Boolean): Boolean =
                findPreference<SwitchPreferenceCompat>(key)?.isChecked ?: default

            val base = store.load()
            return CalTopoConfig(
                baseUrl = s(SettingsStore.KEY_BASE_URL).ifBlank { base.baseUrl },
                connectKey = s(SettingsStore.KEY_CONNECT_KEY),
                deviceId = s(SettingsStore.KEY_DEVICE_ID).ifBlank { base.deviceId },
                reportIntervalSeconds = l(SettingsStore.KEY_REPORT_INTERVAL_SECONDS, base.reportIntervalSeconds),
                skipInvalidFixes = b(SettingsStore.KEY_SKIP_INVALID, base.skipInvalidFixes),
            )
        }

        private fun updateSummaries(cfg: CalTopoConfig) {
            findPreference<EditTextPreference>(SettingsStore.KEY_BASE_URL)?.apply {
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            findPreference<EditTextPreference>(SettingsStore.KEY_CONNECT_KEY)?.apply {
                summary = if (cfg.connectKey.isBlank()) {
                    getString(R.string.settings_connect_key_summary)
                } else "******** (set)"
            }
            findPreference<EditTextPreference>(SettingsStore.KEY_DEVICE_ID)?.apply {
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
            }
            findPreference<EditTextPreference>(SettingsStore.KEY_REPORT_INTERVAL_SECONDS)?.apply {
                summary = "${cfg.reportIntervalSeconds} s · call sign: ${cfg.callSign}"
            }
        }
    }
}
