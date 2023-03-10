@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.bluetrailsoft.drowsinessmodule.facedetector.utils

import android.graphics.PointF
import android.os.SystemClock
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION.LEFT
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import java.lang.Math.PI
import java.util.*
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

class FaceFeatureUtils {

    private val lightningUtils = LightningUtils()

    // returns the current time in milliseconds
    fun currentTime() = SystemClock.elapsedRealtime()

    // given a steering wheel position, returns the correct face to be analyzed
    fun <T> getDriverFace(wheelPosition: WHEEL_POSITION, results: List<T>): T =
        if (wheelPosition == LEFT) getDriverFaceOnLeftSide(results) else getDriverFaceOnRightSide(results)

    // Iterate the faces in the list to get the one on the left side
    private fun <T> getDriverFaceOnLeftSide(results: List<T>) : T {
        return results.first { f ->
            val thisFaceX = when (f) {
                is FaceMesh -> f.boundingBox.centerX()
                is Face -> f.boundingBox.centerX()
                else -> return@first false
            }
            thisFaceX > boundingBoxCenterXLeft
        }
    }

    // Iterate the faces in the list to get the one on the right side
    private fun <T> getDriverFaceOnRightSide(results: List<T>) : T {
        return results.first { f ->
            val thisFaceX = when (f) {
                is FaceMesh -> f.boundingBox.centerX()
                is Face -> f.boundingBox.centerX()
                else -> return@first false
            }
            thisFaceX < boundingBoxCenterXRight
        }
    }

    private var RED_FLAG_LEFT_EYE_THRESHOLD = redFlagLeftEyeThreshold
    private var RED_FLAG_RIGHT_EYE_THRESHOLD = redFlagRightEyeThreshold
    private var RED_FLAG_RIGHT_MOUTH_THRESHOLD = redFlagRightMouthThreshold
    private var RED_FLAG_LEFT_MOUTH_THRESHOLD = redFlagLeftMouthThreshold

    fun adjustLimitsAccordingHeadPositionAndLux(position: String, lux: Int) {
        when (position) {
            extremeUp -> {
                // turning head up -> adjust eyes thresholds
                RED_FLAG_RIGHT_EYE_THRESHOLD = redFlagRightEyeThresholdWhenHeadUp
                RED_FLAG_LEFT_EYE_THRESHOLD = redFlagLeftEyeThresholdWhenHeadUp

                if (lightningUtils.isSceneTooDark(lux)) {
                    RED_FLAG_RIGHT_EYE_THRESHOLD += eyeCompensationForDarkScene
                    RED_FLAG_LEFT_EYE_THRESHOLD += eyeCompensationForDarkScene
                }
            }
            extremeRight -> {
                // turning head too much to right -> disable right eye detection
                RED_FLAG_RIGHT_EYE_THRESHOLD = redFlagRightEyeThresholdWhenHeadTurnedRight

                // normal thresholds for left eye
                RED_FLAG_LEFT_EYE_THRESHOLD = redFlagLeftEyeThresholdWhenHeadTurnedRight

                // turning head to right -> enable Alpha angles for mouth
                RED_FLAG_RIGHT_MOUTH_THRESHOLD = redFlagRightMouthThresholdWhenHeadTurnedRight
                RED_FLAG_LEFT_MOUTH_THRESHOLD = redFlagLeftMouthThresholdWhenHeadTurnedRight

                if (lightningUtils.isSceneTooDark(lux)) {
                    RED_FLAG_LEFT_EYE_THRESHOLD += eyeCompensationForDarkScene
                    RED_FLAG_RIGHT_MOUTH_THRESHOLD -= mouthCompensationForDarkScene
                }
            }
            extremeLeft -> {
                // turning head too much to left -> disable left eye detection
                RED_FLAG_LEFT_EYE_THRESHOLD = redFlagLeftEyeThresholdWhenHeadTurnedLeft

                // normal thresholds for right eye
                RED_FLAG_RIGHT_EYE_THRESHOLD = redFlagRightEyeThresholdWhenHeadTurnedLeft

                // turning head to left -> enable Betta angles for mouth
                RED_FLAG_LEFT_MOUTH_THRESHOLD = redFlagLeftMouthThresholdWhenHeadTurnedLeft
                RED_FLAG_RIGHT_MOUTH_THRESHOLD = redFlagRightMouthThresholdWhenHeadTurnedLeft

                if (lightningUtils.isSceneTooDark(lux)) {
                    RED_FLAG_RIGHT_EYE_THRESHOLD += eyeCompensationForDarkScene
                    RED_FLAG_LEFT_MOUTH_THRESHOLD -= mouthCompensationForDarkScene
                }
            }
            else -> {
                // front face or up or down -> restart all values
                RED_FLAG_LEFT_EYE_THRESHOLD = redFlagLeftEyeThreshold
                RED_FLAG_RIGHT_EYE_THRESHOLD = redFlagRightEyeThreshold
                RED_FLAG_RIGHT_MOUTH_THRESHOLD = redFlagRightMouthThreshold
                RED_FLAG_LEFT_MOUTH_THRESHOLD =  redFlagLeftMouthThreshold

                if (lightningUtils.isSceneTooDark(lux)) {
                    RED_FLAG_LEFT_EYE_THRESHOLD += eyeCompensationForDarkScene
                    RED_FLAG_RIGHT_EYE_THRESHOLD += eyeCompensationForDarkScene
                    RED_FLAG_RIGHT_MOUTH_THRESHOLD -= mouthCompensationForDarkScene
                    RED_FLAG_LEFT_MOUTH_THRESHOLD -= mouthCompensationForDarkScene
                }
            }
        }
    }

    fun <T> computeAllTrackingStates(headPosition: String, face: T) {
        // face is not null -> let's get the states of all feature tracking.
        val eyesState = getEyesState(face)
        val mouthState = getMouthState(face)
        val headState = getHeadState(headPosition)

        // after we get the three states, inform to FaceFeatureDetector via this callback.
        onNewStatusUpdated?.invoke(eyesState, mouthState, headState)
    }

    private fun getFallenOrNormalHead(position: String): HeadPosition {
        return when (position) {
            extremeDown, extremeUp -> HeadPosition.Fallen
            else -> HeadPosition.Front
        }
    }

    /**
     * face mesh points: https://developers.google.com/static/ml-kit/vision/face-mesh-detection/images/uv_unwrap_full.png
     * following the formula: https://miro.medium.com/max/1400/1*BlsrFzC8H6i3xv695Ja6vg.png
     */
    private fun calculateBothEyesEAR(faceMesh: FaceMesh) : Pair<Double, Double> {
        val points = faceMesh.allPoints
        val ar = distance(points[leftEyeP2].toPointF(), points[leftEyeP6].toPointF())
        val br = distance(points[leftEyeP3].toPointF(), points[leftEyeP5].toPointF())
        val cr = distance(points[leftEyeP1].toPointF(), points[leftEyeP4].toPointF())
        val rightEar = (ar + br) / (2.0 * cr)

        val al = distance(points[rightEyeP2].toPointF(), points[rightEyeP6].toPointF())
        val bl = distance(points[rightEyeP3].toPointF(), points[rightEyeP5].toPointF())
        val cl = distance(points[rightEyeP1].toPointF(), points[rightEyeP4].toPointF())
        val leftEar = (al + bl) / (2.0 * cl)

        return Pair(leftEar, rightEar)
    }

    private fun FaceMeshPoint.toPointF() = PointF(position.x, position.y)

    private fun distance(p1: PointF, p2: PointF, isSquare: Boolean = false): Double {
        val xDiff = p1.x - p2.x
        val yDiff = p1.y - p2.y
        return if (!isSquare) {
            sqrt((xDiff * xDiff).toDouble() + (yDiff * yDiff).toDouble())
        } else {
            (xDiff * xDiff + yDiff * yDiff).toDouble()
        }
    }

    private fun <T> getOpenOrCloseEyes(face: T): EyesPosition = getOpenOrCloseEyesAnyMesh(face)

    private fun <T> getOpenOrCloseEyesAnyMesh(face: T): EyesPosition {
        var rightEyeEAR: Float? = 0f
        var leftEyeEAR: Float? = 0f
        // get left and right eyes EAR value according type of mesh
        when (face) {
            is FaceMesh -> {
                val ear = calculateBothEyesEAR(face)
                leftEyeEAR = ear.first.toFloat()
                rightEyeEAR = ear.second.toFloat()
                // Adds some margin of error for the advanced mesh based on tests
                RED_FLAG_LEFT_EYE_THRESHOLD += 0.02
                RED_FLAG_RIGHT_EYE_THRESHOLD += 0.02
            }
            is Face -> {
                rightEyeEAR = face.leftEyeOpenProbability
                leftEyeEAR = face.rightEyeOpenProbability
            }
        }
        // if something went wrong getting the values -> return a close eye position
        if (rightEyeEAR == null || leftEyeEAR == null) return EyesPosition.CloseEyes
        // only if user is showing left eye correctly -> validate it
        val mustValidateLeftEye = RED_FLAG_LEFT_EYE_THRESHOLD > 0
        // only if user is showing right eye correctly -> validate it
        val mustValidateRightEye = RED_FLAG_RIGHT_EYE_THRESHOLD > 0

        return if (mustValidateLeftEye && mustValidateRightEye) {
            // best scenario: both eyes showing correctly -> validate them!
            if (((rightEyeEAR + leftEyeEAR)/2) < ((RED_FLAG_RIGHT_EYE_THRESHOLD + RED_FLAG_LEFT_EYE_THRESHOLD)/2))
                EyesPosition.CloseEyes
            else EyesPosition.OpenEyes
        } else if (mustValidateLeftEye) {
            // right eye is not showing correctly -> validate only left eye!
            if (leftEyeEAR < RED_FLAG_LEFT_EYE_THRESHOLD)
                EyesPosition.CloseEyes
            else EyesPosition.OpenEyes
        } else {
            // left eye is not showing correctly -> validate only right eye!
            if (rightEyeEAR < RED_FLAG_RIGHT_EYE_THRESHOLD)
                EyesPosition.CloseEyes
            else EyesPosition.OpenEyes
        }
    }

    private fun <T> getYawnOrNoYawnMouth(face: T): MouthPosition {
        return (face as? FaceMesh)?.let { getYawnOrNoYawnMouthForAdvancedMesh(it) }
            ?: (face as? Face)?.let { getYawnOrNoYawnMouthForBasicMesh(it) }
            ?: MouthPosition.NoYawning
    }

    private fun getYawnOrNoYawnMouthForBasicMesh(face: Face): MouthPosition {
        val topPointsLips = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val bottomPointsLips = face.getContour(FaceContour.LOWER_LIP_TOP)?.points

        if (topPointsLips != null && bottomPointsLips != null) {
            val A = topPointsLips[leftPoint]
            val B = topPointsLips[rightPoint]
            val C = bottomPointsLips[bottomCenterPoint]

            val a2 = distance(B, C, true)
            val b2 = distance(A, C, true)
            val c2 = distance(A, B, true)

            val a = sqrt(a2).toFloat()
            val b = sqrt(b2).toFloat()
            val c = sqrt(c2).toFloat()

            // From Cosine law
            var alpha = acos((b2 + c2 - a2) / (2 * b * c)).toFloat()
            var gamma = acos((a2 + b2 - c2) / (2 * a * b)).toFloat()

            // To convert radians to degrees we need to multiply the radians by 180°/π radians.
            alpha = (alpha * internalGradesOfTriangle / PI).toFloat()
            gamma = (gamma * internalGradesOfTriangle / PI).toFloat()
            val betta = internalGradesOfTriangle - (alpha + gamma)

            return if (RED_FLAG_RIGHT_MOUTH_THRESHOLD > 0) {
                if (alpha > RED_FLAG_RIGHT_MOUTH_THRESHOLD) MouthPosition.Yawning  else MouthPosition.NoYawning
            } else {
                if (betta > RED_FLAG_LEFT_MOUTH_THRESHOLD) MouthPosition.Yawning else MouthPosition.NoYawning
            }
        }
        return MouthPosition.NoYawning
    }

    private fun getYawnOrNoYawnMouthForAdvancedMesh(faceMesh: FaceMesh): MouthPosition {
        val points = faceMesh.allPoints
        val a = distance(points[mouthP2].toPointF(), points[mouthP6].toPointF())
        val b = distance(points[mouthP3].toPointF(), points[mouthP5].toPointF())
        val c = distance(points[mouthP1].toPointF(), points[mouthP4].toPointF())
        val MAR = (a + b) / (2.0 * c)
        return if (MAR > thresholdMAR) {
            MouthPosition.Yawning
        } else {
            MouthPosition.NoYawning
        }
    }

    /**
     * face mesh points: https://developers.google.com/static/ml-kit/vision/face-mesh-detection/images/uv_unwrap_full.png
     */
    private fun calculateHeadPitch(faceMesh: FaceMesh): Float {
        val noseTip = faceMesh.allPoints[noseTip].position.y
        val noseTop = faceMesh.allPoints[noseTop].position.y
        val chin = faceMesh.allPoints[chin].position.y
        return (noseTip - noseTop) / (chin - noseTip)
    }

    fun <T> getHeadPosition(face: T?) : String =
        (face as? Face)?.let { getHeadPositionForBasicMesh(it) } ?:
        (face as? FaceMesh)?.let { getHeadPositionForAdvancedMesh(it) } ?: front

    private fun getHeadPositionForBasicMesh(face: Face): String {
        val x = face.headEulerAngleX
        val y = face.headEulerAngleY
        return when {
            x < minDownAngle -> when {
                x < maxDownAngle -> extremeDown
                else -> softDown
            }
            x > minUpAngle -> when {
                x > maxUpAngle -> extremeUp
                else -> softUp
            }
            y > minLeftAngle -> when {
                y > maxLeftAngle -> extremeLeft
                else -> softLeft
            }
            y < minRightAngle -> when {
                y < maxRightAngle -> extremeRight
                else -> softRight
            }
            else -> front
        }
    }

    /**
     * face mesh points: https://developers.google.com/static/ml-kit/vision/face-mesh-detection/images/uv_unwrap_full.png
     */
    private fun getHeadPositionForAdvancedMesh(faceMesh: FaceMesh): String {
        val noseTipCenter = faceMesh.allPoints[noseTipCenter]
        val x1 = noseTipCenter.position.x.toDouble()
        val x2 = faceMesh.boundingBox.centerX().toDouble()
        val headPitch = calculateHeadPitch(faceMesh)
        val distanceX = sqrt((x2 - x1).pow(2.0)) * (x2-x1).sign
        val chin = faceMesh.allPoints[chin].position.z
        return if (headPitch > maxHeadPitchAngleDown ) {
            extremeDown
        } else if (chin < maxHeadPitchAngleUp) {
            extremeUp
        } else if (distanceX < maxLeftAngleFaceMesh) {
            extremeLeft
        } else if (distanceX > maxRightAngleFaceMesh) {
            extremeRight
        } else {
            front
        }
    }

    fun getLogsFormatted(
        eyesState: EyesState,
        mouthState: MouthState,
        headState: HeadState
    ): String {
        val eye = eyesState.javaClass.simpleName
        val mouth = mouthState.javaClass.simpleName
        val head = headState.javaClass.simpleName
        return "${camelCaseToSnakeCaseString(eye)} | ${camelCaseToSnakeCaseString(mouth)} | ${camelCaseToSnakeCaseString(head)}"
    }

    private fun camelCaseToSnakeCaseString(log: String) =
        log.replace(Regex("(?<=[a-z])([A-Z])"), "_\$1").lowercase(Locale.getDefault())

    fun getRedFlagDuration(detectionConfig: DetectionConfig) =
        if (detectionConfig.redFlagDuration != defaultLongValue) detectionConfig.redFlagDuration else fiveSeconds

    fun getYellowFlagDuration(detectionConfig: DetectionConfig) =
        if (detectionConfig.yellowFlagDuration != defaultLongValue) detectionConfig.yellowFlagDuration else twoSeconds

    /**
     * Section that determines the position of each tracking feature as enums.
     * [EyesPosition] -> handle open eyes or close eyes
     * [MouthPosition] -> handle mouth yawning or mouth no yawning
     * [HeadPosition] -> handle front head or fallen head -down or up-
     * NOT to be confused with the states of each feature (sealed classes).
     * */
    enum class EyesPosition {
        CloseEyes,
        OpenEyes
    }

    enum class MouthPosition {
        NoYawning,
        Yawning
    }

    enum class HeadPosition {
        Front,
        Fallen,
    }

    /**
     * Section that determines the states of each tracking feature as sealed classes.
     * NOT to be confused with the positions of each feature (enums).
     * */
    sealed class EyesState {
        object Normal: EyesState()
        object Drowsiness: EyesState()
        object RepeatedDrowsiness: EyesState()
        object MicroSleep: EyesState()
        object Sleep: EyesState()
        object Undetectable: EyesState()
    }

    sealed class MouthState {
        object Yawn: MouthState()
        object NoYawn: MouthState()
        object RepeatedYawn: MouthState()
        object Undetectable: MouthState()
    }

    sealed class HeadState {
        object Normal: HeadState()
        object Fall: HeadState()
        object QuickFall: HeadState()
        object RepeatedQuickFall: HeadState()
        object Undetectable: HeadState()
    }

    /**
     * Section that computes every state for given feature (eyes, mouth or head).
     * If a new state is needed, just add it on the corresponding sealed class.
     * */
    fun resetValuesOnStop() {
        timeCloseEyesWereDetected = initLong
        timeDrowsinessEyesWereDetected = initLong
        timeLastBlinkingEyesWereDetected = currentTime()
        lastEyesPosition = EyesPosition.OpenEyes

        timeOpenMouthWasDetected = initLong
        timeYawnWasDetected = initLong
        lastMouthPosition = MouthPosition.NoYawning

        timeFallHeadWasDetected = initLong
        timeQuickFallWasDetected = initLong
        lastHeadPosition = HeadPosition.Front
    }

    private var timeCloseEyesWereDetected = initLong
    private var timeDrowsinessEyesWereDetected = initLong
    private var timeLastBlinkingEyesWereDetected = currentTime()
    private var lastEyesPosition: EyesPosition = EyesPosition.OpenEyes

    private fun <T> getEyesState(face: T): EyesState {
        val eyes = getOpenOrCloseEyes(face)
        if (eyes == EyesPosition.CloseEyes && lastEyesPosition == EyesPosition.OpenEyes) {
            timeCloseEyesWereDetected = currentTime()
            // eyes state change from open to close
            lastEyesPosition = EyesPosition.CloseEyes
        } else if (eyes == EyesPosition.OpenEyes) {
            // eyes state change from close to open
            lastEyesPosition = EyesPosition.OpenEyes
            // validate if 1 minute or more has passed since the last blink
            val timeElapsedSinceLastBlink =  currentTime() - timeLastBlinkingEyesWereDetected
            if (timeElapsedSinceLastBlink >= halfMinute) {
                return EyesState.Undetectable
            }
            return EyesState.Normal
        }

        val riskElapsedMs = currentTime() - timeCloseEyesWereDetected

        return if (riskElapsedMs < halfSecond) { // < 0.5s -> normal blink
            timeLastBlinkingEyesWereDetected = currentTime()
            EyesState.Normal
        } else if (riskElapsedMs in rangeDrowsiness) { // > 0.5s && < 1s -> drowsiness
            val timeElapsedSinceLastDrowsiness = currentTime() - timeDrowsinessEyesWereDetected
            // validate if we have repeated drowsiness to notify
            if (timeElapsedSinceLastDrowsiness < oneSecond) {
                EyesState.Drowsiness
            } else {
                // > 1000 && < 60000L -> repeated drowsiness
                if (timeElapsedSinceLastDrowsiness in rangeRepeatedDrowsiness) {
                    timeDrowsinessEyesWereDetected = currentTime()
                    EyesState.RepeatedDrowsiness
                } else {
                    timeDrowsinessEyesWereDetected = currentTime()
                    EyesState.Drowsiness
                }
            }
        } else if (riskElapsedMs in rangeMicroSleep) {
            EyesState.MicroSleep
        } else {
            EyesState.Sleep
        }
    }

    private var timeOpenMouthWasDetected = initLong
    private var timeYawnWasDetected = initLong
    private var lastMouthPosition: MouthPosition = MouthPosition.NoYawning

    private fun <T> getMouthState(face: T): MouthState {
        val mouth = getYawnOrNoYawnMouth(face)
        if (mouth == MouthPosition.Yawning && lastMouthPosition == MouthPosition.NoYawning) {
            timeOpenMouthWasDetected = currentTime()
            lastMouthPosition = MouthPosition.Yawning
        } else if (mouth == MouthPosition.NoYawning) {
            // mouth state change from yawning to no yawning
            lastMouthPosition = MouthPosition.NoYawning
            return MouthState.NoYawn
        }
        
        val riskElapsedMs = currentTime() - timeOpenMouthWasDetected

        return if (riskElapsedMs < twoSeconds + halfSecond) {
            MouthState.NoYawn
        } else {
            val timeElapsedSinceLastYawn = currentTime() - timeYawnWasDetected

            if (timeElapsedSinceLastYawn <= twoSeconds + halfSecond) {
                MouthState.Yawn
            } else {
                // validate if we have repeated yawning to notify
                if (timeElapsedSinceLastYawn < oneMinute) {
                    timeYawnWasDetected = currentTime()
                    MouthState.RepeatedYawn
                } else {
                    timeYawnWasDetected = currentTime()
                    MouthState.Yawn
                }
            }
        }
    }

    private var timeFallHeadWasDetected = initLong
    private var timeQuickFallWasDetected = initLong
    private var lastHeadPosition: HeadPosition = HeadPosition.Front

    private fun getHeadState(headPosition: String): HeadState {
        val head = getFallenOrNormalHead(headPosition)
        if (head == HeadPosition.Fallen && lastHeadPosition == HeadPosition.Front) {
            timeFallHeadWasDetected = currentTime()
            lastHeadPosition = HeadPosition.Fallen
        } else if (head == HeadPosition.Front) {
            // head state change from fallen to normal
            lastHeadPosition = HeadPosition.Front
            return HeadState.Normal
        }

        val riskElapsedMs = currentTime() - timeFallHeadWasDetected

        return if (riskElapsedMs < oneSecond) {
            HeadState.Normal
        } else if (riskElapsedMs in rangeRepeatedQuickFall) {
            val timeElapsedSinceLastQuickFall = currentTime() - timeQuickFallWasDetected

            // validate if we have repeated quick fall to notify
            if (timeElapsedSinceLastQuickFall < twoSeconds) {
                HeadState.QuickFall
            } else {
                if (timeElapsedSinceLastQuickFall < oneMinute) {
                    timeQuickFallWasDetected = currentTime()
                    HeadState.RepeatedQuickFall
                } else {
                    timeQuickFallWasDetected = currentTime()
                    HeadState.QuickFall
                }
            }
        } else {
            HeadState.Fall
        }
    }

    var onNewStatusUpdated: ((EyesState, MouthState, HeadState) -> Unit)? = null
}
