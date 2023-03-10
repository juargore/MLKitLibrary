package com.bluetrailsoft.drowsinessdetector.utils

import android.graphics.Color

const val channelId = "software.bluetrail.notifications"
const val channelDescription = "Renault notification"
const val renaultFolder = "/Drowsiness/"
const val sdf = "HH:mm:ss"
const val sdf2 = "yyyy_MM_dd_HH_mm_ss"

const val useAdvancedMesh = "useAdvancedMesh"
const val minFps = "minFps"
const val maxFps = "maxFps"
const val currentRedFlagDuration = "redFlagDuration"
const val currentYellowFlagDuration = "yellowFlagDuration"
const val wheelPosition = "wheelPosition"
const val mesh = "showMesh"

const val mTrue = "true"
const val video = "video"
const val meshStr = "mesh"
const val showMesh = "showMesh"
const val advanced = "advanced"
const val processor = "processor"
const val redFlagDuration = "redFlagDuration"
const val yellowFlagDuration = "yellowFlagDuration"
const val left = "Left"
const val wheelPos = "wheelPosition"

const val noData = "NO_DATA"
const val timeElapsedFormat = "%02d:%02d"
const val defaultTimeElapsedFormat = "00:00"
const val uriSchemeContent = "content"
const val extensionFileLength = 4
const val secondsOnMinute = 60
const val defaultSizeBitmap = 500
const val GB = 1024

val redColor    = Color.rgb(255,  50, 50)
val yellowColor = Color.rgb(255, 255, 50)
val greenColor  = Color.rgb(50,  255, 50)
val purpleColor = Color.rgb(255,   0,255)

@Suppress("ClassName")
enum class FLAG_COLOR(val value: String, val icon: String) {
    RED(    value = "2",    icon = "\uD83D\uDD34"),
    YELLOW( value = "1",    icon = "\uD83D\uDFE1"),
    GREEN(  value = "0",    icon = "\uD83D\uDFE2"),
    PURPLE( value = "-1",   icon = "\uD83D\uDFE3")
}
