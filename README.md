# Mike Write

An Android, voice-first memoir writing companion for a hands-free author (built for a
paralyzed storyteller). The writer speaks; the app transcribes, lightly formats, organizes
passages into chapters, reads them back aloud, and exports a manuscript.

## What it actually does

- **Dictation** via Android's `SpeechRecognizer` (on-device or Google's speech service,
  depending on the device). The app does **not** keep audio files.
- **Voice control loop** with a volume-key / switch-access hook
  (`MikeWriteAccessibilityService`) and four input modes: Voice Loop, Push-to-Talk,
  Switch Scan, Eye Gaze Dwell (tap-to-confirm).
- **On-device storage**: transcripts, chapters and settings live in a Room (SQLite)
  database and `SharedPreferences` on the phone.
- **Deterministic editing agents** (filler cleanup, segmentation, entity extraction,
  topic tagging, craft tips) that run locally with no network.
- **Optional AI**: if a Gemini key is present, passage formatting, chapter assignment,
  follow-up interview questions and summaries call
  `generativelanguage.googleapis.com` over HTTPS. Without a key the app falls back to
  deterministic behavior.
- **Export**: PDF, Markdown, and plain text of a chapter or the whole book.
- **Text-to-speech playback** of drafts and the assembled book.

## Privacy (accurate)

- Drafts are stored on the device. The app itself does not save recordings.
- Speech-to-text is performed by your device's speech service and **may transmit audio to
  that provider** (e.g. Google).
- Optional AI features send **story text** (not audio) to Google Gemini over TLS. This is
  transport encryption, **not** end-to-end encryption.
- Nothing is sold or shared with advertisers.

## Install (for the author)

The app runs **fully offline with zero configuration**: dictation, filler cleanup,
chapter organization, craft tips and export all use the on-device deterministic agents.
AI features are entirely optional.

1. Download `app-release.apk` from the repo's **Releases** page and open it on the phone
   (allow "install unknown apps" for your browser/file manager when prompted).
2. Open **Mike Write**. Grant **Microphone** access, then accept the in-app voice/privacy
   consent screen.
3. To use the hands-free switch/volume-key control, enable the accessibility service:
   **Settings → Accessibility → Installed apps → Mike Write → On**. (Its only purpose is
   hands-free control for the author; enable it or leave it off — dictation still works
   via the on-screen orb.)
4. Optional AI: **Caregiver → settings** lets a caregiver paste a Gemini key or a local
   model URL at runtime. Without one, the app stays in deterministic mode and never
   contacts the network.

## Prerequisites

- JDK 21
- Android SDK with platform `android-36` (`ANDROID_HOME` set, or a `local.properties`)
- The Gradle wrapper is included: use `./gradlew` (Windows: `gradlew.bat`), not a system Gradle.

## Configure

1. A `.env` (gitignored) is provided, sourced from the Keywire vault / local fleet. Keys:
   ```
   GEMINI_API_KEY=MY_GEMINI_API_KEY   # optional primary
   OLLAMA_BASE_URL=http://10.0.2.2:11434/v1   # fallback text server (Ollama native OR OpenAI-compatible)
   OLLAMA_MODEL=minicpm5-fable
   OLLAMA_API_KEY=MY_OLLAMA_API_KEY   # only needed for Ollama Cloud
   AI_GATEWAY_API_KEY=vck_...         # Jev tier 1 (Vercel AI Gateway)
   TYPESAFE_BASE_URL=https://ai-gateway.vercel.sh/typesafe
   TYPESAFE_MODEL=typesafe-ai/jev
   JEV_LOCAL_BASE_URL=http://10.0.2.2:8080   # Jev tier 2 (keyless local)
   JEV_LOCAL_MODEL=jev-latest
   ```
   Any provider left blank/placeholder is disabled and the app degrades to the
   deterministic agents. All of these can also be edited at runtime in **Studio → AI
   Providers** (settings override `.env`) without rebuilding.
2. The application id is `com.ncsound919.mikewrite` (`app/build.gradle.kts`). Do not change
   it after the author has installed the app, or updates will not apply.

### Host addressing (important)

Both local services currently bind to `127.0.0.1` on the build machine:
- `:11434` — llama.cpp OpenAI-compatible server (`/v1`), model `minicpm5-fable`.
- `:8080` — LocalJev (`/v1/systemone`, no key), model `localjev-0.2`.

**Physical phone over USB (current setup):** reverse the ports so the phone's loopback
reaches the host's loopback, and use `127.0.0.1` in `.env`:
```
adb reverse tcp:8080 tcp:8080
adb reverse tcp:11434 tcp:11434
```
(Re-run after each reconnect.) **Android emulator:** use `10.0.2.2` instead of `127.0.0.1`.
A **phone over Wi-Fi** with no USB needs those services bound to `0.0.0.0` (fleet change)
or a tunnel.

### AI / decisions wiring

- **Text generation** goes through `AiTextEngine`: Gemini first, then the local server.
  The local tier tries Ollama native (`/api/generate`, with `think:false` for qwen3) and
  falls back to OpenAI-compatible `/v1/chat/completions`, so llama.cpp, LiteLLM and Ollama
  Cloud all work from one config. Used for formatting, follow-up questions, summaries,
  chapter prompts, and rewording.
- **Decisions** go through `JevClient`, a two-tier chain. Each tier speaks its native
  dialect (verified live):
  - **tier 1 — Vercel AI Gateway:** `POST {root}/v1/evaluate`, question types
    `boolean | choice | score`. (`/typesafe/v1/systemone` is the legacy path and 403s.)
  - **tier 2 — local Jev (`:8080`, keyless):** `POST /v1/systemone`, types
    `noul | choice | score`. The client translates and normalizes answers, so callers just
    read `probability(id)` / `score(id)`.
  - Returns calibrated probabilities (never prose) and scores how faithful a reworded
    passage is before offering to apply it.
  - **Current status:** the gateway key resolves and the team has a ~$5 balance, but that is
    the **free-tier monthly credit** (lifetime spend $0.0009), not a purchase. `typesafe-ai/jev`
    is not in the free-tier subset, so tier 1 returns `RestrictedModelsError` (403). **Jev is
    active via tier 2 (local).** Purchasing AI Gateway credits moves the team to paid tier and
    tier 1 engages automatically — Jev pricing is `$0.000000042`/prompt-token and `$0`
    completion, so the $5 free allotment would go extremely far once unlocked.
- **Reword**: say “reword” (or "rewrite this", "rephrase") or tap the AI icon on any
  passage. The text model rewrites it fact-preservingly; Jev rates faithfulness/readability;
  the author says **save** to replace the formatted prose (the raw transcript is kept) or
  **delete** to keep the original. Enable **Auto-apply reworded passages** to skip the
  confirmation when Jev rates the rewrite faithful.
- `usesCleartextTraffic` is enabled so the app can reach a **local** Ollama over plain HTTP
  on your LAN. Gemini and the Jev gateway remain HTTPS. If you only use cloud providers,
  remove that attribute from the manifest.

## Build & test

```
gradlew.bat :app:compileDebugKotlin
gradlew.bat :app:testDebugUnitTest          # full suite (Robolectric; first run is slow)
gradlew.bat :app:assembleDebug              # produces app/build/outputs/apk/debug/app-debug.apk
```

Fast, pure-JVM test subset:

```
gradlew.bat :app:testDebugUnitTest --tests "com.example.speech.*" --tests "com.example.data.BookChaptersTest" --tests "com.example.deterministic.*"
```

Debug builds are signed with the local `debug.keystore` (gitignored). If it is missing,
generate it once:

```
keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey ^
  -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

## Release signing (required before publishing)

`assembleRelease` currently fails unless an upload keystore exists. Provide it via env vars
(recommended) or a file at the configured path:

```
gradlew.bat :app:bundleRelease ^
  -Pandroid.injected.signing.store.file=%KEYSTORE_PATH% ...
```

Simplest supported path: set `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`
environment variables; the `release` signing config reads them. Keep the keystore and its
passwords safe and out of version control — losing them prevents future updates.

Generate an upload key only once, and back it up:

```
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA ^
  -keysize 2048 -validity 10000 -dname "CN=Mike Write, O=Mike Write"
```

Then produce the store artifact with `gradlew.bat :app:bundleRelease` and upload the
`.aab` from `app/build/outputs/bundle/release/`.

## Play Console notes

- Declare **Speech / voice** data usage (sent to the device speech provider for
  "App functionality") and **User content** (optional Gemini AI). The Legal tab in the app
  contains matching data-safety guidance.
- The app declares an accessibility service with `isAccessibilityTool="true"`. Play requires
  a clear accessibility justification; the service's sole purpose is hands-free/switch
  control for the author.

## Microphone behavior (beeping)

By default the voice loop is **turn-based**: the mic opens for a command turn and,
on idle silence, stops and goes quiet (no per-interval re-arm, so the Android
recognizer's own start/stop chime does not repeat). Wake it with the volume key /
switch (`wakeListening`) or dictate from the orb. **Studio → Assistive Navigation →
"Keep Microphone Always Listening"** restores the old always-listening loop. App
earcons are throttled and can be muted in **Studio → Audible Earcons & Haptics**.

## Security notes

- Every key in `.env` (`GEMINI_API_KEY`, `AI_GATEWAY_API_KEY`, any `OLLAMA_API_KEY`) is
  compiled into `BuildConfig` and therefore ships inside the APK. Treat them as public:
  anyone can decompile the app and reuse them, consuming your quota/billing. Anything beyond
  a single trusted user needs a backend proxy that holds the keys server-side. The Jev
  gateway key in particular is a **fleet** credential — rotate it if the APK is distributed.
- The author is paralyzed; the device may be shared with caregivers. The memoir database is
  unencrypted app-private storage. Provide device lock and consider excluding the app from
  cloud backup if the content is confidential.

## Known limitations

- No audio is retained, so the app cannot reproduce the author's own voice; playback is
  synthesized TTS.
- The "autonomous" editorial features (timeline weaver, gap auditor, style harmonizer) are
  keyword heuristics, not validated literary analysis; treat their scores as prompts, not
  verdicts.
- Eye Gaze Dwell requires external gaze/head-mouse hardware that maps to touch; the app
  does not read a camera itself.
