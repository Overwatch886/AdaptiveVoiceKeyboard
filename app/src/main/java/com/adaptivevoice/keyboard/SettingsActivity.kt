package com.adaptivevoice.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var correctionStore: CorrectionStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        correctionStore = CorrectionStore(this)
        val prefs = getSharedPreferences("api_keys", Context.MODE_PRIVATE)

        val etGroqKey = findViewById<TextInputEditText>(R.id.etGroqKey)
        val etGeminiKey = findViewById<TextInputEditText>(R.id.etGeminiKey)
        val tvCorrectionCount = findViewById<TextView>(R.id.tvCorrectionCount)

        // Load saved keys
        etGroqKey.setText(prefs.getString("groq_key", ""))
        etGeminiKey.setText(prefs.getString("gemini_key", ""))

        updateCorrectionCount(tvCorrectionCount)

        findViewById<Button>(R.id.btnOpenKeyboardSettings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<Button>(R.id.btnSaveKeys).setOnClickListener {
            val groqKey = etGroqKey.text?.toString()?.trim() ?: ""
            val geminiKey = etGeminiKey.text?.toString()?.trim() ?: ""

            prefs.edit()
                .putString("groq_key", groqKey)
                .putString("gemini_key", geminiKey)
                .apply()

            Toast.makeText(this, "Keys saved", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnViewCorrections).setOnClickListener {
            showCorrectionsDialog()
        }

        findViewById<Button>(R.id.btnClearCorrections).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear all corrections?")
                .setMessage("This will delete everything the keyboard has learned. Can't be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    correctionStore.clear()
                    updateCorrectionCount(tvCorrectionCount)
                    Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        val tvCorrectionCount = findViewById<TextView>(R.id.tvCorrectionCount)
        updateCorrectionCount(tvCorrectionCount)
    }

    private fun updateCorrectionCount(tv: TextView) {
        tv.text = "Corrections learned: ${correctionStore.count()}"
    }

    private fun showCorrectionsDialog() {
        val corrections = correctionStore.getTopCorrections(50)

        if (corrections.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("No corrections yet")
                .setMessage("Use the keyboard and fix any mistakes. It'll start learning from them automatically.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val lines = corrections.joinToString("\n") {
            "\"${it.wrong}\" → \"${it.correct}\" (${it.count}x)"
        }

        AlertDialog.Builder(this)
            .setTitle("Learned corrections")
            .setMessage(lines)
            .setPositiveButton("OK", null)
            .show()
    }
}
