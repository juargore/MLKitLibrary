package com.bluetrailsoft.drowsinessmodule.facedetector.overlay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face

/** Mimics FaceGraphic but omits drawing  */
@Suppress("unused")
class FaceHiddenGraphic constructor(
    overlay: FakeGraphicOverlay?,
    private val face: Face
) : FakeGraphicOverlay.Graphic(overlay) {

    private val facePaint: Paint
    private val numColors = COLORS.size
    private val idPaints = Array(numColors) { Paint() }
    private val boxPaints = Array(numColors) { Paint() }
    private val labelPaints = Array(numColors) { Paint() }

    init {
        val selectedColor = Color.WHITE
        facePaint = Paint()
        facePaint.color = selectedColor
        for (i in 0 until numColors) {
            idPaints[i] = Paint()
            idPaints[i].color = COLORS[i][0]
            idPaints[i].textSize = ID_TEXT_SIZE
            boxPaints[i] = Paint()
            boxPaints[i].color = COLORS[i][1]
            boxPaints[i].style = Paint.Style.STROKE
            boxPaints[i].strokeWidth = BOX_STROKE_WIDTH
            labelPaints[i] = Paint()
            labelPaints[i].color = COLORS[i][1]
            labelPaints[i].style = Paint.Style.FILL
        }
    }

    companion object {
        private const val ID_TEXT_SIZE = 30.0f
        private const val BOX_STROKE_WIDTH = 5.0f
        private val COLORS =
            arrayOf(
                intArrayOf(Color.BLACK, Color.WHITE),
                intArrayOf(Color.WHITE, Color.MAGENTA),
                intArrayOf(Color.BLACK, Color.LTGRAY),
                intArrayOf(Color.WHITE, Color.RED),
                intArrayOf(Color.WHITE, Color.BLUE),
                intArrayOf(Color.WHITE, Color.DKGRAY),
                intArrayOf(Color.BLACK, Color.CYAN),
                intArrayOf(Color.BLACK, Color.YELLOW),
                intArrayOf(Color.WHITE, Color.BLACK),
                intArrayOf(Color.BLACK, Color.GREEN)
            )
    }

    override fun draw(canvas: Canvas?) { }
}