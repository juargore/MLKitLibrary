package com.bluetrailsoft.drowsinessmodule.facedetector.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import androidx.core.util.Preconditions

class FakeGraphicOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lock = Any()
    private val graphics: MutableList<Graphic> = ArrayList()
    private val scaleFactor = 1.0f
    private var isImageFlipped = false

    abstract class Graphic(private val overlay: FakeGraphicOverlay?) {
        abstract fun draw(canvas: Canvas?)
    }

    /*fun FakeGraphicOverlay(context: Context?, attrs: AttributeSet?) {
        super(context, attrs)
        addOnLayoutChangeListener(
            OnLayoutChangeListener { view: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int -> })
    }*/

    fun clear() {
        synchronized(lock) { graphics.clear() }
        postInvalidate()
    }

    fun add(graphic: Graphic) {
        synchronized(lock) { graphics.add(graphic) }
    }

    @SuppressLint("RestrictedApi")
    fun setImageSourceInfo(imageWidth: Int, imageHeight: Int, isFlipped: Boolean) {
        Preconditions.checkState(imageWidth > 0, "image width must be positive")
        Preconditions.checkState(imageHeight > 0, "image height must be positive")
        synchronized(lock) { isImageFlipped = isFlipped }
        postInvalidate()
    }

    protected override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
    }
}