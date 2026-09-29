package com.adaptivevoice.keyboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class GroqTranscriber(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Sends an audio file to Groq's Whisper endpoint and returns the transcript.
     * Throws an exception if something goes wrong so the caller can handle it.
     */
    fun transcribe(audioFile: File): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody("audio/wav".toMediaType())
            )
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("language", "en")
            .addFormDataPart("response_format", "json")
            .build()

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/audio/transcriptions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Empty response from Groq")
            if (!response.isSuccessful) {
                throw IOException("Groq error ${response.code}: $body")
            }
            val json = JSONObject(body)
            return json.getString("text").trim()
        }
    }
}
