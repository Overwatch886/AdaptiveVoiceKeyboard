package com.adaptivevoice.keyboard

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Stores correction patterns the user has made over time.
 *
 * A "correction" is: Groq heard "X", user changed it to "Y".
 * We track how many times each correction happens so the LLM
 * can prioritise the most common ones.
 */
data class CorrectionEntry(
    val wrong: String,
    val correct: String,
    var count: Int = 1,
    val lastSeen: Long = System.currentTimeMillis()
)

class CorrectionStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("corrections", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<CorrectionEntry> {
        val json = prefs.getString("entries", "[]") ?: "[]"
        val type = object : TypeToken<List<CorrectionEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addCorrection(wrong: String, correct: String) {
        if (wrong.isBlank() || correct.isBlank()) return
        if (wrong.trim().lowercase() == correct.trim().lowercase()) return

        val entries = getAll().toMutableList()
        val existing = entries.find {
            it.wrong.lowercase() == wrong.lowercase() &&
            it.correct.lowercase() == correct.lowercase()
        }

        if (existing != null) {
            existing.count++
        } else {
            entries.add(CorrectionEntry(wrong.trim(), correct.trim()))
        }

        save(entries)
    }

    fun getTopCorrections(limit: Int = 40): List<CorrectionEntry> {
        return getAll()
            .sortedByDescending { it.count }
            .take(limit)
    }

    fun count(): Int = getAll().size

    fun clear() {
        prefs.edit().remove("entries").apply()
    }

    private fun save(entries: List<CorrectionEntry>) {
        prefs.edit().putString("entries", gson.toJson(entries)).apply()
    }

    /**
     * Build a compact prompt snippet listing known corrections.
     * This gets sent to Gemini so it knows what to fix.
     */
    fun buildCorrectionContext(): String {
        val top = getTopCorrections(30)
        if (top.isEmpty()) return ""

        val lines = top.joinToString("\n") { "  \"${it.wrong}\" → \"${it.correct}\"" }
        return "Known corrections for this user (most frequent first):\n$lines"
    }
}
