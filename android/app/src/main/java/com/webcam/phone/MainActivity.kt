package com.webcam.phone

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaCodec
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private var controlServer: ControlServer? = null
    private var videoEncoder: VideoEncoder? = null
    private var udpStreamer: UdpStreamer? = null
    private lateinit var viewFinder: PreviewView
    private lateinit var ipText: TextView
    private lateinit var statusText: TextView
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        viewFinder = findViewById(R.id.viewFinder)
        ipText = findViewById(R.id.ipAddressText)
        statusText = findViewById(R.id.statusText)

        displayIpAddress()

        controlServer = ControlServer(8080)
        controlServer?.onClientConnected = { pcIp ->
            runOnUiThread {
                statusText.text = "Status: Connected to $pcIp"
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

    private fun displayIpAddress() {
        thread {
            try {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                var foundIp = "Not found"
                while (interfaces.hasMoreElements()) {
                    val iface = interfaces.nextElement()
                    if (iface.isLoopback || !iface.isUp) continue
                    val addresses = iface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is java.net.Inet4Address) {
                            foundIp = addr.hostAddress
                            break
                        }
                    }
                }
                runOnUiThread {
                    ipText.text = "IP: $foundIp"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    ipText.text = "IP: Error"
                }
            }
        }
    }

    private fun startStreaming(pcIp: String) {
        videoEncoder?.stop()
        udpStreamer?.stop()

        udpStreamer = UdpStreamer(pcIp, 6000) // Changed to 6000
        udpStreamer?.start()

        videoEncoder = VideoEncoder(640, 480, 1500000, 30) { data, info ->
            val isKeyframe = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
            udpStreamer?.sendFrame(data, info.presentationTimeUs, isKeyframe)
        }
        videoEncoder?.start()
        startCamera()
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
