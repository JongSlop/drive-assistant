# DriveAssistant

A hands-free voice assistant for Android, meant for use while driving. You talk,
it talks back, and an OpenAI-compatible chat model does the thinking in between.
Speech recognition and speech synthesis both go through the system, so whatever
STT and TTS you already have set up is what it uses.

The project targets a degoogled phone (it was written on GrapheneOS) and pulls in
no Play Services. Weather comes from Open-Meteo, location from the plain Android
`LocationManager`.

## What it can do

| Capability | How it works |
|---|---|
| Voice input | the system `SpeechRecognizer`, a recognizer popup, or a voice keyboard (see below) |
| Voice output | `android.speech.tts.TextToSpeech` |
| Media control | media-button key events, plus the active `MediaSession` for "what's playing" |
| Notification readout | reads incoming messaging notifications aloud (Signal, WhatsApp, Telegram, SMS and the like). Sender and latest message only, long messages get clipped. Can be limited to when Android Auto is connected. |
| Weather | Open-Meteo, no API key. Uses a GPS fix if there is one, otherwise a home city you set. |
| Phone calls | optional `place_call` tool, looks up a contact or dials a number |
| Navigation | optional `start_navigation` tool, fires a `google.navigation:` or `geo:` intent |

The model is told to keep answers short and spoken-style, and to call a tool
rather than describe what it would do. Tools live in
`app/src/main/java/dev/smto/driveassistant/tools/` and are registered in
`ToolRegistry`.

## Privacy

Notification content never leaves the device. It gets read aloud and then
forgotten. Nothing is stored, nothing is sent to the model.

Each turn, the model receives:

* the system prompt and a "reply in this language" line
* a short context line with the current date, time, and currently playing track
* the recent conversation, capped at roughly the last 30 messages
* the results of any tool calls it made

The weather tool passes a place name or coarse coordinates, never a precise fix.
Open-Meteo and its geocoder are the only third party the app itself talks to.
Your STT and TTS engines do whatever they normally do.

The API key sits in a plain `DataStore`. On a rooted device that is not really a
secret anyway, but if you want it encrypted at rest, wrap it with Keystore.

## Building

You need the Android SDK (platform 35, build-tools 35) and a JDK, 17 or newer.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Point `local.properties` at your SDK:

```
sdk.dir=/path/to/Android/Sdk
```

Tagged releases are built, signed, and published to the Releases page by GitHub
Actions. See `.github/workflows/release.yml`.

## First run

1. Open the app and tap the gear icon.
2. **Model backend**: set the base URL (for example `https://api.deepseek.com`),
   your API key, and the model name (`deepseek-v4-flash`). Any OpenAI-compatible
   endpoint with tool calling works.
3. **Language**: pick one from the dropdown, German by default. This sets the STT
   language hint, the TTS voice, and tells the model which language to answer in.
   It is independent of the phone's system locale.
4. **Home location**: a city name, used for weather when there is no GPS fix.
5. **Permissions and roles**:
   * Grant notification access. Needed for readout and for "what's playing".
   * Set as the default assistant app, so the steering wheel or gesture button
     can open it.
   * Grant microphone (required), and optionally location, phone, and contacts.

Then tap the big microphone button and talk. When it is launched as the
assistant, it runs a single turn as an overlay and dismisses itself afterwards.

## Voice input modes

Under Settings, "Voice input":

**System recognizer.** A direct call to `SpeechRecognizer`. One tap, fully
hands-free, and only as good as the recognition service your OS points at. The
app retries a chain of language tags (`de-DE`, then `de`, then the system locale,
then English) because some offline engines reject any tag that is not an exact
model match, which otherwise surfaces as error code 13.

**Recognizer popup.** The microphone button launches an `ACTION_RECOGNIZE_SPEECH`
activity, the recognizer app draws its own UI (for example FUTO Voice Input's
floating popup), and it hands back the transcript. This is the same mechanism
Dicio uses to drive FUTO. It works even though FUTO's background recognition
service is only a stub, because FUTO does implement the popup activity. Pick the
exact app under "Recognizer app" so no chooser appears while you are driving. The
assist screen turns the display on and shows over the lock screen, so the gesture
button brings it up even with the screen off.

**Keyboard dictation.** The microphone button is replaced by a text field.
Dictate into it with a voice keyboard, then send with the keyboard's action key
or the send button. You can also just type. Enable your voice keyboard as an
input method first, and use the keyboard icon in the bar to switch keyboards. In
this mode the assist gesture opens the main screen instead of the overlay.

If you have degoogled your phone and need a recognition engine, FUTO Voice Input
has worked best here for German, used either through the popup mode or as a
dictation keyboard.

## Current limitations

* Android only fully grants the assistant role to apps that ship a
  `VoiceInteractionService`, which this one does not yet. It still requests the
  role, and if that fails it sends you to the system voice input settings to pick
  it by hand. A proper `VoiceInteractionService` is the main thing missing for
  smooth wheel-button and lock-screen use.
* Replies are not streamed. One completion per turn. Answers are short, so most
  of the wait is TTS anyway.
* Push-to-talk and the assist gesture are the only triggers. There is no wake
  word.

## License

No license yet. Ask if you want to use it for something.
