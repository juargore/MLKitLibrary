package com.bluetrailsoft.drowsinessmodule.facedetector.models

/**
 * Class to return data from library
 * @param flag -> -1 UNDETECTABLE_FLAG | 0 NO_FLAG | 1 YELLOW_FLAG | 2 RED_FLAG.
 * @param stateLogs -> Strings that helps to understand the flag.
 * */
data class FlagResponse(
    val flag: Int,
    val stateLogs: String?
)
