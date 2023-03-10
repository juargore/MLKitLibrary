package com.bluetrailsoft.drowsinessmodule.facedetector.detector

import android.content.Context
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.R
import com.bluetrailsoft.drowsinessmodule.facedetector.ProcessorBase
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FaceMeshGraphic
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FakeGraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.undetectableFlag
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMesh.LEFT_EYE
import com.google.mlkit.vision.facemesh.FaceMesh.RIGHT_EYE
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetector
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import kotlin.math.abs

class FaceDetectorAdvancedMesh(
    val context: Context,
    private val detectionConfig: DetectionConfig
) : ProcessorBase<List<FaceMesh>>(context) {

    private val advancedDetector: FaceMeshDetector
    private val shared = SharedDetector(detectionConfig) { flag: Int, logs: String? ->
        onFlagUpdated?.invoke(flag, logs)
    }

    init {
        val optionsBuilder = FaceMeshDetectorOptions.Builder()
        optionsBuilder.setUseCase(FaceMeshDetectorOptions.FACE_MESH)
        advancedDetector = FaceMeshDetection.getClient(optionsBuilder.build())
    }

    override fun detectInImage(image: InputImage): Task<List<FaceMesh>> {
        return advancedDetector.process(image)
    }

    override fun onSuccess(results: List<FaceMesh>, fakeOverlay: FakeGraphicOverlay?, graphicOverlay: GraphicOverlay?, lum: Int) {
        // first, validate that results list has faces to process
        if (results.isEmpty()) {
            shared.triggerUndetectableFace()
            return
        }

        // first, get driver face from the given list according the position of the steering wheel
        val faceMesh = utils.getDriverFace(detectionConfig.wheelPosition, results)

        // draw UI only if var showMesh is true and the face actually exists
        if (detectionConfig.showMesh) {
            graphicOverlay?.add(FaceMeshGraphic(graphicOverlay, faceMesh))
        }

        // compute the face params (lux, eyesDelta, headPosition) and continue process
        computeFaceParams(faceMesh, lum).let { head: String ->
            shared.adjustThresholdsAccordingHeadPosition(faceMesh, head, lum)
        }
    }

    private fun computeFaceParams(faceMesh: FaceMesh, lum: Int): String {
        // new flow that returns lightning (as lumen) + eyesDeltaY && eyesDeltaY + head angles
        val leftEyePos = faceMesh.getPoints(LEFT_EYE)[0].position
        val rightEyePos = faceMesh.getPoints(RIGHT_EYE)[0].position
        var deltaY = 0f
        var deltaX = 0f

        if (leftEyePos != null && rightEyePos != null) {
            deltaX = abs(leftEyePos.x - rightEyePos.x)
            deltaY = abs(leftEyePos.y - rightEyePos.y)
        }

        val headPosition = utils.getHeadPosition(faceMesh)
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
        advancedDetector.close()
    }
}
