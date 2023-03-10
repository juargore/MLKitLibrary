package com.bluetrailsoft.drowsinessmodule.facedetector

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.FakeGraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.google.mlkit.common.MlKitException

/** on processor completed interface. Callback from */
interface OnProcessingCompleteListener {
    fun onProcessingComplete()
}

/** Image analysis interface. Stripped everything but CameraX */
internal interface ImageProcessor {
    @Throws(MlKitException::class)
    fun processImageProxy(
        image: ImageProxy,
        fakeOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    )

    @Throws(MlKitException::class)
    fun processBitmap(
        bitmap: Bitmap,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    )

    @Throws(MlKitException::class)
    fun processImageProxyAndBmp(
        image: ImageProxy,
        bitmap: Bitmap,
        fakeGraphicOverlay: FakeGraphicOverlay?,
        graphicOverlay: GraphicOverlay?,
        lum: Int
    )

    fun setOnProcessingCompleteListener(
        onProcessingCompleteListener: OnProcessingCompleteListener?
    )

    fun stop()
}
