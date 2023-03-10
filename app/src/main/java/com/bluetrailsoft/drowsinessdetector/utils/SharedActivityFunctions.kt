package com.bluetrailsoft.drowsinessdetector.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.BatteryManager
import android.os.Environment
import android.util.Size
import android.widget.TextView
import com.bluetrailsoft.drowsinessdetector.R
import com.bluetrailsoft.drowsinessdetector.extensions.showNotification
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class SharedActivityFunctions {

    var currentVideoName = ""
    var mustCreateNewFile = true
    private var currentFile: File? = null
    private var numberOfProcessors = 0
    private var mustContinue = true

    @SuppressLint("NewApi")
    fun setColorsOnSemaphoreAndPrintLogs(
        context: Context,
        source: Source,
        fab: FloatingActionButton,
        soundPlayer: SoundPlayer?,
        txtLog: TextView,
        drowsinessFlag: Int,
        logs: String?,
        timeVideo: Int? = null,
        fpsVideo: Int? = null,
    ) {
        if (!logs.isNullOrEmpty()) {
            val activity = context as Activity
            val flagColor = if (FLAG_COLOR.RED.value == drowsinessFlag.toString()) FLAG_COLOR.RED
            else if (FLAG_COLOR.YELLOW.value == drowsinessFlag.toString()) FLAG_COLOR.YELLOW
            else if (FLAG_COLOR.PURPLE.value == drowsinessFlag.toString()) FLAG_COLOR.PURPLE
            else FLAG_COLOR.GREEN

            showLogsOnScreen(
                context = context,
                source = source,
                timeVideo = timeVideo,
                fpsVideo = fpsVideo,
                message = activity.getString(R.string.logs_on_screen, flagColor.icon, logs),
                txtLog = txtLog
            )

            when (flagColor) {
                FLAG_COLOR.RED -> {
                    activity.showNotification()
                    fab.backgroundTintList = ColorStateList.valueOf(redColor)
                    soundPlayer?.soundOn()
                }
                FLAG_COLOR.YELLOW -> {
                    fab.backgroundTintList = ColorStateList.valueOf(yellowColor)
                    soundPlayer?.soundOff()
                }
                FLAG_COLOR.PURPLE -> {
                    fab.backgroundTintList = ColorStateList.valueOf(purpleColor)
                    soundPlayer?.soundOff()
                }
                else -> {
                    fab.backgroundTintList = ColorStateList.valueOf(greenColor)
                    soundPlayer?.soundOff()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun showLogsOnScreen(
        context: Context,
        source: Source,
        message: String,
        txtLog: TextView,
        timeVideo: Int? = null,
        fpsVideo: Int? = null
    ) {
        // print logs on device screen with traditional format
        val hour = getDateFormatted(sdf)
        txtLog.text = "$hour $message\n${txtLog.text}"
        // only if source is video let's store the formatted logs in txt file
        if (source == Source.VIDEO) {
            val newLog = "${secondsToMinutes(timeVideo)} $message"
            val batteryPercentage = getBatteryPercentage(context)
            val usageMemory = getMemoryUsage(context)
            val usageCPU = getCpuUsage()
            val usageGPU = getGpuUsage(context)
            val temperature = getDeviceTemperature()
            val fps = fpsVideo.toString()
            saveLogsOnFile("$newLog | $batteryPercentage | $usageMemory | $usageCPU | $usageGPU | $temperature | $fps")
        }
    }

    private fun getDateFormatted(SDF: String): String {
        val cTime = System.currentTimeMillis()
        val sdf = SimpleDateFormat(SDF, Locale.US)
        return sdf.format(Date(cTime))
    }

    private fun saveLogsOnFile(text: String) {
        if (currentVideoName.isNotEmpty()) {
            val date = getDateFormatted(sdf2)
            val videoName = currentVideoName
                .replace("-", "_")
                .replace(" ", "_")
                .dropLast(extensionFileLength)
            val filePath = Environment.getExternalStorageDirectory().toString() + renaultFolder
            val mFile = File(filePath)
            if (!mFile.exists() || !mFile.isDirectory) {
                mFile.mkdir()
            }
            if (mustCreateNewFile) {
                mustCreateNewFile = false
                val fileName = "${videoName}_${date}.txt"
                currentFile = File(filePath, fileName)
            }
            currentFile?.appendText(text + "\n")
        }
    }

    private fun secondsToMinutes(seconds: Int?): String {
        if (seconds == null) return defaultTimeElapsedFormat
        val minutes = seconds / secondsOnMinute
        val remainingSeconds = seconds % secondsOnMinute
        return String.format(timeElapsedFormat, minutes, remainingSeconds)
    }

    private fun getBatteryPercentage(context: Context): String {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, iFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level / scale.toFloat()
        return "${(batteryPct * 100).toInt()}%"
    }

    private fun getMemoryUsage(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return "${(memoryInfo.availMem / (GB * GB)).toInt()} mb"
    }

    private fun getGpuUsage(context: Context): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val totalGpuMemory = memoryInfo.totalMem
        val usedGpuMemory = memoryInfo.totalMem - memoryInfo.availMem
        return "${(usedGpuMemory * 100 / totalGpuMemory).toInt()}%"
    }

    private fun getCpuUsage(): String {
        // first get the total number of processors using adb command
        if (mustContinue) {
            val process = Runtime.getRuntime().exec("/system/bin/cat /proc/cpuinfo")
            val br = BufferedReader(InputStreamReader(process.inputStream))
            br.forEachLine {
                if (it.contains(processor)) {
                    numberOfProcessors++
                }
            }
            mustContinue = false
        }
        // now get the total frequency of each processor and make an average
        var total = 0L
        for (i in 0 until numberOfProcessors) {
            val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (file.exists()) {
                try {
                    file.bufferedReader().forEachLine {
                        total += it.toLong()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        // finally iterate each processor and get sum the totalHertz and get average speed
        var totalHertz = 0L
        for (i in 0 until numberOfProcessors) {
            val file = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            if (file.exists()) {
                try {
                    file.bufferedReader().forEachLine {
                        totalHertz += it.toLong()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        // return the percentage of CPU used on specific moment
        return "${((100 * totalHertz) / total).toInt()}%"
    }

    private fun getDeviceTemperature(): String {
        return noData
    }

    fun getWheelPosition(position: String): WHEEL_POSITION {
        return if (position == WHEEL_POSITION.LEFT.name || position.isEmpty())
            WHEEL_POSITION.LEFT else WHEEL_POSITION.RIGHT
    }

    fun getSizeForDesiredSize(width: Int, height: Int, desiredSize: Int = defaultSizeBitmap): Size {
        val w: Int
        val h: Int
        if (width > height) {
            w = desiredSize
            h = (height / width.toFloat() * w).roundToInt()
        } else {
            h = desiredSize
            w = (width / height.toFloat() * h).roundToInt()
        }
        return Size(w, h)
    }

    enum class Source {
        CAMERA,
        VIDEO
    }
}
