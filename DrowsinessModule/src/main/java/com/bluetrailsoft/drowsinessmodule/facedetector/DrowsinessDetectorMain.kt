package com.bluetrailsoft.drowsinessmodule.facedetector

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CaptureRequest
import android.util.Log
import android.util.Range
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.R
import com.bluetrailsoft.drowsinessmodule.facedetector.detector.FaceDetectorBasicMesh
import com.bluetrailsoft.drowsinessmodule.facedetector.detector.FaceDetectorAdvancedMesh
import com.bluetrailsoft.drowsinessmodule.facedetector.extensions.getLumFromImageProxy
import com.bluetrailsoft.drowsinessmodule.facedetector.extensions.getLumFromImageProxy2
import com.bluetrailsoft.drowsinessmodule.facedetector.extensions.toBitmap
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FakeGraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.LightningUtils
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.PreferenceEditor
import com.google.mlkit.common.MlKitException

/**
 * Prepares the analysis and preview use cases, and connects them with the lifecycle-owner/context
 * This is heavily based on ML-Kit sample code
 */
class DrowsinessDetectorMain(
    private val context: Context,
    private val detectionConfig: DetectionConfig,
    private val onResult: (Bitmap?) -> Unit
) {

    var onFlagUpdated: ((Int, String?) -> Unit)? = null
    var onFaceParametersUpdated: ((FaceParameters?) -> Unit)? = null

    private val utils = LightningUtils()
    private var fakeGraphicOverlay: FakeGraphicOverlay? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageProcessor: ImageProcessor? = null
    private var needUpdateGraphicOverlayImageSourceInfo = false
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var cameraSelector: CameraSelector? = null
    private var previewView: PreviewView?
    private var graphicOverlay: GraphicOverlay?
    private var processing = false

    init {
        if (context is AppCompatActivity || context is Activity) {
            previewView = detectionConfig.cameraPreview?.findViewById(R.id.preview_view)
            graphicOverlay = detectionConfig.cameraPreview?.findViewById(R.id.graphic_overlay)
        } else {
            previewView = null
            graphicOverlay = null
        }
    }

    fun startDetection() {
        if (!checkPermissions(context)) {
            Log.e(TAG, "Permissions were not granted, startDetection call aborted.")
            return
        }
        bindCameraProvider()
    }

    fun stopDetection() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }
        if (imageAnalysis != null) {
            cameraProvider!!.unbind(imageAnalysis)
        }
        if (imageProcessor != null) {
            imageProcessor!!.stop()
        }
        processing = false
        Log.i(TAG, "Drowsiness detection stopped.")
    }

    private fun bindCameraProvider() {
        generateFakeOverlay()
        cameraSelector = CameraSelector
            .Builder()
            .requireLensFacing(lensFacing)
            .build()
        cameraProvider = getProcessCameraProvider()
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            startCameraImageAnalysis()
        } else {
            Log.e(TAG, "FIXME: Can't bind camera, cannot obtain the camera provider.")
        }
    }

    private fun getProcessCameraProvider(): ProcessCameraProvider? {
        return ProcessCameraProvider.getInstance(context).get()
    }

    @SuppressLint("InflateParams")
    private fun generateFakeOverlay() {
        if (fakeGraphicOverlay == null) {
            val inflater = LayoutInflater.from(context)
            val hiddenScreen = inflater.inflate(R.layout.camera_layout, null)
            fakeGraphicOverlay = hiddenScreen.findViewById(R.id.graphic_overlay)
        } else {
            Log.d(TAG, "Overlay is ready, not creating a new one")
        }
    }

    private var frameCounter = 0

    @androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
    private fun startCameraImageAnalysis() {
        stopDetection()
        val detector = if (detectionConfig.useAdvancedMesh) {
            FaceDetectorAdvancedMesh(context, detectionConfig)
        } else {
            FaceDetectorBasicMesh(context, detectionConfig)
        }
        detector.onFlagUpdated = { flag, log ->
            onFlagUpdated?.invoke(flag, log)
        }
        detector.onFaceParametersUpdated = {
            onFaceParametersUpdated?.invoke(it)
        }

        imageProcessor = try {
            detector
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize image processor: ${e.localizedMessage}")
            return
        }

        val previewBuilder = Preview.Builder()
        val targetResolution = PreferenceEditor().getCameraXTargetResolution(context, lensFacing)
        if (targetResolution != null) {
            previewBuilder.setTargetResolution(targetResolution)
        }
        previewUseCase = previewBuilder.build()
        previewUseCase!!.setSurfaceProvider(previewView?.surfaceProvider)

        val builder = ImageAnalysis.Builder()
        val minFps = detectionConfig.minFps!!
        val maxFps = detectionConfig.maxFps!!

        if (minFps != -1 && maxFps != -1) {
            Camera2Interop.Extender(builder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(minFps, maxFps))
        }
        if (targetResolution != null) {
            builder.setTargetResolution(targetResolution)
        }
        builder.setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        imageAnalysis = builder.build()
        needUpdateGraphicOverlayImageSourceInfo = true
        imageAnalysis?.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy: ImageProxy ->
            if (needUpdateGraphicOverlayImageSourceInfo) {
                val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                graphicOverlay?.let {
                    if (rotationDegrees == 0 || rotationDegrees == 180) {
                        it.setImageSourceInfo(
                            imageProxy.width,
                            imageProxy.height,
                            isImageFlipped
                        )
                    } else {
                        it.setImageSourceInfo(
                            imageProxy.height,
                            imageProxy.width,
                            isImageFlipped
                        )
                    }
                }
                if (rotationDegrees == 0 || rotationDegrees == 180) {
                    fakeGraphicOverlay!!.setImageSourceInfo(
                        imageProxy.width,
                        imageProxy.height,
                        isImageFlipped
                    )
                } else {
                    fakeGraphicOverlay!!.setImageSourceInfo(
                        imageProxy.height,
                        imageProxy.width,
                        isImageFlipped
                    )
                }
                needUpdateGraphicOverlayImageSourceInfo = false
            }
            if (!processing) {
                frameCounter++
                processing = true
                try {
                    imageProcessor?.setOnProcessingCompleteListener(object: OnProcessingCompleteListener{
                        override fun onProcessingComplete() {
                            processing = false
                        }
                    })

                    if (frameCounter == 4) {
                        // todo: aquí va la función (agregar global variable para almacenar ultimo lum value)
                        frameCounter = 0
                    }

                    // todo: medir tiempos con LOG para ver rapidez
                    // todo: comparar valores output para ver fiabilidad
                    println("AQUI: 1")
                    val lum = imageProxy.getLumFromImageProxy()
                    println("AQUI: 2")
                    //val lum2 = imageProxy.getLumFromImageProxy2()

                    val isSceneDark = utils.isSceneDark(lum)
                    val isSceneTooDark = utils.isSceneTooDark(lum)

                    if (isSceneDark || isSceneTooDark) {
                        // there is no enough light in scene -> convert imageProxy to BMP and apply filters
                        var newFrame = imageProxy.toBitmap()
                        if (newFrame != null) {
                            // everything went correct when transforming imageProxy to BMP -> it can continue
                            if (isSceneDark)
                                newFrame = utils.setFilter(newFrame, 2f, 8f)
                            if (isSceneTooDark)
                                newFrame = utils.equalizeHistogramFull(newFrame)
                            // return bitmap to visualize on UI -> to be removed
                            onResult.invoke(newFrame)
                            imageProcessor!!.processImageProxyAndBmp(imageProxy, newFrame, fakeGraphicOverlay, graphicOverlay, lum)
                        } else {
                            // something went wrong when transforming imageProxy to BMP -> do normal process with Image Proxy
                            onResult.invoke(null)
                            imageProcessor!!.processImageProxy(imageProxy, fakeGraphicOverlay, graphicOverlay, lum)
                        }
                    } else {
                        // normal scene with enough light and can be processed correctly
                        onResult.invoke(null)
                        imageProcessor!!.processImageProxy(imageProxy, fakeGraphicOverlay, graphicOverlay, lum)
                    }
                } catch (e: MlKitException) {
                    processing = false
                    Log.e(TAG, "Error analyzing image: " + e.localizedMessage)
                }
            }
        }

        cameraProvider?.bindToLifecycle(context as LifecycleOwner, cameraSelector!!, imageAnalysis, previewUseCase)
        Log.i(TAG, "Drowsiness detection started.")
    }

    @Suppress("unused")
    companion object {
        private const val TAG = "DrowsinessDetector"
        private const val FACE_DETECTION = "Face Detection"
        private val requiredPermissions : Array<String> = arrayOf(Manifest.permission.CAMERA)

        @JvmStatic fun requestPermissions(context: Context) {
            ActivityCompat.requestPermissions(context as Activity, requiredPermissions, 1)
        }

        @JvmStatic fun checkPermissions(context: Context): Boolean {
            return (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
    }
}
