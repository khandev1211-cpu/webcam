package com.webcam.phone

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

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

    private val TIMEOUT_USEC = 10000L

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // 2 seconds between keyframes

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder?.createInputSurface()
        encoder?.start()

        Thread {
            drainEncoder()
        }.start()
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (encoder != null) {
            val encoderStatus = encoder?.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC) ?: -1
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // No output available yet
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Format changed, can ignore for raw H.264 stream
            } else if (encoderStatus >= 0) {
                val encodedData = encoder?.getOutputBuffer(encoderStatus)
                if (encodedData != null) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    
                    onEncodedFrame(encodedData, bufferInfo)
                    
                    encoder?.releaseOutputBuffer(encoderStatus, false)
                }
            }
        }
    }

    fun stop() {
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
        } catch (e: Exception) {
            Log.e("VideoEncoder", "Error stopping encoder", e)
        }
    }
}
