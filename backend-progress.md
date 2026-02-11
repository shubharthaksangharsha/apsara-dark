# Apsara Dark — Backend Progress

> Node.js relay server for Apsara Dark — bridges Android app ↔ Gemini Live API + Interactions API + tool subsystems.
> Versions follow `v0.0.x` pre-release convention (v0.0.0 → v0.0.9), aligned with the main app progress.

---

## v0.0.0 — Gemini Live API Relay Server (Feb 9, 2026)

### Summary

Server-to-server architecture — Android app connects to our backend via WebSocket, backend connects to Gemini Live API. API key stays safely on the server.

### What was done

- **Tech stack**: Node.js 20+, Express, `ws` (WebSocket), `@google/genai` SDK.
- **WebSocket server** on `/live` path — full bidirectional protocol.
- **Express HTTP** for `/health` and `/config` endpoints.
- **`gemini-live-session.js`** — Wraps `@google/genai` Live API with full feature support:
  - Audio/video/text streaming
  - Voice selection (8 voices), model selection, system instruction, temperature
  - Context window compression, session resumption
  - Affective dialog, proactive audio, thinking (configurable budget)
  - Input/output transcription, Google Search grounding
  - Function calling (sync + async), tool response handling
  - GoAway handling with auto-reconnect, interruption forwarding
  - Usage metadata forwarding
- **`ws-handler.js`** — Bridges app ↔ Gemini Live, handles all message types, auto-reconnect on GoAway.
- **`config.js`** — Default session config, voices, models, audio format constants.
- **Deployment**: Caddy reverse proxy + PM2/systemd on Oracle Cloud.

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
│  ├── ws-handler.js      — Protocol  │
│  ├── gemini-live-session.js — API   │
│  └── config.js          — Defaults  │
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

### WebSocket Protocol

#### Client → Server
| Type | Fields | Description |
|------|--------|-------------|
| `connect` | `config?` | Start Gemini Live session |
| `disconnect` | — | End session |
| `audio` | `data` (base64) | Stream mic audio |
| `video` | `data` (base64), `mimeType?` | Stream camera frame |
| `text` | `text` | Send text message |
| `context` | `turns`, `turnComplete` | Send conversation context |
| `tool_response` | `responses` | Respond to function calls |
| `audio_stream_end` | — | Signal mic pause |
| `update_config` | `config` | Update session config |
| `reconnect` | `config?` | Reconnect with new config |
| `get_state` | — | Get session state |
| `get_config` | — | Get available options |
| `ping` | — | Keepalive |

#### Server → Client
| Type | Fields | Description |
|------|--------|-------------|
| `connected` | — | Session active |
| `disconnected` | `reason?` | Session ended |
| `audio` | `data` (base64), `mimeType` | Gemini audio |
| `text` | `text` | Gemini text |
| `thought` | `text` | Model reasoning |
| `input_transcription` | `text` | User speech transcription |
| `output_transcription` | `text` | Gemini speech transcription |
| `interrupted` | — | User interrupted Gemini |
| `turn_complete` | — | Gemini finished speaking |
| `generation_complete` | — | Full generation done |
| `tool_call` | `functionCalls` | Gemini invoking tools |
| `tool_results` | `results`, `mode` | Tool execution results |
| `tool_progress` | `id`, `name`, `progress` | Tool progress update |
| `go_away` | `timeLeft` | Connection ending soon |
| `session_resumption_update` | `resumable`, `hasHandle` | Handle updated |
| `usage` | token counts | Usage metadata |
| `error` | `message`, `errorType?` | Error |
| `pong` | — | Keepalive response |

### Audio Format

| Direction | Format | Sample Rate | Bit Depth | Channels |
|-----------|--------|-------------|-----------|----------|
| Input (mic → Gemini) | Raw PCM, little-endian | 16 kHz | 16-bit | Mono |
| Output (Gemini → app) | Raw PCM, little-endian | 24 kHz | 16-bit | Mono |

---

## v0.0.1 — Model Cleanup & Config Refinements (Feb 10, 2026)

### Summary

Hardened the backend — removed unsupported models/modalities, added comprehensive logging, fixed disconnect handling, and improved error messages.

### What was done

- **Model support cleanup**: Only `gemini-2.5-flash-native-audio-preview-12-2025` supported for live sessions.
- **TEXT modality removed**: Native audio model doesn't support TEXT-only. Forced `responseModalities: ['AUDIO']` on every connect.
- **Voice/Modality logic**: `speechConfig` only sent when response modality includes AUDIO.
- **Config logging**: Full client config JSON logged on connect + Gemini config + tools config for debugging.
- **Thought forwarding**: `onThought` callback forwards `{ type: "thought", text }` to client.
- **Disconnect logging**: `onclose` distinguishes client vs. server disconnect, logs close code.
- **Audio/video discard**: Silently discards audio/video when not connected (no log spam).

---

## v0.0.2 — Per-Tool Async/Sync Function Calling (Feb 10, 2026)

### Summary

Replaced global async flag with per-tool `toolAsyncModes` — each tool independently configurable as blocking or non-blocking.

### What was done

- **Per-tool async/sync control**: `toolAsyncModes` map (`{ toolName: boolean }`) in session config.
  - `true` → `behavior: 'NON_BLOCKING'` (Gemini continues speaking while tool executes).
  - `false` / default → blocking (Gemini waits for tool response).
- **`config.js`**: `asyncFunctionCalls: false` → `toolAsyncModes: {}`.
- **`gemini-live-session.js`**: `_buildGeminiConfig()` reads `toolAsyncModes`, sets `NON_BLOCKING` per tool.
- **`ws-handler.js`**: `onToolCall` partitions calls into sync (sequential) and async (`Promise.all`) groups:
  - Sync: plain responses.
  - Async: responses with `scheduling: 'INTERRUPT'` so Gemini integrates results mid-turn.
  - Forwards `tool_call` and `tool_results` to client for UI status cards.

### Protocol Additions

| Type | Fields | Description |
|------|--------|-------------|
| `tool_call` | `functionCalls: [{name, id, args}]` | Tools being invoked (for UI) |
| `tool_results` | `results: [{id, name, response}]`, `mode` | Tool results (for UI) |

---

## v0.0.3 — Interactions API (Feb 10, 2026)

### Summary

Separate Gemini text/chat subsystem that runs alongside the Live API — handles complex tool operations needing multi-turn reasoning, longer outputs, and better code generation.

### What was done

- **`interactions-service.js`** — Manages Gemini `GoogleGenAI` chat sessions with tool calling.
- **`interactions-tools.js`** — Tool declarations (get_current_time, get_weather, canvas tools, code tools).
- **`interactions-router.js`** — Express routes for `/interactions/*`.
- **`interactions-ws-handler.js`** — WebSocket handler for streaming interaction responses.
- **`interactions-config.js`** — Model, temperature, defaults.

### Why a Separate API?

| Live API Limitation | Interactions API Solution |
|---------------------|--------------------------|
| Single-shot tool responses | Multi-turn tool chains |
| Native audio model has shorter outputs | Non-live models produce full web apps |
| Tool operations block audio stream | Parallel execution without blocking audio |
| Limited code generation quality | Better code from `gemini-2.5-flash` |

### New Files

```
backend/src/interactions/
├── index.js
├── interactions-service.js
├── interactions-tools.js
├── interactions-router.js
├── interactions-ws-handler.js
└── interactions-config.js
```

---

## v0.0.4 — Apsara Canvas (AI Web App Generation) (Feb 10, 2026)

### Summary

Full AI web app generation pipeline — Apsara creates, serves, lists, views, and edits web applications during live conversations.

### What was done

#### Canvas Service
- **`canvas-service.js`** — Generates HTML/CSS/JS apps from natural language prompts:
  - Multi-step: prompt → code → validation → auto-fix (up to 3 retries) → serve.
  - Supports React (CDN) and vanilla HTML/CSS/JS.
  - Auto-injects responsive viewport for mobile-first rendering.
  - Edit flow: fetches current code → sends edit prompt + code to Gemini → updates in-place.
  - Title extraction from HTML `<title>` tag on edit.

- **`canvas-store.js`** — In-memory store with version history, metadata tracking.
- **`canvas-router.js`** — `GET /canvas/:id` serves the app, `GET /canvas/:id/raw` returns JSON.

#### Canvas Tools (Live API)
| Tool | Description |
|------|-------------|
| `apsara_canvas` | Generate web app from prompt |
| `list_canvases` | List all user's canvases |
| `get_canvas_detail` | Get full canvas details |
| `edit_canvas` | Modify existing canvas |

#### WebSocket Stability Fixes
- Replaced protocol-level pings with application-level JSON `ping`/`pong` (fixes `Control frames must be final`).
- `noServer` WebSocket routing — manual HTTP upgrade prevents Express middleware interfering with WS frames.
- Caddy `flush_interval -1` for proper WebSocket streaming.

### New Files

```
backend/src/canvas/
├── index.js
├── canvas-service.js
├── canvas-store.js
└── canvas-router.js
```

---

## v0.0.5 — WebSocket Stability & Plugin Registration Fix (Feb 10, 2026)

### Summary

Resolved all WebSocket frame corruption issues and fixed plugin tool registration in Gemini session config.

### What was done

- **Root cause fix for `Control frames must be final`**: The `ws` library was receiving fragmented control frames because Express middleware was interfering with the raw WebSocket upgrade. Switched to `noServer` mode with manual `server.on('upgrade')` handling.
- **Application-level ping/pong**: All keepalive now uses JSON `{ type: "ping" }` / `{ type: "pong" }` instead of WebSocket protocol pings.
- **Caddy proxy streaming**: `flush_interval -1` ensures Caddy doesn't buffer WebSocket frames.
- **Plugin registration fix**: `TOOL_DECLARATIONS` were not being included in Gemini session config when function calling was enabled. Fixed `_buildGeminiConfig()` to correctly merge `functionDeclarations` from the config.

---

## v0.0.6 — Apsara Interpreter (Code Execution Engine) (Feb 10, 2026)

### Summary

Sandboxed code execution environment — Apsara writes Python/JavaScript from natural language, executes it, and returns text output + generated images.

### What was done

#### Interpreter Subsystem
- **`interpreter-service.js`** — Execution pipeline:
  - Gemini (Interactions API) writes clean code from user request.
  - Executes in subprocess sandbox.
  - Captures stdout, stderr, and generated images (matplotlib, PIL).
  - Session-based state: variables and imports persist across runs in the same session.
  - Edit flow: modify existing session code, preserving context.

- **`interpreter-store.js`** — Session store (code, outputs, metadata, edit history).
- **`interpreter-router.js`** — Routes for serving generated images.

#### Interpreter Tools (Live API)
| Tool | Description |
|------|-------------|
| `run_code` | Execute Python/JS, return output + images |
| `list_code_sessions` | List all code sessions |
| `get_code_session` | Get full session details |
| `edit_code` | Modify existing code session |

### New Files

```
backend/src/interpreter/
├── interpreter-service.js
├── interpreter-store.js
└── interpreter-router.js
```

---

## v0.0.7 — edit_code & Session Management (Feb 10, 2026)

### Summary

Enhanced the interpreter with code editing capabilities, improved image handling, and refined session management across Canvas and Interpreter.

### What was done

- **`edit_code` tool**: Modify existing code sessions:
  - Receives session ID + edit instructions.
  - Fetches current code, sends edit prompt to Gemini.
  - Replaces session code with updated version.
  - Preserves session context (variables, imports).
  - Single matplotlib image per execution (replaces previous).
  - Edit history tracked in session metadata.

- **Image URL handling**: Fixed URL construction to use correct backend host/port.
- **Canvas edit title**: `edit_canvas` now updates title based on edit instructions and HTML `<title>` extraction.
- **Image deduplication**: Implemented then removed — size-based dedup caused more issues than it solved.

---

## v0.0.8 — URL Context Tool (Feb 11, 2026)

### Summary

New tool enabling Apsara to fetch and analyze web page content during live conversations.

### What was done

- **`url_context` tool declaration**: Accepts `url` (required) and `query` (optional focus/question).
- **Handler flow**:
  1. Sends `tool_progress` → "Fetching URL..."
  2. `fetch()` with User-Agent header to retrieve page content.
  3. Sends `tool_progress` → "Analyzing content..."
  4. Passes raw HTML to Gemini (Interactions API) for intelligent extraction.
  5. Returns clean text, metadata (title, description, word count).
- **Sync/async support** via `toolAsyncModes`.
- **Progress streaming**: `tool_progress` messages keep the client informed of incremental status.
- Added to `interactions-tools.js` for the extraction step.

### Protocol Addition

| Type | Fields | Description |
|------|--------|-------------|
| `tool_progress` | `id`, `name`, `progress` | Incremental tool execution status |

---

## v0.0.9 — Text Interruption & API Refinements (Feb 11, 2026)

### Summary

Text messages now interrupt Apsara's speech via a dual-send approach, completing the real-time interaction model.

### What was done

#### Text Interruption
- **Problem**: `sendClientContent` submits text but does NOT trigger Gemini's activity detection — Apsara keeps speaking.
- **Solution**: `sendText()` now uses dual-send:
  1. `session.sendRealtimeInput({ text })` — triggers activity detection → interruption.
  2. `session.sendClientContent({ turns, turnComplete })` — submits text as conversation turn.

#### API Mechanism Explained

| Method | Purpose | Interrupts? | In History? |
|--------|---------|-------------|-------------|
| `realtimeInput.text` | Real-time input stream (like audio) | ✅ Yes | ❌ Transient |
| `sendClientContent` | Formal conversation turn | ❌ No | ✅ Persisted |

Sending both gives: **interruption** (from realtimeInput) + **proper conversation history** (from clientContent).

#### Code Change

```javascript
// Before (no interruption):
this.session.sendClientContent({ turns: text, turnComplete: true });

// After (with interruption):
try {
  this.session.sendRealtimeInput({ text }); // Step 1: interrupt
} catch (e) {
  console.warn('[GeminiLive] realtimeInput text not supported:', e.message);
}
this.session.sendClientContent({ turns: text, turnComplete: true }); // Step 2: submit turn
```

---

## Version Summary

| Version | Milestone | Date | Key Feature |
|---------|-----------|------|-------------|
| **v0.0.0** | Foundation | Feb 9 | Gemini Live API relay server — full feature support |
| **v0.0.1** | Hardening | Feb 10 | Model cleanup, config logging, disconnect handling |
| **v0.0.2** | Per-Tool Control | Feb 10 | Per-tool async/sync function calling |
| **v0.0.3** | Interactions API | Feb 10 | Text/chat API alongside Live API |
| **v0.0.4** | Canvas | Feb 10 | AI web app generation + serving |
| **v0.0.5** | Stability | Feb 10 | WebSocket fixes, plugin registration |
| **v0.0.6** | Interpreter | Feb 10 | Sandboxed code execution engine |
| **v0.0.7** | Edit & Sessions | Feb 10 | edit_code, session management, image handling |
| **v0.0.8** | URL Context | Feb 11 | Web page fetching and analysis tool |
| **v0.0.9** | Interruption | Feb 11 | Text interrupts speech via dual-send |

---

## Full Tool Registry (as of v0.0.9)

### Live API Tools (`tools.js`)

| Tool | Description | Modes |
|------|-------------|-------|
| `get_server_info` | Server time, uptime, Node.js version | sync |
| `apsara_canvas` | Generate AI web apps from prompts | sync/async |
| `list_canvases` | List all canvas projects | sync |
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

## Backend Architecture (as of v0.0.9)

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
│  .env                        — API key + port config            │
│  package.json                — Dependencies                     │
└─────────────────────────────────────────────────────────────────┘
```
