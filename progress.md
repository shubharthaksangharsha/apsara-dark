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
