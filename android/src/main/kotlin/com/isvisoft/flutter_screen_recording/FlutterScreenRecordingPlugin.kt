package com.isvisoft.flutter_screen_recording

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.io.File
import java.io.IOException


class FlutterScreenRecordingPlugin(
    private val registrar: Registrar
) : MethodCallHandler,
    PluginRegistry.ActivityResultListener {

    var mScreenDensity: Int = 0
    var mMediaRecorder: MediaRecorder? = null
    var mProjectionManager: MediaProjectionManager? = null
    var mMediaProjection: MediaProjection? = null
    var mMediaProjectionCallback: MediaProjectionCallback? = null
    var mVirtualDisplay: VirtualDisplay? = null
    var mDisplayWidth: Int = 1280
    var mDisplayHeight: Int = 720
    var mDelay: Double = 300.0
    var storePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath + File.separator
    var videoName: String? = ""
    var recordAudio: Boolean? = false;
    private val SCREEN_RECORD_REQUEST_CODE = 333
    private val SCREEN_STOP_RECORD_REQUEST_CODE = 334

    private lateinit var _result: MethodChannel.Result

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "flutter_screen_recording")
            val plugin = FlutterScreenRecordingPlugin(registrar)
            channel.setMethodCallHandler(plugin)
            registrar.addActivityResultListener(plugin)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                //initMediaRecorder();

                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed({
                    mMediaProjectionCallback = MediaProjectionCallback()
                    mMediaProjection = mProjectionManager?.getMediaProjection(resultCode, data!!)
                    mMediaProjection?.registerCallback(mMediaProjectionCallback, null)
                    mVirtualDisplay = createVirtualDisplay()
                    mMediaRecorder?.prepare()
                    try {
                        mMediaRecorder?.start()
                    } catch (e: IOException) {
                        println("ERR");
                        Log.d("--INIT-RECORDER", "MediaProjection Start stop")
                        println("Error startRecordScreen")
                        println(e.message)
                    }
                }, mDelay.toLong())
                _result.success(true)
                return true
            } else {
                mMediaRecorder?.release()
                _result.success(false)
            }
        }

        return false
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "startRecordScreen") {
            try {
                _result = result
                mMediaRecorder = MediaRecorder()
                mProjectionManager = registrar.context().applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager?

                videoName = call.argument<String?>("name")
                recordAudio = call.argument<Boolean?>("audio")
                val width = call.argument<Int?>("width");
                val height = call.argument<Int?>("height");
                val delay = call.argument<Double?>("delayDuration");
                delay?.let{
                    mDelay = delay;
                }

                calculeResolution(width, height);
                initMediaRecorder();
                startRecordScreen()
                //result.success(true)
            } catch (e: Exception) {
                println("Error onMethodCall startRecordScreen")
                println(e.message)
                result.success(false)
            }

        } else if (call.method == "stopRecordScreen") {
            try {
                if (mMediaRecorder != null) {
                    stopRecordScreen()
                    result.success("${storePath}${videoName}.mp4")
                } else {
                    result.success("")
                }
            } catch (e: Exception) {
                result.success("")
            }

        } else {
            result.notImplemented()
        }
    }

    fun calculeResolution(width: Int?, height: Int?) {
        val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val screenSize = Point()
        windowManager.defaultDisplay.getRealSize(screenSize);

        println(screenSize.x.toString() + " --- " + screenSize.y.toString())
        println("height - " + height)

        // mDisplayHeight = height.toInt()
        // mDisplayWidth = screenSize.x;
        // mDisplayHeight = screenSize.y;
        mDisplayWidth = width ?: screenSize.x;
        mDisplayHeight = height ?: screenSize.y;

        println("Scaled Density")
        //println(metrics.scaledDensity)
        println("Original Resolution ")
        //println(metrics.widthPixels.toString() + " x " + metrics.heightPixels)
        println("Calcule Resolution ")
        println("$mDisplayWidth x $mDisplayHeight")
    }

    fun initMediaRecorder() {
        mMediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        initAudioRecorder()

        mMediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)

        println(mDisplayWidth.toString() + " " + mDisplayHeight);
        mMediaRecorder?.setVideoSize(mDisplayWidth, mDisplayHeight)
        mMediaRecorder?.setVideoFrameRate(30)

        mMediaRecorder?.setOutputFile("${storePath}${videoName}.mp4")

        println("file --- " + "${storePath}${videoName}.mp4")

        mMediaRecorder?.setVideoEncodingBitRate(5 * mDisplayWidth * mDisplayHeight)
        mMediaRecorder?.prepare()
    }

    private fun initAudioRecorder() {
        // This has to come before setOutputFormat
        if (recordAudio!!) {
            mMediaRecorder?.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        }

        mMediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);

        if (recordAudio!!) {
            mMediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);//AAC //HE_AAC
            mMediaRecorder?.setAudioEncodingBitRate(16 * 44100);
            mMediaRecorder?.setAudioSamplingRate(44100);
        }

    }

    fun startRecordScreen() {
        val permissionIntent = mProjectionManager?.createScreenCaptureIntent()
        ActivityCompat.startActivityForResult(registrar.activity()!!, permissionIntent!!, SCREEN_RECORD_REQUEST_CODE, null)

    }

    fun stopRecordScreen() {
        try {

            mMediaRecorder?.stop()
            mMediaRecorder?.reset()
            mMediaRecorder?.release();
            scanMedia();
            println("stopRecordScreen success")

        } catch (e: Exception) {
            Log.d("--INIT-RECORDER", "MediaProjection Stopped")
            println("stopRecordScreen error")
            println(e.message)

        } finally {
            stopScreenSharing()
        }
    }

    private fun createVirtualDisplay(): VirtualDisplay? {
        val windowManager = registrar.context().applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics: DisplayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        println("density " + mScreenDensity.toString())
        println("msurface " + mMediaRecorder?.getSurface())
        println("aaa" + mDisplayWidth.toString() + " " + mDisplayHeight);

        return mMediaProjection?.createVirtualDisplay(
            "MainActivity",
            mDisplayWidth,
            mDisplayHeight,
            mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mMediaRecorder?.getSurface(),
            null,
            null
        )
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay != null) {
            mVirtualDisplay?.release()
            if (mMediaProjection != null) {
                mMediaProjection?.unregisterCallback(mMediaProjectionCallback)
                mMediaProjection?.stop()
                mMediaProjection = null
            }
            Log.d("TAG", "MediaProjection Stopped")
        }
    }
    private fun scanMedia(){
        SingleMediaScanner(registrar.context(), File("${storePath}${videoName}.mp4"));
    }

    inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            mMediaRecorder?.stop()
            mMediaRecorder?.reset()

            mMediaProjection = null
            stopScreenSharing()
        }
    }

}