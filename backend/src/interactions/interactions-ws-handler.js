/**
 * Apsara Dark — Interactions API Backend
 * 
 * WebSocket handler for Interactions API.
 * Provides a persistent WebSocket connection for the Android app
 * to do real-time text chat using the Interactions API with 
 * stateful conversation management.
 * 
 * Protocol (JSON messages over WebSocket):
 * 
 * Client → Server:
 *   { type: "chat", input: "...", config?: { ... } }                     — Send a message
 *   { type: "chat_stream", input: "...", config?: { ... } }              — Send a message (streaming response)
 *   { type: "continue", input: "...", config?: { ... } }                 — Continue from last interaction
 *   { type: "continue_stream", input: "...", config?: { ... } }          — Continue from last interaction (streaming)
 *   { type: "set_config", config: { ... } }                              — Update default config for this session
 *   { type: "get_history" }                                              — Get conversation interaction IDs
 *   { type: "reset" }                                                    — Clear conversation history
 *   { type: "ping" }                                                     — Keepalive
 * 
 * Server → Client:
 *   { type: "response", ... }                                            — Full interaction response
 *   { type: "stream_start" }                                             — Streaming started
 *   { type: "stream_delta", delta: { ... } }                             — Streaming chunk
 *   { type: "stream_end", interaction?: { ... } }                        — Streaming complete
 *   { type: "config_updated", config: { ... } }                          — Config was updated
 *   { type: "history", interactions: [...] }                              — Conversation history IDs
 *   { type: "reset_done" }                                               — History cleared
 *   { type: "error", message: "..." }                                    — Error
 *   { type: "pong" }                                                     — Keepalive response
 */

import { InteractionsService } from './interactions-service.js';
import {
  DEFAULT_MODEL,
  DEFAULT_GENERATION_CONFIG,
  DEFAULT_SYSTEM_INSTRUCTION,
} from './interactions-config.js';

export function handleInteractionsWebSocket(ws, apiKey) {
  const service = new InteractionsService(apiKey);
  let heartbeatInterval = null;

  // Per-session config (overridable by client)
  let sessionConfig = {
    model: DEFAULT_MODEL,
    system_instruction: DEFAULT_SYSTEM_INSTRUCTION,
    generation_config: { ...DEFAULT_GENERATION_CONFIG },
    tools: { functionCalling: true },
  };

  // Stateful conversation tracking
  let lastInteractionId = null;
  let interactionHistory = []; // List of interaction IDs for this session

  console.log('[InteractionsWS] Client connected');

  function send(msg) {
    if (ws.readyState === ws.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  ws.on('message', async (data) => {
    let msg;
    try {
      msg = JSON.parse(data.toString());
    } catch (e) {
      send({ type: 'error', message: 'Invalid JSON' });
      return;
    }

    switch (msg.type) {
      // ── Send a new message (non-streaming) ────────────────────────────
      case 'chat': {
        try {
          const config = { ...sessionConfig, ...msg.config };
          const result = await service.createInteraction({
            input: msg.input,
            model: config.model,
            system_instruction: config.system_instruction,
            generation_config: config.generation_config,
            tools: config.tools,
            response_format: msg.response_format,
            previous_interaction_id: msg.continuePrevious ? lastInteractionId : undefined,
          });

          lastInteractionId = result.id;
          interactionHistory.push(result.id);

          send({ type: 'response', ...result });
        } catch (error) {
          console.error('[InteractionsWS] Chat error:', error.message);
          send({ type: 'error', message: error.message });
        }
        break;
      }

      // ── Send a new message (streaming) ────────────────────────────────
      case 'chat_stream': {
        try {
          const config = { ...sessionConfig, ...msg.config };
          send({ type: 'stream_start' });

          const stream = service.createStreamingInteraction({
            input: msg.input,
            model: config.model,
            system_instruction: config.system_instruction,
            generation_config: config.generation_config,
            tools: config.tools,
            response_format: msg.response_format,
            previous_interaction_id: msg.continuePrevious ? lastInteractionId : undefined,
          });

          let finalInteraction = null;

          for await (const chunk of stream) {
            if (chunk.event_type === 'interaction.complete') {
              finalInteraction = chunk.interaction;
            }
            send({ type: 'stream_delta', ...chunk });
          }

          if (finalInteraction) {
            lastInteractionId = finalInteraction.id;
            interactionHistory.push(finalInteraction.id);
          }

          send({ type: 'stream_end', interaction: finalInteraction });
        } catch (error) {
          console.error('[InteractionsWS] Stream error:', error.message);
          send({ type: 'error', message: error.message });
        }
        break;
      }

      // ── Continue from last interaction (non-streaming) ────────────────
      case 'continue': {
        if (!lastInteractionId) {
          send({ type: 'error', message: 'No previous interaction to continue from. Send a "chat" message first.' });
          return;
        }
        try {
          const config = { ...sessionConfig, ...msg.config };
          const result = await service.createInteraction({
            input: msg.input,
            model: config.model,
            system_instruction: config.system_instruction,
            generation_config: config.generation_config,
            tools: config.tools,
            response_format: msg.response_format,
            previous_interaction_id: lastInteractionId,
          });

          lastInteractionId = result.id;
          interactionHistory.push(result.id);

          send({ type: 'response', ...result });
        } catch (error) {
          console.error('[InteractionsWS] Continue error:', error.message);
          send({ type: 'error', message: error.message });
        }
        break;
      }

      // ── Continue from last interaction (streaming) ────────────────────
      case 'continue_stream': {
        if (!lastInteractionId) {
          send({ type: 'error', message: 'No previous interaction to continue from.' });
          return;
        }
        try {
          const config = { ...sessionConfig, ...msg.config };
          send({ type: 'stream_start' });

          const stream = service.createStreamingInteraction({
            input: msg.input,
            model: config.model,
            system_instruction: config.system_instruction,
            generation_config: config.generation_config,
            tools: config.tools,
            response_format: msg.response_format,
            previous_interaction_id: lastInteractionId,
          });

          let finalInteraction = null;

          for await (const chunk of stream) {
            if (chunk.event_type === 'interaction.complete') {
              finalInteraction = chunk.interaction;
            }
            send({ type: 'stream_delta', ...chunk });
          }

          if (finalInteraction) {
            lastInteractionId = finalInteraction.id;
            interactionHistory.push(finalInteraction.id);
          }

          send({ type: 'stream_end', interaction: finalInteraction });
        } catch (error) {
          console.error('[InteractionsWS] Continue stream error:', error.message);
          send({ type: 'error', message: error.message });
        }
        break;
      }

      // ── Update session config ─────────────────────────────────────────
      case 'set_config': {
        if (msg.config) {
          sessionConfig = {
            ...sessionConfig,
            ...msg.config,
            generation_config: {
              ...sessionConfig.generation_config,
              ...(msg.config.generation_config || {}),
            },
            tools: {
              ...sessionConfig.tools,
              ...(msg.config.tools || {}),
            },
          };
        }
        send({ type: 'config_updated', config: sessionConfig });
        break;
      }

      // ── Get conversation history ──────────────────────────────────────
      case 'get_history': {
        send({
          type: 'history',
          interactions: interactionHistory,
          lastInteractionId,
        });
        break;
      }

      // ── Reset conversation ────────────────────────────────────────────
      case 'reset': {
        lastInteractionId = null;
        interactionHistory = [];
        send({ type: 'reset_done' });
        break;
      }

      // ── Keepalive ─────────────────────────────────────────────────────
      case 'ping': {
        send({ type: 'pong' });
        break;
      }

      default: {
        send({ type: 'error', message: `Unknown message type: ${msg.type}` });
      }
    }
  });

  // Heartbeat — NO server-side ws.ping().
  // Caddy doesn't transparently proxy WebSocket ping/pong frames.
  // Detect stale connections by tracking message activity.
  let lastActivity = Date.now();
  ws.on('message', () => { lastActivity = Date.now(); });
  heartbeatInterval = setInterval(() => {
    if (Date.now() - lastActivity > 90000) {
      console.log('[InteractionsWS] No activity for 90s — terminating');
      ws.terminate();
    }
  }, 30000);

  // Cleanup
  ws.on('close', () => {
    console.log('[InteractionsWS] Client disconnected');
    clearInterval(heartbeatInterval);
  });

  ws.on('error', (error) => {
    console.error('[InteractionsWS] WebSocket error:', error.message);
  });
}
