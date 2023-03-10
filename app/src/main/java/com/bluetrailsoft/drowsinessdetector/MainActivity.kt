@file:Suppress("PrivatePropertyName")

package com.bluetrailsoft.drowsinessdetector

import android.animation.ValueAnimator.INFINITE
import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluetrailsoft.drowsinessdetector.databinding.ActivityMainBinding
import com.bluetrailsoft.drowsinessdetector.services.DetectorBackgroundService
import com.bluetrailsoft.drowsinessdetector.services.MSG_FLAG_VALUE_NEW
import com.bluetrailsoft.drowsinessdetector.utils.*
import com.bluetrailsoft.drowsinessdetector.utils.SharedActivityFunctions.Source.CAMERA
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector.Companion.checkPermissions
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector.Companion.requestPermissions
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {

    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    /**
     * Variables used to Start/Stop the Service background
     * */
    var bound = false
    var mService: Messenger? = null
    private var mMessenger: Messenger? = null
    private val MSG_START_DETECTION = 1
    private val MSG_STOP_DETECTION = 2
    private lateinit var prefs: SharedPrefs
    private lateinit var pulse: Animation
    private val shFunc = SharedActivityFunctions()

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mService = Messenger(service)
            bound = true
            startDetection()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            mService = null
            bound = false
        }
    }

    init {
        mMessenger = Messenger(IncomingHandler { flag, logs ->
            shFunc.setColorsOnSemaphoreAndPrintLogs(
                context = this@MainActivity,
                source = CAMERA,
                fab = binding.fab,
                soundPlayer = soundPlayer,
                txtLog = binding.txtLog,
                drowsinessFlag = flag,
                logs = logs
            )
        })
    }

    /**
     * Variables used to Start/Stop the normal application
     * */
    private var isDetectionRunning = false
    private var soundPlayer: SoundPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        prefs = SharedPrefs(this)
        if (prefs.getAsService()) {
            binding.layImgCentral.visibility = View.VISIBLE
        } else {
            binding.layPreview.visibility = View.VISIBLE
        }
        setupViewsFromXML()
    }

    @SuppressLint("StringFormatMatches")
    private fun setupViewsFromXML() {
        val advancedMesh = prefs.getUseAdvancedMesh()
        val minFps = prefs.getMinFPS()
        var maxFps = prefs.getMaxFPS()
        val redDuration = prefs.getRedFlagDuration()
        val yellowDuration = prefs.getYellowFlagDuration()
        var wheelPosition = prefs.getWheelPosition()
        val service = prefs.getAsService()
        val sound = prefs.getSoundEnabled()
        val mesh = prefs.getShowMesh()
        if (maxFps == -1) { maxFps = 30 }
        if (wheelPosition.isEmpty()) {
            wheelPosition = WHEEL_POSITION.LEFT.name
        }

        binding.txtFPS.text = getString(R.string.log_top_right, maxFps, wheelPosition)
        soundPlayer = if (sound) SoundPlayer(this, R.raw.alarm_one) else null

        if (service) {
            // start detection as service
            pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            pulse.repeatCount = INFINITE
            enableButtonToStartService()
        } else {
            // start detection as normal application
            runDetectionWithPreview(
                advancedMesh = advancedMesh,
                minFps = minFps,
                maxFps = maxFps,
                redDuration = redDuration,
                yellowDuration = yellowDuration,
                wheelPosition = wheelPosition,
                showMesh = mesh
            )
        }
    }

    private fun runDetectionWithPreview(
        advancedMesh: Boolean,
        minFps: Int,
        maxFps: Int,
        redDuration: Long,
        yellowDuration: Long,
        wheelPosition: String,
        showMesh: Boolean
    ) {
        if (!checkPermissions(this)) {
            requestPermissions(this)
            return
        }
        val position = shFunc.getWheelPosition(wheelPosition)
        val cameraPreview = findViewById<ConstraintLayout>(R.id.layPreview)
        val detectionConfig = DetectionConfig(
            useAdvancedMesh = advancedMesh,
            minFps = minFps,
            maxFps = maxFps,
            redFlagDuration = redDuration,
            yellowFlagDuration = yellowDuration,
            cameraPreview = cameraPreview,
            wheelPosition = position,
            showMesh = showMesh
        )
        val detector = DrowsinessDetector(this, detectionConfig) {
            // show top|left imageView only if bitmap exists (it means 'dark filter' is active)
            binding.imgTest.visibility = if (it == null) View.GONE else View.VISIBLE
            binding.imgTest.setImageBitmap(it)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                detector.fatigueStateFlow.collect { result ->
                    val stateFlag = result.flag
                    val logs = result.stateLogs
                    shFunc.setColorsOnSemaphoreAndPrintLogs(
                        context = this@MainActivity,
                        source = CAMERA,
                        fab = binding.fab,
                        soundPlayer = soundPlayer,
                        txtLog = binding.txtLog,
                        drowsinessFlag = stateFlag,
                        logs = logs
                    )
                }
            }
        }

        // start detection automatically after the class is instantiated
        Handler(Looper.getMainLooper()).postDelayed({
            startAppDetection(detector, isService = false)
        }, 10)

        with(binding.fabStart) {
            setOnClickListener {
                isDetectionRunning = if (isDetectionRunning) {
                    setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_play))
                    detector.stopDetection()
                    shFunc.showLogsOnScreen(
                        context = context,
                        source = CAMERA,
                        message = "⏹️ —> STOP Detection",
                        txtLog = binding.txtLog
                    )
                    false
                } else {
                    startAppDetection(detector, isService = false)
                    true
                }
            }
        }
    }

    private fun startAppDetection(detector: DrowsinessDetector? = null, isService: Boolean) {
        if (isService) {
            startService()
            binding.imgCentral.startAnimation(pulse)
            shFunc.showLogsOnScreen(
                context = this,
                source = CAMERA,
                message = "▶️ —> START Detection as Service",
                txtLog = binding.txtLog
            )
        } else {
            detector?.startDetection()
            shFunc.showLogsOnScreen(
                context = this,
                source = CAMERA,
                message = "▶️ —> START Detection",
                txtLog = binding.txtLog
            )
        }
        binding.fabStart.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_pause))
        isDetectionRunning = true
    }

    private fun enableButtonToStartService() {
        with(binding.fabStart) {
            setOnClickListener {
                isDetectionRunning = if (isDetectionRunning) {
                    setImageDrawable(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_play))
                    stopService()
                    binding.imgCentral.clearAnimation()
                    shFunc.showLogsOnScreen(
                        context = context,
                        source = CAMERA,
                        message = "⏹️ —> STOP Detection as Service",
                        txtLog = binding.txtLog
                    )
                    false
                } else {
                    startAppDetection(isService = true)
                    true
                }
            }
        }

        // start detection automatically after the class is instantiated
        Handler(Looper.getMainLooper()).postDelayed({
            startAppDetection(isService = true)
        }, 10)
    }

    internal class IncomingHandler(private val result: (Int, String?) -> Unit) : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_FLAG_VALUE_NEW -> {
                    // Here receive the values (flag + logs) from the Service
                    val drowsinessFlag = msg.arg1
                    val logs = msg.data.getString("logs")
                    result.invoke(drowsinessFlag, logs)
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    private fun startService() {
        if (!checkPermissions(this)) {
            requestPermissions(this)
            return
        }
        if (bound) { stopService() }
        bindService(getServiceIntent(), mConnection, BIND_AUTO_CREATE)
    }

    private fun getServiceIntent() = Intent(this,
        DetectorBackgroundService::class.java).apply {
            putExtra(useAdvancedMesh, prefs.getUseAdvancedMesh())
            putExtra(minFps, prefs.getMinFPS())
            putExtra(maxFps, prefs.getMaxFPS())
            putExtra(currentRedFlagDuration, prefs.getRedFlagDuration())
            putExtra(currentYellowFlagDuration, prefs.getYellowFlagDuration())
            putExtra(wheelPosition, prefs.getWheelPosition())
            putExtra(mesh, prefs.getShowMesh())
        }

    private fun stopService() {
        if (!bound) {
            bindService(getServiceIntent(), mConnection, BIND_AUTO_CREATE)
        }
        stopDetection()
        unbindService(mConnection)
        mService = null
        bound = false
        stopService(getServiceIntent())
    }

    private fun stopDetection() {
        if (!bound) return
        val msg = Message.obtain(null, MSG_STOP_DETECTION, 0, 0)
        try {
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }

    fun startDetection() {
        if (!bound) return
        try {
            val msg = Message.obtain(null, MSG_START_DETECTION, 0, 0)
            msg.replyTo = mMessenger
            mService?.send(msg)
        } catch (e: RemoteException) {
            e.printStackTrace()
        }
    }
}
