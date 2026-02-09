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
