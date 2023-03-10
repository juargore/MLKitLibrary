@file:Suppress("ClassName")

package com.bluetrailsoft.drowsinessmodule

import androidx.constraintlayout.widget.ConstraintLayout
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.fiveSeconds
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.twoSeconds

const val MIN_FPS = 15
const val MAX_FPS = 30

data class DetectionConfig(
    var useAdvancedMesh: Boolean = true,
    val minFps: Int? = MIN_FPS,
    val maxFps: Int? = MAX_FPS,
    var redFlagDuration: Long = fiveSeconds,
    var yellowFlagDuration: Long = twoSeconds,
    val cameraPreview: ConstraintLayout?,
    var showMesh: Boolean,
    var wheelPosition: WHEEL_POSITION = WHEEL_POSITION.LEFT
)

enum class WHEEL_POSITION {
    LEFT,
    RIGHT
}
