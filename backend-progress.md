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

---

## v2.0.0 — Interactions API (Text/Chat Alongside Live) (Feb 10, 2026)

### What was done

- **Interactions API**: A separate Gemini text/chat subsystem that runs alongside the Live API. While the Live API handles real-time audio/video streaming, the Interactions API handles complex tool operations that need full multi-turn Gemini reasoning:
  - `interactions-service.js` — Manages Gemini `GoogleGenAI` chat sessions with tool calling support. Creates a new chat session per interaction, with configurable model, temperature, and system instruction.
  - `interactions-tools.js` — Tool declarations specific to the Interactions API (get_current_time, get_weather, canvas tools, code tools).
  - `interactions-router.js` — Express routes for `/interactions/*` HTTP endpoints.
  - `interactions-ws-handler.js` — WebSocket handler for streaming interaction responses back to the live session client.
  - `interactions-config.js` — Configuration (model, temperature, defaults).

- **Architecture**: The Live API detects a tool call → delegates to the Interactions API for complex operations (canvas generation, code execution, URL fetching) → streams results back to the client via the live WebSocket.

### Why a separate API?

The Gemini Live API is optimized for real-time audio streaming but has limitations for complex tool operations:
1. **No multi-turn reasoning**: Live API tool responses are single-shot; Interactions API supports multi-turn tool chains
2. **Better code generation**: Non-live models (e.g., `gemini-2.5-flash`) produce higher-quality code than the native audio model
3. **Longer outputs**: Live API has output length limits; Interactions API can generate full web apps
4. **Parallel execution**: Tool operations run on the Interactions API without blocking the live audio stream

### New files

```
backend/src/interactions/
├── index.js                    — Module exports
├── interactions-service.js     — Gemini chat session manager
├── interactions-tools.js       — Tool declarations (time, weather, canvas, code)
├── interactions-router.js      — Express routes
├── interactions-ws-handler.js  — WS streaming handler
└── interactions-config.js      — Config
```

---

## v2.1.0 — Canvas Plugin System (Feb 10, 2026)

### What was done

- **Apsara Canvas**: AI-generated web applications served directly from the backend:
  - `canvas-service.js` — Uses Gemini (via Interactions API) to generate complete HTML/CSS/JS web apps from natural language prompts:
    - Multi-step generation: prompt → code → validation → auto-fix → serve
    - Supports React (CDN) and vanilla HTML/CSS/JS
    - Auto-injects responsive viewport meta tags for mobile-first rendering
    - Error auto-fix: if generated code has issues, sends errors back to Gemini for correction (up to 3 retries)
  - `canvas-store.js` — In-memory store for canvas projects:
    - Stores code, metadata (title, prompt, timestamps), version history
    - Each canvas gets a unique ID and a public URL
  - `canvas-router.js` — Express routes:
    - `GET /canvas/:id` — Serves the generated web app HTML
    - `GET /canvas/:id/raw` — Returns raw code + metadata as JSON

- **`apsara_canvas` tool**: Live API tool declaration — when a user asks Apsara to "make a game" or "build a dashboard", Gemini invokes this tool. The handler:
  1. Sends `tool_progress` to client ("Generating code...")
  2. Calls `CanvasService.generateCanvas(prompt, title)`
  3. Sends `tool_progress` updates as generation proceeds
  4. Returns URL + metadata to Gemini for the spoken response

- **WebSocket heartbeat fix**: Replaced WebSocket protocol-level pings with application-level JSON `ping`/`pong` messages. Protocol-level pings were causing `Control frames must be final` errors when interleaved with large data frames.

- **noServer WebSocket routing**: Fixed root cause of frame corruption by using `ws` library's `noServer` mode with manual HTTP upgrade handling, preventing Express middleware from interfering with WebSocket frames.

- **Caddy proxy fix**: Added `flush_interval -1` to Caddy reverse_proxy for proper WebSocket streaming without buffering.

### New files

```
backend/src/canvas/
├── index.js              — Module exports
├── canvas-service.js     — AI code generation + validation + auto-fix
├── canvas-store.js       — In-memory project store
└── canvas-router.js      — Express routes for serving canvases
```

### Protocol additions

#### Server → Client messages (new)
| Type | Fields | Description |
|------|--------|-------------|
| `tool_progress` | `id`, `name`, `progress` | Incremental tool execution progress updates |
| `canvas_created` | `id`, `title`, `url` | A new canvas was generated and is ready to view |

---

## v2.2.0 — Canvas Detail, Edit & List Tools (Feb 10, 2026)

### What was done

- **`list_canvases` tool**: Returns all user canvases with ID, title, and creation date. Apsara can recall what apps the user has built.
- **`get_canvas_detail` tool**: Returns full details of a specific canvas — code, prompt, generation log, metadata, version count.
- **`edit_canvas` tool**: Modifies an existing canvas based on user instructions:
  - Fetches current code from `canvas-store`
  - Sends edit prompt + current code to Gemini (Interactions API)
  - Replaces canvas code with new version
  - Tracks edit history in canvas metadata
  - **Title update**: Extracts `<title>` from generated HTML to update the canvas title

- **Plugin registration fix**: Fixed `TOOL_DECLARATIONS` not being included in the Gemini session config when plugins were enabled. The `_buildGeminiConfig()` method now correctly merges tool declarations with Google Search tool.

### Files changed

```
backend/src/
├── tools.js                    — list_canvases, get_canvas_detail, edit_canvas declarations + handlers
├── canvas/canvas-service.js    — Edit flow, title extraction from HTML <title>
└── canvas/canvas-store.js      — Version history, updateCanvas() method
```

---

## v3.0.0 — Apsara Interpreter (Code Execution Engine) (Feb 10, 2026)

### What was done

- **Interpreter subsystem**: A sandboxed code execution environment on the backend:
  - `interpreter-service.js` — Receives code execution requests from the `run_code` tool:
    - Uses Gemini (Interactions API) to write clean Python/JavaScript code from the user's natural language request
    - Executes code in a subprocess sandbox
    - Captures stdout, stderr, and generated images (matplotlib, PIL)
    - Supports session-based state: variables and imports persist across multiple `run_code` calls in the same session
  - `interpreter-store.js` — In-memory session store:
    - Each session tracks: code, outputs, images, metadata, edit history
    - Sessions persist across the live session lifetime
  - `interpreter-router.js` — Express routes for serving generated images (`/interpreter/images/:id`)

- **`run_code` tool**: Live API tool — when a user asks "calculate this" or "plot a graph", Gemini invokes `run_code`:
  1. Sends `tool_progress` → "Writing code..."
  2. Gemini generates Python code via Interactions API
  3. Sends `tool_progress` → "Executing..."
  4. Executes in sandbox, captures output
  5. Returns code + output + image URLs to Gemini

- **`list_code_sessions` tool**: Apsara can enumerate all code sessions (analogous to `list_canvases`)
- **`get_code_session` tool**: Apsara can retrieve full details of a code session

- **Image serving**: Generated images (matplotlib plots, PIL outputs) are stored in a separate in-memory image store and served via `/images/:id` routes.

### New files

```
backend/src/interpreter/
├── interpreter-service.js     — Code execution engine
├── interpreter-store.js       — Session store
└── interpreter-router.js      — Image serving routes

backend/src/images/
├── image-store.js             — In-memory image store
└── image-router.js            — Express routes for serving images
```

### Tools added

| Tool | Mode | Description |
|------|------|-------------|
| `run_code` | sync/async | Execute Python/JS code, return output + images |
| `list_code_sessions` | sync | List all code execution sessions |
| `get_code_session` | sync | Get full details of a code session |

---

## v3.1.0 — edit_code Tool & Interpreter Refinements (Feb 10, 2026)

### What was done

- **`edit_code` tool**: Apsara can now modify existing code sessions instead of creating new ones:
  - Receives session ID + edit instructions
  - Fetches current code from session store
  - Sends edit prompt + current code to Gemini for modification
  - Replaces session code with updated version
  - Preserves session context (variables, imports)
  - Single matplotlib image per execution (replaces previous)
  - Edit history tracked in session metadata

- **Image URL handling**: Fixed image URL construction to use the correct backend host and port, not hardcoded localhost.

- **Image deduplication** (added then removed): Implemented size-based image dedup to prevent duplicate matplotlib plots, then removed the logic as it caused more issues than it solved — duplicate images are acceptable and the dedup was incorrectly filtering legitimate different images.

### Files changed

```
backend/src/
├── tools.js                           — edit_code tool declaration + handler
├── interpreter/interpreter-service.js — Edit flow, single image mode
├── interpreter/interpreter-store.js   — Edit history tracking
└── images/image-store.js              — Image cleanup (dedup added then removed)
```

---

## v4.0.0 — URL Context Tool & Text Interruption (Feb 11, 2026)

### What was done

#### URL Context Tool
- **`url_context` tool**: Apsara can fetch and analyze web page content during live conversations:
  - Declaration in `TOOL_DECLARATIONS`: accepts `url` (required) and `query` (optional focus/question)
  - Handler flow:
    1. Sends `tool_progress` → "Fetching URL..."
    2. Fetches page content via `fetch()` with User-Agent header
    3. Sends `tool_progress` → "Analyzing content..."
    4. Passes raw HTML to Gemini (Interactions API) for intelligent content extraction
    5. Returns clean text, metadata (title, description, word count) to Gemini
  - Supports both sync (blocking) and async (non-blocking) modes via `toolAsyncModes`
  - Added to `interactions-tools.js` as well for the Interactions API to handle the extraction step

#### Text Interruption
- **`sendText()` now triggers interruption**: Previously, sending a text message during a live session did NOT interrupt Apsara's speech — only voice input could interrupt. Fixed by using a dual-send approach:
  1. First sends `realtimeInput.text` — this triggers Gemini's activity detection and interruption mechanism (same as voice input)
  2. Then sends `clientContent` — this submits the actual text as a conversation turn
  - This mimics how voice naturally triggers interruption via `realtimeInput`

- **Backend `sendText()` change** in `gemini-live-session.js`:
  ```
  Before: session.sendClientContent({ turns: [{ role: 'user', parts: [{ text }] }] })
  After:  session.sendRealtimeInput({ text }) → then → session.sendClientContent({ turns: [...] })
  ```

### Protocol notes

**`realtimeInput.text` vs `sendClientContent`** — these are two different Gemini Live API mechanisms:
- `realtimeInput.text`: Part of the real-time input stream (like audio). Triggers activity detection and can interrupt the model. The text is transient — it's processed in the moment.
- `sendClientContent`: Submits text as a formal conversation turn. Becomes part of the conversation history. Does NOT trigger interruption on its own.

By sending both, we get: interruption (from realtimeInput) + proper conversation history (from clientContent).

### Files changed

```
backend/src/
├── tools.js                           — url_context tool declaration + handler
├── gemini-live-session.js             — sendText: realtimeInput.text + clientContent dual-send
├── ws-handler.js                      — tool_progress forwarding
└── interactions/interactions-tools.js — url_context in Interactions API tools
```

---

## Full Tool Registry (as of v4.0.0)

### Live API Tools (`tools.js`)

| Tool | Description | Modes |
|------|-------------|-------|
| `get_server_info` | Server time, uptime, Node.js version | sync |
| `apsara_canvas` | Generate AI web apps from prompts | sync/async |
| `list_canvases` | List all user's canvas projects | sync |
| `get_canvas_detail` | Get full canvas details | sync |
| `edit_canvas` | Modify existing canvas | sync/async |
| `run_code` | Execute Python/JS code | sync/async |
| `list_code_sessions` | List all code sessions | sync |
| `get_code_session` | Get code session details | sync |
| `edit_code` | Modify existing code session | sync/async |
| `url_context` | Fetch and analyze web page content | sync/async |

### Interactions API Tools (`interactions-tools.js`)

| Tool | Description |
|------|-------------|
| `get_current_time` | Current date/time |
| `get_weather` | Weather information |
| `list_canvases` | List canvas projects |
| `get_canvas_detail` | Canvas details |
| `edit_canvas` | Modify canvas |
| `run_code` | Execute code |
| `list_code_sessions` | List code sessions |
| `get_code_session` | Code session details |

---

## Backend Architecture (as of v4.0.0)

```
┌─────────────────────────────────────────────────────────────────┐
│  Apsara Dark Backend (Node.js)                                  │
│                                                                 │
│  src/                                                           │
│  ├── server.js              — Express + WS server entry         │
│  ├── config.js              — Global config, voices, models     │
│  ├── ws-handler.js          — Live WS protocol handler          │
│  ├── gemini-live-session.js — Gemini Live API session wrapper    │
│  ├── tools.js               — 10 tool declarations + handlers   │
│  │                                                              │
│  ├── interactions/           — Text/Chat API (non-live)         │
│  │   ├── interactions-service.js   — Gemini chat sessions       │
│  │   ├── interactions-tools.js     — Tool declarations          │
│  │   ├── interactions-router.js    — Express routes             │
│  │   ├── interactions-ws-handler.js — WS streaming              │
│  │   └── interactions-config.js    — Config                     │
│  │                                                              │
│  ├── canvas/                 — AI Web App Generation            │
│  │   ├── canvas-service.js   — Code gen + validation            │
│  │   ├── canvas-store.js     — Project store (in-memory)        │
│  │   └── canvas-router.js    — Serve generated apps             │
│  │                                                              │
│  ├── interpreter/            — Code Execution Engine            │
│  │   ├── interpreter-service.js — Code gen + sandbox exec       │
│  │   ├── interpreter-store.js   — Session store                 │
│  │   └── interpreter-router.js  — Image serving                 │
│  │                                                              │
│  └── images/                 — Generated Image Store            │
│      ├── image-store.js      — In-memory image storage          │
│      └── image-router.js     — Image serving routes             │
│                                                                 │
│  .env                        — API key + port config            │
│  package.json                — Dependencies                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Version Summary

| Version | Date | Key Feature |
|---------|------|-------------|
| v1.0.0 | Feb 9 | Gemini Live API relay server — full feature support |
| v1.1.0 | Feb 10 | Model cleanup, voice/modality logic, error handling |
| v1.2.0 | Feb 10 | TEXT modality removed, config logging, thought forwarding |
| v1.3.0 | Feb 10 | Disconnect reason logging, close code logging |
| v1.4.0 | Feb 10 | Per-tool async/sync function calling (toolAsyncModes) |
| v1.5.0 | Feb 9 | Tool system planning (utility plugin box architecture) |
| v2.0.0 | Feb 10 | Interactions API — text/chat alongside Live API |
| v2.1.0 | Feb 10 | Canvas plugin — AI web app generation + serving |
| v2.2.0 | Feb 10 | Canvas detail, edit & list tools |
| v3.0.0 | Feb 10 | Apsara Interpreter — sandboxed code execution |
| v3.1.0 | Feb 10 | edit_code tool, interpreter refinements |
| v4.0.0 | Feb 11 | URL Context tool, text interruption |
