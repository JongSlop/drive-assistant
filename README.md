# DriveAssistant

Hands-free Android voice assistant for driving. Speech in via the system
recognizer, spoken replies via system TTS, and an OpenAI-compatible chat model
(DeepSeek by default) driving the logic through **function calling**.

## Features

| Capability | How |
|---|---|
| Voice input | `android.speech.SpeechRecognizer` (system STT) |
| Voice output | `android.speech.tts.TextToSpeech` (system TTS) |
| Media control | media-button key events + active `MediaSession` for "now playing" |
| Notification readout | `NotificationListenerService` — **messaging notifications only** (category `MESSAGE` / MessagingStyle: Signal, WhatsApp, Telegram, SMS, …), spoken sender + latest message, body clipped to ~220 chars. Nothing retained, nothing sent to the model. Optionally gated to Android Auto only (`androidx.car.app` `CarConnection`) |
| Weather | Open-Meteo (no API key); current location via `LocationManager` (no Play Services) |
| Phone calls *(optional)* | `place_call` tool — contact lookup or explicit number |
| Navigation *(optional)* | `start_navigation` tool — `google.navigation:` / `geo:` intent |

The model is told to keep replies short and spoken-style and to prefer calling a
tool over describing the action. Tools live in `tools/` and are registered in
`ToolRegistry`.

## Build

Needs Android SDK (platform 35, build-tools 35) and JDK 17+.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` must point at the SDK (`sdk.dir=/path/to/Android/Sdk`).

## First run

1. Open the app, tap the gear.
2. **Model backend**: set base URL (e.g. `https://api.deepseek.com`), API key, model
   (`deepseek-chat`). Any OpenAI-compatible endpoint with tool-calling works.
3. **Language**: pick from the dropdown (default Deutsch). Drives the STT language
   hint, the TTS voice, and instructs the model to reply in that language. It is
   independent of the phone's system locale.
4. **Home location**: a city name, used for weather when GPS is unavailable.
5. **Permissions & roles**:
   - *Grant notification access* — required for readout and "now playing".
   - *Set as default assistant app* — lets the steering-wheel / gesture ASSIST
     button open `AssistActivity`.
   - *App permissions* — microphone (required), location (weather), phone +
     contacts (optional call tool).

Tap the big mic button to talk. As the assistant it runs one turn as an overlay
and dismisses itself.

## Voice input modes

Settings → **Voice input**:

- **System recognizer** — direct `SpeechRecognizer` call. Fast, one tap, hands-free.
  Quality depends entirely on the installed recognition service (see below).
- **Recognizer popup (FUTO)** — the mic button fires an `ACTION_RECOGNIZE_SPEECH`
  *activity*; the recognizer app draws its own overlay (FUTO Voice Input's floating
  popup) and returns the transcript. This is exactly how Dicio drives FUTO —
  `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)` launched for result, read from
  `EXTRA_RESULTS`. FUTO implements this activity (`org.futo.voiceinput/.RecognizeActivity`)
  even though its `RecognitionService` is a stub. Pick the exact app in
  *Settings → Voice input → Recognizer app* so no chooser appears while driving.
  `AssistActivity` sets `showWhenLocked` / `turnScreenOn`, so the ASSIST gesture
  brings it up with the screen off.
- **Keyboard dictation (IME)** — the mic button is replaced by a focused text field.
  Dictate into it with a voice keyboard (FUTO Voice Input works best for German),
  then it submits on the keyboard's send action or the send button. Set the voice
  keyboard as an enabled input method first. Use the keyboard icon in the bar to
  switch input methods. This path also lets you just type. The ASSIST gesture
  opens the main screen in this mode instead of the overlay.

## Speech recognition backend (System recognizer mode)

The app calls the system `SpeechRecognizer`, so it uses whatever
`RecognitionService` the OS points at (`settings get secure voice_recognition_service`).
On GrapheneOS without Google, open-source options:

| App | Engine | RecognitionService? | German | Notes |
|---|---|---|---|---|
| **Transcribro** (`dev.soupslurpr.transcribro`) | whisper.cpp + Silero VAD, offline | **yes** | good (Whisper) | best current pick; F-Droid / GrapheneOS-friendly |
| **Dicio** (`org.stypox.dicio`) | Vosk (Kaldi), offline | yes (`.io.input.stt_service.SttService`) | ok with `de` model | set STT to *Vosk* (not "system") and download the German model |
| Kõnele (`ee.ioc.phon.android.speak`) | Kaldi / self-hosted server | yes | weak | mostly Estonian |
| FUTO Voice Input (`org.futo.voiceinput`) | Whisper, offline | **no** — dummy service (`category=TEST`) | excellent | keyboard IME only, not usable as a `SpeechRecognizer` backend |
| Google "Speech Services" | SODA, on-device | yes | good | not open source; avoid if degoogling |

Set the provider (needs `WRITE_SECURE_SETTINGS`, i.e. adb or root):

```
adb shell settings put secure voice_recognition_service \
  "<package>/<component>"
# find the component: adb shell dumpsys package <package> | grep -A2 RecognitionService
```

The app already retries a language chain (`de-DE` → `de` → system locale → `en`)
because some offline engines reject any tag that isn't an exact model match
(surfaces as `ERROR_LANGUAGE_UNAVAILABLE` / code 13).

## What reaches the network

**Model endpoint** (`baseUrl`, Bearer API key) — per turn:
- The system prompt + the "reply in <language>" line.
- An ephemeral context line: current date/time, and the currently-playing track
  ("Title by Artist") if a media session is active. Rebuilt each turn, not stored.
- The running transcript, capped at the last **30** non-system messages
  (`AssistantOrchestrator.MAX_HISTORY_MESSAGES`) — roughly 5–7 turns.
- Tool-call results: media state, weather (place label only, never coordinates),
  now-playing, resolved phone number for `place_call`.

Notification content is **never** sent — `get_recent_notifications` was removed
from the registry; readout is local-only and nothing is retained.

**Other hosts:** `open-meteo.com` + its geocoder get GPS coordinates (rounded to
~11 m) or a city name — no key, no account. STT/TTS stay on-device with FUTO /
a local engine.

## Known limitations / next steps

- **Assistant role**: Android only fully grants `ROLE_ASSISTANT` to apps with a
  `VoiceInteractionService`. The request intent is attempted; otherwise it drops
  you in system voice-input settings to pick the app manually. A real
  `VoiceInteractionService` is the follow-up for wheel-button + lock-screen use.
- No streaming — one non-streaming completion per turn (replies are short, so
  latency is dominated by TTS anyway).
- API key is stored in a plain `DataStore`. On a rooted device that is not a real
  boundary; wrap with Keystore / `security-crypto` if you want at-rest encryption.
- Trigger is push-to-talk / ASSIST only — no wake word.
