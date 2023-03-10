package com.bluetrailsoft.drowsinessdetector

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.bluetrailsoft.drowsinessdetector.databinding.SettingsActivityBinding
import com.bluetrailsoft.drowsinessdetector.extensions.checkForNotificationAtRuntimePermissions
import com.bluetrailsoft.drowsinessdetector.utils.SharedPrefs
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import com.google.android.material.button.MaterialButton

class SettingsActivity : AppCompatActivity() {

    private val binding: SettingsActivityBinding by lazy {
        SettingsActivityBinding.inflate(layoutInflater)
    }

    private lateinit var parent: ConstraintLayout
    private lateinit var txtVersion: TextView
    private var cameraIntent = true

    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        checkForNotificationAtRuntimePermissions {
            if (!DrowsinessDetector.checkPermissions(this)) {
                DrowsinessDetector.requestPermissions(this)
            }
            parent = findViewById(R.id.parent)
            txtVersion = findViewById(R.id.txtVersion)
            supportFragmentManager.beginTransaction().replace(R.id.settings, SettingsFragment()).commit()
            setupViews()
        }
    }

    private fun setupViews() {
        txtVersion.text = BuildConfig.VERSION_NAME
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val fr = supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment
            when (checkedId) {
                R.id.radioCamera -> {
                    cameraIntent = true
                    parent.setBackgroundColor(Color.WHITE)
                    fr.disableServiceWhenVideoSelected(false)
                }
                R.id.radioVideo -> {
                    cameraIntent = false
                    parent.setBackgroundColor(getColor(R.color.soft_gray))
                    fr.disableServiceWhenVideoSelected(true)
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnGo).setOnClickListener {
            if (cameraIntent) {
                startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
            } else {
                // videoIntent
                startActivity(Intent(this@SettingsActivity, VideoActivity::class.java))
            }
        }
    }

    override fun onResume() {
        val shrPrefs = SharedPrefs(this)
        // refresh UI since comes from settings or notification screen
        if (shrPrefs.getCheckingSettings()) {
            shrPrefs.saveCheckingSettings(false)
            finish()
            startActivity(intent)
        }
        super.onResume()
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var prefs: SharedPrefs
        private var switchUseAdvancedMesh: SwitchPreference? = null
        private var switchMesh: SwitchPreference? = null
        private var switchSound: SwitchPreference? = null
        private var switchService: SwitchPreference? = null
        private var listFps: ListPreference? = null
        private var listRedFlagDuration: ListPreference? = null
        private var listYellowFlagDuration: ListPreference? = null
        private var listWheelPosition: ListPreference? = null
        private var frames = listOf<IntArray>()

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }

        override fun onCreateView(infl: LayoutInflater, cont: ViewGroup?, state: Bundle?): View {
            prefs = SharedPrefs(requireContext())
            with (preferenceManager) {
                switchUseAdvancedMesh = findPreference(getString(R.string.use_advanced_mesh_key))
                switchMesh = findPreference(getString(R.string.mesh_tracking_key))
                switchSound = findPreference(getString(R.string.sound_tracking_key))
                listFps = findPreference(getString(R.string.fps_tracking_key))
                listRedFlagDuration = findPreference(getString(R.string.red_flag_duration_tracking_key))
                listYellowFlagDuration = findPreference(getString(R.string.yellow_flag_duration_tracking_key))
                listWheelPosition = findPreference(getString(R.string.wheel_position_tracking_key))
                switchService = findPreference(getString(R.string.service_tracking_key))
            }
            return super.onCreateView(infl, cont, state)
        }

        fun disableServiceWhenVideoSelected(selected: Boolean) {
            switchService?.isChecked = false
            switchService?.isEnabled = !selected
            if (selected) {
                switchUseAdvancedMesh?.isEnabled = true
                switchMesh?.isEnabled = true
            }
        }

        override fun onResume() {
            switchUseAdvancedMesh?.isChecked = prefs.getUseAdvancedMesh()
            switchMesh?.isChecked = prefs.getShowMesh()
            switchSound?.isChecked = prefs.getSoundEnabled()
            switchService?.isChecked = prefs.getAsService()
            prefs.resetMinMaxFps()

            frames = getListOfFrames() // (10,10) , (15,30) , (30,30)
            val fpsEntries = arrayOfNulls<String>(frames.size) // labels
            val fpsEntryValues = arrayOfNulls<String>(frames.size) // values
            frames.forEachIndexed { i, value ->
                val min = value[0]
                val max = value[1]
                fpsEntries[i] = "min: ${min}fps  |  max: ${max}fps"
                fpsEntryValues[i] = "${min}|${max}"
            }
            listFps?.entries = fpsEntries
            listFps?.entryValues = fpsEntryValues

            val seconds = (1..7).toList() // generate a simple list from 1 to 7 seconds
            val durationEntries = arrayOfNulls<String>(seconds.size) // labels
            seconds.forEachIndexed { i, value ->
                durationEntries[i] = value.toString()
            }
            listRedFlagDuration?.entries = durationEntries
            listRedFlagDuration?.entryValues = durationEntries

            listYellowFlagDuration?.entries = durationEntries
            listYellowFlagDuration?.entryValues = durationEntries

            val wheelPositionEntries = arrayOf(
                WHEEL_POSITION.LEFT.name,
                WHEEL_POSITION.RIGHT.name
            )
            listWheelPosition?.entries = wheelPositionEntries
            listWheelPosition?.entryValues = wheelPositionEntries

            addOnPreferenceChange(switchUseAdvancedMesh)
            addOnPreferenceChange(switchMesh)
            addOnPreferenceChange(switchSound)
            addOnPreferenceChange(switchService)
            super.onResume()
        }

        override fun onPause() {
            super.onPause()
            val finalValue = listFps?.value
            val min = finalValue?.substringBefore("|")?.toInt()
            val max = finalValue?.substringAfter("|")?.toInt()
            min?.let { prefs.saveMinFPS(it) }
            max?.let { prefs.saveMaxFPS(max) }
            listRedFlagDuration?.value?.let {
                val value = (it.toInt() * 1000).toLong()
                prefs.saveRedFlagDuration(value)
            }
            listYellowFlagDuration?.value?.let {
                val value = (it.toInt() * 1000).toLong()
                prefs.saveYellowFlagDuration(value)
            }
            listWheelPosition?.value?.let {
                prefs.saveWheelPosition(it)
            }
        }

        private fun getListOfFrames(): List<IntArray> {
            val listOfFrames = mutableListOf<IntArray>()
            val manager = requireActivity().getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = manager.cameraIdList[0]
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            fpsRanges?.forEach { listOfFrames.add(intArrayOf(it.lower, it.upper)) }
            return listOfFrames
        }

        private fun addOnPreferenceChange(mPreference: SwitchPreference?) {
            mPreference?.setOnPreferenceChangeListener { preference, newValue ->
                val value = newValue as Boolean
                when (preference) {
                    switchUseAdvancedMesh -> prefs.saveUseAdvancedMesh(value)
                    switchMesh -> prefs.saveShowMesh(value)
                    switchSound -> prefs.saveSoundEnabled(value)
                    switchService -> prefs.saveAsService(value)
                }
                true
            }
        }
    }
}
