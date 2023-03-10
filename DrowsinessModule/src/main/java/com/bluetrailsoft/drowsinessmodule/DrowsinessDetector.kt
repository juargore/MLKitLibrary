@file:Suppress("BooleanMethodIsAlwaysInverted", "BooleanMethodIsAlwaysInverted",
    "BooleanMethodIsAlwaysInverted", "BooleanMethodIsAlwaysInverted"
)

package com.bluetrailsoft.drowsinessmodule

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.bluetrailsoft.drowsinessmodule.facedetector.DrowsinessDetectorMain
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FaceParameters
import com.bluetrailsoft.drowsinessmodule.facedetector.models.FlagResponse
import kotlinx.coroutines.flow.MutableStateFlow

/**
* Entry point for the drowsiness module. The module can be used in two ways:
* 1. Without camera preview (cameraPreview arg as null)
* 2. With camera preview (cameraPreview with reference to View in the host app)
*
* More details:
 * In order to display the camera preview, with meshes and metadata, add an include in the main
 * application layout, referencing a ConstraintLayout bundled in this library with the
 * id "camera_preview".
 * In other words, follow these steps
* 2.a In the host app, insert the camera_preview wherever you want the preview to be rendered.
* 2.b Pass its reference to this library in the cameraPreview argument.
* */
class DrowsinessDetector(
    context: Context,
    detectionConfig: DetectionConfig,
    private val onResult: ((Bitmap?) -> Unit)? = null
) {

    private var drowsinessDetectorMain: DrowsinessDetectorMain? = null
    val fatigueStateFlow = MutableStateFlow(FlagResponse(0, ""))
    val faceParamsStateFlow: MutableStateFlow<FaceParameters?> = MutableStateFlow(null)

    init {
        drowsinessDetectorMain = DrowsinessDetectorMain(context, detectionConfig) {
            onResult?.invoke(it)
        }
        drowsinessDetectorMain?.onFlagUpdated = { flag, logs ->
            fatigueStateFlow.value = FlagResponse(flag, logs)
        }
        drowsinessDetectorMain?.onFaceParametersUpdated = {
            faceParamsStateFlow.value = it
        }
    }

    fun startDetection() {
        drowsinessDetectorMain?.startDetection()
    }

    fun stopDetection() {
        drowsinessDetectorMain?.stopDetection()
    }

    companion object {
        private val requiredPermissions : Array<String> = arrayOf(
            Manifest.permission.CAMERA
        )

        @JvmStatic fun requestPermissions(context: Context) {
            ActivityCompat.requestPermissions(context as Activity, requiredPermissions, 1)
        }

        @Suppress("BooleanMethodIsAlwaysInverted")
        @JvmStatic fun checkPermissions(context: Context): Boolean {
            return (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
    }
}
