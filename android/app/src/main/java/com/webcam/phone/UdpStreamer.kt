package com.webcam.phone

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UdpStreamer(private val address: String, private val port: Int) {
    private var socket: DatagramSocket? = null
    private var sequenceNumber = 0L
    private val MTU = 1300 // Safe MTU for UDP to avoid fragmentation

    fun start() {
        try {
            socket = DatagramSocket()
            Log.d("UdpStreamer", "Streaming to $address:$port")
        } catch (e: Exception) {
            Log.e("UdpStreamer", "Failed to start UDP socket", e)
        }
    }

    fun sendFrame(data: ByteBuffer, timestamp: Long, isKeyframe: Boolean) {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)

        // Fragment data if larger than MTU
        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(bytes.size - offset, MTU)
            
            // Header: Seq(4) + Timestamp(8) + Flags(1) = 13 bytes
            val packetData = ByteBuffer.allocate(13 + chunkSize).order(ByteOrder.BIG_ENDIAN)
            packetData.putInt(sequenceNumber.toInt())
            packetData.putLong(timestamp)
            packetData.put(if (isKeyframe) 1.toByte() else 0.toByte())
            packetData.put(bytes, offset, chunkSize)

            try {
                val packet = DatagramPacket(
                    packetData.array(),
                    packetData.capacity(),
                    InetAddress.getByName(address),
                    port
                )
                socket?.send(packet)
            } catch (e: Exception) {
                Log.e("UdpStreamer", "Send failed", e)
            }

            offset += chunkSize
            sequenceNumber++
        }
    }

    fun stop() {
        socket?.close()
        socket = null
    }
}
