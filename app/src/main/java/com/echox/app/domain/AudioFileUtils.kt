package com.echox.app.domain

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.io.DEFAULT_BUFFER_SIZE

object AudioFileUtils {

    suspend fun convertPcmToWav(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): File = withContext(Dispatchers.IO) {
        FileInputStream(pcmFile).use { input ->
            FileOutputStream(wavFile).use { output ->
                val totalAudioLen = pcmFile.length()
                val totalDataLen = totalAudioLen + 36
                val byteRate = sampleRate * channels * bitsPerSample / 8

                val header = ByteArray(44)
                writeWavFileHeader(
                    header,
                    totalAudioLen,
                    totalDataLen,
                    sampleRate,
                    channels,
                    byteRate,
                    bitsPerSample
                )
                output.write(header, 0, 44)

                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
        pcmFile.delete()
        wavFile
    }

    suspend fun extractSegment(
        sourceWav: File,
        outputWav: File,
        startMs: Long,
        durationMs: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ): File = withContext(Dispatchers.IO) {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val audioDataOffset = 44L
        val startByte = (startMs * byteRate / 1000).coerceAtLeast(0)
        val desiredBytes = (durationMs * byteRate / 1000).coerceAtLeast(0)
        val maxBytes = sourceWav.length() - audioDataOffset - startByte
        val bytesToCopy = desiredBytes.coerceAtMost(maxBytes).coerceAtLeast(0)

        FileInputStream(sourceWav).use { input ->
            input.skip(audioDataOffset + startByte)
            FileOutputStream(outputWav).use { output ->
                val header = ByteArray(44)
                writeWavFileHeader(
                    header,
                    bytesToCopy,
                    bytesToCopy + 36,
                    sampleRate,
                    channels,
                    byteRate,
                    bitsPerSample
                )
                output.write(header, 0, 44)

                var remaining = bytesToCopy
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        outputWav
    }

    private fun writeWavFileHeader(
        header: ByteArray,
        totalAudioLen: Long,
        totalDataLen: Long,
        sampleRate: Int,
        channels: Int,
        byteRate: Int,
        bitsPerSample: Int
    ) {
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = ((channels * bitsPerSample) / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
    }
}

