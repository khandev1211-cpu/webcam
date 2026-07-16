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

    fun start() {
        if (isRunning.get()) return
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = encoder?.createInputSurface()
            encoder?.start()
            
            isRunning.set(true)
            Thread {
                val info = MediaCodec.BufferInfo()
                while (isRunning.get()) {
                    val codec = encoder ?: break
                    try {
                        val status = codec.dequeueOutputBuffer(info, 10000)
                        if (status >= 0) {
                            codec.getOutputBuffer(status)?.let { onEncodedFrame(it, info) }
                            codec.releaseOutputBuffer(status, false)
                        }
                    } catch (e: Exception) { break }
                }
            }.start()
        } catch (e: Exception) { Log.e("VideoEncoder", "Start failed", e) }
    }

    fun stop() {
        isRunning.set(false)
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            inputSurface?.release()
            inputSurface = null
        } catch (e: Exception) {}
    }
}
