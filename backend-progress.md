# Apsara Dark — Backend Progress

## v1.0.0 — Gemini Live API Backend (Feb 9, 2026)

### What was done

- **Node.js relay server** — server-to-server architecture where the Android app connects to our backend via WebSocket, and the backend connects to Gemini Live API. API key stays safely on the server.

- **Tech stack**: Node.js 20+, Express, `ws` (WebSocket), `@google/genai` SDK.

### Architecture

```
Android App (Apsara Dark)
    │
    │  WebSocket (JSON protocol)
    ▼
┌─────────────────────────────────────┐
│  Apsara Dark Backend (Node.js)      │
│                                     │
│  src/                               │
│  ├── server.js          — Entry     │
│  │   ├── Express HTTP (health,      │
│  │   │   config endpoints)          │
│  │   └── WebSocket server (/live)   │
│  │                                  │
│  ├── ws-handler.js      — Protocol  │
│  │   Bridges app ↔ Gemini Live      │
│  │   Handles all message types      │
│  │   Auto-reconnect on GoAway      │
│  │                                  │
│  ├── gemini-live-session.js         │
│  │   Wraps @google/genai Live API   │
│  │   Full feature support           │
│  │                                  │
│  └── config.js          — Defaults  │
│      Voices, models, audio format   │
│      Default session config         │
│                                     │
│  .env                   — API key   │
│  package.json           — Deps      │
└─────────────────────────────────────┘
    │
    │  WebSocket (Gemini SDK)
    ▼
┌─────────────────────────────────────┐
│  Google Gemini Live API             │
│  gemini-2.5-flash-native-audio     │
└─────────────────────────────────────┘
```

### Features implemented

#### Core connection
- WebSocket server on `/live` path
- Express HTTP for `/health` and `/config` endpoints
- Single-user design — no auth needed, API key on server only

#### Gemini Live API — full feature support

| Feature | Status | Notes |
|---------|--------|-------|
| **Audio streaming (bidirectional)** | ✅ | PCM 16kHz in → 24kHz out, base64 over WS |
| **Video streaming** | ✅ | JPEG frames via `sendRealtimeInput` |
| **Text input/output** | ✅ | `sendClientContent` + text parts |
| **Voice selection** | ✅ | 8 voices: Puck, Charon, Kore, Fenrir, Aoede, Leda, Orus, Zephyr |
| **Model selection** | ✅ | Native audio + standard live models |
| **System instruction** | ✅ | Custom Apsara personality |
| **Temperature control** | ✅ | 0.0 – 2.0 range |
| **Context window compression** | ✅ | Sliding window — unlimited sessions |
| **Session resumption** | ✅ | Auto-stores handle, auto-reconnect on GoAway |
| **Affective dialog** | ✅ | Emotion-aware responses |
| **Proactive audio** | ✅ | Model decides when to respond |
| **Thinking (native audio)** | ✅ | Dynamic thinking, configurable budget |
| **Thought summaries** | ✅ | Optional `includeThoughts` |
| **Input audio transcription** | ✅ | Real-time transcription of user speech |
| **Output audio transcription** | ✅ | Real-time transcription of Gemini speech |
| **Google Search grounding** | ✅ | Reduces hallucinations |
| **Function calling** | ✅ | Sync + async (NON_BLOCKING, scheduling) |
| **Tool response handling** | ✅ | Manual responses from client |
| **Multiple tools** | ✅ | Google Search + function calling combined |
| **GoAway handling** | ✅ | Auto-reconnect before disconnect |
| **Interruption handling** | ✅ | Forwards interrupt events to client |
| **Turn/generation complete** | ✅ | Client knows when Gemini is done |
| **Usage metadata** | ✅ | Token counts forwarded |
| **Automatic VAD** | ✅ | Uses Gemini's built-in VAD (not configured manually) |

#### NOT configured (by design)
- **Manual VAD** — uses Gemini's automatic VAD as requested
- **Authentication layer** — single-user, no need
- **Ephemeral tokens** — server-to-server approach, not needed
- **Rate limiting** — single user

### WebSocket protocol

#### Client → Server messages
| Type | Fields | Description |
|------|--------|-------------|
| `connect` | `config?` | Start Gemini Live session with optional config overrides |
| `disconnect` | — | End session |
| `audio` | `data` (base64) | Stream audio chunk from mic |
| `video` | `data` (base64), `mimeType?` | Stream video frame from camera |
| `text` | `text` | Send text message |
| `context` | `turns`, `turnComplete` | Send conversation context |
| `tool_response` | `responses` | Respond to function calls |
| `audio_stream_end` | — | Signal mic pause |
| `update_config` | `config` | Update session config |
| `reconnect` | `config?` | Reconnect with optional new config |
| `get_state` | — | Get current session state |
| `get_config` | — | Get available config options |
| `ping` | — | Keepalive |

#### Server → Client messages
| Type | Fields | Description |
|------|--------|-------------|
| `connected` | — | Gemini session active |
| `disconnected` | `reason?` | Session ended |
| `audio` | `data` (base64), `mimeType` | Audio from Gemini |
| `text` | `text` | Text from Gemini |
| `input_transcription` | `text` | User speech transcription |
| `output_transcription` | `text` | Gemini speech transcription |
| `interrupted` | — | Gemini was interrupted by user |
| `turn_complete` | — | Gemini finished speaking |
| `generation_complete` | — | Full generation done |
| `tool_call` | `functionCalls` | Gemini wants to call functions |
| `go_away` | `timeLeft` | Connection ending soon |
| `session_resumption_update` | `resumable`, `hasHandle` | Resumption handle updated |
| `usage` | token counts | Usage metadata |
| `state` | session state fields | Current session state |
| `config_options` | voices, models, defaults, audio | Available options |
| `error` | `message`, `errorType?` | Error |
| `pong` | — | Keepalive response |

### Config options (for Live Settings panel in app)

These are the configurable settings exposed to the Android app:

- **Model**: `gemini-2.5-flash-native-audio-preview-12-2025`, `gemini-live-2.5-flash-preview`
- **Voice**: Puck, Charon, Kore, Fenrir, Aoede, Leda, Orus, Zephyr
- **Response modality**: Audio, Text, or both
- **Temperature**: 0.0 – 2.0 slider
- **System instruction**: Custom text
- **Affective dialog**: Toggle (emotion-aware)
- **Proactive audio**: Toggle (model decides when to speak)
- **Thinking budget**: Slider (0 = off, null = dynamic default)
- **Thought summaries**: Toggle
- **Input transcription**: Toggle
- **Output transcription**: Toggle
- **Context window compression**: Toggle (for unlimited sessions)
- **Google Search**: Toggle
- **Function calling**: Toggle

### How to run

```bash
cd backend
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY
npm install
npm start
```

Server starts at:
- HTTP: `http://0.0.0.0:3000`
- WebSocket: `ws://0.0.0.0:3000/live`

### Audio format reference

| Direction | Format | Sample Rate | Bit Depth | Channels |
|-----------|--------|-------------|-----------|----------|
| Input (mic → server → Gemini) | Raw PCM, little-endian | 16 kHz | 16-bit | Mono |
| Output (Gemini → server → app) | Raw PCM, little-endian | 24 kHz | 16-bit | Mono |

---

### Next steps

- Connect Android app to this backend via WebSocket
- Build Live Settings panel in Settings screen (expandable/collapsible)
- Implement audio recording + playback on Android side
- Add video streaming from camera
- Build Talk screen UI with live waveform visualization

---

## v1.1.0 — Backend Refinements (Feb 10, 2026)

### What was done

- **Model support cleanup**: Removed unsupported `gemini-live-2.5-flash-preview` model. Only `gemini-2.5-flash-native-audio-preview-12-2025` is supported for live sessions.
- **Voice/Modality logic**: Voice configuration (`speechConfig`) is only sent to Gemini when response modality includes AUDIO. Prevents invalid config errors when using TEXT-only modality.
- **Error handling improvements**: Better error messages for unsupported model/modality combinations, returned to client as structured `{ type: "error", message }` messages.
- **Audio/Video log spam fix**: Backend now silently discards audio/video data received when Gemini session is not connected, instead of logging warnings on every chunk.
- **Output transcription**: Confirmed async — `output_transcription` events are forwarded to the client immediately as they arrive from Gemini, not batched or delayed until `turn_complete`.

### Files changed

```
backend/src/
├── config.js              — Single model, updated defaults
├── ws-handler.js          — Improved error handling, silent discard of audio/video when disconnected
└── gemini-live-session.js — Voice config conditional on modality
```

---

## v1.2.0 — TEXT Modality Removed, Config Logging, Tools Debugging (Feb 10, 2026)

### What was done

- **TEXT modality removed**: The native audio model (`gemini-2.5-flash-native-audio-preview-12-2025`) does not support TEXT-only modality. Removed `TEXT` from `MODALITIES` constant. `ws-handler.js` now forces `responseModalities: ['AUDIO']` on every connect, regardless of what the client sends.
- **Config logging**: Backend now logs the full client config JSON on connect (`[WS] Client connect config: { ... }`). Also logs the Gemini config built from it (`[GeminiLive] Full Gemini config: { ... }`), and specifically logs the tools config (`[GeminiLive] Tools config — googleSearch: true/false → tools sent: [...]`).
- **Thought forwarding**: Already implemented `onThought` callback in `ws-handler.js` to forward `{ type: "thought", text }` messages to the client when model sends thought parts (`part.thought === true`).

### Files changed

```
backend/src/
├── config.js              — Removed TEXT from MODALITIES
├── ws-handler.js          — Config logging on connect, force AUDIO modality
└── gemini-live-session.js — Tools config logging
```

---

## v1.3.0 — Disconnect Logging (Feb 10, 2026)

### What was done

- **Disconnect reason fixed**: `onclose` handler now distinguishes between client-initiated disconnect (`client disconnected`) and server-initiated close (`server closed connection`), instead of showing `unknown`.
- **Disconnect logging**: `disconnect()` method now logs `[GeminiLive] Disconnecting (client requested)...` and sets `connected = false` before closing, so the `onclose` callback can infer the reason.
- **Close code logged**: `onclose` now logs the WebSocket close code alongside the reason.

---

## v1.4.0 — Per-Tool Async/Sync Function Calling (Feb 10, 2026)

### What was done

- **Per-tool async/sync control**: Replaced the global `asyncFunctionCalls` boolean with a per-tool `toolAsyncModes` map (`{ toolName: boolean }`). Each tool can now independently be configured as async (NON_BLOCKING) or sync (blocking).

- **`config.js`**: Default config changed from `asyncFunctionCalls: false` to `toolAsyncModes: {}`.

- **`gemini-live-session.js`**: `_buildGeminiConfig()` reads `toolAsyncModes` from the session config. For each function declaration, if `toolAsyncModes[toolName] === true`, the declaration gets `behavior: { NON_BLOCKING: {} }` (Gemini continues speaking while the tool executes). Otherwise, the tool defaults to blocking behavior.

- **`ws-handler.js`**: `onToolCall` handler now partitions incoming function calls into two groups based on `toolAsyncModes`:
  - **Sync tools**: Executed sequentially, responses sent with default scheduling.
  - **Async tools**: Executed concurrently via `Promise.all`, responses sent with `scheduling: 'INTERRUPT'` so Gemini can integrate results mid-turn.
  - Added `tool_call` and `tool_results` message types forwarded to the client for real-time UI status cards.

### Protocol additions

#### Server → Client messages (new)
| Type | Fields | Description |
|------|--------|-------------|
| `tool_call` | `functionCalls: [{name, id, args}]` | Gemini is invoking tools — shown as "running" cards in UI |
| `tool_results` | `results: [{id, name, response}]`, `mode` | Tool execution completed — updates cards to "completed" |

### Files changed

```
backend/src/
├── config.js              — toolAsyncModes replaces asyncFunctionCalls
├── ws-handler.js          — Per-tool async/sync partitioning, tool_call/tool_results events
└── gemini-live-session.js — Per-tool NON_BLOCKING in _buildGeminiConfig()
```

---

## v1.5.0 — Tool System Planning (Feb 9, 2026)

### Planning: Utility Plugin Box

**Goal**: Create a comprehensive set of tools that provide Android device capabilities, laptop (Linux) control, and server utilities — all manageable from the Plugins screen with per-tool on/off and sync/async toggles.

**Architecture decisions (pending):**

1. **Phone tools**: Backend forwards `tool_call` to Android app via WebSocket instead of auto-executing. App handles phone-specific tools (screenshot, battery, clipboard, etc.) and sends `tool_response` back.

2. **Laptop tools**: Two options being evaluated:
   - **Option A**: Small Node.js agent on laptop connects to backend via WebSocket (real-time, bidirectional)
   - **Option B**: Backend SSHs into laptop for command execution (simpler setup)

3. **Server tools**: Already executing on backend (e.g., `get_server_info`). New server tools like `web_fetch` run directly on the Oracle Cloud backend.

**Proposed tool list:**

| Tool | Tier | Execution | Description |
|------|------|-----------|-------------|
| `take_screenshot_phone` | 1 | Phone | MediaProjection screenshot → Gemini vision |
| `take_screenshot_laptop` | 1 | Laptop | scrot/gnome-screenshot via SSH/agent |
| `get_phone_battery` | 1 | Phone | Battery level, charging status |
| `get_phone_info` | 1 | Phone | Device model, Android version, storage, RAM |
| `get_laptop_info` | 1 | Laptop | CPU, RAM, disk, uptime via SSH |
| `send_clipboard` | 1 | Phone | Get/set clipboard text |
| `web_fetch` | 1 | Server | Fetch URL content (articles, APIs) |
| `run_shell_command` | 2 | Laptop | Execute shell commands (with safeguards) |
| `open_app` | 2 | Phone | Launch apps by name/package |
| `toggle_flashlight` | 2 | Phone | Flashlight on/off |
| `control_media` | 2 | Laptop | Play/pause/next via playerctl |
| `get_running_processes` | 2 | Laptop | Top processes by CPU/memory |

### Files to be changed (when implemented)

```
backend/src/
├── tools.js               — New tool declarations + handlers
├── ws-handler.js          — Phone tool forwarding logic

app/src/main/java/com/shubharthak/apsaradark/
├── live/LiveWebSocketClient.kt  — Handle phone tool requests from backend
├── live/LiveSessionViewModel.kt — Phone tool execution (screenshot, battery, etc.)
├── data/LiveSettingsManager.kt  — Per-tool toggles for new tools
├── data/MockData.kt             — Plugin cards for new tools
└── ui/screens/PluginsScreen.kt  — UI for new tool toggles
```
