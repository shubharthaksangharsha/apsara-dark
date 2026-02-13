# Apsara Dark — Progress

> Android AI companion powered by Gemini Live API — real-time voice, vision, code execution, and web app generation.
> Versions follow `v0.0.x` pre-release convention (v0.0.0 → v0.0.9).

---

## v0.0.0 — Project Setup, UI Shell & Theme System (Feb 9, 2026)

### Summary

Foundation of the Android app — Jetpack Compose UI shell with dark theme, navigation, 8 switchable themes, and all core layout components.

### What was done

- **Project setup**: Android Jetpack Compose with Kotlin, Material 3, targeting SDK 35 (min SDK 26).
- **Dark theme**: Custom `ApsaraDarkTheme` — true-black surfaces (`#0D0D0D`), purple accent (`#B388FF`), custom color palette and typography.
- **Navigation drawer**: Slide-from-left hamburger menu with My Canvas, My Plugins, Laptop Control, Settings.
- **Home screen**: Centered tagline, minimal 2×2 feature card grid (Talk, Design, Control, Reminders), clean hamburger-only top bar.
- **Bottom input bar**: Text input with attach button, mic/send toggle, focus-aware animated border, raised for thumb reach.
- **Logo integration**: `apsara_logo.png` as launcher icon + in drawer header, animated purple shimmer title.
- **8 VS Code-inspired themes**: Dark (default), Monokai, Nightly, Solarized, Dracula, Nord, Light, Monochrome.
- **ThemeManager**: Singleton persisting theme choice to SharedPreferences — survives restarts. All components read from `LocalThemeManager`.
- **Settings screen**: Expandable/collapsible theme grid with names only, minimal pill-style chips.
- **Dynamic system bars**: Status/nav bar colors update per theme automatically.

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
    │   ├── HomeScreen.kt
    │   └── SettingsScreen.kt
    └── theme/
        ├── Color.kt
        ├── Theme.kt
        ├── Type.kt
        ├── AppThemes.kt
        └── ThemeManager.kt
```

---

## v0.0.1 — Gemini Live API Integration (Feb 9, 2026)

### Summary

Full-stack live voice integration — Node.js backend relay server + Android WebSocket client + audio streaming + all Gemini Live API features.

### What was done

#### Backend (Node.js)
- Express + WebSocket relay server bridging Android ↔ Gemini Live API.
- Full JSON protocol: audio, text, video, tool calls, session resumption, context compression.
- Health (`/health`) and config (`/config`) HTTP endpoints.
- Single-user design, API key stored in `.env` on server.
- Deployed to Oracle Cloud at `wss://apsara-dark-backend.devshubh.me/live`.

#### Android
- **`LiveSettingsManager`** — persists all Gemini Live config to SharedPreferences (backend URL, model, voice, temperature, system instruction, 7+ toggles).
- **`LiveWebSocketClient`** — OkHttp WebSocket client with JSON protocol, SharedFlow streams for audio/text/transcription/events.
- **`LiveAudioManager`** — PCM audio record (16kHz) + playback (24kHz), mute toggle, queue-based playback, hardware AEC.
- **`LiveSessionViewModel`** — orchestrates WS ↔ audio ↔ UI state, connect/disconnect/mute/sendText.

#### Home Screen Integration
- Live mode triggered by tapping **Talk card** or **Live icon** in input bar.
- **Live mode UI**: Animated pulsing orb, status text, input/output transcripts, live input bar (+ | Type | Mic | End).
- Top bar hides during live mode. Drawer gestures disabled during live mode.

#### Settings — Live Settings Panel
- Expandable "Live Settings" section with all configurable options:
  - Backend URL, Model, Voice, Temperature slider, System Instruction.
  - Toggles: Affective Dialog, Proactive Audio, Input/Output Transcription, Context Compression, Google Search, Include Thoughts.

### Gemini Live API Features Supported

| Feature | Status |
|---------|--------|
| Audio streaming (bidirectional) | ✅ PCM 16kHz in → 24kHz out |
| Video streaming | ✅ JPEG frames via `sendRealtimeInput` |
| Text input/output | ✅ |
| Voice selection (8 voices) | ✅ Puck, Charon, Kore, Fenrir, Aoede, Leda, Orus, Zephyr |
| System instruction | ✅ Custom Apsara personality |
| Temperature control | ✅ 0.0 – 2.0 |
| Context window compression | ✅ Sliding window for unlimited sessions |
| Session resumption | ✅ Auto-stores handle, auto-reconnect |
| Affective dialog | ✅ Emotion-aware responses |
| Proactive audio | ✅ Model decides when to respond |
| Thinking (configurable budget) | ✅ |
| Input/Output transcription | ✅ Real-time |
| Google Search grounding | ✅ |
| Function calling (sync + async) | ✅ |
| GoAway handling | ✅ Auto-reconnect |
| Interruption handling | ✅ |
| Automatic VAD | ✅ Gemini built-in |

---

## v0.0.2 — Live Mode UX, Thoughts & Tool System (Feb 9–10, 2026)

### Summary

Refined the live chat experience — plain text Apsara output, collapsible thoughts UI, plugin system with per-tool async/sync control, embedded tool call cards, and audio routing fixes.

### What was done

#### Live Mode Chat UX
- **Apsara output**: Plain text (no bubble, no border) — clean left-aligned text. User messages only get chat bubbles.
- **Async transcription**: Output shown immediately as received (not waiting for turn complete).
- **Conversation history**: `LiveMessage` objects in ViewModel with `isStreaming` flag.
- **Center orb removed**: Shows "Start talking" if no conversation yet.
- **Connecting state**: Centered spinner with "Connecting…" text.

#### Thoughts UI
- Collapsible "▸ Thoughts" / "▾ Thoughts" section **above** response text.
- Bold markdown parsing (`**text**` → bold) in thought text.
- Backend forwards `thought` message type when `includeThoughts` is enabled.

#### Plugin / Tool System
- **Plugins screen** (`PluginsScreen.kt`): UI for managing tools with per-tool on/off and async/sync toggles.
- **Per-tool async/sync**: Backend `toolAsyncModes` map — each tool independently configured as `NON_BLOCKING` or blocking.
- **Tool call UI cards**: Embedded in Apsara messages — shows tool name, status (running/completed), async/sync label, expandable JSON result.
- **Echo prevention**: Hardware AEC for echo cancellation + thought filtering when disabled.

#### Audio Routing Fix
- Shared audio session ID between `AudioRecord` and `AudioTrack` for proper hardware AEC.
- `AudioTrack` restart on output device change.
- Audio output device dropdown UI with proper positioning.

#### Backend Changes
- TEXT modality removed (native audio model doesn't support it).
- Config logging on connect for debugging.
- Per-tool `toolAsyncModes` replaces global `asyncFunctionCalls`.
- Disconnect reason logging improved.

---

## v0.0.3 — Audio Visualizer, Attachments & Haptic Feedback (Feb 9, 2026)

### Summary

Real audio-driven visualizer, live mode attachment sheet, image paste support, haptic feedback synced to Apsara's speech, and refactored to live-mode-only.

### What was done

#### Amplitude-Driven Visualizer
- Real-time RMS amplitude from mic PCM data and playback queue.
- Exposed as `inputAmplitude` / `outputAmplitude` StateFlows (0.0–1.0).
- Organic sine-wave bars scaled by actual audio volume.
- User speech detection: `activeSpeaker = USER` set instantly when mic amplitude > 0.05 (no waiting for backend transcription roundtrip).
- Boosted sensitivity: lower RMS divisor (4000), `sqrt()` curve, faster smoothing (0.45).

#### Live Mode Attachment Bottom Sheet
- Tapping `+` opens `ModalBottomSheet` with 2-row layout: Video, Photos, Files + Camera, Screenshare.
- Image paste support via `RichContentEditText` + image preview strip.

#### Haptic Feedback
- Transcript-synced vibration — pulses per new word/chunk from output transcription.
- Duration scales with word count (45–100ms), moderate-strong intensity.
- Toggle in General Settings with note about Output Transcription requirement.

#### Live-Mode-Only Refactor
- Removed normal text input mode — app now only shows input bar in live mode.
- "Talk" feature card is the sole entry point to live mode.

#### Session Resumption (Android Client)
- Parses `session_resumption_update` from backend.
- Detects actual reconnections (not first handle) and shows "⟳ Session resumed" system message.

---

## v0.0.4 — Session Resilience & Background Service (Feb 9–10, 2026)

### Summary

Foreground service for background persistence, session resumption/compression controls, GoAway handling, notification actions, wake/WiFi locks, and connection stability fixes.

### What was done

#### Foreground Service (`LiveSessionService`)
- Persistent "Apsara is listening…" notification while live mode is active.
- `FOREGROUND_SERVICE_TYPE_MICROPHONE | MEDIA_PLAYBACK` for Android 14+.
- Mute/Unmute and End Session notification actions with live speaker animation icon.
- `StopLiveReceiver` broadcast receiver for notification → ViewModel communication via `LiveSessionBridge` SharedFlow.

#### Connection Stability
- **ViewModel hoisting**: Moved from `HomeScreen` to `AppNavigation` (Activity scope) — survives Settings/Plugins navigation.
- **Wake lock**: `PowerManager.PARTIAL_WAKE_LOCK` prevents CPU sleep during live session.
- **WiFi lock**: `WifiManager.WifiLock` keeps network stable in background.
- **Fixed "Software caused connection abort"**: Root cause was ViewModel destruction during navigation.

#### Session Controls
- **Session Resumption toggle**: On/off in Live Settings, controls whether Gemini provides resumption handles.
- **GoAway indicator**: "⏳ Connection refreshing — reconnecting seamlessly…" system message when Gemini sends GoAway.
- **GoAway reconnect fix**: Fixed infinite loop (GoAway → reconnect → immediate GoAway). Added `suppressDisconnect` flag and loop-guard logic.

#### Permissions Added
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `POST_NOTIFICATIONS`, `WAKE_LOCK`, `VIBRATE`

### New Files

```
app/src/main/java/com/shubharthak/apsaradark/live/
├── LiveSessionService.kt     — Foreground service
├── LiveSessionBridge.kt      — Notification → ViewModel bridge
└── StopLiveReceiver.kt       — End Session broadcast receiver
```

---

## v0.0.5 — Live Video Camera & Annotations (Feb 10, 2026)

### Summary

CameraX-based live video streaming to Gemini, annotation drawing overlay, media resolution settings, and markdown rendering attempt (reverted).

### What was done

#### Live Video Camera
- Full CameraX camera preview during live mode:
  - Tap-to-focus (auto-resets after 3s)
  - Pinch-to-zoom (smooth gesture detection)
  - Flash toggle (on/off)
  - Camera flip (front ↔ rear)
  - Minimize / PiP-style mode (camera shrinks to floating window)
  - JPEG frames extracted and streamed to Gemini via WebSocket `video` message type.

#### Camera Annotation Drawing
- Pen-based freehand drawing overlay on top of camera preview.
- Icon-only draw toolbar (pen, clear) positioned outside camera gesture area.
- Annotations visible to Gemini through the video feed.

#### Media Resolution Setting
- `mediaResolution` added to `LiveSettingsManager` (LOW, MEDIUM, HIGH).
- Controls quality/size of video frames sent to Gemini.
- Backend maps to `MEDIA_RESOLUTION_LOW/MEDIUM/HIGH` in Gemini config.

#### Markdown Rendering (Attempted & Reverted)
- Added rich markdown rendering for Apsara output (bold, italic, headers, code blocks).
- Added deferred rendering (plain text during streaming, format after 1s idle).
- **Reverted entirely**: Partial markdown tokens cause visual glitches during streaming; re-rendering on every chunk is expensive; deferred formatting creates jarring visual jumps.

---

## v0.0.6 — Interactions API & Apsara Canvas (Feb 10, 2026)

### Summary

Introduced the Interactions API (Gemini text/chat alongside Live API) and Apsara Canvas — AI-generated web applications created during live conversations and served in-app.

### What was done

#### Backend: Interactions API
- Separate Gemini text/chat subsystem (`/backend/src/interactions/`) for complex tool operations needing multi-turn reasoning.
- While Live API handles real-time audio, Interactions API handles Canvas generation, code execution, URL analysis.
- `interactions-service.js` — Gemini chat sessions with tool calling.
- `interactions-tools.js` — Tool declarations (time, weather, canvas, code tools).

#### Backend: Canvas Plugin
- **`canvas-service.js`** — Generates complete HTML/CSS/JS web apps from natural language prompts:
  - Multi-step: prompt → code → validation → auto-fix (up to 3 retries) → serve.
  - Supports React (CDN) and vanilla HTML/CSS/JS.
  - Auto-injects responsive viewport for mobile-first rendering.
- **`canvas-store.js`** — In-memory project store with version history.
- **`canvas-router.js`** — Express routes serving generated web apps at `/canvas/:id`.

#### Live API Tools
- `apsara_canvas` — Generate web apps from prompts.
- `list_canvases` — Enumerate all user's canvases.
- `get_canvas_detail` — Get full canvas details (code, prompt, log, metadata).
- `edit_canvas` — Modify existing canvas with Gemini + title extraction from `<title>` tag.

#### Android: Canvas Screens
- **My Canvas list** — All generated canvases with title, timestamp, preview.
- **Canvas viewer** — Full-screen WebView for generated web apps.
- **Canvas detail** — Tabbed interface: Code, Prompt, Log, Info, Config tabs.
- Canvas back button returns to live session (not drawer).
- In-app notifications for canvas creation progress.

#### WebSocket Stability Fixes
- Replaced protocol-level pings with application-level JSON `ping`/`pong` (fixes `Control frames must be final`).
- `noServer` WebSocket routing to prevent Express middleware interference.
- Caddy `flush_interval -1` for proper WebSocket streaming.

### New Files

```
backend/src/
├── interactions/
│   ├── index.js, interactions-service.js, interactions-tools.js
│   ├── interactions-router.js, interactions-ws-handler.js, interactions-config.js
├── canvas/
│   ├── index.js, canvas-service.js, canvas-store.js, canvas-router.js

app/src/main/java/com/shubharthak/apsaradark/ui/screens/
├── CanvasScreen.kt            — Canvas list, detail, viewer
```

---

## v0.0.7 — Apsara Interpreter (Inline Code Execution) (Feb 10, 2026)

### Summary

Full code execution environment — Apsara writes and runs Python/JavaScript code in a sandboxed backend environment, with inline results (text, images, plots) displayed in live chat.

### What was done

#### Backend: Interpreter Engine
- **`interpreter-service.js`** — Receives execution requests:
  - Gemini (Interactions API) writes clean code from natural language.
  - Executes in subprocess sandbox, captures stdout/stderr/images.
  - Session-based state: variables and imports persist across runs.
- **`interpreter-store.js`** — Session store (code, outputs, metadata, edit history).
- **`interpreter-router.js`** — Express routes for serving generated images.

#### Live API Tools
- `run_code` — Execute Python/JS, return output + images.
- `list_code_sessions` — Enumerate all code sessions.
- `get_code_session` — Get full session details.
- `edit_code` — Modify existing code session (preserves context, single matplotlib image per run, edit history tracked).

#### Android: Code Execution UI
- **`CodeExecutionCard`** — Embedded in Apsara messages:
  - Syntax-highlighted code display.
  - Execution output (stdout/stderr).
  - Inline generated images with zoom.
  - Loading/completed status indicators.
- **My Code screen** (`InterpreterScreen.kt`) — Lists all code sessions.
- **Code detail screen** — Tabbed: Code, Output, Images, Config tabs.
- **Haptic feedback for tool calls** — Vibrates on tool call start/complete.

### New Files

```
backend/src/
├── interpreter/
│   ├── interpreter-service.js, interpreter-store.js, interpreter-router.js

app/src/main/java/com/shubharthak/apsaradark/ui/screens/
├── InterpreterScreen.kt       — Code session list + detail
```

---

## v0.0.8 — URL Context Plugin & Settings UX (Feb 11, 2026)

### Summary

New URL Context tool for web page analysis during live conversations, plus Settings UX refinements with smart navigation and highlighting.

### What was done

#### URL Context Tool
- **`url_context` tool** — Apsara fetches and analyzes web pages during live conversations:
  - User says "summarize this article" + URL → Apsara fetches content via Interactions API.
  - Gemini extracts clean text, metadata (title, description, word count).
  - Supports sync (blocking) and async (non-blocking) modes.
  - Progress streaming: `tool_progress` WebSocket messages → Android shows incremental status (fetching → analyzing → complete).
  - Tool call card shows URL metadata on completion.

#### Settings UX Refinement
- **Haptic Feedback description highlight**: Tapping "Make sure you turn on Output Transcriptions" text:
  1. Auto-expands the Live Settings section.
  2. Smooth-scrolls to the Output Transcription toggle.
  3. Highlights it with animated accent border (fades after 2s).

#### Canvas Edit Title Fix
- `edit_canvas` now updates the app title based on edit instructions and HTML `<title>` extraction.

---

## v0.0.9 — Text Interruption & Polish (Feb 11, 2026)

### Summary

Text messages now interrupt Apsara's speech (matching voice interruption behavior), completing the real-time interaction model. Final polish across the system.

### What was done

#### Text Interruption
- **Problem**: Sending a text message while Apsara was speaking did NOT interrupt her — only voice could.
- **Solution (Backend)**: `sendText()` now uses a dual-send approach:
  1. `session.sendRealtimeInput({ text })` — triggers Gemini's activity detection → interruption event.
  2. `session.sendClientContent({ turns, turnComplete })` — submits the text as a formal conversation turn.
- **Solution (Android)**: `sendText()` proactively clears the audio playback queue before the server `interrupted` signal arrives → instant silence.

#### How `realtimeInput.text` vs `sendClientContent` Work

| Method | Purpose | Interrupts? | History? |
|--------|---------|-------------|----------|
| `realtimeInput.text` | Real-time input stream (like audio) | ✅ Yes | ❌ Transient |
| `sendClientContent` | Formal conversation turn | ❌ No | ✅ Persisted |

By sending both: interruption (from realtimeInput) + proper conversation history (from clientContent).

#### System-Wide Polish
- All 10 Live API tools fully functional with sync/async modes.
- Consistent tool progress streaming across all async tools.
- Settings UI refinements and navigation improvements.

---

## v0.1.0 — Canvas UX Round 2 & Per-Plugin Settings (Feb 13, 2026)

### Summary

Refined Canvas user experience — version-aware code navigation, simplified streaming cards, direct Ready→Canvas navigation, and per-plugin interaction settings.

### What was done

#### Version-Aware Code View
- Tapping "Code" from a specific version preview now opens `CanvasDetailViewer` pre-selected to that version's code.
- `CanvasViewer` passes `viewingVersion` to `onViewCode`, `CanvasDetailViewer` accepts `initialVersion`.

#### Simplified CanvasStreamCard
- During streaming: card shows header only (no auto-expand, no live code).
- Tap during streaming: expands to show code in monospace view.
- Tap when "Ready": navigates directly to `canvas/{canvasId}` route → opens that canvas preview.
- New `canvas/{canvasId}` route in `Navigation.kt`, `CanvasScreen` auto-fetches and opens the canvas.

#### Per-Plugin Interaction Settings
- Replaced single shared interaction config with per-plugin fields: Canvas, Interpreter, URL Context.
- Each plugin has independent: model, temperature, thinking level, thinking summaries, max output tokens.
- `SettingsScreen` shows collapsible per-plugin sections via reusable `PluginSettingsContent` composable.
- `buildConfigMap()` sends nested `interactionConfig { canvas: {...}, interpreter: {...}, url_context: {...} }`.
- Backend `ws-handler.js` reads per-tool config with flat-format backward compatibility.

### Files Modified

| File | Changes |
|------|---------|
| `CanvasScreen.kt` | Version-aware code view, `canvasId` auto-open |
| `HomeScreen.kt` | Simplified `CanvasStreamCard`, `onNavigateToCanvasApp` threading |
| `Navigation.kt` | `canvas/{canvasId}` route with NavType.StringType |
| `LiveSettingsManager.kt` | Per-plugin settings fields, setters, nested config map |
| `SettingsScreen.kt` | Collapsible per-plugin `InteractionSettingsPanel` |
| `ws-handler.js` | Per-tool interaction config lookup with fallback |
| `HomeScreen.kt` | CanvasStreamCard tap fix: code cards toggle code, non-code cards navigate |
| `canvas-service.js` | Built-in URL Context tool for create/edit/fix generation |
| `tools.js` | Updated tool descriptions for URL context routing |
| `config.js` | Live API system instruction: URL context routing guidance |

---

### v1.0.0 — Canvas UX Refinements (Feb 14)

#### CanvasStreamCard Navigation Fix
- Fixed pending buffer missing `canvasId` extraction for canvas tools — no-code cards now navigate properly
- Added `canvasId` and `canvasRenderUrl` extraction to `pendingToolCalls` update path in `LiveSessionViewModel.kt`

#### Stream Thought Summaries + Tool Call Status
- Backend: `_generateStreaming` now captures thought/thought_summary deltas and tool execution events from Interactions API
- `ws-handler.js`: Accumulated thought text sent as `canvas_progress` messages with 'thinking' status; tool calls sent with 'tool_call' status
- Android: `CanvasStreamCard` subtitle shows thought summaries (italic, 2 lines) and tool call status during generation

| File | Changes |
|------|---------|
| `LiveSessionViewModel.kt` | `canvasId`/`canvasRenderUrl` extraction in pending buffer path |
| `canvas-service.js` | `_generateStreaming` emits thought/tool events; `generateApp`/`editApp` thread callbacks |
| `tools.js` | `executeCanvasTool`/`executeCanvasEditTool` accept `onThought`/`onToolStatus` |
| `ws-handler.js` | Accumulated thought text + tool status callbacks in sync/async paths |
| `HomeScreen.kt` | `CanvasStreamCard` subtitle: italic thoughts, 2-line maxLines, ellipsis overflow |

---

## Version Summary

| Version | Milestone | Date | Key Feature |
|---------|-----------|------|-------------|
| **v0.0.0** | Foundation | Feb 9 | UI shell, 8 themes, navigation, dark theme |
| **v0.0.1** | Live Voice | Feb 9 | Gemini Live API — full backend + Android integration |
| **v0.0.2** | UX & Tools | Feb 9–10 | Chat UX, thoughts, plugin system, per-tool async/sync |
| **v0.0.3** | Audio & Haptics | Feb 9 | Amplitude visualizer, attachments, haptic feedback |
| **v0.0.4** | Resilience | Feb 9–10 | Foreground service, session resumption, GoAway handling |
| **v0.0.5** | Camera | Feb 10 | Live video camera, annotations, media resolution |
| **v0.0.6** | Canvas | Feb 10 | Interactions API, AI web app generation & serving |
| **v0.0.7** | Interpreter | Feb 10 | Inline code execution, images, session management |
| **v0.0.8** | URL Context | Feb 11 | Web page analysis tool, Settings UX polish |
| **v0.0.9** | Interruption | Feb 11 | Text interrupts speech, system-wide polish |
| **v0.1.0** | Canvas UX R2 | Feb 13 | Version-aware code, simplified stream card, per-plugin settings |

---

## Full Architecture (as of v0.0.9)

### Android App

```
com.shubharthak.apsaradark/
├── MainActivity.kt
├── data/
│   ├── LiveSettingsManager.kt         — All settings (SharedPreferences)
│   └── MockData.kt                    — Plugin cards, feature cards
├── live/
│   ├── LiveWebSocketClient.kt         — OkHttp WS client, JSON protocol
│   ├── LiveAudioManager.kt            — PCM record/playback, AEC, amplitude
│   ├── LiveSessionViewModel.kt        — Session orchestrator
│   ├── LiveSessionService.kt          — Foreground service
│   ├── LiveSessionBridge.kt           — Notification → ViewModel bridge
│   └── StopLiveReceiver.kt            — End Session receiver
└── ui/
    ├── components/
    │   ├── AppDrawer.kt               — Navigation drawer
    │   ├── BottomInputBar.kt          — Input bar + visualizer
    │   ├── AttachmentBottomSheet.kt   — Attachment options
    │   ├── CameraPreviewCard.kt       — CameraX preview
    │   └── FeatureCard.kt             — Home feature cards
    ├── navigation/
    │   └── Navigation.kt             — NavHost + routes
    ├── screens/
    │   ├── HomeScreen.kt             — Main screen + live mode
    │   ├── SettingsScreen.kt         — Themes + Live Settings
    │   ├── PluginsScreen.kt          — Tool management
    │   ├── CanvasScreen.kt           — Canvas list + detail + viewer
    │   └── InterpreterScreen.kt      — Code session list + detail
    └── theme/
        ├── Color.kt, Theme.kt, Type.kt
        ├── AppThemes.kt              — 8 theme palettes
        └── ThemeManager.kt           — Theme persistence
```

### Backend

```
backend/src/
├── server.js                          — Express + WS server
├── config.js                          — Global config
├── ws-handler.js                      — Live WS protocol handler
├── gemini-live-session.js             — Gemini Live API wrapper
├── tools.js                           — 10 tool declarations + handlers
├── interactions/                      — Text/Chat API (non-live)
│   ├── interactions-service.js, interactions-tools.js
│   ├── interactions-router.js, interactions-ws-handler.js
│   └── interactions-config.js
├── canvas/                            — AI Web App Generation
│   ├── canvas-service.js, canvas-store.js, canvas-router.js
├── interpreter/                       — Code Execution Engine
│   ├── interpreter-service.js, interpreter-store.js, interpreter-router.js
```
