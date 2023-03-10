package com.bluetrailsoft.drowsinessmodule.facedetector.utils

// drowsiness constants flags
const val undetectableFlag = -1
const val greenFlag = 0
const val yellowFlag = 1
const val redFlag = 2

// general constants as strings
const val tag = "FaceDetectorProcessor"
const val mlContextAlreadyInit = "MlKitContext is already initialized"

// general constants as milliseconds
const val halfSecond = 500L
const val oneSecond = 999L
const val twoSeconds = 2000L
const val fiveSeconds = 5000L
const val halfMinute = 30000L
const val oneMinute = 60000L
const val defaultLongValue = -1L
const val initLong = 0L
const val internalGradesOfTriangle = 180
const val boundingBoxCenterXRight = 9999
const val boundingBoxCenterXLeft = 0

// constants as range of milliseconds
val rangeDrowsiness = (500L..999L)
val rangeRepeatedDrowsiness = (1001..60000L)
val rangeMicroSleep = (1000L..2999L)
val rangeRepeatedQuickFall = (1001..2999)

// Head positions angles to detect 'soft' or 'extreme' movements
const val minDownAngle = -5
const val maxDownAngle = -14
const val minUpAngle = 20
const val maxUpAngle = 30
const val minLeftAngle = 18
const val maxLeftAngle = 38
const val minRightAngle = -18
const val maxRightAngle = -38

// Head positions
const val front = "Front face"
const val softRight = "Soft Right"
const val softLeft = "Soft Left"
const val softUp = "Soft Up"
const val softDown = "Soft Down"
const val extremeRight = "Extreme Right"
const val extremeLeft = "Extreme Left"
const val extremeUp = "Extreme Up"
const val extremeDown = "Extreme Down"

// face mesh points as constants
const val leftEyeP1 = 33
const val leftEyeP2 = 160
const val leftEyeP3 = 158
const val leftEyeP4 = 133
const val leftEyeP5 = 153
const val leftEyeP6 = 144
const val rightEyeP1 = 263
const val rightEyeP2 = 387
const val rightEyeP3 = 385
const val rightEyeP4 = 362
const val rightEyeP5 = 380
const val rightEyeP6 = 373
const val mouthP1 = 78
const val mouthP2 = 81
const val mouthP3 = 311
const val mouthP4 = 308
const val mouthP5 = 402
const val mouthP6 = 178
const val thresholdMAR = 0.61
const val noseTipCenter = 4
const val noseTip = 1
const val noseTop = 6
const val chin = 199

const val leftPoint = 0
const val rightPoint = 8
const val bottomCenterPoint = 4

// Head positions angles to detect 'soft' or 'extreme' movements
const val maxHeadPitchAngleDown = 1.0
const val maxHeadPitchAngleUp = -35
const val maxLeftAngleFaceMesh = -42
const val maxRightAngleFaceMesh = 42

// Dynamic thresholds
const val redFlagLeftEyeThreshold = 0.05
const val redFlagLeftEyeThresholdWhenHeadUp = 0.025
const val redFlagLeftEyeThresholdWhenHeadTurnedRight = 0.144
const val redFlagLeftEyeThresholdWhenHeadTurnedLeft = -1.0

const val redFlagRightEyeThreshold = 0.05
const val redFlagRightEyeThresholdWhenHeadUp = 0.025
const val redFlagRightEyeThresholdWhenHeadTurnedRight = -1.0
const val redFlagRightEyeThresholdWhenHeadTurnedLeft = 0.144

const val redFlagRightMouthThreshold = 42.0
const val redFlagLeftMouthThreshold = 42.0
const val redFlagRightMouthThresholdWhenHeadTurnedRight = redFlagRightMouthThreshold + 5.0
const val redFlagLeftMouthThresholdWhenHeadTurnedRight = -1.0
const val redFlagRightMouthThresholdWhenHeadTurnedLeft = -1.0
const val redFlagLeftMouthThresholdWhenHeadTurnedLeft = redFlagLeftMouthThreshold + 5.0

const val eyeCompensationForDarkScene = 4.0
const val mouthCompensationForDarkScene = 4.0
