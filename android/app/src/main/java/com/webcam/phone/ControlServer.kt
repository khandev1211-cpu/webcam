package com.webcam.phone

import android.util.Log
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class ControlServer(private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(port)
                Log.d("ControlServer", "Server started on port $port")
                while (isRunning) {
                    val client = serverSocket?.accept()
                    if (client != null) {
                        handleClient(client)
                    }
                }
            } catch (e: Exception) {
                Log.e("ControlServer", "Server error", e)
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread {
            try {
                val output = DataOutputStream(socket.getOutputStream())
                
                // Send "hello" message as per protocol
                val helloMsg = JSONObject().apply {
                    put("type", "hello")
                    put("deviceName", android.os.Build.MODEL)
                    put("resolutions", listOf("1280x720", "1920x1080")) // Placeholder
                }
                
                sendJson(output, helloMsg)
                
                // Keep connection open and listen for commands
                val input = socket.getInputStream()
                while (isRunning && !socket.isClosed) {
                    // Logic to read length-prefixed JSON from PC
                    // (To be implemented)
                    Thread.sleep(1000)
                }
                
            } catch (e: Exception) {
                Log.e("ControlServer", "Client error", e)
            } finally {
                socket.close()
            }
        }
    }

    private fun sendJson(output: DataOutputStream, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charsets.UTF_8)
        val lengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(bytes.size)
        output.write(lengthBuffer.array())
        output.write(bytes)
        output.flush()
    }

    fun stop() {
        isRunning = false
        serverSocket?.close()
    }
}
