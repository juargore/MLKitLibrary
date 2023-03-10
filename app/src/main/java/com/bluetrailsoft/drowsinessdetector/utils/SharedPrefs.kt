package com.bluetrailsoft.drowsinessdetector.utils

import android.content.Context

class SharedPrefs(context: Context) {

    private val sharedPref = context.getSharedPreferences(DACIA_PREFERENCES, Context.MODE_PRIVATE)

    fun saveUseAdvancedMesh(mesh: Boolean) = put(ADVANCED_MESH, mesh)
    fun getUseAdvancedMesh() = sharedPref.getBoolean(ADVANCED_MESH, true)

    fun saveShowMesh(mesh: Boolean) = put(MESH, mesh)
    fun getShowMesh() = get(MESH, Boolean::class.java)

    fun saveSoundEnabled(sound: Boolean) = put(SOUND, sound)
    fun getSoundEnabled() = get(SOUND, Boolean::class.java)

    fun saveAsService(asService: Boolean) = put(SERVICE, asService)
    fun getAsService() = get(SERVICE, Boolean::class.java)

    fun saveMinFPS(fps: Int) = put(MIN_FPS, fps)
    fun getMinFPS() = get(MIN_FPS, Int::class.java)

    fun saveMaxFPS(fps: Int) = put(MAX_FPS, fps)
    fun getMaxFPS() = get(MAX_FPS, Int::class.java)

    fun saveRedFlagDuration(duration: Long) = put(RED_FLAG_DURATION, duration)
    fun getRedFlagDuration() = get(RED_FLAG_DURATION, Long::class.java)

    fun saveYellowFlagDuration(duration: Long) = put(YELLOW_FLAG_DURATION, duration)
    fun getYellowFlagDuration() = get(YELLOW_FLAG_DURATION, Long::class.java)

    fun saveWheelPosition(position: String) = put(WHEEL_POSITION, position)
    fun getWheelPosition() = get(WHEEL_POSITION, String::class.java)

    fun saveCheckingSettings(checking: Boolean) = put(CHECKING_SETTINGS, checking)
    fun getCheckingSettings() = get(CHECKING_SETTINGS, Boolean::class.java)

    fun resetMinMaxFps() {
        put(MIN_FPS, -1)
        put(MAX_FPS, -1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> get(key: String, clazz: Class<T>): T =
        when (clazz) {
            String::class.java -> sharedPref.getString(key, "")
            Boolean::class.java -> sharedPref.getBoolean(key, false)
            Float::class.java -> sharedPref.getFloat(key, -1f)
            Double::class.java -> sharedPref.getFloat(key, -1f)
            Int::class.java -> sharedPref.getInt(key, -1)
            Long::class.java -> sharedPref.getLong(key, -1L)
            else -> null
        } as T

    private fun <T> put(key: String, data: T) {
        val editor = sharedPref.edit()
        when (data) {
            is String -> editor.putString(key, data)
            is Boolean -> editor.putBoolean(key, data)
            is Float -> editor.putFloat(key, data)
            is Double -> editor.putFloat(key, data.toFloat())
            is Int -> editor.putInt(key, data)
            is Long -> editor.putLong(key, data)
        }
        editor.apply()
    }

    companion object {
        private const val DACIA_PREFERENCES = "DaciaPreferences"
        private const val ADVANCED_MESH = "advanced_mesh"
        private const val MESH = "mesh"
        private const val SOUND = "sound"
        private const val SERVICE = "service"
        private const val MIN_FPS = "min_fps"
        private const val MAX_FPS = "max_fps"
        private const val RED_FLAG_DURATION = "redFlagDuration"
        private const val YELLOW_FLAG_DURATION = "yellowFlagDuration"
        private const val WHEEL_POSITION = "wheelPosition"
        private const val CHECKING_SETTINGS = "isCheckingSettings"
    }
}
