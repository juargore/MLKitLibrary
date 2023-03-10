package com.bluetrailsoft.drowsinessmodule.facedetector.utils

import android.annotation.SuppressLint
import android.content.Context
import androidx.preference.PreferenceManager
import android.util.Size
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.core.util.Preconditions
import com.bluetrailsoft.drowsinessmodule.R
import com.google.mlkit.vision.face.FaceDetectorOptions

class PreferenceEditor {
    @SuppressLint("RestrictedApi")
    fun getCameraXTargetResolution(context: Context, lensFacing: Int): Size? {
        Preconditions.checkArgument(
            lensFacing == CameraSelector.LENS_FACING_BACK
                    || lensFacing == CameraSelector.LENS_FACING_FRONT
        )
        val prefKey =
            if (lensFacing == CameraSelector.LENS_FACING_BACK) context.getString(R.string.pref_key_camerax_rear_camera_target_resolution) else context.getString(
                R.string.pref_key_camerax_front_camera_target_resolution
            )
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        return try {
            Size.parseSize(sharedPreferences.getString(prefKey, null))
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("RedundantNullableReturnType")
    fun getFaceDetectorOptions(context: Context): FaceDetectorOptions? {
        val contour = FaceDetectorOptions.CONTOUR_MODE_ALL
        val contourMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_contour_mode,
            contour
        )
        val landmarkMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_landmark_mode,
            FaceDetectorOptions.LANDMARK_MODE_ALL
        )
        val classificationMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_classification_mode,
            FaceDetectorOptions.CLASSIFICATION_MODE_ALL
        )
        val performanceMode = getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_performance_mode,
            FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
        )
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val enableFaceTracking = sharedPreferences.getBoolean(
            context.getString(R.string.pref_key_live_preview_face_detection_face_tracking),
            false
        )
        val minFaceSize =
            sharedPreferences.getString(
                context.getString(R.string.pref_key_live_preview_face_detection_min_face_size),
                "0.1"
            )!!.toFloat()
        val optionsBuilder = FaceDetectorOptions.Builder()
            .setLandmarkMode(landmarkMode)
            .setContourMode(contourMode)
            .setClassificationMode(classificationMode)
            .setPerformanceMode(performanceMode)
            .setMinFaceSize(minFaceSize)
        if (enableFaceTracking) {
            optionsBuilder.enableTracking()
        }
        return optionsBuilder.build()
    }

    private fun getModeTypePreferenceValue(
        context: Context, @StringRes prefKeyResId: Int, defaultValue: Int
    ): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(prefKeyResId)
        return sharedPreferences.getString(prefKey, defaultValue.toString())!!.toInt()
    }
}
