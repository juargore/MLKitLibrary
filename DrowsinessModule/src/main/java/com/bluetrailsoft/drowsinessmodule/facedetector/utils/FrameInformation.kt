package com.bluetrailsoft.drowsinessmodule.facedetector.utils

class FrameInformation(
    width: Int,
    height: Int,
    rotation: Int
) {

    private var width = 0
    private var height = 0
    private var rotation = 0

    init {
        this.width = width
        this.height = height
        this.rotation = rotation
    }

    fun getWidth(): Int {
        return width
    }

    fun getHeight(): Int {
        return height
    }

    fun getRotation(): Int {
        return rotation
    }

    class Builder {
        private var width = 0
        private var height = 0
        private var rotation = 0
        fun setWidth(width: Int): Builder {
            this.width = width
            return this
        }

        fun setHeight(height: Int): Builder {
            this.height = height
            return this
        }

        fun setRotation(rotation: Int): Builder {
            this.rotation = rotation
            return this
        }

        fun build(): FrameInformation {
            return FrameInformation(width, height, rotation)
        }
    }
}