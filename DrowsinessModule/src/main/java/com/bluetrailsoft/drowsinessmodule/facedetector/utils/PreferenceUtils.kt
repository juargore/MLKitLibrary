@file:Suppress("DEPRECATION")

package com.bluetrailsoft.drowsinessmodule.facedetector.utils

import android.content.Context
import android.preference.PreferenceManager
import com.bluetrailsoft.drowsinessmodule.R
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions

/** Utility class to retrieve shared preferences. */
class PreferenceUtils {
    fun getFaceMeshUseCase(context: Context): Int {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val prefKey = context.getString(R.string.pref_key_face_mesh_use_case)
        return sharedPreferences.getString(prefKey, FaceMeshDetectorOptions.FACE_MESH.toString())!!.toInt()
    }
}