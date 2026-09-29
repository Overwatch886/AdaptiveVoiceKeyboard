# Adaptive Voice Keyboard

A custom Android keyboard that transcribes your voice via Groq Whisper,
then automatically corrects it using patterns it learns from your own edits.

---

## How it works

1. You tap the mic, speak, tap again to stop
2. Groq Whisper transcribes your audio
3. Gemini Flash applies your learned corrections automatically
4. The corrected text gets typed into whatever app you're using
5. If you fix anything manually, the keyboard silently remembers it
6. Over time, it gets better at catching your specific mishearings

---

## Building the APK

### Requirements
- Android Studio Hedgehog (2023.1) or newer
- Android SDK 34
- A physical Android device or emulator running Android 8.0+

### Steps

1. Open Android Studio
2. File → Open → select the `AdaptiveVoiceKeyboard` folder
3. Wait for Gradle sync to finish (it'll download dependencies automatically)
4. Connect your Android phone via USB with developer mode on
   OR use an emulator (note: emulator mic may not work well)
5. Click the green Run ▶ button or go to Build → Build APK(s)
6. The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

---

## First-time setup on your phone

### Step 1 — Install the keyboard
If you ran it directly from Android Studio, it auto-installs.
If you built an APK, transfer it to your phone and install it
(you may need to allow "Install from unknown sources" in settings).

### Step 2 — Enable it
- Open the app (it's called "Adaptive Voice Keyboard")
- Tap "Open Keyboard Settings"
- Find "Adaptive Voice" in the list and toggle it on
- Android will warn you about accessibility — this is normal for all keyboards

### Step 3 — Set it as default
- Go to Settings → System → Language & Input → Default keyboard
- Select "Adaptive Voice"
- You can switch back to your normal keyboard anytime

### Step 4 — Add your API keys
Back in the app:
- Paste your Groq API key (starts with `gsk_`)
  Get one free at: https://console.groq.com
- Paste your Gemini API key (starts with `AIza`)
  Get one free at: https://aistudio.google.com/app/apikey
- Tap Save

---

## Using the keyboard

The keyboard is minimal by design — it's a voice keyboard, not a full typing keyboard.
It has:
- **Mic button** (blue) — tap to start recording, tap again to stop
- **Backspace** — single tap deletes one char, long press deletes ~10
- **Space** — adds a space (also tells the keyboard you're done editing)
- **Enter** — sends/confirms
- **, and .** — punctuation shortcuts
- **⚙** — opens settings

### The learning loop
- Speak → keyboard types the corrected transcription
- If it's wrong, just backspace and fix it like normal
- When you hit Space or Enter after fixing, the keyboard notices the difference
- It stores: "Groq said X, you changed it to Y"
- Next recording, Gemini sees your correction list and applies them pre-emptively

### Viewing what it learned
Open the app → tap "View Learned Corrections"
You'll see a list like:
```
"groq api" → "Groq API" (12x)
"gonna" → "going to" (3x)
```

---

## Troubleshooting

**"No Groq key" error**
→ Go to settings and make sure you saved the key properly

**Transcription is working but no auto-correction**
→ Corrections only kick in once you've built up some patterns.
  The keyboard needs at least a few corrections stored before Gemini
  has anything to work with. Use it for a day or two first.

**Keyboard doesn't appear**
→ Make sure you enabled it in Android Settings and set it as default

**Recording permission denied**
→ Go to Android Settings → Apps → Adaptive Voice Keyboard → Permissions
  → Enable Microphone

---

## Privacy note

Your voice audio is sent to Groq's servers for transcription.
Your correction patterns are stored only on your device.
Corrected transcriptions are sent to Google's Gemini API.
No data is sent anywhere else.

---

## What's next (Windows version)

The Windows version will be a system tray app with a hotkey.
Hold a key → speak → release → text appears wherever your cursor is.
Same Groq + Gemini setup, same learning system.
