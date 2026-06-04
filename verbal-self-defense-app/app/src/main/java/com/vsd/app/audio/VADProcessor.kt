package com.vsd.app.audio

import kotlin.math.sqrt

/**
 * Simple energy-based Voice Activity Detection (VAD).
 *
 * Uses RMS energy threshold to detect when speech starts and stops.
 * Tunable parameters for different microphone sensitivities and environments.
 */
class VADProcessor(
    /** RMS energy threshold above which speech is considered active */
    private val energyThreshold: Float = 500f,
    /** Number of contiguous speech frames needed to confirm speech started */
    private val minSpeechFrames: Int = 5,
    /** How many seconds of silence before speech is considered ended (in frames) */
    private val silenceTimeoutFrames: Int = 30
) {
    private var speechFrameCount = 0
    private var silenceFrameCount = 0
    private var isSpeaking = false

    /**
     * Analyze one audio frame and return current speech state.
     * @return true if speech is currently detected, false otherwise
     */
    fun analyzeFrame(audioData: ShortArray): Boolean {
        val energy = computeRmsEnergy(audioData)

        if (energy > energyThreshold) {
            speechFrameCount++
            silenceFrameCount = 0
            if (speechFrameCount >= minSpeechFrames && !isSpeaking) {
                isSpeaking = true
            }
        } else {
            speechFrameCount = 0
            if (isSpeaking) {
                silenceFrameCount++
                if (silenceFrameCount >= silenceTimeoutFrames) {
                    isSpeaking = false
                    silenceFrameCount = 0
                }
            }
        }

        return isSpeaking
    }

    /** True if the VAD currently considers speech active */
    val isActive: Boolean get() = isSpeaking

    /**
     * Called when a new speech segment starts.
     * @return true if speech just started this frame
     */
    fun isSpeechStart(): Boolean =
        isSpeaking && speechFrameCount == minSpeechFrames

    /**
     * Called when a speech segment ends.
     * @return true if speech just ended this frame
     */
    fun isSpeechEnd(): Boolean =
        !isSpeaking && silenceFrameCount == 0

    private fun computeRmsEnergy(samples: ShortArray): Float {
        if (samples.isEmpty()) return 0f
        val sumSq = samples.map { it.toFloat() }
            .sumOf { (it * it).toDouble() }
        return sqrt(sumSq / samples.size).toFloat()
    }

    fun reset() {
        speechFrameCount = 0
        silenceFrameCount = 0
        isSpeaking = false
    }
}
