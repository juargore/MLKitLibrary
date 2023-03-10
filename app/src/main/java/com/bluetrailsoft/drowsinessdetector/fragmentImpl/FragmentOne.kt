package com.bluetrailsoft.drowsinessdetector.fragmentImpl

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluetrailsoft.drowsinessdetector.R
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import kotlinx.coroutines.launch

class FragmentOne : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_one, container, false)

        if (!DrowsinessDetector.checkPermissions(requireContext())) {
            DrowsinessDetector.requestPermissions(requireContext())
            return null
        }

        val cameraPreview = rootView.findViewById<ConstraintLayout>(R.id.layPreview)
        val detectionConfig = DetectionConfig(
            useAdvancedMesh = true,
            minFps = 15,
            maxFps = 30,
            redFlagDuration = 5000L,
            yellowFlagDuration = 2500L,
            cameraPreview = cameraPreview,
            wheelPosition = WHEEL_POSITION.LEFT,
            showMesh = true
        )

        val detector = DrowsinessDetector(requireContext(), detectionConfig)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                detector.fatigueStateFlow.collect { result ->
                    val stateFlag = result.flag
                    val logs = result.stateLogs
                }
            }
        }
        detector.startDetection()

        return rootView
    }
}
