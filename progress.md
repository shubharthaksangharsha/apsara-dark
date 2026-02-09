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
└── data/LiveSettingsManager.kt       — Persistent live settings

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
