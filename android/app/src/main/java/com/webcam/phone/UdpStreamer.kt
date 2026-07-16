package com.webcam.phone

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue

class UdpStreamer(private val address: String, private val port: Int) {
    private var socket: DatagramSocket? = null
    private var sequenceNumber = 0L
    private val MTU = 1300
    private var isRunning = false
    private val packetQueue = LinkedBlockingQueue<ByteArray>(2000)
    private var worker: Thread? = null

    fun start() {
        if (isRunning) return
        try {
            socket = DatagramSocket()
            socket?.sendBufferSize = 1024 * 1024
            isRunning = true
            worker = Thread {
                val inetAddress = InetAddress.getByName(address)
                while (isRunning) {
                    try {
                        val data = packetQueue.take()
                        val packet = DatagramPacket(data, data.size, inetAddress, port)
                        socket?.send(packet)
                    } catch (e: Exception) { }
                }
            }
            worker?.priority = Thread.MAX_PRIORITY
            worker?.start()
            Log.d("UdpStreamer", "UDP Engine Started")
        } catch (e: Exception) { Log.e("UdpStreamer", "Start failed", e) }
    }

    fun sendFrame(data: ByteBuffer, timestamp: Long, isKeyframe: Boolean) {
        if (!isRunning) return
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

            if (!packetQueue.offer(packetData.array())) {
                packetQueue.clear() // Drop if network is too slow to stay real-time
            }
            offset += chunkSize
            sequenceNumber++
        }
    }

    fun stop() {
        isRunning = false
        worker?.interrupt()
        socket?.close()
        socket = null
        packetQueue.clear()
    }
}
