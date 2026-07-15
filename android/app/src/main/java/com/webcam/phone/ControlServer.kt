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
    var onClientConnected: ((String) -> Unit)? = null
    var onSwitchCamera: (() -> Unit)? = null

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
                        val clientIp = client.inetAddress.hostAddress
                        Log.d("ControlServer", "PC Connected from: $clientIp")
                        onClientConnected?.invoke(clientIp)
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
                val input = socket.getInputStream()
                
                val helloMsg = JSONObject().apply {
                    put("type", "hello")
                    put("deviceName", android.os.Build.MODEL)
                }
                sendJson(output, helloMsg)
                
                val lengthBuffer = ByteArray(4)
                while (isRunning && !socket.isClosed) {
                    val read = input.read(lengthBuffer)
                    if (read == 4) {
                        val length = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.BIG_ENDIAN).int
                        val payloadBytes = ByteArray(length)
                        input.read(payloadBytes)
                        val json = JSONObject(String(payloadBytes))
                        
                        if (json.getString("type") == "switch_camera") {
                            onSwitchCamera?.invoke()
                        }
                    }
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
