package ru.sash0k.bluetooth_terminal.activity

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.preference.EditTextPreference
import android.preference.ListPreference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.view.MenuItem
import ru.sash0k.bluetooth_terminal.R
import ru.sash0k.bluetooth_terminal.Utils

/**
 * Created by sash0k on 29.11.13.
 * Настройки приложения
 */
@Suppress("deprecation")
class SettingsActivity : PreferenceActivity(), OnSharedPreferenceChangeListener {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings_activity)
        val bar = actionBar
        bar!!.setHomeButtonEnabled(true)
        bar.setDisplayHomeAsUpEnabled(true)
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.registerOnSharedPreferenceChangeListener(this)
        setPreferenceTitle(getString(R.string.pref_commands_mode))
        setPreferenceTitle(getString(R.string.pref_checksum_mode))
        setPreferenceTitle(getString(R.string.pref_commands_ending))
        setPreferenceTitle(getString(R.string.pref_log_limit))
        setPreferenceTitle(getString(R.string.pref_log_limit_size))
    }

    // ============================================================================
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return when (id) {
            android.R.id.home -> {
                finish()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // ============================================================================
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, value: String) {
        setPreferenceTitle(value)
    }
    // ============================================================================
    /**
     * Установка заголовка списка
     */
    private fun setPreferenceTitle(TAG: String) {
        val preference = findPreference(TAG) ?: return
        if (getString(R.string.pref_log_limit) == preference.key) {
            val isEnabled = (preference as CheckBoxPreference).isChecked
            val limitSize = findPreference(getString(R.string.pref_log_limit_size))
            if (limitSize != null) limitSize.isEnabled = isEnabled
        }
        if (preference is ListPreference) {
            if (preference.entry == null) return
            val title = preference.entry.toString()
            preference.setTitle(title)
        } else if (preference is EditTextPreference) {
            val text = preference.text
            preference.setSummary(Integer.toString(Utils.formatNumber(text)))
        }
    } // ============================================================================
}