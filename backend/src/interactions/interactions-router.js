/**
 * Apsara Dark — Interactions API Backend
 * 
 * Express router for Interactions API HTTP endpoints.
 * 
 * Endpoints:
 * 
 *   POST /api/interactions              — Create interaction (text, multimodal, tools)
 *   POST /api/interactions/stream       — Create streaming interaction (SSE)
 *   GET  /api/interactions/:id          — Get a previous interaction by ID
 *   POST /api/interactions/:id/continue — Continue a conversation from a previous interaction
 *   GET  /api/interactions/config       — Get available models, tools, and defaults
 */

import { Router } from 'express';
import { InteractionsService } from './interactions-service.js';
import {
  INTERACTIONS_MODELS,
  INTERACTIONS_AGENTS,
  DEFAULT_MODEL,
  DEFAULT_GENERATION_CONFIG,
  DEFAULT_SYSTEM_INSTRUCTION,
  THINKING_LEVELS,
} from './interactions-config.js';
import { FUNCTION_TOOLS, getFunctionNames } from './interactions-tools.js';

export function createInteractionsRouter(apiKey) {
  const router = Router();
  const service = new InteractionsService(apiKey);

  // ─── GET /api/interactions/config ─────────────────────────────────────────
  // Returns available models, tools, and default configuration
  router.get('/config', (req, res) => {
    res.json({
      models: INTERACTIONS_MODELS,
      agents: INTERACTIONS_AGENTS,
      defaults: {
        model: DEFAULT_MODEL,
        generation_config: DEFAULT_GENERATION_CONFIG,
        system_instruction: DEFAULT_SYSTEM_INSTRUCTION,
      },
      thinking_levels: THINKING_LEVELS,
      available_tools: {
        builtin: ['google_search', 'code_execution', 'url_context'],
        functions: FUNCTION_TOOLS.map(t => ({
          name: t.name,
          description: t.description,
        })),
      },
    });
  });

  // ─── POST /api/interactions ───────────────────────────────────────────────
  // Create an interaction (non-streaming)
  //
  // Request body:
  // {
  //   input: string | ContentPart[] | ConversationTurn[],
  //   model?: string,
  //   agent?: string,
  //   previous_interaction_id?: string,
  //   system_instruction?: string,
  //   generation_config?: { temperature, max_output_tokens, thinking_level, thinking_summaries, ... },
  //   tools?: { googleSearch, codeExecution, urlContext, functionCalling, enabledFunctions, customTools },
  //   response_format?: object (JSON schema),
  //   store?: boolean,
  //   background?: boolean,
  //   autoExecuteTools?: boolean,
  // }
  router.post('/', async (req, res) => {
    try {
      const result = await service.createInteraction(req.body);
      res.json(result);
    } catch (error) {
      console.error('[Interactions] Error:', error.message);
      res.status(error.status || 500).json({
        error: true,
        message: error.message,
        code: error.code || 'INTERACTION_ERROR',
      });
    }
  });

  // ─── POST /api/interactions/stream ────────────────────────────────────────
  // Create a streaming interaction (Server-Sent Events)
  //
  // Same request body as POST /api/interactions
  // Response is an SSE stream with events:
  //   data: { event_type: 'content.delta', delta: { type: 'text', text: '...' } }
  //   data: { event_type: 'content.delta', delta: { type: 'thought', thought: '...' } }
  //   data: { event_type: 'interaction.complete', interaction: { ... } }
  router.post('/stream', async (req, res) => {
    // Set SSE headers
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no'); // Disable nginx buffering
    res.flushHeaders();

    try {
      const stream = service.createStreamingInteraction(req.body);

      for await (const chunk of stream) {
        // Send each chunk as an SSE event
        res.write(`data: ${JSON.stringify(chunk)}\n\n`);
      }

      // Signal stream end
      res.write(`data: [DONE]\n\n`);
      res.end();
    } catch (error) {
      console.error('[Interactions] Stream error:', error.message);
      res.write(`data: ${JSON.stringify({ error: true, message: error.message })}\n\n`);
      res.end();
    }
  });

  // ─── GET /api/interactions/:id ────────────────────────────────────────────
  // Retrieve a previous interaction by ID
  router.get('/:id', async (req, res) => {
    try {
      const result = await service.getInteraction(req.params.id);
      if (!result) {
        return res.status(404).json({ error: true, message: 'Interaction not found' });
      }
      res.json(result);
    } catch (error) {
      console.error('[Interactions] Get error:', error.message);
      res.status(error.status || 500).json({
        error: true,
        message: error.message,
        code: error.code || 'GET_INTERACTION_ERROR',
      });
    }
  });

  // ─── POST /api/interactions/:id/continue ──────────────────────────────────
  // Continue a conversation from a previous interaction (stateful)
  //
  // Request body:
  // {
  //   input: string | ContentPart[],
  //   ... (same options as POST /api/interactions, except previous_interaction_id is set from URL)
  // }
  router.post('/:id/continue', async (req, res) => {
    try {
      const result = await service.createInteraction({
        ...req.body,
        previous_interaction_id: req.params.id,
      });
      res.json(result);
    } catch (error) {
      console.error('[Interactions] Continue error:', error.message);
      res.status(error.status || 500).json({
        error: true,
        message: error.message,
        code: error.code || 'CONTINUE_INTERACTION_ERROR',
      });
    }
  });

  // ─── POST /api/interactions/:id/continue/stream ───────────────────────────
  // Continue a conversation with streaming (SSE)
  router.post('/:id/continue/stream', async (req, res) => {
    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders();

    try {
      const stream = service.createStreamingInteraction({
        ...req.body,
        previous_interaction_id: req.params.id,
      });

      for await (const chunk of stream) {
        res.write(`data: ${JSON.stringify(chunk)}\n\n`);
      }

      res.write(`data: [DONE]\n\n`);
      res.end();
    } catch (error) {
      console.error('[Interactions] Continue stream error:', error.message);
      res.write(`data: ${JSON.stringify({ error: true, message: error.message })}\n\n`);
      res.end();
    }
  });

  return router;
}
