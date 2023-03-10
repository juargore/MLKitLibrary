package com.bluetrailsoft.drowsinessdetector.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluetrailsoft.drowsinessdetector.utils.maxFps
import com.bluetrailsoft.drowsinessdetector.utils.mesh
import com.bluetrailsoft.drowsinessdetector.utils.minFps
import com.bluetrailsoft.drowsinessdetector.utils.currentRedFlagDuration
import com.bluetrailsoft.drowsinessdetector.utils.useAdvancedMesh
import com.bluetrailsoft.drowsinessdetector.utils.wheelPosition
import com.bluetrailsoft.drowsinessdetector.utils.currentYellowFlagDuration
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.DrowsinessDetector
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import kotlinx.coroutines.launch

private const val MSG_START_DETECTION = 1
private const val MSG_STOP_DETECTION = 2
const val MSG_FLAG_VALUE_NEW = 3

class DetectorBackgroundService: Service(), LifecycleOwner {

    private val mServiceLifecycleDispatcher = ServiceLifecycleDispatcher(this)
    private lateinit var mMessenger: Messenger
    private var detector: DrowsinessDetector? = null
    private var mClients = ArrayList<Messenger>()

    internal class IncomingHandler(
        private val detector: DrowsinessDetector,
        private val mClients: ArrayList<Messenger>
    ) : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_START_DETECTION -> {
                    mClients.add(msg.replyTo)
                    detector.startDetection()
                }
                MSG_STOP_DETECTION -> {
                    mClients.remove(msg.replyTo)
                    detector.stopDetection()
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun getLifecycle() = mServiceLifecycleDispatcher.lifecycle

    override fun onBind(p0: Intent?): IBinder? {
        p0?.extras?.let {
            val advancedMesh = it.getBoolean(useAdvancedMesh)
            val minFps = it.getInt(minFps)
            val maxFps = it.getInt(maxFps)
            val redDuration = it.getLong(currentRedFlagDuration)
            val yellowDuration = it.getLong(currentYellowFlagDuration)
            val wheelPosition = it.getString(wheelPosition)
            val showMesh = it.getBoolean(mesh)

            val position =
                if (wheelPosition == WHEEL_POSITION.LEFT.name || wheelPosition.isNullOrEmpty())
                    WHEEL_POSITION.LEFT else WHEEL_POSITION.RIGHT

            mServiceLifecycleDispatcher.onServicePreSuperOnBind()
            val detectionConfig = DetectionConfig(
                useAdvancedMesh = advancedMesh,
                minFps = minFps,
                maxFps = maxFps,
                redFlagDuration = redDuration,
                yellowFlagDuration = yellowDuration,
                wheelPosition = position,
                showMesh = showMesh,
                cameraPreview = null
            )
            detector = DrowsinessDetector(
                context = this,
                detectionConfig = detectionConfig
            ) {

            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                detector?.fatigueStateFlow?.collect { result ->
                    val drowsinessFlag = result.flag
                    val logs = result.stateLogs
                    for (i in mClients.indices.reversed()) {
                        try {
                            val r = Message.obtain(null, MSG_FLAG_VALUE_NEW, drowsinessFlag, 0)
                            r.data.putString("logs", logs)
                            mClients[i].send(r)
                        } catch (e: RemoteException) {
                            mClients.removeAt(i)
                        }
                    }
                }
            }
        }

        mMessenger = Messenger(IncomingHandler(detector!!, mClients))
        return mMessenger.binder
    }

    override fun onCreate() {
        mServiceLifecycleDispatcher.onServicePreSuperOnCreate()
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        mServiceLifecycleDispatcher.onServicePreSuperOnStart()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        detector?.stopDetection()
        mServiceLifecycleDispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        alarmService[AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000] =
            restartServicePendingIntent
        super.onTaskRemoved(rootIntent)
    }
}
