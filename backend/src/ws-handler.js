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
import { executeTool, executeCanvasTool, executeCanvasEditTool, executeInterpreterTool, executeCodeEditTool, executeUrlContextTool, isLongRunningTool, TOOL_DECLARATIONS, getToolNames } from './tools.js';

export function handleWebSocket(ws, apiKey) {
  let geminiSession = null;
  let heartbeatInterval = null;
  let isReconnecting = false; // Prevents double-reconnect (GoAway + onDisconnected racing)

  console.log('[WS] Client connected');

  // Send helper — guard against sending on non-OPEN connections
  function send(msg) {
    if (ws.readyState === ws.OPEN) {
      try {
        ws.send(JSON.stringify(msg));
      } catch (err) {
        console.error('[WS] Send error:', err.message);
      }
    }
  }

  // Create Gemini Live callbacks that forward to the WebSocket client
  function createCallbacks() {
    return {
      onConnected: () => {
        console.log('[WS] Gemini session connected (isReconnecting:', isReconnecting, ')');
        send({ type: 'connected' });
      },
      onDisconnected: ({ reason }) => {
        console.log('[WS] Gemini session disconnected, reason:', reason, '(isReconnecting:', isReconnecting, ')');
        // During GoAway reconnect, don't tell the client about the intermediate disconnect.
        // The client should only see: go_away → (pause) → connected. The disconnect/reconnect
        // is an internal detail that the client doesn't need to know about.
        if (isReconnecting) {
          console.log('[WS] Suppressing disconnected message — GoAway reconnect in progress');
          return;
        }
        send({ type: 'disconnected', reason });
        // Try auto-reconnect with session resumption (only if enabled and not already reconnecting)
        if (geminiSession && geminiSession.resumptionHandle && geminiSession.config.sessionResumption) {
          console.log('[WS] Unexpected disconnect — auto-reconnecting with session resumption...');
          isReconnecting = true;
          setTimeout(() => {
            geminiSession?.reconnect().then(success => {
              isReconnecting = false;
              if (!success) {
                console.error('[WS] Auto-reconnect failed');
                send({ type: 'error', message: 'Auto-reconnect failed' });
              } else {
                console.log('[WS] Auto-reconnect succeeded');
              }
            }).catch((err) => {
              isReconnecting = false;
              console.error('[WS] Auto-reconnect error:', err.message);
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
      onThought: ({ text }) => {
        console.log(`[WS] 🧠 Thought received (${text ? text.length : 0} chars):`, text ? text.substring(0, 200) : '(empty)');
        send({ type: 'thought', text });
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
      onToolCall: async ({ functionCalls }) => {
        // Notify client about the tool call
        send({ type: 'tool_call', functionCalls });

        // Per-tool async/sync modes from session config
        const toolAsyncModes = geminiSession?.config?.toolAsyncModes || {};

        // Auto-execute registered tools and send responses back to Gemini
        if (functionCalls && functionCalls.length > 0 && geminiSession) {
          // Partition function calls into async and sync groups
          const asyncCalls = functionCalls.filter(fc => toolAsyncModes[fc.name] === true);
          const syncCalls = functionCalls.filter(fc => toolAsyncModes[fc.name] !== true);

          console.log(`[WS] Tool calls: ${asyncCalls.length} async, ${syncCalls.length} sync`);

          // ── Handle SYNC tools: execute sequentially, responses without scheduling ──
          if (syncCalls.length > 0) {
            // Separate long-running tools (like canvas) from instant tools
            const instantSync = syncCalls.filter(fc => !isLongRunningTool(fc.name));
            const longRunningSync = syncCalls.filter(fc => isLongRunningTool(fc.name));

            // Execute instant sync tools immediately
            if (instantSync.length > 0) {
              console.log(`[WS] Executing ${instantSync.length} instant SYNC tool(s)...`);
              const syncResponses = instantSync.map(fc => {
                console.log(`[WS] [SYNC] Executing tool: ${fc.name}`, JSON.stringify(fc.args || {}));
                const result = executeTool(fc.name, fc.args || {});
                console.log(`[WS] [SYNC] Tool result (${fc.name}):`, JSON.stringify(result));
                return {
                  id: fc.id,
                  name: fc.name,
                  response: result,
                };
              });
              geminiSession.sendToolResponse(syncResponses);
              send({ type: 'tool_results', results: syncResponses, mode: 'sync' });
              console.log(`[WS] [SYNC] ${syncResponses.length} tool response(s) sent`);
            }

            // Execute long-running sync tools (like canvas create/edit, interpreter, url_context) asynchronously but still respond as sync
            for (const fc of longRunningSync) {
              console.log(`[WS] [SYNC-LONG] Executing long-running tool: ${fc.name}`, JSON.stringify(fc.args || {}));
              const progressType = (fc.name === 'run_code' || fc.name === 'edit_code') ? 'interpreter_progress'
                : fc.name === 'url_context' ? 'url_context_progress'
                  : 'canvas_progress';
              send({ type: progressType, tool_call_id: fc.id, status: 'running', message: `Processing...` });

              try {
                const progressCb = (status, message) => {
                  send({ type: progressType, tool_call_id: fc.id, status, message });
                };
                let result;
                if (fc.name === 'run_code') {
                  const iConfig = geminiSession?.config?.interactionConfig || {};
                  result = await executeInterpreterTool(fc.args || {}, progressCb, iConfig);
                  // Send image data to client for inline display
                  if (result.success && result.images && result.images.length > 0) {
                    send({ type: 'interpreter_images', sessionId: result.session_id, images: result.images });
                  }
                } else if (fc.name === 'edit_code') {
                  const iConfig = geminiSession?.config?.interactionConfig || {};
                  result = await executeCodeEditTool(fc.args || {}, progressCb, iConfig);
                  if (result.success && result.images && result.images.length > 0) {
                    send({ type: 'interpreter_images', sessionId: result.session_id, images: result.images });
                  }
                } else if (fc.name === 'url_context') {
                  const iConfig = geminiSession?.config?.interactionConfig || {};
                  result = await executeUrlContextTool(fc.args || {}, progressCb, iConfig);
                } else if (fc.name === 'edit_canvas') {
                  const iConfig = geminiSession?.config?.interactionConfig || {};
                  result = await executeCanvasEditTool(fc.args || {}, progressCb, iConfig);
                } else {
                  const iConfig = geminiSession?.config?.interactionConfig || {};
                  result = await executeCanvasTool(fc.args || {}, progressCb, iConfig);
                }
                console.log(`[WS] [SYNC-LONG] Tool result (${fc.name}):`, JSON.stringify(result));
                const response = { id: fc.id, name: fc.name, response: result };
                geminiSession.sendToolResponse([response]);
                send({ type: 'tool_results', results: [response], mode: 'sync' });
              } catch (err) {
                console.error(`[WS] [SYNC-LONG] Tool error (${fc.name}):`, err.message);
                const errResponse = { id: fc.id, name: fc.name, response: { success: false, error: err.message } };
                geminiSession.sendToolResponse([errResponse]);
                send({ type: 'tool_results', results: [errResponse], mode: 'sync' });
              }
            }
          }

          // ── Handle ASYNC tools: fire concurrently, responses with scheduling=INTERRUPT ──
          if (asyncCalls.length > 0) {
            console.log(`[WS] Executing ${asyncCalls.length} tool(s) ASYNC...`);
            Promise.all(
              asyncCalls.map(async (fc) => {
                console.log(`[WS] [ASYNC] Executing tool: ${fc.name}`, JSON.stringify(fc.args || {}));

                let result;
                if (isLongRunningTool(fc.name)) {
                  // Long-running async tool (e.g., canvas create/edit, interpreter, url_context)
                  const progressType = (fc.name === 'run_code' || fc.name === 'edit_code') ? 'interpreter_progress'
                    : fc.name === 'url_context' ? 'url_context_progress'
                      : 'canvas_progress';
                  send({ type: progressType, tool_call_id: fc.id, status: 'running', message: 'Processing...' });
                  const progressCb = (status, message) => {
                    send({ type: progressType, tool_call_id: fc.id, status, message });
                  };
                  if (fc.name === 'run_code') {
                    const iConfig = geminiSession?.config?.interactionConfig || {};
                    result = await executeInterpreterTool(fc.args || {}, progressCb, iConfig);
                    if (result.success && result.images && result.images.length > 0) {
                      send({ type: 'interpreter_images', sessionId: result.session_id, images: result.images });
                    }
                  } else if (fc.name === 'edit_code') {
                    const iConfig = geminiSession?.config?.interactionConfig || {};
                    result = await executeCodeEditTool(fc.args || {}, progressCb, iConfig);
                    if (result.success && result.images && result.images.length > 0) {
                      send({ type: 'interpreter_images', sessionId: result.session_id, images: result.images });
                    }
                  } else if (fc.name === 'url_context') {
                    const iConfig = geminiSession?.config?.interactionConfig || {};
                    result = await executeUrlContextTool(fc.args || {}, progressCb, iConfig);
                  } else if (fc.name === 'edit_canvas') {
                    const iConfig = geminiSession?.config?.interactionConfig || {};
                    result = await executeCanvasEditTool(fc.args || {}, progressCb, iConfig);
                  } else {
                    const iConfig = geminiSession?.config?.interactionConfig || {};
                    result = await executeCanvasTool(fc.args || {}, progressCb, iConfig);
                  }
                } else {
                  result = executeTool(fc.name, fc.args || {});
                }

                console.log(`[WS] [ASYNC] Tool result (${fc.name}):`, JSON.stringify(result));
                return {
                  id: fc.id,
                  name: fc.name,
                  response: {
                    ...result,
                    scheduling: 'INTERRUPT', // Interrupt model and use response immediately
                  },
                };
              })
            ).then((asyncResponses) => {
              geminiSession?.sendToolResponse(asyncResponses);
              send({ type: 'tool_results', results: asyncResponses, mode: 'async' });
              console.log(`[WS] [ASYNC] ${asyncResponses.length} tool response(s) sent with scheduling=INTERRUPT`);
            });
          }
        }
      },
      onGoAway: ({ timeLeft }) => {
        console.log('[WS] GoAway received, timeLeft:', timeLeft, '(isReconnecting:', isReconnecting, ')');
        send({ type: 'go_away', timeLeft });
        // Auto-reconnect before disconnect — set flag to suppress intermediate disconnected messages
        if (geminiSession && !isReconnecting) {
          console.log('[WS] GoAway: scheduling reconnect in 2s...');
          isReconnecting = true;
          setTimeout(() => {
            if (!geminiSession) {
              console.log('[WS] GoAway reconnect cancelled — session was destroyed');
              isReconnecting = false;
              return;
            }
            console.log('[WS] GoAway: executing reconnect now...');
            geminiSession.reconnect().then(success => {
              isReconnecting = false;
              if (success) {
                console.log('[WS] GoAway reconnect succeeded');
              } else {
                console.error('[WS] GoAway reconnect failed');
                send({ type: 'error', message: 'GoAway reconnect failed' });
              }
            }).catch((err) => {
              isReconnecting = false;
              console.error('[WS] GoAway reconnect error:', err.message);
              send({ type: 'error', message: 'GoAway reconnect error: ' + err.message });
            });
          }, 2000);
        } else {
          console.log('[WS] GoAway: skipping reconnect (already reconnecting or no session)');
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

        // Force AUDIO modality — TEXT not supported by native audio model
        if (sessionConfig.responseModalities) {
          sessionConfig.responseModalities = ['AUDIO'];
        }

        // Extract per-tool async modes from client config
        const toolAsyncModes = sessionConfig.toolAsyncModes || {};
        if (Object.keys(toolAsyncModes).length > 0) {
          console.log('[WS] Per-tool async modes:', JSON.stringify(toolAsyncModes));
        }
        // Store in config for use during tool execution
        sessionConfig.toolAsyncModes = toolAsyncModes;
        // Extract interaction config for Canvas/Interpreter tools
        const interactionConfig = sessionConfig.interactionConfig || {};
        sessionConfig.interactionConfig = interactionConfig;
        // Clean up old global flag if present
        delete sessionConfig.asyncFunctionCalls;

        // Google Search — only enabled when explicitly requested by client
        const googleSearchEnabled = sessionConfig.tools?.googleSearch === true;
        console.log('[WS] Google Search:', googleSearchEnabled ? 'ENABLED' : 'DISABLED');

        // Filter function declarations based on client's enabled tools list
        if (sessionConfig.enabledTools && Array.isArray(sessionConfig.enabledTools)) {
          const enabledSet = new Set(sessionConfig.enabledTools);
          sessionConfig.functionDeclarations = TOOL_DECLARATIONS.filter(t => enabledSet.has(t.name));
          // Enable function calling if any tools are enabled
          if (sessionConfig.functionDeclarations.length > 0) {
            sessionConfig.tools = { ...sessionConfig.tools, functionCalling: true };
          }
          console.log('[WS] Enabled tools:', sessionConfig.enabledTools, '→ declarations:', sessionConfig.functionDeclarations.map(t => t.name));
          delete sessionConfig.enabledTools; // Clean up — not a Gemini config field
        }

        // Log the config received from client
        console.log('[WS] Client connect config:', JSON.stringify(sessionConfig, null, 2));

        geminiSession = new GeminiLiveSession(apiKey, sessionConfig, createCallbacks());
        const success = await geminiSession.connect();
        if (!success) {
          send({ type: 'error', message: 'Failed to connect to Gemini Live API' });
        }
        break;
      }

      case 'disconnect': {
        console.log('[WS] Client requested disconnect');
        if (geminiSession) {
          isReconnecting = true; // Prevent onDisconnected from auto-reconnecting
          await geminiSession.disconnect();
          isReconnecting = false;
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

      case 'get_tools': {
        send({
          type: 'available_tools',
          tools: TOOL_DECLARATIONS.map(t => ({
            name: t.name,
            description: t.description,
          })),
        });
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
    console.log('[WS] Client WebSocket closed (isReconnecting:', isReconnecting, ')');
    clearInterval(heartbeatInterval);
    isReconnecting = true; // Prevent onDisconnected from auto-reconnecting after client leaves
    if (geminiSession) {
      console.log('[WS] Cleaning up Gemini session...');
      await geminiSession.disconnect();
      geminiSession = null;
    }
    isReconnecting = false;
  });

  ws.on('error', (error) => {
    console.error('[WS] WebSocket error:', error.message);
  });
}
