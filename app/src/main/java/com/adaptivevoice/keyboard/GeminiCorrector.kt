package com.adaptivevoice.keyboard

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class GeminiCorrector(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Takes raw Groq transcription + known corrections, returns fixed text.
     * If Gemini fails for any reason, returns the original text unchanged
     * so the user always gets something.
     */
    fun correct(rawTranscript: String, correctionContext: String): String {
        if (apiKey.isBlank()) return rawTranscript

        val prompt = buildPrompt(rawTranscript, correctionContext)

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 500)
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: return rawTranscript
                if (!response.isSuccessful) return rawTranscript

                val json = JSONObject(responseBody)
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() == 0) return rawTranscript

                val content = candidates.getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()

                // Sanity check — if Gemini returns something very different in length,
                // fall back to original
                if (content.length > rawTranscript.length * 3) return rawTranscript
                content
            }
        } catch (e: Exception) {
            rawTranscript
        }
    }

    private fun buildPrompt(transcript: String, correctionContext: String): String {
        val contextSection = if (correctionContext.isNotBlank()) {
            "\n\n$correctionContext\n"
        } else {
            ""
        }

        return """You are a transcription corrector. A voice-to-text system produced the following transcript.
Your job is to fix any likely mishearings based on the user's known correction patterns.
$contextSection
Rules:
- Only fix words that clearly match a known correction pattern
- Do NOT rephrase, reformat, or change anything else
- Do NOT add punctuation that wasn't implied
- Return ONLY the corrected text, nothing else

Transcript: $transcript"""
    }
}
