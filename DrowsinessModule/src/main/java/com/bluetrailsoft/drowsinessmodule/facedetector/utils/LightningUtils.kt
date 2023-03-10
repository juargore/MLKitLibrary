package com.bluetrailsoft.drowsinessmodule.facedetector.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

class LightningUtils {

    fun isSceneDark(lum: Int) = lum in 36..58

    fun isSceneTooDark(lum: Int) = lum < 36

    fun equalizeHistogramFull(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Create arrays to store the histograms
        val redHistogram = IntArray(256)
        val greenHistogram = IntArray(256)
        val blueHistogram = IntArray(256)

        // Calculate the histograms
        for (i in pixels.indices) {
            val color = pixels[i]
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            redHistogram[red]++
            greenHistogram[green]++
            blueHistogram[blue]++
        }

        // Calculate the cumulative histograms
        for (i in 1 until redHistogram.size) {
            redHistogram[i] += redHistogram[i - 1]
            greenHistogram[i] += greenHistogram[i - 1]
            blueHistogram[i] += blueHistogram[i - 1]
        }

        // Normalize the cumulative histograms
        for (i in redHistogram.indices) {
            redHistogram[i] = (redHistogram[i] * 255) / (width * height)
            greenHistogram[i] = (greenHistogram[i] * 255) / (width * height)
            blueHistogram[i] = (blueHistogram[i] * 255) / (width * height)
        }

        // Apply the equalization to the pixels
        for (i in pixels.indices) {
            val color = pixels[i]
            val alpha = Color.alpha(color)
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            pixels[i] = Color.argb(alpha, redHistogram[red], greenHistogram[green], blueHistogram[blue])
        }

        // Create a new bitmap with the equalized pixels
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    fun setFilter(bmp: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f,
                brightness, 0f,
                contrast, 0f, 0f,
                brightness, 0f, 0f,
                contrast, 0f,
                brightness, 0f, 0f, 0f, 1f, 0f
            )
        )
        val ret = Bitmap.createBitmap(bmp.width, bmp.height, bmp.config)
        val canvas = Canvas(ret)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        return ret
    }

    /**
     * Calculates the estimated brightness of an Android Bitmap.
     * pixelSpacing tells how many pixels to skip each pixel. Higher values result in better performance, but a more rough estimate.
     * When pixelSpacing = 1, the method actually calculates the real average brightness, not an estimate.
     * This is what the calculateBrightness() shorthand is for.
     * Do not use values for pixelSpacing that are smaller than 1.
     */
    fun calculateLumFromBitmap(bitmap: Bitmap): Int {
        var r = 0
        var g = 0
        var b = 0
        val pixelSpacing = 1
        val height = bitmap.height
        val width = bitmap.width
        var n = 0
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        var i = 0
        while (i < pixels.size) {
            val color = pixels[i]
            r += Color.red(color)
            g += Color.green(color)
            b += Color.blue(color)
            n++
            i += pixelSpacing
        }
        return (r + b + g) / (n * 3)
    }
}
