package com.bluetrailsoft.drowsinessmodule.facedetector

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.facedetector.detector.FaceDetectorBasicMesh
import com.bluetrailsoft.drowsinessmodule.facedetector.detector.FaceDetectorAdvancedMesh
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FlagResponse
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.LightningUtils
import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.flow.MutableStateFlow

class DrowsinessDetectorMainVideo(
    private val context: Context,
    private val graphicOverlay: GraphicOverlay,
    private val detectionConfig: DetectionConfig
) {

    val fatigueStateFlow = MutableStateFlow(FlagResponse(0, ""))
    val faceParamsStateFlow: MutableStateFlow<FaceParameters?> = MutableStateFlow(null)

    private val utils = LightningUtils()
    private var imageProcessor: ImageProcessor? = null
    private var processing = false
    private var frameHeight = 0
    private var frameWidth = 0

    fun startDetection() {
        if (!DrowsinessDetectorMain.checkPermissions(context)) {
            Log.e("TAG", "Permissions were not granted, startDetection call aborted.")
            return
        }
    }

    fun stopImageProcessor() {
        if (imageProcessor != null) {
            imageProcessor?.stop()
            imageProcessor = null
            processing = false
        }
    }

    fun createImageProcessor() {
        stopImageProcessor()
        val mDetector = if (detectionConfig.useAdvancedMesh) {
            FaceDetectorAdvancedMesh(context, detectionConfig)
        } else {
            FaceDetectorBasicMesh(context, detectionConfig)
        }

        mDetector.onFlagUpdated = { flag, logs ->
            fatigueStateFlow.value = FlagResponse(flag, logs)
        }
        mDetector.onFaceParametersUpdated = {
            faceParamsStateFlow.value = it
        }

        imageProcessor =
            try {
                mDetector
            } catch (e: Exception) {
                Log.e("TAG", "Failed to initialize image processor", e)
                return
            }
    }

    fun processFrame(frame: Bitmap, onResult:(Bitmap?) -> Unit) {
        if (imageProcessor != null && !processing) {
            processing = true
            var newFrame = frame
            val lum = utils.calculateLumFromBitmap(frame)
            val isSceneDark = utils.isSceneDark(lum)
            val isSceneTooDark = utils.isSceneTooDark(lum)
            if (isSceneDark) {
                newFrame = utils.setFilter(frame, 2f, 8f)
                onResult.invoke(newFrame)
            } else if (isSceneTooDark) {
                newFrame = utils.equalizeHistogramFull(frame)
                onResult.invoke(newFrame)
            } else {
                onResult.invoke(null)
            }
            try {
                if (frameWidth != newFrame.width || frameHeight != newFrame.height) {
                    frameWidth = newFrame.width
                    frameHeight = newFrame.height
                    graphicOverlay.setImageSourceInfo(frameWidth, frameHeight, false)
                }
                imageProcessor?.setOnProcessingCompleteListener(object: OnProcessingCompleteListener{
                    override fun onProcessingComplete() {
                        processing = false
                    }
                })
                imageProcessor!!.processBitmap(newFrame, graphicOverlay, lum)
            } catch (e: MlKitException) {
                processing = false
                Log.e("TAG", "Error analyzing image: " + e.localizedMessage)
            }
        }
    }
}
