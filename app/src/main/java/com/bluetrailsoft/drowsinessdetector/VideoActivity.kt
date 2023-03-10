package com.bluetrailsoft.drowsinessdetector

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bluetrailsoft.drowsinessdetector.databinding.ActivityBaseVideoBinding
import com.bluetrailsoft.drowsinessdetector.extensions.checkForStoragePermissions
import com.bluetrailsoft.drowsinessdetector.utils.SharedActivityFunctions
import com.bluetrailsoft.drowsinessdetector.utils.SharedActivityFunctions.Source.VIDEO
import com.bluetrailsoft.drowsinessdetector.utils.SharedPrefs
import com.bluetrailsoft.drowsinessdetector.utils.SoundPlayer
import com.bluetrailsoft.drowsinessdetector.utils.advanced
import com.bluetrailsoft.drowsinessdetector.utils.left
import com.bluetrailsoft.drowsinessdetector.utils.mTrue
import com.bluetrailsoft.drowsinessdetector.utils.meshStr
import com.bluetrailsoft.drowsinessdetector.utils.redFlagDuration
import com.bluetrailsoft.drowsinessdetector.utils.renaultFolder
import com.bluetrailsoft.drowsinessdetector.utils.showMesh
import com.bluetrailsoft.drowsinessdetector.utils.uriSchemeContent
import com.bluetrailsoft.drowsinessdetector.utils.video
import com.bluetrailsoft.drowsinessdetector.utils.wheelPos
import com.bluetrailsoft.drowsinessdetector.utils.yellowFlagDuration
import com.bluetrailsoft.drowsinessmodule.DetectionConfig
import com.bluetrailsoft.drowsinessmodule.WHEEL_POSITION
import com.bluetrailsoft.drowsinessmodule.facedetector.DrowsinessDetectorMainVideo
import com.bluetrailsoft.drowsinessmodule.facedetector.overlay.GraphicOverlay
import com.bluetrailsoft.drowsinessmodule.facedetector.utils.oneSecond
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class VideoActivity: AppCompatActivity(), TextureView.SurfaceTextureListener {

    private val binding: ActivityBaseVideoBinding by lazy {
        ActivityBaseVideoBinding.inflate(layoutInflater)
    }

    private var textureView: TextureView? = null
    private var playerSurface: Surface? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var soundPlayer: SoundPlayer? = null
    private lateinit var player: ExoPlayer
    private lateinit var prefs: SharedPrefs

    private lateinit var graphicOverlay: GraphicOverlay
    private lateinit var detector: DrowsinessDetectorMainVideo
    private val shFunc = SharedActivityFunctions()

    private var frameCount = 0
    private var fps = 0
    private var prevTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForStoragePermissions {
            setContentView(binding.root)
            prefs = SharedPrefs(this)
            player = ExoPlayer.Builder(this).build()

            val advancedMesh = prefs.getUseAdvancedMesh()
            val minFps = prefs.getMinFPS()
            var maxFps = prefs.getMaxFPS()
            val redDuration = prefs.getRedFlagDuration()
            val yellowDuration = prefs.getYellowFlagDuration()
            val wheelPosition = prefs.getWheelPosition()
            val sound = prefs.getSoundEnabled()
            val mesh = prefs.getShowMesh()
            if (maxFps == -1) { maxFps = 30 }

            binding.playerView.player = player
            soundPlayer = if (sound) SoundPlayer(this, R.raw.alarm_one) else null

            val videoFrameView = createVideoFrameView()
            val contentFrame = binding.playerView.findViewById<FrameLayout>(
                com.google.android.exoplayer2.ui.R.id.exo_content_frame
            )
            contentFrame.addView(videoFrameView)
            graphicOverlay = GraphicOverlay(this, null)
            contentFrame.addView(graphicOverlay)

            val position: WHEEL_POSITION = shFunc.getWheelPosition(wheelPosition)
            val detectionConfig = DetectionConfig(
                useAdvancedMesh = advancedMesh,
                minFps = minFps,
                maxFps = maxFps,
                redFlagDuration = redDuration,
                yellowFlagDuration = yellowDuration,
                cameraPreview = null,
                wheelPosition = position,
                showMesh = mesh
            )

            // code used when video is sent from adb command for automation purposes
            val video = intent.extras?.getString(video)
            if (video != null) {
                val path = Environment.getExternalStorageDirectory().toString() + "$renaultFolder$video"
                val file = File(path)
                if (!file.exists()) {
                    detector = DrowsinessDetectorMainVideo(this, graphicOverlay, detectionConfig)
                } else {
                    shFunc.mustCreateNewFile = true
                    shFunc.currentVideoName = getFileNameFromUri(Uri.fromFile(file))
                    prevTime = System.currentTimeMillis()
                    setupPlayer(Uri.fromFile(file))
                    detector = DrowsinessDetectorMainVideo(this, graphicOverlay, detectionConfig.also { config ->
                        intent.extras?.getString(showMesh)?.let { config.showMesh = it == mTrue }
                        intent.extras?.getString(meshStr)?.let { config.useAdvancedMesh = it == advanced }
                        intent.extras?.getString(redFlagDuration)?.let { config.redFlagDuration = it.toLong() }
                        intent.extras?.getString(yellowFlagDuration)?.let { config.yellowFlagDuration = it.toLong() }
                        intent.extras?.getString(wheelPos)?.let {
                            config.wheelPosition = if (it == left) WHEEL_POSITION.LEFT else WHEEL_POSITION.RIGHT
                        }
                    })
                    detector.startDetection()
                    detector.createImageProcessor()
                }
            } else {
                // no adb command found -> continue video normally
                binding.chooseBtn.setOnClickListener {
                    startChooseVideoIntentForResult()
                }
                detector = DrowsinessDetectorMainVideo(this, graphicOverlay, detectionConfig)
            }

            // start collecting the data that state flow is returning from library
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    detector.fatigueStateFlow.collect { result ->
                        val drowsinessFlag = result.flag
                        val logs = result.stateLogs
                        val timeVideo = (player.currentPosition / 1000).toInt()
                        shFunc.setColorsOnSemaphoreAndPrintLogs(
                            context = this@VideoActivity,
                            source = VIDEO,
                            fab = binding.fab,
                            soundPlayer = soundPlayer,
                            txtLog = binding.txtLog,
                            drowsinessFlag = drowsinessFlag,
                            logs = logs,
                            timeVideo = timeVideo,
                            fpsVideo = fps
                        )
                    }
                }
            }
        }
    }

    private fun createVideoFrameView(): TextureView? {
        textureView = TextureView(this)
        textureView?.surfaceTextureListener = this
        return textureView
    }

    private val chooseVideoIntentForResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    shFunc.mustCreateNewFile = true
                    shFunc.currentVideoName = getFileNameFromUri(it)
                    prevTime = System.currentTimeMillis()
                    detector.startDetection()
                    detector.createImageProcessor()
                    setupPlayer(it)
                }
            }
        }

    @SuppressLint("Range")
    fun getFileNameFromUri(uri: Uri): String {
        var result = ""
        if (uri.scheme == uriSchemeContent) {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.let {
                if (it.moveToFirst()) {
                    result = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }; cursor?.close()
        } else {
            uri.path?.let {
                result = it
                val cut = result.lastIndexOf('/')
                if (cut != -1) {
                    result = result.substring(cut + 1)
                }; return result
            }
        }
        return result
    }

    private fun startChooseVideoIntentForResult() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_GET_CONTENT
        chooseVideoIntentForResult.launch(intent)
    }

    private fun setupPlayer(uri: Uri, path: String? = null) {
        val mediaItem = if (path != null) {
            MediaItem.fromUri(path)
        } else {
            MediaItem.fromUri(uri)
        }
        player.stop()
        player.setMediaItem(mediaItem)
        player.prepare()

        binding.layControl.visibility = View.INVISIBLE
        lifecycleScope.launch {
            delay(oneSecond)
            player.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::player.isInitialized) {
            player.pause()
        }
        stopImageProcessor()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::player.isInitialized) {
            player.stop()
            player.release()
        }
    }

    private fun stopImageProcessor() {
        if (::detector.isInitialized) {
            detector.stopImageProcessor()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceTexture = surface
        playerSurface = Surface(surface)
        player.setVideoSurface(playerSurface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        player.setVideoSurface(null)
        playerSurface?.release()
        playerSurface = null
        surfaceTexture?.release()
        surfaceTexture = null
        return true
    }

    override fun onResume() {
        val shrPrefs = SharedPrefs(this)
        if (shrPrefs.getCheckingSettings()) {
            // refresh UI because it comes from settings or notification screen
            shrPrefs.saveCheckingSettings(false)
            finish()
            startActivity(intent)
        }
        super.onResume()
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        val size = shFunc.getSizeForDesiredSize(textureView!!.width, textureView!!.height)
        val frame = textureView!!.getBitmap(size.width, size.height)

        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - prevTime >= 1000) {
            fps = frameCount
            frameCount = 0
            prevTime = currentTime
        }

        frame?.let { bmp ->
            detector.processFrame(bmp) {
                // show top|left imageView only if bitmap exists (it means 'dark filter' is active)
                binding.imgTest.visibility = if (it == null) View.GONE else View.VISIBLE
                binding.imgTest.setImageBitmap(it)
            }
        }
    }
}
