package com.bluetrailsoft.drowsinessmodule.facedetector.models

/**
 * Class to return data from library
 * @param lux -> a measurement of 1 lux is equal to the illumination of a one metre square surface that is one metre away from a single candle.
 * @param eyesDeltaX -> used to calculate distance between eyes separately for each axis. These values are in abstract units.
 * @param eyesDeltaY -> used to calculate distance between eyes separately for each axis. These values are in abstract units.
 * */
data class FaceParameters(
    val lux: Int = 0,
    val eyesDeltaX: Float,
    val eyesDeltaY: Float,
    val headPosition: String,
)
