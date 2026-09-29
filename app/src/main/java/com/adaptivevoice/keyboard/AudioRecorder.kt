package com.adaptivevoice.keyboard

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorder(private val outputDir: File) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null
    private var outputFile: File? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    fun startRecording(): File {
        outputFile = File(outputDir, "voice_${System.currentTimeMillis()}.wav")

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        val file = outputFile!!
        recordingThread = Thread {
            writeAudioToFile(file)
        }
        recordingThread?.start()

        return file
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        recordingThread?.join()
    }

    private fun writeAudioToFile(file: File) {
        val pcmData = mutableListOf<Byte>()
        val buffer = ByteArray(bufferSize)

        while (isRecording) {
            val read = audioRecord?.read(buffer, 0, bufferSize) ?: break
            if (read > 0) {
                pcmData.addAll(buffer.take(read))
            }
        }

        writeWavFile(file, pcmData.toByteArray())
    }

    private fun writeWavFile(file: File, pcmData: ByteArray) {
        val totalDataLen = pcmData.size + 36
        val byteRate = sampleRate * 2 // 16-bit mono

        FileOutputStream(file).use { fos ->
            // WAV header
            fos.write("RIFF".toByteArray())
            fos.write(intToBytes(totalDataLen))
            fos.write("WAVE".toByteArray())
            fos.write("fmt ".toByteArray())
            fos.write(intToBytes(16))           // Subchunk1Size
            fos.write(shortToBytes(1))           // PCM format
            fos.write(shortToBytes(1))           // Mono
            fos.write(intToBytes(sampleRate))
            fos.write(intToBytes(byteRate))
            fos.write(shortToBytes(2))           // BlockAlign
            fos.write(shortToBytes(16))          // BitsPerSample
            fos.write("data".toByteArray())
            fos.write(intToBytes(pcmData.size))
            fos.write(pcmData)
        }
    }

    private fun intToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte(),
        (value shr 16 and 0xff).toByte(),
        (value shr 24 and 0xff).toByte()
    )

    private fun shortToBytes(value: Int): ByteArray = byteArrayOf(
        (value and 0xff).toByte(),
        (value shr 8 and 0xff).toByte()
    )
}
