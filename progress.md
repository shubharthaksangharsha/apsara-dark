# Apsara Dark — Progress

## v0.1.0 — Initial UI Shell (Feb 9, 2026)

### What was done

- **Project setup**: Android Jetpack Compose project with Kotlin, Material 3, targeting SDK 35 (min SDK 26).
- **Dark theme**: Custom `ApsaraDarkTheme` — true-black surfaces (`#0D0D0D`), purple accent (`#B388FF`), custom color palette and typography.
- **Navigation drawer** (hamburger menu):
  - Slide-from-left or tap hamburger icon to open.
  - Items: My Canvas, Latest Videos, My Plugins (with badge), Laptop Control, Settings.
  - Status footer showing version and online state.
- **Main interface**:
  - Top app bar with Apsara avatar, app name, and online status.
  - Greeting section with user name.
  - 2×2 feature card grid: **Talk**, **Design**, **Control**, **Reminders** — each with icon, title, and subtitle.
  - Recent conversation section with mock chat bubbles (user + Apsara).
- **Bottom input bar**:
  - Text input with placeholder ("Ask Apsara anything…").
  - Attach button (+ icon).
  - Mic button (when empty) / Send button (when text is entered).
  - Focus-aware animated border.
- **Mock data**: Sample chat messages, drawer items, and feature cards — all using mock data, no backend yet.
- **Debug APK**: Successfully assembled at `app/build/outputs/apk/debug/app-debug.apk`.

### Architecture

```
com.shubharthak.apsaradark
├── MainActivity.kt
├── data/
│   └── MockData.kt
└── ui/
    ├── components/
    │   ├── AppDrawer.kt
    │   ├── BottomInputBar.kt
    │   ├── ChatBubble.kt
    │   └── FeatureCard.kt
    ├── screens/
    │   └── HomeScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## v0.2.0 — UI Cleanup & Logo in App (Feb 9, 2026)

### What was done

- **Logo in top bar**: Replaced placeholder "A" circle with actual `apsara_logo.png` image.
- **Animated title**: "Apsara Dark" text in top bar has a purple shimmer animation.
- **Removed "Online · Ready"** status from top bar — clean look.
- **Drawer cleanup**:
  - Removed "Apsara / Your AI companion" header and close button.
  - Removed "Latest Videos" item.
  - Removed plugin badge count.
  - Removed bottom "v0.1.0 — Online" status footer.
  - Items now: My Canvas, My Plugins, Laptop Control, Settings.
- **Main interface**:
  - Top app bar with Apsara avatar, app name, and online status.
  - Greeting section with user name.
  - 2×2 feature card grid: **Talk**, **Design**, **Control**, **Reminders** — each with icon, title, and subtitle.
  - Recent conversation section with mock chat bubbles (user + Apsara).
- **Bottom input bar**:
  - Text input with placeholder ("Ask Apsara anything…").
  - Attach button (+ icon).
  - Mic button (when empty) / Send button (when text is entered).
  - Focus-aware animated border.
- **Mock data**: Sample chat messages, drawer items, and feature cards — all using mock data, no backend yet.
- **Debug APK**: Successfully assembled at `app/build/outputs/apk/debug/app-debug.apk`.

### Architecture

```
com.shubharthak.apsaradark
├── MainActivity.kt
├── data/
│   └── MockData.kt
└── ui/
    ├── components/
    │   ├── AppDrawer.kt
    │   ├── BottomInputBar.kt
    │   ├── ChatBubble.kt
    │   └── FeatureCard.kt
    ├── screens/
    │   └── HomeScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        └── Type.kt
```

---

## v0.3.0 — Theme System (Feb 9, 2026)

### What was done

- **8 VS Code-inspired themes**: Dark (default), Monokai, Nightly, Solarized, Dracula, Nord, Light, Monochrome.
- **Settings screen**: Accessible from drawer → Settings. Shows a scrollable list of theme cards.
- **Each theme card** shows: 3 color preview dots, theme name, dark/light label, mini preview bar, and a check indicator for the active theme.
- **ThemeManager**: Singleton that persists theme choice to SharedPreferences — survives app restarts.
- **All components refactored**: HomeScreen, AppDrawer, FeatureCard, BottomInputBar all read from `LocalThemeManager` — no more hardcoded colors.
- **Dynamic status/nav bar**: System bars update color and light/dark appearance per theme.

### New files

- `ui/theme/AppThemes.kt` — all 8 palette definitions
- `ui/theme/ThemeManager.kt` — state holder + SharedPreferences persistence + CompositionLocal
- `ui/screens/SettingsScreen.kt` — theme picker UI

---

## v1.0.2 — UI Polish & Layout Refinements (Feb 9, 2026)

### What was done

- **Home screen redesigned**:
  - Removed greeting ("Good evening, Shubharthak") — replaced with centered "Apsara is here for you!" tagline.
  - Removed Apsara logo and animated title from top bar — just a clean hamburger icon now.
  - Feature grid (Talk, Design, Control, Reminders) — minimal text-only chips, no icons, 2×2 centered grid.
- **Drawer updated**:
  - Apsara logo moved to top of drawer (above menu items).
  - Clean layout: logo → My Canvas, My Plugins, Laptop Control, Settings.
- **Bottom input bar polish**:
  - Placeholder changed to "Ask Apsara".
  - Input bar raised higher from the bottom edge for better thumb reach.
  - Added spacing between mic and live icons for cleaner look.
  - Separate `+` attach button on the left, input box curves fully to the right.
- **Settings themes**: Expandable/collapsible grid layout, theme names only (no color dots, no previews, no icons).
- **Feature cards**: Stripped to just names — no icons, no subtitles. Minimal pill-style chips.

---

### Next steps

- Wire up Gemini Live API for the Talk feature.
- Implement voice input (speech-to-text).
- Build out My Canvas, Plugins, Laptop Control, and Settings screens.
- Add right-side panel (TBD).
- Backend integration.

---

## v2.0.0 — Gemini Live API Integration (Feb 9, 2026)

### What was done

- **Node.js Backend** (`/backend`):
  - Express + WebSocket relay server bridging Android app ↔ Gemini Live API.
  - Full JSON protocol (audio, text, video, tool calls, session resumption, context compression).
  - Health (`/health`) and config (`/config`) HTTP endpoints.
  - Single-user design, API key stored in `.env` on server.
  - Deployment: Caddy reverse proxy config + PM2 service + systemd service (DEPLOY.md).
  - Backend deployed to Oracle Cloud at `wss://apsara-dark-backend.devshubh.me/live`.

- **Android Live Session Layer**:
  - `LiveSettingsManager` — persists all Gemini Live config to SharedPreferences (backend URL, model, voice, temperature, system instruction, response modality, 7 toggles).
  - `LiveWebSocketClient` — OkHttp WebSocket client with JSON protocol, state flows, audio/text/transcription streams.
  - `LiveAudioManager` — PCM audio record (16kHz) + playback (24kHz), mute toggle, queue-based playback.
  - `LiveSessionViewModel` — orchestrates WS ↔ audio ↔ UI state, handles connect/disconnect/mute/sendText.

- **HomeScreen Integration**:
  - Live mode triggered ONLY by explicit user action: tapping the **Live (GraphicEq) icon** in the input bar OR the **"Talk" feature card**.
  - Microphone permission requested at live mode entry.
  - **Normal mode**: text input + mic + live icon (unchanged default).
  - **Live mode**: ChatGPT Voice-style UI with animated pulsing orb, status text, input/output transcripts, and the live input bar (+ | Type | Mic | End).
  - Top bar hides during live mode. Drawer gestures disabled during live mode.

- **BottomInputBar**:
  - Two-mode `AnimatedContent`: Normal ↔ Live, keyed on `liveState != IDLE`.
  - Normal: text field + mic + GraphicEq (live trigger).
  - Live: + attach, type field with send, mic mute/unmute (with spinner while connecting), End button.

- **Settings — Live Settings Panel**:
  - Expandable "Live Settings" section in SettingsScreen.
  - Backend URL text field, Model/Voice/Modality dropdowns, Temperature slider, System Instruction multiline field.
  - 7 toggle switches: Affective Dialog, Proactive Audio, Input/Output Transcription, Context Compression, Google Search, Include Thoughts.
  - All settings persist to SharedPreferences and are used when starting live sessions.

- **FeatureCard**: Added optional `onClick` callback so "Talk" card triggers live mode.

- **Build fix**: Renamed setter methods in `LiveSettingsManager` from `setX()` to `updateX()` to avoid JVM signature clash with Kotlin's `private set`.

### New files

```
backend/
├── src/
│   ├── server.js              — Express + WS server
│   ├── ws-handler.js          — WebSocket message handler
│   ├── gemini-live-session.js — Gemini Live API session wrapper
│   └── config.js              — Default config, voices, models
├── .env                       — API key + port config
├── package.json
├── Caddyfile                  — Caddy reverse proxy config
└── DEPLOY.md                  — Full Oracle Cloud deployment guide

app/src/main/java/com/shubharthak/apsaradark/
├── data/
│   └── LiveSettingsManager.kt — Persistent live settings
└── live/
    ├── LiveWebSocketClient.kt — OkHttp WS client
    ├── LiveAudioManager.kt    — PCM record/playback
    └── LiveSessionViewModel.kt — Session orchestrator
```

### Dependencies added

- `com.squareup.okhttp3:okhttp:4.12.0` — WebSocket client
- `com.google.code.gson:gson:2.11.0` — JSON serialization
- `androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7` — ViewModel for Compose

### Debug APK

Successfully assembled at `app/build/outputs/apk/debug/app-debug.apk`.

---

## v2.1.0 — Live Mode UI/UX Refinement (Feb 10, 2026)

### What was done

- **Live mode chat UI overhaul**:
  - **User messages only** have chat bubbles (right-aligned, accent-tinted rounded rectangle).
  - **Apsara output** is now plain text — no bubble, no border, no background. Renders as simple left-aligned text.
  - Removed streaming cursor animation (blinking `▌`) from Apsara output transcription.
  - Output transcription is shown **as soon as received** (async, not waiting for turn complete). Each `output_transcription` event from the backend immediately appends to the visible message.
  - Conversation history is maintained as a list of `LiveMessage` objects in `LiveSessionViewModel`, with `isStreaming` flag for in-progress messages.

- **Center orb removed**: No center pulsing orb in live mode. Shows "Start talking" if no conversation yet.
- **Transcription toggle logic**: Chat respects `inputTranscription` and `outputTranscription` toggles — messages are filtered based on user settings.
- **Mini visualizer**: End button has a mini gradient visualizer, color/animation changes based on who is talking (user = accent, Apsara = secondary color).
- **Connecting state**: Shows a centered progress spinner with "Connecting…" text.

- **Backend model cleanup**:
  - Only `gemini-2.5-flash-native-audio-preview-12-2025` is supported as the live model.
  - Improved error handling for unsupported model/modality combinations.

- **Voice/Modality logic**:
  - Voice config is only sent to Gemini if response modality is AUDIO (both backend and frontend).
  - Voice selector is disabled in Settings UI when modality is TEXT.

- **Code cleanup**: Removed unused imports (`Activity`, `CircleShape`, `Color`, `Brush`, `TextAlign`) from HomeScreen.

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
├── ui/screens/HomeScreen.kt          — LiveModeContent, ApsaraBubble (plain text), UserBubble (chat bubble)
├── ui/components/BottomInputBar.kt   — Two-mode input bar (normal/live)
├── ui/screens/SettingsScreen.kt      — Voice selector disabled for TEXT modality
├── live/LiveSessionViewModel.kt      — Conversation tracking, streaming state, async output
├── live/LiveWebSocketClient.kt       — WebSocket flows for transcription events
├── live/LiveAudioManager.kt          — PCM audio record/playback
├── data/LiveSettingsManager.kt       — Persistent live settings

backend/src/
├── ws-handler.js                     — Improved error handling
└── config.js                         — Single model support
```

---

## v2.2.0 — TEXT Modality Removed, Thoughts UI, Config Logging (Feb 10, 2026)

### What was done

- **TEXT modality removed**: `gemini-2.5-flash-native-audio-preview-12-2025` only supports AUDIO modality. Removed TEXT option from:
  - Backend: `MODALITIES` constant, forced AUDIO in `ws-handler.js` connect handler.
  - Frontend: Removed `responseModality` setting, dropdown, and conditional voice logic from `LiveSettingsManager` and `SettingsScreen`.
  - Voice selector is always enabled (always AUDIO modality).

- **Config logging**: Backend now logs the full client config JSON when a session connects, plus tools config details (googleSearch on/off → tools array sent to Gemini). This helps debug config issues.

- **Google Search toggle fix**: Added explicit logging of `tools.googleSearch` value vs. actual tools array sent to Gemini, so discrepancies can be identified. The toggle logic itself was correct — `if (this.config.tools?.googleSearch)` only adds `{ googleSearch: {} }` to the tools array when `true`.

- **Thoughts UI**: When "Include Thoughts" is enabled in settings:
  - Backend forwards `thought` message type (model reasoning text with `part.thought === true`).
  - `LiveWebSocketClient` emits thoughts via a new `thought` SharedFlow.
  - `LiveSessionViewModel` accumulates thought text and attaches it to Apsara messages.
  - `LiveMessage` data class now has an optional `thought: String?` field.
  - `ApsaraBubble` in HomeScreen shows a collapsible "▸ Thoughts" / "▾ Thoughts" section below Apsara's response text. Tap to expand/collapse.

- **System Instruction Clear button**: Settings screen now shows a "Clear" text button next to the System Instruction label when text is present.

### Files changed

```
backend/src/
├── config.js                         — Removed TEXT from MODALITIES
├── ws-handler.js                     — Config logging on connect, force AUDIO modality
└── gemini-live-session.js            — Tools config logging

app/src/main/java/com/shubharthak/apsaradark/
├── data/LiveSettingsManager.kt       — Removed responseModality, added clearSystemInstruction(), always AUDIO
├── ui/screens/SettingsScreen.kt      — Removed modality dropdown, removed voice disable, added Clear button
├── ui/screens/HomeScreen.kt          — ApsaraBubble with collapsible thoughts
├── live/LiveSessionViewModel.kt      — Thought buffer, thought attachment to messages
└── live/LiveWebSocketClient.kt       — thought SharedFlow
```

---

## v2.3.0 — Thoughts UI Polish, Disconnect Logging (Feb 10, 2026)

### What was done

- **Thoughts position**: Collapsible "▸ Thoughts" section now appears **above** the main response text, so users see the reasoning context first.
- **Bold markdown in thoughts**: `**text**` patterns in thought text are now parsed and rendered as bold (using `AnnotatedString` with `FontWeight.Bold`). The `**` markers are stripped from display.
- **Disconnect logging**: Improved backend close handler — when user clicks End, the log now shows `Connection closed: client disconnected` instead of `Connection closed: unknown`. Also logs disconnect code.

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
└── ui/screens/HomeScreen.kt          — Thoughts above text, parseBoldMarkdown()

backend/src/
└── gemini-live-session.js            — Better onclose reason, disconnect logging
```

---

## v2.4.0 — Per-Tool Async/Sync & Audio Output Routing Fix (Feb 10, 2026)

### What was done

- **Backend: Per-tool async/sync function calling**:
  - `gemini-live-session.js` — `_buildGeminiConfig()` now reads `toolAsyncModes` (a `{ toolName: boolean }` map) from the session config. Tools marked `true` get `behavior: 'NON_BLOCKING'` in their function declaration; all others default to blocking. Removed the old global `asyncFunctionCalls` flag.
  - `ws-handler.js` — `onToolCall` handler now partitions incoming function calls into async and sync groups based on `toolAsyncModes`. Sync tools execute sequentially with plain responses; async tools execute concurrently with `scheduling: 'INTERRUPT'` on their responses.
  - `config.js` — Replaced `asyncFunctionCalls: false` with `toolAsyncModes: {}` in default config.

- **Android: Audio output device routing fix**:
  - `LiveAudioManager.kt` — Added a shared audio session ID (`sharedAudioSessionId`) so `AudioRecord` and `AudioTrack` share the same session, which is required for hardware AEC to correctly cancel playback from the mic input.
  - `AudioTrack` is now created with the shared session ID (matching the reference `aec-info.kt` implementation) instead of using the default session.
  - `setAudioOutputDevice()` now restarts the `AudioTrack` when switching output devices mid-session (via new `restartPlayback()` method), ensuring the routing change actually takes effect.
  - `sharedAudioSessionId` is reset on `stopRecording()` to avoid stale sessions.

### Files changed

```
backend/src/
├── config.js                         — toolAsyncModes replaces asyncFunctionCalls
├── ws-handler.js                     — Per-tool async/sync partitioning in onToolCall
└── gemini-live-session.js            — Per-tool NON_BLOCKING in _buildGeminiConfig()

app/src/main/java/com/shubharthak/apsaradark/
└── live/LiveAudioManager.kt          — Shared audio session, AudioTrack restart on route change
```

---

## v2.5.0 — Per-Tool Async/Sync, Audio Routing Fix, Tool Call UI (Feb 10, 2026)

### What was done

- **Per-tool async/sync toggles (Frontend)**:
  - `PluginsScreen` — Each tool card now has its own async/sync toggle instead of a single global toggle. Toggle state stored per-tool in `LiveSettingsManager`.
  - `LiveSettingsManager` — `toolAsyncModes` map (`{ toolName: Boolean }`) persisted to SharedPreferences. Sent to backend in `buildConfigMap()`.

- **Audio output routing fix**:
  - `LiveAudioManager.kt` — `AudioTrack` is now created *after* `AudioRecord` starts, sharing the same audio session ID. This ensures `MODE_IN_COMMUNICATION` is active before the `AudioTrack` is built, so `isSpeakerphoneOn` routing (loudspeaker/earpiece/bluetooth) works correctly.
  - `LiveSessionViewModel.kt` — `startAudio()` now calls `startRecording()` first, then `startPlayback()`.
  - `AndroidManifest.xml` — Added `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH`, and `BLUETOOTH_CONNECT` permissions.

- **Audio output device dropdown UI**:
  - `BottomInputBar.kt` — `DropdownMenu` offset left by 120dp so it doesn't clip off the right edge of the screen.

- **Tool call status cards in chat**:
  - `LiveWebSocketClient.kt` — Parses `tool_call` and `tool_results` WebSocket messages into `ToolCallEvent` and `ToolResultEvent` flows.
  - `LiveSessionViewModel.kt` — Accumulates tool calls in a `pendingToolCalls` buffer and attaches them as `EmbeddedToolCall` objects inside the next/current APSARA message.
  - `LiveMessage` — Added `toolCalls: List<EmbeddedToolCall>` field for embedded tool calls, plus `ToolStatus` enum and backward-compat `TOOL_CALL` role.
  - `EmbeddedToolCall` data class — Holds tool name, id, status (RUNNING/COMPLETED), mode (sync/async), and result JSON.

- **Tool call card placement & cleanup**:
  - `ApsaraBubble` — Now accepts `toolCalls` parameter and renders `ToolCallCard` components **after the collapsible "Thoughts" section** and **before the main response text**.
  - Removed bolt (`Icons.Outlined.Bolt`) and build (`Icons.Outlined.Build`) icons from tool call cards — only the status spinner (running) or checkmark (completed) is shown.
  - Tool calls no longer appear as separate chat items in the LazyColumn — they are embedded inside the Apsara message.
  - `ToolCallCard` — Inline composable with status icon, tool name, async/sync mode label, and expandable JSON result on tap.

### New data classes

```kotlin
data class EmbeddedToolCall(
    val name: String,
    val id: String,
    val status: LiveMessage.ToolStatus,
    val mode: String,      // "sync" or "async"
    val result: String?
)
```

### Files changed

```
app/src/main/
├── AndroidManifest.xml                           — Added MODIFY_AUDIO_SETTINGS, BLUETOOTH permissions
├── java/com/shubharthak/apsaradark/
│   ├── live/
│   │   ├── LiveSessionViewModel.kt               — EmbeddedToolCall, pendingToolCalls buffer, tool call/result handlers
│   │   └── LiveWebSocketClient.kt                — ToolCallEvent, ToolResultEvent flows, tool_call/tool_results parsing
│   └── ui/
│       ├── components/BottomInputBar.kt           — Dropdown offset fix
│       └── screens/HomeScreen.kt                  — ApsaraBubble with embedded tool calls, ToolCallCard, removed Bolt/Build icons
```

---

## v2.6.0 — Amplitude-Driven Visualizer (Feb 9, 2026)

### What was done

- **Real audio amplitude visualizer**: The mini visualizer in the bottom input bar (inside the End button during live mode) now responds to **actual audio levels** instead of using a constant sine wave animation.
  
- **User input amplitude**: `LiveAudioManager` computes RMS (Root Mean Square) amplitude from mic PCM data in real-time. When you speak louder, the bars move more; when you're quiet, they stay small.

- **Apsara output amplitude**: `LiveAudioManager` also computes RMS from the playback queue audio data. When Apsara speaks, the bars animate proportionally to her audio volume.

- **Smooth decay**: When no audio is playing, the output amplitude smoothly decays to zero (exponential moving average) rather than snapping to flat.

- **Amplitude exposed as StateFlows**: `inputAmplitude` and `outputAmplitude` (both `StateFlow<Float>`, range 0.0–1.0) are exposed from `LiveAudioManager`, collected in `HomeScreen`, and passed through `BottomInputBar` → `LiveModeBar` → `MiniVisualizer`.

- **Organic variation preserved**: The visualizer still uses phase-offset sine waves for per-bar variation, but the sine amplitude is now **scaled by the real audio amplitude**. This gives natural-looking organic movement proportional to actual voice volume.

- **Muted state**: Input amplitude is still computed when muted (so the visualizer could show it), but audio data is not sent to the backend.

### How it works

1. `LiveAudioManager.startRecording()` — every PCM chunk read from mic → `computeAmplitude()` → `smoothAmplitude()` → `_inputAmplitude` StateFlow
2. `LiveAudioManager.startPlayback()` — every PCM chunk dequeued for playback → `computeAmplitude()` → `smoothAmplitude()` → `_outputAmplitude` StateFlow; decays to 0 when queue is empty
3. `MiniVisualizer` receives `amplitude: Float` (switched by `activeSpeaker` — input for USER, output for APSARA)
4. Bar heights = `minHeight + amplitude * (maxHeight - minHeight) * (0.4 + 0.6 * sineVariation)`

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
├── live/LiveAudioManager.kt          — computeAmplitude(), smoothAmplitude(), inputAmplitude/outputAmplitude StateFlows
├── ui/components/BottomInputBar.kt   — MiniVisualizer now amplitude-driven, new inputAmplitude/outputAmplitude params
└── ui/screens/HomeScreen.kt          — Passes amplitude StateFlows to BottomInputBar
```

---

## v2.6.1 — Visualizer Fix: User Speech Detection & Amplitude Boost (Feb 9, 2026)

### What was done

- **Fixed: User speech not moving the visualizer bars**
  - **Root cause**: `activeSpeaker` was only set to `USER` when a `input_transcription` WebSocket message arrived from the backend. This has a significant roundtrip delay (user speaks → audio sent to backend → backend sends to Gemini → Gemini transcribes → transcription sent back). During that delay, `activeSpeaker` was `NONE`, so the visualizer was showing `amplitude = 0f`.
  - **Fix**: Added an `inputAmplitude` observer in `LiveSessionViewModel` that sets `activeSpeaker = USER` immediately when mic amplitude exceeds 0.05 (voice detected locally), without waiting for the backend transcription. Resets to `NONE` when amplitude drops below 0.02 and Apsara isn't playing audio.
  - Apsara's audio data events still correctly override to `APSARA` when she speaks.

- **Boosted amplitude sensitivity**
  - Reduced RMS normalization divisor from 12000 → 4000. `VOICE_COMMUNICATION` audio source outputs quieter PCM than raw mic due to AEC preprocessing — the old divisor was too high, making speech barely register.
  - Added `sqrt()` curve on top of normalization — this boosts quieter speech so even soft talking is visible in the visualizer.
  - Increased smoothing factor from 0.3 → 0.45 for faster bar response.

- **More dramatic bar movement**
  - Increased `maxHeight` from 0.85 → 0.95 of canvas height (bars go taller).
  - Reduced `minHeight` from 0.15 → 0.12 (bars go shorter when idle).
  - Applied 1.5x amplitude boost in the visualizer formula so bars react more visibly.
  - Changed variation mix from `0.4 + 0.6 * variation` → `0.3 + 0.7 * variation` for more per-bar contrast.

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
├── live/LiveAudioManager.kt          — Amplitude: lower divisor (4000), sqrt curve, faster smoothing (0.45)
├── live/LiveSessionViewModel.kt      — inputAmplitude observer for instant USER detection
└── ui/components/BottomInputBar.kt   — Boosted visualizer scaling (1.5x, taller bars, more variation)
```

---

## v2.7.0 — Live Mode Attachment Bottom Sheet (Feb 9, 2026)

### What was done

- **Attachment bottom sheet**: Tapping the `+` button during live mode now opens a Material3 `ModalBottomSheet` with three options:
  - **Camera** — `PhotoCamera` icon (for sending camera frames to live session)
  - **Photos** — `PhotoLibrary` icon (for picking photos to send)
  - **Files** — `AttachFile` icon (for picking files to send)

- **Live-mode only**: The bottom sheet only appears when in live mode (`isLiveActive == true`). The `+` button in normal mode is reserved for a different attach menu (TBD).

- **Themed**: Uses app palette — `surfaceContainer` background, `surfaceContainerHigh` icon boxes, `textSecondary` icons, rounded corners, minimal drag handle.

- **Auto-dismiss**: Each option dismisses the sheet after being tapped.

### New files

```
app/src/main/java/com/shubharthak/apsaradark/
└── ui/components/AttachmentBottomSheet.kt   — ModalBottomSheet with Camera, Photos, Files
```

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
└── ui/screens/HomeScreen.kt                 — showLiveAttachmentSheet state, live-only + button logic
```

---

## v2.8.0 — Live Mode Only, General Settings, Haptic Feedback & Session Resumption (Feb 9, 2026)

### What was done

- **Live Mode Only Refactor**:
  - Removed normal text input mode — app now only shows the input bar in live mode.
  - "Talk" feature card is the sole entry point to live mode.
  - Cleaned up `HomeScreen` and `BottomInputBar` to remove all normal mode code and state.

- **General Settings section**:
  - Added a collapsible "General Settings" section in `SettingsScreen` (before Themes).
  - Added a **Haptic Feedback** toggle with description "Make sure you turn on Output Transcriptions".

- **Haptic Feedback — transcript-synced vibration**:
  - Added `hapticFeedback` boolean to `LiveSettingsManager`, persisted via SharedPreferences.
  - Vibration is driven by **output transcription changes** (not raw audio amplitude), so pulses are naturally synced to Apsara's speech rhythm — one pulse per new word/chunk.
  - Duration scales with word count (45–100ms), intensity is moderate-strong (100–220 out of 255).
  - Added `VIBRATE` permission to `AndroidManifest.xml` (normal permission, auto-granted).
  - Vibrator instance cached via `remember`, with `hasVibrator()` check and full try-catch safety.
  - Transcript length tracker resets when Apsara stops speaking.

- **Crash fix**:
  - Missing `VIBRATE` permission was causing `SecurityException` crash when haptic was enabled.
  - Added defensive try-catch around all vibration calls.

- **Session Resumption — Android client support**:
  - Backend already sends `session_resumption_update` messages when Gemini provides a new resumption handle (logs `[GeminiLive] Session resumption handle updated`).
  - Added `SessionResumptionEvent` data class and `sessionResumptionUpdate` SharedFlow to `LiveWebSocketClient`.
  - `LiveWebSocketClient.handleMessage()` now parses `session_resumption_update` messages and emits events.
  - `LiveSessionViewModel` observes resumption updates — when a resumable handle arrives, it sets `sessionResumed = true` and adds a system message in chat: "⟳ Session resumed — continuing from where we left off".
  - `sessionResumed` flag resets on each new `startLive()` call.

### How Session Resumption Works

Session resumption is a Gemini Live API feature that allows sessions to survive WebSocket reconnections:

1. **Backend enables it**: `sessionResumption: {}` is set in `DEFAULT_SESSION_CONFIG` (config.js), so every session opts in.
2. **Gemini sends handles**: During the session, Gemini periodically sends `sessionResumptionUpdate` messages with a `newHandle` token. The backend stores this in `this.resumptionHandle`.
3. **Auto-reconnect on GoAway**: When Gemini sends a `goAway` (connection will terminate), the backend schedules a `reconnect()` after 2s. On reconnect, `_buildGeminiConfig()` passes `sessionResumption: { handle: this.resumptionHandle }` to resume the same session.
4. **Auto-reconnect on disconnect**: If the Gemini connection drops unexpectedly and a resumption handle exists, the backend auto-reconnects with it.
5. **Android sees it**: The backend forwards `session_resumption_update` to the Android client, which now shows it in the live chat as a system indicator.

The repeated `[GeminiLive] Session resumption handle updated` logs mean Gemini is regularly refreshing the handle — this is **normal and expected**. It ensures the handle stays fresh for reconnection.

### Files changed

```
app/src/main/
├── AndroidManifest.xml                                — Added VIBRATE permission

app/src/main/java/com/shubharthak/apsaradark/
├── data/LiveSettingsManager.kt                        — hapticFeedback field + updateHapticFeedback()
├── live/LiveWebSocketClient.kt                        — SessionResumptionEvent, sessionResumptionUpdate flow, handler
├── live/LiveSessionViewModel.kt                       — sessionResumed state, resumption observer, system message in chat
├── ui/screens/HomeScreen.kt                           — Transcript-synced haptic feedback (LaunchedEffect on outputTranscript)
└── ui/screens/SettingsScreen.kt                       — General Settings section, Haptic Feedback toggle with note
```

---

## v2.9.0 — Session Resumption & Context Compression Controls, GoAway Indicator (Feb 9, 2026)

### What was done

- **Session Resumption toggle** in Live Settings:
  - Added `sessionResumption` boolean to `LiveSettingsManager` (default: `true`), persisted via SharedPreferences.
  - When enabled, sends `sessionResumption: {}` in the connect config — Gemini will provide resumption handles for auto-reconnect.
  - When disabled, no resumption config is sent — sessions will terminate on disconnect.
  - Updated backend: `DEFAULT_SESSION_CONFIG.sessionResumption` changed from `{}` to `null` (off by default, controlled by client toggle).
  - Backend `_buildGeminiConfig()` now checks `typeof === 'object'` to distinguish enabled (`{}`) from disabled (`null`).
  - Auto-reconnect in `ws-handler.js` only triggers if session resumption was enabled in the config.

- **Session Resumed indicator** — improved logic:
  - No longer triggers on first handle update (which is normal for every session).
  - Now detects actual reconnections: tracks `hasBeenConnected` and `hasResumptionHandle` — only shows "⟳ Session resumed — continuing from where we left off" when a second `LIVE_CONNECTED` event fires after the first, meaning a disconnect + reconnect happened mid-session.

- **GoAway message handling**:
  - Added `GoAwayEvent` data class and `goAway` SharedFlow to `LiveWebSocketClient`.
  - Parses `go_away` messages from backend (sent when Gemini's connection is about to terminate).
  - `LiveSessionViewModel` observes GoAway events and adds a system message: "⏳ Connection refreshing — reconnecting seamlessly…" so the user knows what's happening.

- **Updated toggle descriptions**:
  - Context Compression: "Unlimited session length via sliding window"
  - Session Resumption: "Auto-reconnect without losing context"

### How the three features interact

```
┌─────────────────────────────────────────────────────────────────┐
│  You start talking to Apsara...                                 │
│                                                                 │
│  Context Compression ON → session can go beyond 15 min          │
│  Session Resumption ON → handles stored for reconnection        │
│                                                                 │
│  At ~10 min: Gemini sends GoAway                                │
│    → App shows "⏳ Connection refreshing..."                     │
│    → Backend auto-reconnects with stored handle                 │
│    → App shows "⟳ Session resumed"                              │
│    → Conversation continues seamlessly                          │
│                                                                 │
│  Without Session Resumption:                                    │
│    → GoAway → disconnect → session lost → user must restart     │
│                                                                 │
│  Without Context Compression:                                   │
│    → Hard 15-min limit → session terminates even with resumption│
└─────────────────────────────────────────────────────────────────┘
```

### Files changed

```
backend/src/
├── config.js                         — sessionResumption default changed to null
├── gemini-live-session.js            — Stricter sessionResumption check (typeof === 'object')
└── ws-handler.js                     — Auto-reconnect gated on sessionResumption config

app/src/main/java/com/shubharthak/apsaradark/
├── data/LiveSettingsManager.kt       — sessionResumption toggle + sent in buildConfigMap()
├── live/LiveWebSocketClient.kt       — GoAwayEvent, goAway flow, go_away handler
├── live/LiveSessionViewModel.kt      — GoAway observer, improved reconnection detection
└── ui/screens/SettingsScreen.kt      — Session Resumption toggle, updated descriptions
```

---

## v3.0.0 — Background Foreground Service + Connection Stability (Feb 9, 2026)

### What was done

#### 1. Fixed "Live error: Software caused connection abort"
- **Root cause**: The `LiveSessionViewModel` was scoped to `HomeScreen` composable. When navigating to Settings or Plugins, the NavHost could lose reference to the ViewModel, and more critically, when the app went to background, Android's power management would kill the WebSocket connection and audio resources.
- **Fix — ViewModel hoisting**: Moved `LiveSessionViewModel` creation from `HomeScreen` to `AppNavigation` (Activity-level scope). The ViewModel now survives navigation between Home ↔ Settings ↔ Plugins without being destroyed or recreated.
- **Fix — Foreground Service**: Created `LiveSessionService` to keep the app process alive in background with a persistent notification.

#### 2. Foreground Service for Live Mode (`LiveSessionService`)
- **New file**: `LiveSessionService.kt` — A foreground service that:
  - Shows a persistent "Apsara is listening…" notification while live mode is active
  - Uses `FOREGROUND_SERVICE_TYPE_MICROPHONE | MEDIA_PLAYBACK` for Android 14+
  - Includes an "End Session" action in the notification to stop live from the shade
  - Auto-starts when `startLive()` is called, auto-stops on `stopLive()`
- **New file**: `StopLiveReceiver.kt` — BroadcastReceiver to handle "End Session" notification action
- **New file**: `LiveSessionBridge.kt` — Singleton SharedFlow bridge for notification → ViewModel communication
- **Permissions added** to AndroidManifest:
  - `FOREGROUND_SERVICE` — Required for all foreground services
  - `FOREGROUND_SERVICE_MICROPHONE` — Required on Android 14+ for mic access in foreground service
  - `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — Required on Android 14+ for audio playback in foreground service
  - `POST_NOTIFICATIONS` — Required on Android 13+ to show notifications
  - `WAKE_LOCK` — Keeps CPU alive during background operation

#### 3. Wake Lock + WiFi Lock
- Added `PowerManager.PARTIAL_WAKE_LOCK` to `LiveAudioManager` — prevents CPU from sleeping during live session
- Added `WifiManager.WifiLock` — prevents WiFi from going to low-power mode, keeping the WebSocket connection stable
- Both locks are acquired when recording starts and released when recording stops

#### 4. Session Resumption Icon Fix
- Replaced `🔒` (lock) icon with `✦` (four-pointed star) in the "Session resumption active" indicator
- Better fits the app's visual theme and doesn't imply security/encryption

#### 5. Notification Permission Handling
- Added `POST_NOTIFICATIONS` permission request on Android 13+ in `HomeScreen`
- Non-blocking — if user denies, live mode still works (notification just won't show)

### Architecture

```
com.shubharthak.apsaradark.live/
├── LiveSessionService.kt     — Foreground service (persistent notification + process keep-alive)
├── LiveSessionBridge.kt      — SharedFlow bridge for notification → ViewModel events
├── StopLiveReceiver.kt       — BroadcastReceiver for "End Session" notification action
├── LiveSessionViewModel.kt   — Now starts/stops foreground service + observes stop bridge
├── LiveWebSocketClient.kt    — (unchanged)
└── LiveAudioManager.kt       — Added wake lock + WiFi lock for background stability

com.shubharthak.apsaradark.ui/
├── navigation/Navigation.kt  — ViewModel hoisted to Activity scope (survives navigation)
└── screens/HomeScreen.kt     — Accepts ViewModel as parameter, notification permission
```

### Flow: Background Persistence

```
┌─────────────────────────────────────────────────────────────────┐
│                  Live Session Lifecycle                          │
│                                                                 │
│  User taps "Talk" → startLive()                                 │
│    1. WebSocket connects to backend                             │
│    2. Foreground service starts → persistent notification        │
│    3. Wake lock + WiFi lock acquired                            │
│    4. Audio recording + playback begin                          │
│                                                                 │
│  User presses Home / switches app:                              │
│    → Foreground service keeps process alive ✓                   │
│    → Wake lock keeps CPU active ✓                               │
│    → WiFi lock keeps network stable ✓                           │
│    → WebSocket stays connected ✓                                │
│    → Audio continues recording/playing ✓                        │
│                                                                 │
│  User navigates to Settings/Plugins:                            │
│    → ViewModel hoisted to Activity scope ✓                      │
│    → Session survives navigation ✓                              │
│    → Audio continues in background ✓                            │
│                                                                 │
│  User taps "End Session" (notification or in-app):              │
│    1. Audio recording + playback stop                           │
│    2. WebSocket disconnects                                     │
│    3. Wake lock + WiFi lock released                            │
│    4. Foreground service stops → notification removed            │
└─────────────────────────────────────────────────────────────────┘
```

### Files changed

```
NEW:
app/src/main/java/com/shubharthak/apsaradark/live/
├── LiveSessionService.kt             — Foreground service
├── LiveSessionBridge.kt              — Notification → ViewModel bridge
└── StopLiveReceiver.kt               — "End Session" broadcast receiver

MODIFIED:
app/src/main/AndroidManifest.xml      — Service, receiver, permissions
app/src/main/java/com/shubharthak/apsaradark/
├── live/LiveSessionViewModel.kt      — Service start/stop, bridge observer, icon fix
├── live/LiveAudioManager.kt          — Wake lock + WiFi lock
├── ui/navigation/Navigation.kt       — ViewModel hoisted to Activity scope
└── ui/screens/HomeScreen.kt          — Accept ViewModel param, notification permission
```

---

## v3.1.0 — Notification Actions & GoAway Reconnect Fix (Feb 9–10, 2026)

### What was done

- **Notification mute/end actions**: Foreground notification now has **Mute/Unmute** and **End Session** action buttons, plus a live speaker animation icon (changes when Apsara is speaking vs. listening).
- **Simplified notification**: Reduced notification complexity — removed redundant channels and consolidated into a single clean notification.
- **GoAway reconnect infinite loop fix**: Fixed a critical bug where GoAway → reconnect → immediate GoAway would loop forever. Added `suppressDisconnect` flag during reconnection to prevent intermediate disconnect events from killing the session, and added loop-guard logic.
- **GoAway reconnect stability**: Suppress intermediate disconnect messages during GoAway → reconnect cycle so the user doesn't see a disconnect → reconnect flicker.

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
├── live/LiveSessionService.kt        — Mute/End actions, speaker animation icon
├── live/LiveSessionViewModel.kt      — GoAway reconnect loop guard
└── live/LiveWebSocketClient.kt       — suppressDisconnect during GoAway

backend/src/
├── ws-handler.js                     — GoAway reconnect guard
└── gemini-live-session.js            — Suppress intermediate disconnect
```

---

## v3.2.0 — Live Video Camera Preview (Feb 10, 2026)

### What was done

- **Live video camera preview**: Full CameraX-based camera preview during live mode:
  - Tap-to-focus (auto-resets after 3s)
  - Pinch-to-zoom (smooth gesture detection)
  - Flash toggle (on/off)
  - Camera flip (front ↔ rear)
  - Minimize / PiP-style mode (camera shrinks to small floating window)
  - JPEG frames extracted from camera and streamed to Gemini via WebSocket (`video` message type)

- **Media resolution setting**: Added `mediaResolution` config to `LiveSettingsManager` — controls the quality/size of video frames sent to Gemini (LOW, MEDIUM, HIGH).

- **Camera annotation drawing overlay**: Pen-based drawing overlay on top of camera preview — user can annotate what the camera sees:
  - Draw freehand annotations on camera feed
  - Icon-only draw toolbar (pen, clear)
  - Draw/clear buttons positioned outside camera gesture area for reliable tapping

### New files

```
app/src/main/java/com/shubharthak/apsaradark/
├── ui/components/CameraPreview.kt     — CameraX preview with gesture controls
├── ui/components/DrawingOverlay.kt    — Pen-based annotation overlay
```

### Files changed

```
app/src/main/java/com/shubharthak/apsaradark/
├── data/LiveSettingsManager.kt        — mediaResolution setting
├── live/LiveWebSocketClient.kt        — Video frame sending
├── live/LiveSessionViewModel.kt       — Camera state management
└── ui/screens/HomeScreen.kt           — Camera preview integration, draw toolbar
```

---

## v3.3.0 — Markdown Rendering (Attempted & Reverted) (Feb 10, 2026)

### What was done

- **Markdown rendering for output transcription**: Added rich markdown rendering for Apsara's output (bold, italic, headers, code blocks, lists).
- **Deferred markdown rendering**: Performance optimization — renders plain text during streaming, then formats to markdown after 1s idle to avoid jank.
- **Reverted**: Markdown rendering caused visual inconsistencies and performance issues with real-time streaming. Reverted entirely back to plain text output.

### Lesson learned

Real-time streaming text is incompatible with complex markdown rendering because:
1. Partial markdown tokens (e.g., `**bold` without closing `**`) cause visual glitches
2. Re-rendering annotated strings on every chunk is expensive
3. Deferred rendering creates jarring visual jumps

---

## v4.0.0 — Interactions API & Canvas Plugin (Feb 10, 2026)

### What was done

#### Backend: Interactions API
- **New subsystem**: `/backend/src/interactions/` — a separate Gemini text/chat API (non-live) for tool operations that need full Gemini reasoning:
  - `interactions-service.js` — Manages Gemini chat sessions with tool calling support
  - `interactions-tools.js` — Tool declarations for the Interactions API (get_current_time, get_weather, canvas tools, code tools)
  - `interactions-router.js` — Express routes for `/interactions/*`
  - `interactions-ws-handler.js` — WebSocket handler for streaming interaction responses
  - `interactions-config.js` — Configuration for Interactions API
- **Architecture**: Live API → detects tool call → delegates to Interactions API for complex operations → streams results back to client.

#### Backend: Canvas Plugin
- **Apsara Canvas**: AI-generated web applications served in-app:
  - `canvas-service.js` — Uses Gemini to generate full HTML/CSS/JS web apps from prompts, with auto-validation and error fixing
  - `canvas-store.js` — In-memory store for canvas projects (code, metadata, versions)
  - `canvas-router.js` — Express routes for serving canvas pages (`/canvas/:id`)
  - Tools: `apsara_canvas` (create), `list_canvases`, `get_canvas_detail`, `edit_canvas`

#### Android: Canvas Integration
- **My Canvas screen**: Lists all generated canvases with title, timestamp, and preview
- **Canvas viewer**: Full-screen WebView rendering of generated web apps
- **Canvas detail viewer**: Tabbed interface with Code, Prompt, Log, and Info tabs
- **Mobile-first rendering**: Canvas HTML is injected with responsive viewport meta tags
- **In-app notifications**: Toast/snackbar for canvas creation progress

#### WebSocket Fixes
- **Heartbeat error fix**: Replaced WebSocket protocol-level pings with application-level JSON `ping`/`pong` messages to prevent `Control frames must be final` errors
- **noServer WebSocket routing**: Fixed root cause of WebSocket frame corruption by using `noServer` mode with manual upgrade handling in Express
- **Caddy proxy fix**: Added `flush_interval -1` to Caddy reverse_proxy config for proper WebSocket streaming

### New files

```
backend/src/
├── canvas/
│   ├── index.js              — Canvas module exports
│   ├── canvas-service.js     — AI code generation + validation
│   ├── canvas-store.js       — In-memory project store
│   └── canvas-router.js      — Express routes for serving canvases
├── interactions/
│   ├── index.js              — Interactions module exports
│   ├── interactions-service.js  — Gemini chat session manager
│   ├── interactions-tools.js    — Tool declarations
│   ├── interactions-router.js   — Express routes
│   ├── interactions-ws-handler.js — WS streaming handler
│   └── interactions-config.js   — Config

app/src/main/java/com/shubharthak/apsaradark/
├── ui/screens/CanvasListScreen.kt     — My Canvas list
├── ui/screens/CanvasViewerScreen.kt   — WebView renderer
├── ui/screens/CanvasDetailScreen.kt   — Code/Prompt/Log/Info tabs
```

---

## v4.1.0 — Canvas Detail & Edit Tools (Feb 10, 2026)

### What was done

- **Canvas detail viewer**: Full tabbed detail screen for each canvas:
  - **Code tab**: Syntax-highlighted source code viewer
  - **Prompt tab**: Original generation prompt
  - **Log tab**: Generation/validation log history
  - **Info tab**: Metadata (title, created date, version count, URL)

- **Canvas edit tool**: `edit_canvas` Live API tool — Apsara can modify existing canvases based on user instructions:
  - Extracts current canvas code from store
  - Sends edit prompt to Gemini for modification
  - Updates canvas in-place with new version
  - **Title update**: Edit instructions now update the app title based on HTML `<title>` extraction

- **Canvas list tool**: `list_canvases` — Apsara can enumerate all user's canvases
- **Canvas detail tool**: `get_canvas_detail` — Apsara can retrieve full details of a canvas

- **Plugin registration fix**: Fixed plugin tools not being registered correctly in Live API session configuration

### Files changed

```
backend/src/
├── tools.js                          — list_canvases, get_canvas_detail, edit_canvas tools
├── canvas/canvas-service.js          — Edit flow, title extraction
└── canvas/canvas-store.js            — Version history, update methods

app/src/main/java/com/shubharthak/apsaradark/
├── ui/screens/CanvasDetailScreen.kt  — Tabbed detail viewer
├── ui/screens/CanvasListScreen.kt    — List with navigation
├── ui/navigation/Navigation.kt       — Canvas detail routes
└── data/MockData.kt                  — Canvas plugin card
```

---

## v5.0.0 — Apsara Interpreter (Inline Code Execution) (Feb 10, 2026)

### What was done

- **Apsara Interpreter**: A full code execution environment accessible from live chat:
  - `run_code` tool — Apsara writes and executes Python/JavaScript code in a sandboxed Docker-like environment on the backend
  - Code runs in isolated sessions with shared state (variables persist across runs in the same session)
  - Supports text output, image generation (matplotlib, PIL), and structured data
  - Results streamed back to live chat in real-time

- **Code execution cards in chat**: New `CodeExecutionCard` composable embedded in Apsara messages:
  - Shows code being executed (syntax highlighted)
  - Displays execution output (stdout, stderr)
  - Shows generated images inline with zoom support
  - Loading/completed status indicators

- **My Code screen**: Lists all code execution sessions (analogous to My Canvas for canvases)
- **Code session management**: `list_code_sessions`, `get_code_session` tools for Apsara to recall previous code work

### New files

```
backend/src/interpreter/
├── interpreter-service.js     — Code execution engine (Gemini-powered code gen + sandbox execution)
├── interpreter-store.js       — Session store (code, outputs, metadata)
└── interpreter-router.js      — Express routes for code images

backend/src/images/
├── image-store.js             — In-memory image store for generated visuals
└── image-router.js            — Express routes for serving images

app/src/main/java/com/shubharthak/apsaradark/
├── ui/screens/MyCodeScreen.kt         — Code session list
├── ui/screens/CodeDetailScreen.kt     — Code session detail viewer
└── ui/components/CodeExecutionCard.kt — Inline code card in chat
```

---

## v5.1.0 — Interpreter Improvements & Config Tabs (Feb 10, 2026)

### What was done

- **`edit_code` tool**: Apsara can now modify existing code sessions — edits update the same session instead of creating a new one:
  - Preserves session context (variables, imports)
  - Single matplotlib image per execution (replaces previous)
  - Edit history tracked in session metadata

- **Haptic feedback for tool calls**: Device vibrates when a tool call starts and completes (respects haptic feedback setting).

- **Config tab**: Added a "Config" tab to both Canvas detail and MyCode detail screens — shows the generation config (model, temperature, tools used).

- **Image deduplication**: Implemented and then removed size-based image dedup logic — decided redundant images are acceptable and dedup was causing more issues than it solved.

- **UX cleanup**:
  - Simplified CodeExecutionCard — removed redundant copy snackbar
  - Show edit history in detail views
  - Better image URL handling

### Files changed

```
backend/src/
├── tools.js                           — edit_code tool declaration + handler
├── interpreter/interpreter-service.js — Edit flow, single image per execution
├── interpreter/interpreter-store.js   — Edit history tracking
└── images/image-store.js              — Image cleanup

app/src/main/java/com/shubharthak/apsaradark/
├── ui/screens/CodeDetailScreen.kt     — Config tab, edit history
├── ui/screens/CanvasDetailScreen.kt   — Config tab
├── ui/components/CodeExecutionCard.kt — Simplified UX
└── live/LiveSessionViewModel.kt       — Haptic for tool calls
```

---

## v6.0.0 — URL Context Plugin, Settings UX & Text Interruption (Feb 11, 2026)

### What was done

#### URL Context Tool
- **New `url_context` tool**: Apsara can fetch and analyze web page content during live conversations:
  - User says "summarize this article" + provides URL → Apsara fetches content via Interactions API
  - Uses Gemini to extract clean text, metadata (title, description, word count)
  - Supports both sync (blocking) and async (non-blocking) execution modes
  - Progress streaming: backend sends incremental status updates → Android shows progress in tool call cards (fetching → analyzing → complete)
  - Tool call card in chat shows URL metadata (title, word count) on completion

- **Backend**: `url_context` handler in `tools.js` delegates to Interactions API for intelligent content extraction. Sends `tool_progress` WebSocket messages during processing.
- **Android**: `LiveWebSocketClient` parses `tool_progress` events, `LiveSessionViewModel` updates `EmbeddedToolCall` progress in real-time.

#### Settings UX Refinement
- **Haptic Feedback description highlight**: Tapping the "Make sure you turn on Output Transcriptions" description text now:
  1. Auto-expands the Live Settings section
  2. Smooth-scrolls to the Output Transcription toggle
  3. Highlights it with an animated accent border that fades after 2 seconds
  - Uses `LaunchedEffect` with scroll state and `Animatable` border alpha

#### Canvas Edit Title Fix
- **Title updates on edit**: When `edit_canvas` tool modifies a canvas, the title now updates based on:
  1. The edit instruction (if descriptive enough)
  2. HTML `<title>` tag extraction from the generated code

#### Text Interruption
- **Text messages now interrupt Apsara's speech**: Previously, sending a text message while Apsara was speaking in live mode did NOT interrupt her — only voice could interrupt. Now:
  - **Backend**: `sendText()` first sends `realtimeInput.text` (which triggers Gemini's activity detection and interruption), then sends `clientContent` (which submits the actual text turn). This two-step approach mimics how voice input naturally triggers interruption.
  - **Android**: `sendText()` proactively clears the audio playback queue before the server `interrupted` signal arrives, giving instant silence when the user types.

### Protocol additions

#### Server → Client messages (new)
| Type | Fields | Description |
|------|--------|-------------|
| `tool_progress` | `id`, `name`, `progress` | Incremental tool execution progress |

### Files changed

```
backend/src/
├── tools.js                          — url_context tool declaration + handler
├── ws-handler.js                     — tool_progress forwarding, sendText dual-send
├── gemini-live-session.js            — sendText: realtimeInput.text + clientContent
└── interactions/interactions-tools.js — url_context in Interactions API

app/src/main/java/com/shubharthak/apsaradark/
├── data/LiveSettingsManager.kt       — URL Context plugin entry
├── data/MockData.kt                  — URL Context plugin card
├── live/LiveWebSocketClient.kt       — tool_progress parsing, sendText audio clear
├── live/LiveSessionViewModel.kt      — Tool progress updates, proactive audio clear
├── ui/screens/HomeScreen.kt          — Tool progress in cards, URL metadata display
├── ui/screens/PluginsScreen.kt       — URL Context toggle
└── ui/screens/SettingsScreen.kt      — Haptic description clickable, scroll-to-highlight
```

---

## Version Summary

| Version | Codename | Date | Key Feature |
|---------|----------|------|-------------|
| v0.1.0 | Initial UI Shell | Feb 9 | Project setup, dark theme, navigation drawer |
| v0.2.0 | UI Cleanup & Logo | Feb 9 | Logo integration, animated title, drawer cleanup |
| v0.3.0 | Theme System | Feb 9 | 8 VS Code-inspired themes, settings screen |
| v1.0.2 | UI Polish | Feb 9 | Home redesign, minimal chips, input bar polish |
| v2.0.0 | Gemini Live API | Feb 9 | Full backend + Android live session integration |
| v2.1.0 | Live Mode UX | Feb 10 | Plain text Apsara output, async transcription |
| v2.2.0 | Thoughts UI | Feb 10 | Collapsible thoughts, config logging |
| v2.3.0 | Thoughts Polish | Feb 10 | Thoughts above text, bold markdown, disconnect logging |
| v2.4.0 | Per-Tool Async | Feb 10 | Per-tool async/sync, audio routing fix |
| v2.5.0 | Tool Call UI | Feb 10 | Embedded tool call cards, plugin UI cleanup |
| v2.6.0 | Amplitude Visualizer | Feb 9 | Real audio amplitude-driven visualizer |
| v2.6.1 | Visualizer Fix | Feb 9 | User speech detection, amplitude boost |
| v2.7.0 | Attachment Sheet | Feb 9 | Live mode bottom sheet (Camera, Photos, Files) |
| v2.8.0 | Live Mode Only | Feb 9 | Removed normal mode, haptic feedback, session resumption |
| v2.9.0 | Session Controls | Feb 9 | Resumption toggle, GoAway indicator |
| v3.0.0 | Foreground Service | Feb 9 | Background persistence, wake/WiFi locks |
| v3.1.0 | Notification Actions | Feb 9–10 | Mute/End in notification, GoAway fix |
| v3.2.0 | Live Video Camera | Feb 10 | CameraX preview, annotations, media resolution |
| v3.3.0 | Markdown (Reverted) | Feb 10 | Attempted markdown rendering, reverted |
| v4.0.0 | Canvas & Interactions | Feb 10 | AI web app generation, Interactions API |
| v4.1.0 | Canvas Detail & Edit | Feb 10 | Canvas viewer tabs, edit tool, list tool |
| v5.0.0 | Apsara Interpreter | Feb 10 | Inline code execution in live chat |
| v5.1.0 | Interpreter Polish | Feb 10 | edit_code, config tabs, haptic for tools |
| v6.0.0 | URL Context & Interruption | Feb 11 | URL tool, Settings UX, text interruption |
