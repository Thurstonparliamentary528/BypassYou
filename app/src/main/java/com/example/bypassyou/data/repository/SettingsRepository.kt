package com.example.bypassyou.data.repository

import android.content.Context
import android.content.SharedPreferences

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isBypassEnabled: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_BYPASS_ENABLED, value).apply()

    var targetVolumePercent: Int
        get() = prefs.getInt(KEY_TARGET_VOLUME, 100)
        set(value) = prefs.edit().putInt(KEY_TARGET_VOLUME, value.coerceIn(0, 100)).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    companion object {
        private const val PREFS_NAME = "bypass_you_settings"
        private const val KEY_BYPASS_ENABLED = "key_bypass_enabled"
        private const val KEY_TARGET_VOLUME = "key_target_volume"
        private const val KEY_THEME_MODE = "key_theme_mode"
    }
}
