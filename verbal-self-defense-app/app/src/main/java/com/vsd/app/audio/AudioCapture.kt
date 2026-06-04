package com.vsd.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * Low-level audio capture via AudioRecord.
 * Captures 16kHz mono PCM 16-bit from the device microphone.
 */
class AudioCapture {
    private var recorder: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val bufferSize: Int = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096) // ensure minimum buffer

    /**
     * Start capturing audio from the microphone.
     * @return true if initialization and recording started successfully
     */
    fun startCapture(): Boolean {
        if (isRecording) return true

        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        return recorder?.run {
            if (state != AudioRecord.STATE_INITIALIZED) {
                release()
                recorder = null
                return false
            }
            startRecording()
            isRecording = true
            true
        } ?: false
    }

    /**
     * Read one buffer of audio data.
     * @return ShortArray of PCM data, or null if not recording or read failed
     */
    fun readBuffer(): ShortArray? {
        if (!isRecording) return null
        val buffer = ShortArray(bufferSize / 2) // 16-bit = 2 bytes per sample
        val read = recorder?.read(buffer, 0, buffer.size) ?: -1
        return if (read > 0) {
            if (read < buffer.size) buffer.copyOf(read) else buffer
        } else null
    }

    fun stopCapture() {
        if (!isRecording) return
        isRecording = false
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) {
            // already stopped
        }
        recorder?.release()
        recorder = null
    }

    val sampleRateHz: Int get() = sampleRate

    val isActive: Boolean get() = isRecording
}
