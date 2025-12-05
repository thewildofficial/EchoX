package com.echox.app.domain

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoPostEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_POST, DEFAULT_AUTO_POST)
        set(value) = sharedPreferences.edit().putBoolean(KEY_AUTO_POST, value).apply()

    var videoQuality: VideoQuality
        get() {
            val qualityString =
                    sharedPreferences.getString(KEY_VIDEO_QUALITY, DEFAULT_VIDEO_QUALITY.name)
            return VideoQuality.valueOf(qualityString ?: DEFAULT_VIDEO_QUALITY.name)
        }
        set(value) = sharedPreferences.edit().putString(KEY_VIDEO_QUALITY, value.name).apply()

    companion object {
        private const val PREFS_NAME = "echox_app_preferences"
        private const val KEY_AUTO_POST = "auto_post_enabled"
        private const val KEY_VIDEO_QUALITY = "video_quality"
        private val DEFAULT_AUTO_POST = false
        private val DEFAULT_VIDEO_QUALITY = VideoQuality.P720
    }
}

enum class VideoQuality {
    P720,
    P1080
}
