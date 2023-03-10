package com.bluetrailsoft.drowsinessmodule.facedetector.detector

import android.content.Context
import android.util.Log
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.R
import com.bluetrailsoft.drowsinessmodule.facedetector.ProcessorBase
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FaceGraphic
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FaceHiddenGraphic
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FakeGraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.mlContextAlreadyInit
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.PreferenceEditor
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.tag
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.undetectableFlag
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs

class FaceDetectorBasicMesh(
    val context: Context,
    private val detectionConfig: DetectionConfig
) : ProcessorBase<List<Face>>(context) {

    private val basicDetector: FaceDetector
    private val shared = SharedDetector(detectionConfig) { flag: Int, logs: String? ->
        onFlagUpdated?.invoke(flag, logs)
    }

    init {
        try {
            MlKit.initialize(context)
        } catch (e: IllegalStateException) {
            Log.d(tag, mlContextAlreadyInit)
        }
        val preferences = PreferenceEditor().getFaceDetectorOptions(context)
        basicDetector = FaceDetection.getClient(
            /* options = */ preferences ?: FaceDetectorOptions.Builder()
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .enableTracking().build()
        )
    }

    override fun detectInImage(image: InputImage): Task<List<Face>> {
        return basicDetector.process(image)
    }

    override fun onSuccess(results: List<Face>, fakeOverlay: FakeGraphicOverlay?, graphicOverlay: GraphicOverlay?, lum: Int) {
        // first, validate that results list has faces to process
        if (results.isEmpty()) {
            shared.triggerUndetectableFace()
            return
        }

        // get driver face from the given list according the position of the steering wheel
        val face = utils.getDriverFace(detectionConfig.wheelPosition, results)

        // draw UI only if var showMesh is true and the face actually exists
        if (detectionConfig.showMesh) {
            fakeOverlay?.add(FaceHiddenGraphic(fakeOverlay, face))
            graphicOverlay?.add(FaceGraphic(graphicOverlay, face))
        }

        // compute the face params (lux, eyesDelta, headPosition) and continue process
        computeFaceParams(face, lum).let { head: String ->
            shared.adjustThresholdsAccordingHeadPosition(face, head, lum)
        }
    }

    private fun computeFaceParams(face: Face, lum: Int): String {
        // new flow that returns lightning (as lumen) + eyesDeltaY && eyesDeltaY + head angles
        val leftEyePos = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyePos = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
        var deltaY = 0f
        var deltaX = 0f

        if (leftEyePos != null && rightEyePos != null) {
            deltaX = abs(leftEyePos.x - rightEyePos.x)
            deltaY = abs(leftEyePos.y - rightEyePos.y)
        }

        val headPosition = utils.getHeadPosition(face)
        val data = FaceParameters(
            lux = lum,
            eyesDeltaX = deltaX,
            eyesDeltaY = deltaY,
            headPosition = headPosition
        )

        onFaceParametersUpdated?.invoke(data)
        return headPosition
    }

    override fun onFailure(e: Exception) {
        shared.notifyDriverIfNeeded(undetectableFlag, context.resources.getString(R.string.on_failure_log))
    }

    override fun stop() {
        super.stop()
        utils.resetValuesOnStop()
        basicDetector.close()
    }
}
