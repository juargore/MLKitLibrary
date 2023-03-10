package com.bluetrailsoft.drowsinessmodule.facedetector.overlay

import android.graphics.Canvas

class CameraHiddenGraphic(
    overlay: FakeGraphicOverlay
) : FakeGraphicOverlay.Graphic(overlay) {

    override fun draw(canvas: Canvas?) { }

}
