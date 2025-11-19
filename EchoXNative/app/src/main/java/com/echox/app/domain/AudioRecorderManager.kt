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

class AudioRecorderManager {

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    fun startRecording(outputFile: File, scope: CoroutineScope) {
        if (isRecording) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingJob = scope.launch(Dispatchers.IO) {
            val data = ByteArray(bufferSize)
            val outputStream = FileOutputStream(outputFile)

            try {
                while (isActive && isRecording) {
                    val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        outputStream.write(data, 0, read)
                        
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
                outputStream.close()
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
    }
}
