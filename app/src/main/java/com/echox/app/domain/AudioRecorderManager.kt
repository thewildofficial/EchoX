package com.echox.app.domain

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

enum class RecordingState {
    Idle,
    Recording,
    Paused
}

class AudioRecorderManager {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var outputStream: FileOutputStream? = null
    private var isPaused = false

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val sampleRate = SAMPLE_RATE
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, scope: CoroutineScope) {
        if (_recordingState.value != RecordingState.Idle) return

        // Create new AudioRecord instance
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        // Open file for new recording
        outputStream = FileOutputStream(outputFile)

        audioRecord?.startRecording()
        isPaused = false
        _recordingState.value = RecordingState.Recording

        recordingJob = scope.launch(Dispatchers.IO) {
            val data = ByteArray(bufferSize)

            try {
                while (isActive && _recordingState.value != RecordingState.Idle) {
                    // Skip reading when paused
                    if (isPaused) {
                        _amplitude.value = 0f
                        kotlinx.coroutines.delay(100)
                        continue
                    }

                    val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        outputStream?.write(data, 0, read)
                        
                        // Calculate Amplitude (RMS)
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            // PCM 16-bit is 2 bytes per sample
                            val sample = (data[i].toInt() and 0xFF) or (data[i + 1].toInt() shl 8)
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / (read / 2))
                        _amplitude.value = rms.toFloat()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                outputStream?.close()
                outputStream = null
            }
        }
    }

    fun pauseRecording() {
        if (_recordingState.value == RecordingState.Recording) {
            isPaused = true
            _recordingState.value = RecordingState.Paused
            // Stop reading but keep AudioRecord and FileOutputStream alive
            audioRecord?.stop()
        }
    }

    @SuppressLint("MissingPermission")
    fun resumeRecording() {
        if (_recordingState.value == RecordingState.Paused && audioRecord != null) {
            isPaused = false
            _recordingState.value = RecordingState.Recording
            // Resume reading from AudioRecord
            audioRecord?.startRecording()
        }
    }

    fun stopRecording() {
        _recordingState.value = RecordingState.Idle
        isPaused = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        outputStream?.close()
        outputStream = null
    }
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 1
        const val BITS_PER_SAMPLE = 16
    }
}
