package com.webcam.phone

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitRate: Int,
    private val frameRate: Int,
    private val onEncodedFrame: (ByteBuffer, MediaCodec.BufferInfo) -> Unit
) {
    private var encoder: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    private val isRunning = AtomicBoolean(false)
    private var frameCount = 0
    private val TIMEOUT_USEC = 10000L

    fun start() {
        if (isRunning.get()) return
        
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1 second
            format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            // Force Baseline profile for maximum Windows compatibility
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            
            // Add extra parameters to force SPS/PPS headers frequently
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                format.setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder?.createInputSurface()
            encoder?.start()
            
            isRunning.set(true)
            Log.d("VideoEncoder", "Encoder started: ${width}x${height}")

            Thread {
                drainEncoder()
            }.start()
        } catch (e: Exception) {
            Log.e("VideoEncoder", "Failed to start encoder", e)
        }
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isRunning.get()) {
            val codec = encoder ?: break
            try {
                val encoderStatus = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)
                if (encoderStatus >= 0) {
                    val encodedData = codec.getOutputBuffer(encoderStatus)
                    if (encodedData != null) {
                        onEncodedFrame(encodedData, bufferInfo)
                        codec.releaseOutputBuffer(encoderStatus, false)
                        
                        frameCount++
                        if (frameCount % 100 == 0) {
                            val firstBytes = StringBuilder()
                            for (i in 0 until minOf(bufferInfo.size, 10)) {
                                firstBytes.append(String.format("%02X ", encodedData.get(bufferInfo.offset + i)))
                            }
                            Log.d("VideoEncoder", "Encoded $frameCount frames. Latest size: ${bufferInfo.size}, First bytes: $firstBytes")
                        }
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = codec.outputFormat
                    Log.d("VideoEncoder", "Encoder output format changed: $newFormat")
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e("VideoEncoder", "Error in drain loop", e)
                }
                break
            }
        }
        Log.d("VideoEncoder", "Drain thread finished")
    }

    fun stop() {
        isRunning.set(false)
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            inputSurface?.release()
            inputSurface = null
        } catch (e: Exception) {
            Log.e("VideoEncoder", "Error stopping encoder", e)
        }
    }
}
