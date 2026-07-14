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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.viewFinder)

        controlServer = ControlServer(8080)
        controlServer?.start()

        // For now, start streaming to localhost (USB mode default)
        udpStreamer = UdpStreamer("127.0.0.1", 5005)
        udpStreamer?.start()

        videoEncoder = VideoEncoder(1280, 720, 2000000, 30) { data, info ->
            val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            udpStreamer?.sendFrame(data, info.presentationTimeUs, isKeyframe)
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            videoEncoder?.start()

            val recorder = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider { request ->
                        val surface = videoEncoder?.inputSurface
                        if (surface != null) {
                            request.provideSurface(surface, ContextCompat.getMainExecutor(this)) {
                                // Surface result
                            }
                        }
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, recorder
                )
            } catch (e: Exception) {
                Log.e("MainActivity", "Use case binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        controlServer?.stop()
        videoEncoder?.stop()
        udpStreamer?.stop()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
