package com.bluetrailsoft.drowsinessdetector.utils

import android.content.Context
import android.media.SoundPool

/**
 * Sound player, saving drowsi peoples' lives since 2022
 * */
@Suppress("PrivatePropertyName")
class SoundPlayer(context: Context, sound: Int) {
    private var soundPool: SoundPool? = null
    private val SOUND_ID = 1
    private val MAX_STREAMS = 6
    private val LOAD_PRIORITY = 1
    private val PLAY_PRIORITY = 0
    private val VOLUME = 1F
    private val LOOP = 2
    private val RATE = 1F

    init {
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .build()
        soundPool!!.load(context, sound, LOAD_PRIORITY)
    }

    fun soundOn() {
        soundPool?.play(SOUND_ID, VOLUME, VOLUME, PLAY_PRIORITY, LOOP, RATE)
    }

    fun soundOff() {
        soundPool?.stop(SOUND_ID)
    }
}
