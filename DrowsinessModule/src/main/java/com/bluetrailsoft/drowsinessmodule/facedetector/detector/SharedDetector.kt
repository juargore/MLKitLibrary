package com.bluetrailsoft.drowsinessmodule.facedetector.detector

import android.os.Handler
import android.os.Looper
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.FaceFeatureUtils
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.FaceFeatureUtils.*
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.greenFlag
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.initLong
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.oneMinute
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.redFlag
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.undetectableFlag
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.yellowFlag

class SharedDetector(
    private val detectionConfig: DetectionConfig,
    private val onFlagUpdated: ((Int, String?) -> Unit)?,
) {
    private var mHandler = Handler(Looper.getMainLooper())
    private val utils: FaceFeatureUtils = FaceFeatureUtils()
    private var trackingFlag = undetectableFlag
    private var lastFlagProcessed = undetectableFlag
    private var timeRepeatedYawnWasDetected = initLong
    private var timeRepeatedQuickFallWasDetected = initLong
    private var mustPrintNextStateDueToSimpleLog = false
    private var isTrackingRisk = false

    fun <T> adjustThresholdsAccordingHeadPosition(face: T, headPosition: String, lux: Int) {
        // update thresholds according given head position
        utils.adjustLimitsAccordingHeadPositionAndLux(headPosition, lux)

        // all set! calculate the states for each tracking state
        utils.computeAllTrackingStates(headPosition, face)

        // receive here all the states for each feature
        utils.onNewStatusUpdated = { eyes, mouth, head ->
            // send the three states to be normalized according matrix
            normalizeDataAccordingMatrix(eyes, mouth, head)
        }
    }

    fun triggerUndetectableFace() {
        normalizeDataAccordingMatrix(
            EyesState.Undetectable,
            MouthState.Undetectable,
            HeadState.Undetectable
        )
    }

    private fun normalizeDataAccordingMatrix(
        eyesState: EyesState,
        mouthState: MouthState,
        headState: HeadState
    ) {
        // determine if repeated_yawn has been repeated within last minute
        if (mouthState == MouthState.RepeatedYawn) { timeRepeatedYawnWasDetected = utils.currentTime() }
        val timeElapsedSinceLastRepeatedYawn = utils.currentTime() - timeRepeatedYawnWasDetected
        val hasRepeatedYawnWithinLastMinute = timeElapsedSinceLastRepeatedYawn <= oneMinute

        // determine if repeated_quick_fall has been repeated within last minute
        if (headState == HeadState.RepeatedQuickFall) { timeRepeatedQuickFallWasDetected = utils.currentTime() }
        val timeElapsedSinceLastRepeatedQuickFall = utils.currentTime() - timeRepeatedQuickFallWasDetected
        val hasRepeatedQuickFallWithinLastMinute = timeElapsedSinceLastRepeatedQuickFall <= oneMinute

        // get logs formatted such as: normal | repeated_yawn | quick_fall
        val logs = if (
            (hasRepeatedYawnWithinLastMinute && headState == HeadState.RepeatedQuickFall) ||
            (hasRepeatedQuickFallWithinLastMinute && mouthState == MouthState.RepeatedYawn)
        ) {
            // special cases: interval for repeated_yawn or repeated_quick_fall still open -> hardcode logs so they make sense
            utils.getLogsFormatted(eyesState, MouthState.RepeatedYawn, HeadState.RepeatedQuickFall)
        } else if (
            (hasRepeatedYawnWithinLastMinute && eyesState == EyesState.Drowsiness && (headState == HeadState.Normal || headState == HeadState.QuickFall))
        ) {
            utils.getLogsFormatted(EyesState.Drowsiness, MouthState.RepeatedYawn, headState)
        } else if (
            (hasRepeatedQuickFallWithinLastMinute && eyesState == EyesState.Drowsiness && (headState == HeadState.Normal || headState == HeadState.QuickFall))
        ) {
            utils.getLogsFormatted(EyesState.Drowsiness, mouthState, HeadState.RepeatedQuickFall)
        } else {
            // no special case detected -> print the given state normally
            utils.getLogsFormatted(eyesState, mouthState, headState)
        }

        // evaluate the states as input and get a flag (Int) as output
        val flag = if (
            (headState == HeadState.Undetectable) ||
            (eyesState == EyesState.Undetectable && mouthState == MouthState.Undetectable && headState != HeadState.Undetectable)
        ) {
            undetectableFlag
        } else if (
            (headState == HeadState.Fall) ||
            (eyesState == EyesState.MicroSleep || eyesState == EyesState.Sleep || eyesState == EyesState.RepeatedDrowsiness) ||
            (eyesState == EyesState.Drowsiness && headState == HeadState.RepeatedQuickFall) ||
            ((eyesState == EyesState.Normal || eyesState == EyesState.Undetectable) && headState == HeadState.RepeatedQuickFall && mouthState == MouthState.RepeatedYawn) ||
            (hasRepeatedYawnWithinLastMinute && headState == HeadState.RepeatedQuickFall) ||
            (hasRepeatedQuickFallWithinLastMinute && mouthState == MouthState.RepeatedYawn) ||
            (hasRepeatedYawnWithinLastMinute && eyesState == EyesState.Drowsiness && (headState == HeadState.Normal || headState == HeadState.QuickFall)) ||
            (hasRepeatedQuickFallWithinLastMinute && eyesState == EyesState.Drowsiness && (headState == HeadState.Normal || headState == HeadState.QuickFall))
        ) {
            redFlag
        } else if (
            (eyesState == EyesState.Drowsiness && (headState == HeadState.Normal || headState == HeadState.QuickFall) && (mouthState == MouthState.Yawn || mouthState == MouthState.NoYawn || mouthState == MouthState.Undetectable)) ||
            ((eyesState == EyesState.Normal || eyesState == EyesState.Undetectable) && headState == HeadState.RepeatedQuickFall && (mouthState == MouthState.Yawn || mouthState == MouthState.NoYawn)) ||
            (eyesState == EyesState.Normal && headState == HeadState.RepeatedQuickFall && (mouthState == MouthState.Yawn || mouthState == MouthState.NoYawn || mouthState == MouthState.Undetectable)) ||
            ((eyesState == EyesState.Normal || eyesState == EyesState.Undetectable) && (headState == HeadState.Normal || headState == HeadState.QuickFall) && mouthState == MouthState.RepeatedYawn)
        ) {
            yellowFlag
        } else {
            greenFlag
        }
        notifyDriverIfNeeded(flag, logs)
    }

    fun notifyDriverIfNeeded(flag: Int, logs: String) {
        val simpleYawnWasDetected = logs.contains("| yawn |")
        val simpleQuickFallWasDetected = logs.contains("| quick_fall")
        val undetectableEyesWereDetected = logs.contains("undetectable |")

        val stateChanged = if (lastFlagProcessed == flag) {
            // repeated flag -> true only if the flag is red or yellow
            (flag == redFlag || flag == yellowFlag ||
                    simpleYawnWasDetected || simpleQuickFallWasDetected || undetectableEyesWereDetected)
        } else {
            // new flag is different -> it can be processed!
            true
        }
        if (stateChanged || mustPrintNextStateDueToSimpleLog) {
            mustPrintNextStateDueToSimpleLog = false
            // red flag spotted within tracking of the yellow, take the red one
            if (isTrackingRisk && trackingFlag <= flag) {
                trackingFlag = flag
                isTrackingRisk = false // let this condition happen
            }
            if (!isTrackingRisk) {
                isTrackingRisk = true
                trackingFlag = flag
                onFlagUpdated?.invoke(trackingFlag, logs)
                updateDelaysAccordingFlags()

                if (simpleYawnWasDetected || simpleQuickFallWasDetected || undetectableEyesWereDetected) {
                    mustPrintNextStateDueToSimpleLog = true
                }
            }
        }
    }

    private fun updateDelaysAccordingFlags() {
        when (trackingFlag) {
            redFlag -> restartCountDown(utils.getRedFlagDuration(detectionConfig))
            yellowFlag -> startCountDown(utils.getYellowFlagDuration(detectionConfig))
            else -> { // green or purple
                isTrackingRisk = false
                lastFlagProcessed = trackingFlag
                stopCountDown()
            }
        }
    }

    private var mRunnable = Runnable {
        isTrackingRisk = false
        lastFlagProcessed = trackingFlag
        stopCountDown()
    }

    private fun startCountDown(time: Long) {
        mHandler.postDelayed(mRunnable, time)
    }

    private fun stopCountDown() {
        mHandler.removeCallbacks(mRunnable)
    }

    private fun restartCountDown(time: Long) {
        stopCountDown()
        startCountDown(time)
    }
}
