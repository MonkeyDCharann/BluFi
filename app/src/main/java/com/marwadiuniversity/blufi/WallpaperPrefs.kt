package com.example.blufi

import android.content.Context
import androidx.core.content.ContextCompat

object WallpaperPrefs {
    private const val PREFS_NAME = "chat_prefs"
    private const val KEY_WALLPAPER_COLOR = "wallpaper_color_res"

    fun saveWallpaperColor(context: Context, colorResId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WALLPAPER_COLOR, colorResId).apply()
    }

    fun getWallpaperColor(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Return 0 if no color is saved
        return prefs.getInt(KEY_WALLPAPER_COLOR, 0)
    }
}