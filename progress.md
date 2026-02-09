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
- **Feature cards**: Removed subtitles — now just icon + title (Talk, Design, Control, Reminders).
- **Removed recent chat history** section and `ChatBubble` component entirely.
- **Removed `ChatMessage`** data class — no longer needed.

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
