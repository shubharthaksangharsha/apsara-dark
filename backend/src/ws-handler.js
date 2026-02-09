/**
 * Apsara Dark — Gemini Live Backend
 * 
 * WebSocket handler: Bridges the Android app ↔ Gemini Live API.
 * 
 * Protocol (JSON messages over WebSocket):
 * 
 * Client → Server:
 *   { type: "connect", config?: { ... } }           — Start/reconnect Gemini Live session
 *   { type: "disconnect" }                           — End Gemini Live session
 *   { type: "audio", data: "<base64>" }              — Stream audio chunk
 *   { type: "video", data: "<base64>", mimeType? }   — Stream video frame
 *   { type: "text", text: "..." }                    — Send text message
 *   { type: "context", turns: [...], turnComplete }  — Send context/history
 *   { type: "tool_response", responses: [...] }      — Send function call responses
 *   { type: "audio_stream_end" }                     — Signal mic pause
 *   { type: "update_config", config: { ... } }       — Update session config (requires reconnect)
 *   { type: "get_state" }                            — Request current session state
 *   { type: "get_config" }                           — Request available config options
 *   { type: "ping" }                                 — Keepalive
 * 
 * Server → Client:
 *   { type: "connected" }                            — Gemini session established
 *   { type: "disconnected", reason? }                — Gemini session ended
 *   { type: "audio", data: "<base64>", mimeType }    — Audio from Gemini
 *   { type: "text", text: "..." }                    — Text from Gemini
 *   { type: "input_transcription", text: "..." }     — Transcription of user's audio
 *   { type: "output_transcription", text: "..." }    — Transcription of Gemini's audio
 *   { type: "interrupted" }                          — Gemini was interrupted
 *   { type: "turn_complete" }                        — Gemini finished speaking
 *   { type: "generation_complete" }                  — Generation fully done
 *   { type: "tool_call", functionCalls: [...] }      — Gemini wants to call functions
 *   { type: "go_away", timeLeft }                    — Connection will end soon
 *   { type: "session_resumption_update", ... }       — Resumption handle updated
 *   { type: "usage", ... }                           — Token usage metadata
 *   { type: "state", ... }                           — Current session state
 *   { type: "config_options", ... }                  — Available config options
 *   { type: "error", message }                       — Error
 *   { type: "pong" }                                 — Keepalive response
 */

import { GeminiLiveSession } from './gemini-live-session.js';
import { AVAILABLE_VOICES, AVAILABLE_MODELS, DEFAULT_SESSION_CONFIG, AUDIO } from './config.js';

export function handleWebSocket(ws, apiKey) {
  let geminiSession = null;
  let heartbeatInterval = null;

  console.log('[WS] Client connected');

  // Send helper
  function send(msg) {
    if (ws.readyState === ws.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  // Create Gemini Live callbacks that forward to the WebSocket client
  function createCallbacks() {
    return {
      onConnected: () => {
        send({ type: 'connected' });
      },
      onDisconnected: ({ reason }) => {
        send({ type: 'disconnected', reason });
        // Try auto-reconnect with session resumption
        if (geminiSession && geminiSession.resumptionHandle) {
          console.log('[WS] Auto-reconnecting with session resumption...');
          setTimeout(() => {
            geminiSession?.reconnect().then(success => {
              if (!success) {
                send({ type: 'error', message: 'Auto-reconnect failed' });
              }
            });
          }, 1000);
        }
      },
      onAudioData: ({ data, mimeType }) => {
        send({ type: 'audio', data, mimeType });
      },
      onTextData: ({ text }) => {
        send({ type: 'text', text });
      },
      onInputTranscription: ({ text }) => {
        send({ type: 'input_transcription', text });
      },
      onOutputTranscription: ({ text }) => {
        send({ type: 'output_transcription', text });
      },
      onInterrupted: () => {
        send({ type: 'interrupted' });
      },
      onTurnComplete: () => {
        send({ type: 'turn_complete' });
      },
      onGenerationComplete: () => {
        send({ type: 'generation_complete' });
      },
      onToolCall: ({ functionCalls }) => {
        send({ type: 'tool_call', functionCalls });
      },
      onGoAway: ({ timeLeft }) => {
        send({ type: 'go_away', timeLeft });
        // Auto-reconnect before disconnect
        if (geminiSession) {
          console.log('[WS] GoAway received, scheduling reconnect...');
          setTimeout(() => {
            geminiSession?.reconnect();
          }, 2000);
        }
      },
      onSessionResumptionUpdate: ({ resumable, hasHandle }) => {
        send({ type: 'session_resumption_update', resumable, hasHandle });
      },
      onUsageMetadata: (metadata) => {
        send({ type: 'usage', ...metadata });
      },
      onError: ({ type, message }) => {
        send({ type: 'error', errorType: type, message });
      },
      onExecutableCode: ({ code }) => {
        send({ type: 'executable_code', code });
      },
      onCodeExecutionResult: ({ output }) => {
        send({ type: 'code_execution_result', output });
      },
    };
  }

  // Handle messages from the Android client
  ws.on('message', async (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      send({ type: 'error', message: 'Invalid JSON' });
      return;
    }

    switch (msg.type) {
      case 'connect': {
        const sessionConfig = msg.config || {};
        geminiSession = new GeminiLiveSession(apiKey, sessionConfig, createCallbacks());
        const success = await geminiSession.connect();
        if (!success) {
          send({ type: 'error', message: 'Failed to connect to Gemini Live API' });
        }
        break;
      }

      case 'disconnect': {
        if (geminiSession) {
          await geminiSession.disconnect();
          geminiSession = null;
        }
        send({ type: 'disconnected', reason: 'client_requested' });
        break;
      }

      case 'audio': {
        // Silently discard if no session — audio chunks are very frequent
        if (!geminiSession) return;
        geminiSession.sendAudio(msg.data);
        break;
      }

      case 'video': {
        // Silently discard if no session
        if (!geminiSession) return;
        geminiSession.sendVideo(msg.data, msg.mimeType || 'image/jpeg');
        break;
      }

      case 'text': {
        if (!geminiSession) {
          send({ type: 'error', message: 'Not connected — send "connect" first' });
          return;
        }
        geminiSession.sendText(msg.text);
        break;
      }

      case 'context': {
        if (!geminiSession) {
          send({ type: 'error', message: 'Not connected — send "connect" first' });
          return;
        }
        geminiSession.sendContext(msg.turns, msg.turnComplete || false);
        break;
      }

      case 'tool_response': {
        if (!geminiSession) {
          send({ type: 'error', message: 'Not connected — send "connect" first' });
          return;
        }
        geminiSession.sendToolResponse(msg.responses);
        break;
      }

      case 'audio_stream_end': {
        if (geminiSession) {
          geminiSession.sendAudioStreamEnd();
        }
        break;
      }

      case 'update_config': {
        if (geminiSession) {
          geminiSession.updateConfig(msg.config || {});
          send({ type: 'state', ...geminiSession.getState(), configUpdated: true });
        } else {
          send({ type: 'error', message: 'No active session' });
        }
        break;
      }

      case 'reconnect': {
        if (geminiSession) {
          const success = await geminiSession.reconnect(msg.config || null);
          if (!success) {
            send({ type: 'error', message: 'Reconnect failed' });
          }
        } else {
          send({ type: 'error', message: 'No session to reconnect' });
        }
        break;
      }

      case 'get_state': {
        if (geminiSession) {
          send({ type: 'state', ...geminiSession.getState() });
        } else {
          send({ type: 'state', connected: false });
        }
        break;
      }

      case 'get_config': {
        send({
          type: 'config_options',
          voices: AVAILABLE_VOICES,
          models: AVAILABLE_MODELS,
          defaults: {
            model: DEFAULT_SESSION_CONFIG.model,
            voice: DEFAULT_SESSION_CONFIG.voice,
            temperature: DEFAULT_SESSION_CONFIG.temperature,
            responseModalities: DEFAULT_SESSION_CONFIG.responseModalities,
            enableAffectiveDialog: DEFAULT_SESSION_CONFIG.enableAffectiveDialog,
            proactiveAudio: DEFAULT_SESSION_CONFIG.proactiveAudio,
            thinkingBudget: DEFAULT_SESSION_CONFIG.thinkingBudget,
            includeThoughts: DEFAULT_SESSION_CONFIG.includeThoughts,
            inputAudioTranscription: DEFAULT_SESSION_CONFIG.inputAudioTranscription,
            outputAudioTranscription: DEFAULT_SESSION_CONFIG.outputAudioTranscription,
            contextWindowCompression: !!DEFAULT_SESSION_CONFIG.contextWindowCompression,
            googleSearch: DEFAULT_SESSION_CONFIG.tools.googleSearch,
            functionCalling: DEFAULT_SESSION_CONFIG.tools.functionCalling,
          },
          audio: AUDIO,
        });
        break;
      }

      case 'ping': {
        send({ type: 'pong' });
        break;
      }

      default: {
        send({ type: 'error', message: `Unknown message type: ${msg.type}` });
      }
    }
  });

  // Heartbeat to keep connection alive
  heartbeatInterval = setInterval(() => {
    if (ws.readyState === ws.OPEN) {
      ws.ping();
    }
  }, 30000);

  // Cleanup on disconnect
  ws.on('close', async () => {
    console.log('[WS] Client disconnected');
    clearInterval(heartbeatInterval);
    if (geminiSession) {
      await geminiSession.disconnect();
      geminiSession = null;
    }
  });

  ws.on('error', (error) => {
    console.error('[WS] WebSocket error:', error.message);
  });
}
