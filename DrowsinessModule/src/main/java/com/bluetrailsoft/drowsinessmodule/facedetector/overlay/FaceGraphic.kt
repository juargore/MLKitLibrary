package com.bluetrailsoft.drowsinessmodule.facedetector.overlay


import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceLandmark.LandmarkType

/**
 * Graphic instance for rendering face position, contour, and landmarks within the associated
 * graphic overlay view.
 */
class FaceGraphic constructor(
    overlay: GraphicOverlay,
    private val face: Face
) : GraphicOverlay.Graphic(overlay) {

    private val facePositionPaint: Paint
    private val numColors = COLORS.size
    private val idPaints = Array(numColors) { Paint() }
    private val boxPaints = Array(numColors) { Paint() }
    private val labelPaints = Array(numColors) { Paint() }

    init {
        val selectedColor = Color.LTGRAY
        facePositionPaint = Paint()
        facePositionPaint.color = selectedColor
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

    /** Draws the face annotations for position on the supplied canvas. */
    override fun draw(canvas: Canvas?) {
        // Draws a circle at the position of the detected face, with the face's track id below.
        val p1 = face.boundingBox.centerX().toFloat()
        val p2 = face.boundingBox.centerY().toFloat()
        val x = translateX(p1)
        val y = translateY(p2)
        canvas?.drawCircle(x, y, FACE_POSITION_RADIUS, facePositionPaint)

        // Draws all face contours.
        for (contour in face.allContours) {
            for (point in contour.points) {
                canvas?.drawCircle(
                    translateX(point.x),
                    translateY(point.y),
                    FACE_POSITION_RADIUS,
                    facePositionPaint
                )
            }
        }

        // Draw facial landmarks
        canvas?.let {
            drawFaceLandmark(canvas, FaceLandmark.LEFT_EYE)
            drawFaceLandmark(canvas, FaceLandmark.RIGHT_EYE)
            drawFaceLandmark(canvas, FaceLandmark.LEFT_CHEEK)
            drawFaceLandmark(canvas, FaceLandmark.RIGHT_CHEEK)
            drawFaceLandmark(canvas, FaceLandmark.MOUTH_BOTTOM)
        }
    }

    private fun drawFaceLandmark(canvas: Canvas, @LandmarkType landmarkType: Int) {
        val faceLandmark = face.getLandmark(landmarkType)
        if (faceLandmark != null) {
            canvas.drawCircle(
                translateX(faceLandmark.position.x),
                translateY(faceLandmark.position.y),
                FACE_POSITION_RADIUS,
                facePositionPaint
            )
        }
    }

    companion object {
        private const val FACE_POSITION_RADIUS = 4.0f
        private const val ID_TEXT_SIZE = 30.0f
        private const val BOX_STROKE_WIDTH = 2.0f
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
}