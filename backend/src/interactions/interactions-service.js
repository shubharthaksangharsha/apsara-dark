/**
 * Apsara Dark — Interactions API Backend
 * 
 * Core service that wraps the @google/genai Interactions API.
 * Handles: text interactions, stateful conversations, streaming,
 * function calling (with auto-execution), built-in tools,
 * structured output, thinking, and multimodal input.
 * 
 * Uses @google/genai >= 1.33.0 for interactions support.
 */

import { GoogleGenAI } from '@google/genai';
import {
  DEFAULT_MODEL,
  DEFAULT_GENERATION_CONFIG,
  DEFAULT_SYSTEM_INSTRUCTION,
  BUILTIN_TOOLS,
} from './interactions-config.js';
import { FUNCTION_TOOLS, executeFunction } from './interactions-tools.js';

export class InteractionsService {
  constructor(apiKey) {
    this.apiKey = apiKey;
    this.client = new GoogleGenAI({ apiKey });
  }

  /**
   * Build the tools array from a request config.
   * 
   * @param {Object} toolsConfig - { googleSearch, codeExecution, urlContext, functionCalling, customTools }
   * @returns {Array} tools array for the Interactions API
   */
  _buildTools(toolsConfig = {}) {
    const tools = [];

    // Built-in tools
    if (toolsConfig.googleSearch) {
      tools.push(BUILTIN_TOOLS.GOOGLE_SEARCH);
    }
    if (toolsConfig.codeExecution) {
      tools.push(BUILTIN_TOOLS.CODE_EXECUTION);
    }
    if (toolsConfig.urlContext) {
      tools.push(BUILTIN_TOOLS.URL_CONTEXT);
    }

    // Function calling — add our registered function tools
    if (toolsConfig.functionCalling !== false) {
      // Filter to only requested tools, or include all
      const enabledNames = toolsConfig.enabledFunctions;
      const funcs = enabledNames
        ? FUNCTION_TOOLS.filter(t => enabledNames.includes(t.name))
        : FUNCTION_TOOLS;
      tools.push(...funcs);
    }

    // Custom tools from client (ad-hoc function definitions)
    if (toolsConfig.customTools && Array.isArray(toolsConfig.customTools)) {
      tools.push(...toolsConfig.customTools);
    }

    return tools;
  }

  /**
   * Build generation_config from request params.
   */
  _buildGenerationConfig(config = {}) {
    const genConfig = {};

    if (config.temperature !== undefined) {
      genConfig.temperature = config.temperature;
    }
    if (config.max_output_tokens !== undefined) {
      genConfig.max_output_tokens = config.max_output_tokens;
    }
    if (config.thinking_level) {
      genConfig.thinking_level = config.thinking_level;
    }
    if (config.thinking_summaries) {
      genConfig.thinking_summaries = config.thinking_summaries;
    }
    if (config.response_modalities) {
      genConfig.response_modalities = config.response_modalities;
    }
    if (config.speech_config) {
      genConfig.speech_config = config.speech_config;
    }
    if (config.image_config) {
      genConfig.image_config = config.image_config;
    }

    return Object.keys(genConfig).length > 0 ? genConfig : undefined;
  }

  /**
   * Create an interaction (non-streaming).
   * 
   * Supports:
   * - Text input (string or content array)
   * - Stateful conversations (previous_interaction_id)
   * - Function calling with auto-execution
   * - Built-in tools (Google Search, Code Execution, URL Context)
   * - Structured output (response_format / JSON schema)
   * - Thinking configuration
   * - Multimodal input
   * - Image/audio generation
   * 
   * @param {Object} params
   * @returns {Object} interaction result
   */
  async createInteraction(params = {}) {
    const {
      input,
      model = DEFAULT_MODEL,
      agent,
      previous_interaction_id,
      system_instruction = DEFAULT_SYSTEM_INSTRUCTION,
      generation_config = {},
      tools: toolsConfig = {},
      response_format,
      store,
      background,
      autoExecuteTools = true, // Auto-execute registered function tools
    } = params;

    // Build the request object
    const request = {};

    // Model or agent (mutually exclusive)
    if (agent) {
      request.agent = agent;
    } else {
      request.model = model;
    }

    // Input
    if (input !== undefined) {
      request.input = input;
    }

    // Previous interaction for stateful conversation
    if (previous_interaction_id) {
      request.previous_interaction_id = previous_interaction_id;
    }

    // System instruction
    if (system_instruction) {
      request.system_instruction = system_instruction;
    }

    // Generation config
    const mergedGenConfig = { ...DEFAULT_GENERATION_CONFIG, ...generation_config };
    const genConfig = this._buildGenerationConfig(mergedGenConfig);
    if (genConfig) {
      request.generation_config = genConfig;
    }

    // Tools
    const tools = this._buildTools(toolsConfig);
    if (tools.length > 0) {
      request.tools = tools;
    }

    // Structured output
    if (response_format) {
      request.response_format = response_format;
    }

    // Storage control
    if (store !== undefined) {
      request.store = store;
    }

    // Background (agents only)
    if (background !== undefined) {
      request.background = background;
    }

    console.log('[Interactions] Creating interaction:', {
      model: request.model || request.agent,
      hasInput: !!request.input,
      hasPreviousId: !!request.previous_interaction_id,
      toolCount: tools.length,
      hasResponseFormat: !!request.response_format,
    });

    // Create the interaction
    let interaction = await this.client.interactions.create(request);

    // Auto-execute function calls if enabled
    if (autoExecuteTools && interaction.outputs) {
      interaction = await this._handleFunctionCalls(interaction, request);
    }

    return this._formatResponse(interaction);
  }

  /**
   * Create a streaming interaction.
   * Returns an async generator of chunks.
   * 
   * @param {Object} params - Same as createInteraction
   * @returns {AsyncGenerator} stream of chunks
   */
  async *createStreamingInteraction(params = {}) {
    const {
      input,
      model = DEFAULT_MODEL,
      agent,
      previous_interaction_id,
      system_instruction = DEFAULT_SYSTEM_INSTRUCTION,
      generation_config = {},
      tools: toolsConfig = {},
      response_format,
      store,
    } = params;

    const request = { stream: true };

    if (agent) {
      request.agent = agent;
    } else {
      request.model = model;
    }

    if (input !== undefined) request.input = input;
    if (previous_interaction_id) request.previous_interaction_id = previous_interaction_id;
    if (system_instruction) request.system_instruction = system_instruction;
    if (store !== undefined) request.store = store;

    const mergedGenConfig = { ...DEFAULT_GENERATION_CONFIG, ...generation_config };
    const genConfig = this._buildGenerationConfig(mergedGenConfig);
    if (genConfig) request.generation_config = genConfig;

    const tools = this._buildTools(toolsConfig);
    if (tools.length > 0) request.tools = tools;
    if (response_format) request.response_format = response_format;

    console.log('[Interactions] Creating streaming interaction:', {
      model: request.model || request.agent,
      hasInput: !!request.input,
    });

    const stream = await this.client.interactions.create(request);

    for await (const chunk of stream) {
      yield chunk;
    }
  }

  /**
   * Get a previous interaction by ID.
   */
  async getInteraction(interactionId) {
    const interaction = await this.client.interactions.get(interactionId);
    return this._formatResponse(interaction);
  }

  /**
   * Handle function calls in the interaction response.
   * Auto-executes registered functions and sends results back.
   * Loops until no more function calls are returned.
   */
  async _handleFunctionCalls(interaction, originalRequest, maxRounds = 5) {
    let currentInteraction = interaction;
    let round = 0;

    while (round < maxRounds) {
      const functionCalls = currentInteraction.outputs?.filter(o => o.type === 'function_call');
      if (!functionCalls || functionCalls.length === 0) break;

      console.log(`[Interactions] Function calls detected (round ${round + 1}):`,
        functionCalls.map(fc => fc.name));

      // Execute each function call
      const functionResults = [];
      for (const fc of functionCalls) {
        console.log(`[Interactions] Executing: ${fc.name}(${JSON.stringify(fc.arguments || {})})`);
        const result = await executeFunction(fc.name, fc.arguments || {});
        console.log(`[Interactions] Result: ${result}`);

        functionResults.push({
          type: 'function_result',
          name: fc.name,
          call_id: fc.id,
          result: result,
        });
      }

      // Send results back using stateful conversation
      const followUpRequest = {
        model: originalRequest.model,
        previous_interaction_id: currentInteraction.id,
        input: functionResults,
      };

      // Re-include tools and system instruction (they're interaction-scoped)
      if (originalRequest.tools) followUpRequest.tools = originalRequest.tools;
      if (originalRequest.system_instruction) followUpRequest.system_instruction = originalRequest.system_instruction;
      if (originalRequest.generation_config) followUpRequest.generation_config = originalRequest.generation_config;

      currentInteraction = await this.client.interactions.create(followUpRequest);
      round++;
    }

    if (round >= maxRounds) {
      console.warn(`[Interactions] Reached max function call rounds (${maxRounds})`);
    }

    return currentInteraction;
  }

  /**
   * Format the interaction response for the client.
   */
  _formatResponse(interaction) {
    if (!interaction) return null;

    const response = {
      id: interaction.id,
      status: interaction.status,
      model: interaction.model,
      agent: interaction.agent,
      outputs: interaction.outputs || [],
      usage: interaction.usage || null,
    };

    // Extract text outputs for convenience
    const textOutputs = response.outputs.filter(o => o.type === 'text');
    if (textOutputs.length > 0) {
      response.text = textOutputs[textOutputs.length - 1].text;
    }

    // Extract thought outputs
    const thoughts = response.outputs.filter(o => o.type === 'thought');
    if (thoughts.length > 0) {
      response.thoughts = thoughts.map(t => ({
        summary: t.summary || null,
        signature: t.signature || null,
      }));
    }

    // Extract function calls (if any remain unprocessed)
    const functionCalls = response.outputs.filter(o => o.type === 'function_call');
    if (functionCalls.length > 0) {
      response.functionCalls = functionCalls;
    }

    // Extract image outputs
    const images = response.outputs.filter(o => o.type === 'image');
    if (images.length > 0) {
      response.images = images.map(img => ({
        data: img.data,
        mime_type: img.mime_type,
      }));
    }

    // Extract audio outputs
    const audio = response.outputs.filter(o => o.type === 'audio');
    if (audio.length > 0) {
      response.audio = audio.map(a => ({
        data: a.data,
        mime_type: a.mime_type,
      }));
    }

    return response;
  }
}
