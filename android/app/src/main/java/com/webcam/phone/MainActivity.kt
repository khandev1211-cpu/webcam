package com.webcam.phone

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private var controlServer: ControlServer? = null
    private var videoEncoder: VideoEncoder? = null
    private var udpStreamer: UdpStreamer? = null
    private lateinit var viewFinder: PreviewView
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.viewFinder)

        controlServer = ControlServer(8080)
        controlServer?.onClientConnected = { pcIp ->
            runOnUiThread {
                startStreaming(pcIp)
            }
        }
        controlServer?.onSwitchCamera = {
            runOnUiThread {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                startCamera()
            }
        }
        controlServer?.start()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, 10)
        }
    }

    private fun startStreaming(pcIp: String) {
        udpStreamer?.stop()
        videoEncoder?.stop()

        udpStreamer = UdpStreamer(pcIp, 5005)
        udpStreamer?.start()

        videoEncoder = VideoEncoder(640, 480, 1500000, 30) { data, info ->
            val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            udpStreamer?.sendFrame(data, info.presentationTimeUs, isKeyframe)
        }
        videoEncoder?.start()
        startCamera() // Re-bind camera to new encoder surface
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(viewFinder.surfaceProvider) }
            
            val recorder = Preview.Builder().build().also {
                it.setSurfaceProvider { request ->
                    videoEncoder?.inputSurface?.let { surface ->
                        request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {}
                    }
                }
            }

            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, recorder)
            } catch (e: Exception) {
                Log.e("MainActivity", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        controlServer?.stop()
        videoEncoder?.stop()
        udpStreamer?.stop()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
