package com.example.scheduledradio

import android.content.Context

class RadioPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("radio_prefs", Context.MODE_PRIVATE)

    var streamUrl: String
        get() = prefs.getString("stream_url", "https://stream.live.vc.bbcmedia.co.uk/bbc_world_service") ?: ""
        set(value) = prefs.edit().putString("stream_url", value.trim()).apply()

    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean("schedule_enabled", true)
        set(value) = prefs.edit().putBoolean("schedule_enabled", value).apply()

    var volumeLevel: Float
        get() = prefs.getFloat("volume_level", 0.8f) // Default 80%
        set(value) = prefs.edit().putFloat("volume_level", value.coerceIn(0f, 1f)).apply()

    var isMuted: Boolean
        get() = prefs.getBoolean("is_muted", false)
        set(value) = prefs.edit().putBoolean("is_muted", value).apply()
}
