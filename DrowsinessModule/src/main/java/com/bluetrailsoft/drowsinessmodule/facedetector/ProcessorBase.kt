package com.bluetrailsoft.drowsinessmodule.facedetector

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.*
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.BitmapUtils
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.FaceFeatureUtils
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.FrameInformation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.android.gms.tasks.Tasks
import com.google.android.odml.image.MlImage
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.util.*

/**
* This code is heavily based on ML-Kit sample app for face detection. Main diffs are:
* 1. We allow to use a fake overlay (doesn't require a view on an app to work)
* 2. Optionally allows passing a view to render camera preview with overlay
*
* All features to process frames and other sources than androidx CameraX were stripped.
* Core logic is in processImageProxy()
* */
abstract class ProcessorBase<T>(context: Context) : ImageProcessor {

    val utils = FaceFeatureUtils()
    var onFlagUpdated: ((Int, String?) -> Unit)? = null
    var onFaceParametersUpdated: ((FaceParameters?) -> Unit)? = null

    @Suppress("PrivatePropertyName")
    private val TAG = "VisionProcessorBase"
    private var onProcessingCompleteListener: OnProcessingCompleteListener? = null
    private var activityManager: ActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val executor = StoppableExecutor(TaskExecutors.MAIN_THREAD)
    private val fpsTimer = Timer()
    private var isShutdown = false

    // Used to calculate latency, running in the same thread, no sync needed.
    private var numRuns = 0
    private var totalFrameMs = 0L
    private var maxFrameMs = 0L
    private var minFrameMs = Long.MAX_VALUE
    private var totalDetectorMs = 0L
    private var maxDetectorMs = 0L
    private var minDetectorMs = Long.MAX_VALUE

    // Frame count that have been processed so far in an one second interval to calculate FPS.
    private var frameProcessedInOneSecondInterval = 0
    private var framesPerSecond = 0

    // To keep the latest images and its metadata.
    @GuardedBy("this") private var latestImage: ByteBuffer? = null
    @GuardedBy("this") private var latestImageMetaData: FrameInformation? = null
    @GuardedBy("this") private var processingImage: ByteBuffer? = null
    @GuardedBy("this") private var processingMetaData: FrameInformation? = null

    init {
        fpsTimer.scheduleAtFixedRate(
            object : TimerTask() {
                override fun run() {
                    framesPerSecond = frameProcessedInOneSecondInterval
                    frameProcessedInOneSecondInterval = 0
                }
            }, 0, 1000
        )
    }

    // notify DrowsinessDetectorMain or DrowsinessDetectorMainVideo that the current frame has finished processing
    override fun setOnProcessingCompleteListener(onProcessingCompleteListener: OnProcessingCompleteListener?) {
        this.onProcessingCompleteListener = onProcessingCompleteListener
    }

    override fun processBitmap(bitmap: Bitmap, graphicOverlay: GraphicOverlay?, lum: Int) {
        val frameStartMs = SystemClock.elapsedRealtime()
        if (isShutdown) {
            return
        }
        requestDetectInImage(
            image = InputImage.fromBitmap(bitmap, 0),
            fakeGraphicOverlay = null,
            graphicOverlay = graphicOverlay,
            originalCameraImage = bitmap,
            frameStartMs = frameStartMs,
            lum = lum
        ).addOnCompleteListener {
            bitmap.recycle()
        }
    }

    @ExperimentalGetImage
    override fun processImageProxyAndBmp(
        image: ImageProxy,
        bitmap: Bitmap,
        fakeGraphicOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    ) {
        val frameStartMs = SystemClock.elapsedRealtime()
        if (isShutdown) {
            return
        }
        requestDetectInImage(
            image = InputImage.fromBitmap(bitmap, 0),
            fakeGraphicOverlay = fakeGraphicOverlay,
            graphicOverlay = graphicOverlay,
            originalCameraImage = bitmap,
            frameStartMs = frameStartMs,
            lum = lum
        ).addOnCompleteListener {
            bitmap.recycle()
            image.close()
        }
    }

    @ExperimentalGetImage
    override fun processImageProxy(
        image: ImageProxy,
        fakeOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    ) {
        val frameStartMs = SystemClock.elapsedRealtime()
        if (isShutdown) {
            return
        }
        val bitmap: Bitmap? = BitmapUtils().getBitmap(image)
        requestDetectInImage(
            image = InputImage.fromMediaImage(image.image!!, image.imageInfo.rotationDegrees),
            fakeGraphicOverlay = fakeOverlay,
            graphicOverlay = graphicOverlay,
            originalCameraImage = bitmap,
            frameStartMs = frameStartMs,
            lum = lum
        ).addOnCompleteListener {
            image.close()
        }
    }

    private fun requestDetectInImage(
        image: InputImage,
        fakeGraphicOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        originalCameraImage: Bitmap?,
        frameStartMs: Long,
        lum: Int
    ): Task<T> {
        return setUpListener(
            detectInImage(image),
            fakeGraphicOverlay,
            graphicOverlay,
            originalCameraImage,
            frameStartMs,
            lum
        )
    }

    private fun setUpListener(
        task: Task<T>,
        fakeGraphicOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        originalCameraImage: Bitmap?,
        frameStartMs: Long,
        lum: Int
    ): Task<T> {
        val detectorStartMs = SystemClock.elapsedRealtime()
        return task
            .addOnSuccessListener(
                executor
            ) { results: T ->
                val endMs = SystemClock.elapsedRealtime()
                val currentFrameLatencyMs = endMs - frameStartMs
                val currentDetectorLatencyMs = endMs - detectorStartMs
                if (numRuns >= 500) {
                    resetLatencyStats()
                }
                numRuns++
                frameProcessedInOneSecondInterval++
                totalFrameMs += currentFrameLatencyMs
                maxFrameMs = currentFrameLatencyMs.coerceAtLeast(maxFrameMs)
                minFrameMs = currentFrameLatencyMs.coerceAtMost(minFrameMs)
                totalDetectorMs += currentDetectorLatencyMs
                maxDetectorMs = currentDetectorLatencyMs.coerceAtLeast(maxDetectorMs)
                minDetectorMs = currentDetectorLatencyMs.coerceAtMost(minDetectorMs)

                // Only log inference info once per second. When frameProcessedInOneSecondInterval is
                // equal to 1, it means this is the first frame processed during the current second.
                val logStats = false
                if (logStats && frameProcessedInOneSecondInterval == 1) {
                    Log.d(TAG, "Num of Runs: $numRuns")
                    Log.d(
                        TAG,
                        "Frame latency: max=" +
                                maxFrameMs +
                                ", min=" +
                                minFrameMs +
                                ", avg=" +
                                totalFrameMs / numRuns
                    )
                    Log.d(
                        TAG,
                        "Detector latency: max=" +
                                maxDetectorMs +
                                ", min=" +
                                minDetectorMs +
                                ", avg=" +
                                totalDetectorMs / numRuns
                    )
                    val mi = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(mi)
                    val availableMegs: Long = mi.availMem / 0x100000L
                    Log.d(TAG, "Memory available in system: $availableMegs MB")
                }

                graphicOverlay?.let {
                    it.clear()
                    if (originalCameraImage != null) {
                        it.add(
                            CameraImageGraphic(
                                it
                            )
                        )
                    }
                    if (originalCameraImage != null) {
                        it.add(
                            CameraImageGraphic(
                                graphicOverlay
                            )
                        )
                    }
                    it.postInvalidate()
                }
                fakeGraphicOverlay?.clear()
                graphicOverlay?.clear()

                if (originalCameraImage != null) {
                    fakeGraphicOverlay?.add(
                        CameraHiddenGraphic(
                            fakeGraphicOverlay
                        )
                    )
                    graphicOverlay?.add(
                        CameraImageGraphic(
                            graphicOverlay
                        )
                    )
                }
                this@ProcessorBase.onSuccess(results, fakeGraphicOverlay, graphicOverlay, lum)
                fakeGraphicOverlay?.postInvalidate()
                graphicOverlay?.postInvalidate()
            }
            .addOnFailureListener(
                executor
            ) { e: Exception ->
                graphicOverlay?.clear()
                graphicOverlay?.postInvalidate()
                fakeGraphicOverlay?.clear()
                fakeGraphicOverlay?.postInvalidate()
                val error = "Detector process failed: " + e.localizedMessage
                Log.d(TAG, error)
                e.printStackTrace()
                this@ProcessorBase.onFailure(e)
            }
            .addOnCompleteListener {
                if (onProcessingCompleteListener != null) {
                    onProcessingCompleteListener!!.onProcessingComplete()
                }
            }
    }

    override fun stop() {
        executor.shutdown()
        isShutdown = true
        resetLatencyStats()
        fpsTimer.cancel()
    }

    private fun resetLatencyStats() {
        numRuns = 0
        totalFrameMs = 0
        maxFrameMs = 0
        minFrameMs = Long.MAX_VALUE
        totalDetectorMs = 0
        maxDetectorMs = 0
        minDetectorMs = Long.MAX_VALUE
    }

    protected abstract fun detectInImage(image: InputImage): Task<T>

    protected open fun detectInImage(image: MlImage): Task<T> {
        return Tasks.forException(
            MlKitException(
                "Invalid image for requested feature",
                MlKitException.INVALID_ARGUMENT
            )
        )
    }

    protected abstract fun onSuccess(
        results: T,
        fakeOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    )

    protected abstract fun onFailure(e: Exception)
}
