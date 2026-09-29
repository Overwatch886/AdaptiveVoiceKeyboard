package com.adaptivevoice.keyboard

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import java.io.File

class AdaptiveKeyboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private lateinit var correctionStore: CorrectionStore
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false

    // Track what we typed so we can detect corrections
    private var lastTypedText = ""
    private var lastRawTranscript = ""
    private var typedSinceVoice = StringBuilder()

    // UI refs
    private var statusText: TextView? = null
    private var transcriptionPreview: TextView? = null
    private var btnMic: FloatingActionButton? = null

    override fun onCreate() {
        super.onCreate()
        correctionStore = CorrectionStore(this)
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        statusText = view.findViewById(R.id.statusText)
        transcriptionPreview = view.findViewById(R.id.transcriptionPreview)
        btnMic = view.findViewById(R.id.btnMic)

        view.findViewById<Button>(R.id.btnBackspace).apply {
            setOnClickListener { sendBackspace() }
            setOnLongClickListener {
                // Long press clears whole word
                repeat(10) { sendBackspace() }
                true
            }
        }

        view.findViewById<Button>(R.id.btnSpace).setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
            // When user hits space after voice input, check if they corrected anything
            checkForCorrections()
        }

        view.findViewById<Button>(R.id.btnEnter).setOnClickListener {
            currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_DONE)
            checkForCorrections()
        }

        view.findViewById<Button>(R.id.btnComma).setOnClickListener {
            currentInputConnection?.commitText(",", 1)
        }

        view.findViewById<Button>(R.id.btnPeriod).setOnClickListener {
            currentInputConnection?.commitText(".", 1)
        }

        view.findViewById<Button>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }

        btnMic?.setOnClickListener {
            if (isRecording) stopVoiceInput() else startVoiceInput()
        }

        return view
    }

    private fun startVoiceInput() {
        val prefs = getSharedPreferences("api_keys", Context.MODE_PRIVATE)
        val groqKey = prefs.getString("groq_key", "") ?: ""

        if (groqKey.isBlank()) {
            setStatus("No Groq key — add it in Settings ⚙")
            return
        }

        isRecording = true
        btnMic?.backgroundTintList = getColorStateList(R.color.mic_active)
        setStatus(getString(R.string.recording))
        typedSinceVoice.clear()

        val cacheDir = File(cacheDir, "audio").also { it.mkdirs() }
        audioRecorder = AudioRecorder(cacheDir)
        audioRecorder?.startRecording()
    }

    private fun stopVoiceInput() {
        isRecording = false
        btnMic?.backgroundTintList = getColorStateList(R.color.mic_idle)
        setStatus(getString(R.string.processing))

        serviceScope.launch(Dispatchers.IO) {
            try {
                val audioFile = audioRecorder?.let {
                    it.stopRecording()
                    // Get the file it just wrote
                    File(cacheDir, "audio").listFiles()
                        ?.filter { f -> f.name.endsWith(".wav") }
                        ?.maxByOrNull { f -> f.lastModified() }
                } ?: run {
                    withContext(Dispatchers.Main) { setStatus("Recording failed") }
                    return@launch
                }

                val prefs = getSharedPreferences("api_keys", Context.MODE_PRIVATE)
                val groqKey = prefs.getString("groq_key", "") ?: ""
                val geminiKey = prefs.getString("gemini_key", "") ?: ""

                // Step 1: Groq transcription
                val rawTranscript = GroqTranscriber(groqKey).transcribe(audioFile)
                lastRawTranscript = rawTranscript

                withContext(Dispatchers.Main) {
                    transcriptionPreview?.text = rawTranscript
                    transcriptionPreview?.visibility = View.VISIBLE
                }

                // Step 2: Gemini correction using learned patterns
                val correctionContext = correctionStore.buildCorrectionContext()
                val finalText = if (geminiKey.isNotBlank() && correctionContext.isNotEmpty()) {
                    withContext(Dispatchers.Main) { setStatus(getString(R.string.correcting)) }
                    GeminiCorrector(geminiKey).correct(rawTranscript, correctionContext)
                } else {
                    rawTranscript
                }

                withContext(Dispatchers.Main) {
                    typeText(finalText)
                    lastTypedText = finalText
                    setStatus(getString(R.string.tap_mic))
                    transcriptionPreview?.visibility = View.GONE

                    // Clean up audio file
                    audioFile.delete()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus("Error: ${e.message?.take(50)}")
                }
            }
        }
    }

    private fun typeText(text: String) {
        currentInputConnection?.commitText(text, 1)
        typedSinceVoice.append(text)
    }

    private fun sendBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        // Track that user is editing
        if (typedSinceVoice.isNotEmpty()) {
            typedSinceVoice.deleteCharAt(typedSinceVoice.length - 1)
        }
    }

    /**
     * Called when user appears done editing (space, enter, or switches away).
     * Compares what we originally typed vs what's there now.
     * If different, records it as a correction.
     */
    private fun checkForCorrections() {
        if (lastRawTranscript.isBlank()) return

        // Get a small window of text before the cursor to compare
        val extractedText = currentInputConnection?.getExtractedText(
            android.view.inputmethod.ExtractedTextRequest(), 0
        )?.text?.toString() ?: return

        val currentText = typedSinceVoice.toString().trim()
        val originalText = lastTypedText.trim()

        if (currentText != originalText && originalText.isNotBlank() && currentText.isNotBlank()) {
            learnFromCorrection(originalText, currentText)
        }
    }

    /**
     * Diff the original and corrected text word by word.
     * Store any changed words as correction pairs.
     */
    private fun learnFromCorrection(original: String, corrected: String) {
        val originalWords = original.split(" ")
        val correctedWords = corrected.split(" ")

        // Simple word-level diff — match words by position
        val minLen = minOf(originalWords.size, correctedWords.size)
        for (i in 0 until minLen) {
            val wrong = originalWords[i].trim().lowercase()
            val right = correctedWords[i].trim().lowercase()
            if (wrong != right && wrong.isNotBlank() && right.isNotBlank()) {
                correctionStore.addCorrection(wrong, right)
            }
        }

        // Reset tracking
        lastTypedText = ""
        lastRawTranscript = ""
        typedSinceVoice.clear()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        checkForCorrections()
        if (isRecording) stopVoiceInput()
    }

    private fun setStatus(message: String) {
        statusText?.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
