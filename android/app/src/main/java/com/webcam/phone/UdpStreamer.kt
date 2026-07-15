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
    private var packetCount = 0L
    private val MTU = 1300 

    fun start() {
        try {
            socket = DatagramSocket()
            Log.d("UdpStreamer", "Streaming socket opened, target: $address:$port")
        } catch (e: Exception) {
            Log.e("UdpStreamer", "Failed to start UDP socket", e)
        }
    }

    fun sendFrame(data: ByteBuffer, timestamp: Long, isKeyframe: Boolean) {
        val bytes = ByteArray(data.remaining())
        data.get(bytes)

        var offset = 0
        while (offset < bytes.size) {
            val chunkSize = minOf(bytes.size - offset, MTU)
            val isEndOfFrame = (offset + chunkSize) >= bytes.size
            
            var flags = 0
            if (isKeyframe) flags = flags or 0x01
            if (isEndOfFrame) flags = flags or 0x02

            val packetData = ByteBuffer.allocate(13 + chunkSize).order(ByteOrder.BIG_ENDIAN)
            packetData.putInt(sequenceNumber.toInt())
            packetData.putLong(timestamp)
            packetData.put(flags.toByte())
            packetData.put(bytes, offset, chunkSize)

            try {
                val packet = DatagramPacket(
                    packetData.array(),
                    packetData.capacity(),
                    InetAddress.getByName(address),
                    port
                )
                socket?.send(packet)
                packetCount++
                if (packetCount % 500 == 0L) {
                    Log.d("UdpStreamer", "Sent $packetCount packets to $address")
                }
            } catch (e: Exception) {
                Log.e("UdpStreamer", "Send failed to $address", e)
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
