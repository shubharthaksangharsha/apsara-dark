/**
 * Apsara Dark — Canvas Service
 * 
 * Uses Gemini Interactions API (streaming) to generate full HTML/CSS/JS apps.
 * Model: gemini-3-flash-preview (configurable)
 * Max output tokens: 65,536
 * Reasoning: high
 * 
 * Flow:
 * 1. User (via Gemini Live tool call) requests an app
 * 2. Canvas service generates code via streaming Interactions API
 * 3. Code is validated (basic HTML parse + JS syntax check)
 * 4. If errors found, auto-fix loop (up to 3 attempts)
 * 5. Final result stored and served via iframe-friendly endpoint
 */

import { GoogleGenAI } from '@google/genai';
import { canvasStore } from './canvas-store.js';
import vm from 'vm';

// ─── Default Canvas Config ──────────────────────────────────────────────────

export const CANVAS_DEFAULTS = {
  model: 'gemini-3-flash-preview',
  max_output_tokens: 65536,
  thinking_level: 'high',
  thinking_summaries: 'auto',
  temperature: 0.7,
};

const CANVAS_SYSTEM_INSTRUCTION = `You are Apsara Canvas — a world-class full-stack web app builder.

Your mission: Generate complete, self-contained, single-file web applications using HTML, CSS, and JavaScript.

**Rules:**
1. Always output a SINGLE, complete HTML file that works standalone in any browser.
2. All CSS must be inside a <style> tag in the <head>.
3. All JavaScript must be inside a <script> tag before </body>.
4. Use modern HTML5, CSS3, and ES6+ JavaScript.
5. Make the UI beautiful, responsive, and polished — use gradients, shadows, smooth transitions, and clean typography.
6. Dark theme by default with a modern aesthetic (matching Apsara Dark's style).
7. You can use inline SVGs for icons — do NOT use external CDN links unless the user explicitly asks for a library.
8. If the user asks for React, include React and ReactDOM via CDN (unpkg), use JSX via Babel standalone, and render into a root div.
9. The app must be fully functional — no placeholder text like "TODO" or "come soon".
10. Include proper error handling in JavaScript.
11. Output ONLY the HTML code — no markdown, no backticks, no explanation. Start with <!DOCTYPE html> and end with </html>.

**CRITICAL — Mobile-First Responsive Design:**
- ALWAYS include this meta tag in <head>: <meta name="viewport" content="width=device-width, initial-scale=1.0">
- The app MUST work perfectly on mobile phones (360px wide) AND desktops.
- Use CSS media queries for different screen sizes.
- For multi-panel/split layouts: use a STACKED layout on mobile (flex-direction: column) and side-by-side on desktop (@media (min-width: 768px) { flex-direction: row }).
- Use percentage widths or CSS Grid/Flexbox instead of fixed pixel widths.
- All text must be readable without zooming (minimum 14px font size).
- All buttons/interactive elements must be at least 44x44px touch targets on mobile.
- Use max-width: 100% and box-sizing: border-box on all elements.
- Avoid horizontal scrolling — everything must fit within the viewport width.

**Style Guidelines:**
- Primary background: #0D0D0D to #1A1A1A
- Accent color: #B388FF (purple) or user-specified
- Text: #E8E8E8 (primary), #9E9E9E (secondary)
- Border radius: 12-16px for cards, 8px for buttons
- Font: system-ui, -apple-system, sans-serif
- Smooth hover transitions (0.2s ease)
- Use \`* { box-sizing: border-box; }\` and \`body { margin: 0; overflow-x: hidden; }\`

**URL Context:**
You have access to the URL Context tool. If the user references any URL (e.g., "create a portfolio like devshubh.me" or "clone this page: https://example.com"), you can automatically fetch and analyze the content at that URL to inform your design. Use this to replicate layouts, color schemes, content structure, or any relevant information from the referenced site.`;

export class CanvasService {
  constructor(apiKey) {
    this.apiKey = apiKey;
    this.client = new GoogleGenAI({ apiKey });
  }

  /**
   * Generate a canvas app from a prompt.
   * Returns the canvas app object with status updates.
   * 
   * @param {Object} params
   * @param {string} params.prompt - What app to build
   * @param {string} [params.title] - App title
   * @param {Object} [params.config] - Override default config
   * @param {Function} [params.onProgress] - Progress callback (status, message)
   * @param {Function} [params.onThoughtStart] - Called when a new thought summary block begins
   * @param {Function} [params.onThought] - Thought summary delta callback (incremental text)
   * @param {Function} [params.onToolStatus] - Tool execution status callback (status, toolType)
   * @returns {Promise<Object>} The canvas app
   */
  async generateApp({ prompt, title, config = {}, onProgress, onChunk, onThoughtStart, onThought, onToolStatus }) {
    const mergedConfig = { ...CANVAS_DEFAULTS, ...config };

    // Create the canvas entry
    const autoTitle = title || this._generateTitle(prompt);
    const canvas = canvasStore.create({
      title: autoTitle,
      description: prompt,
      prompt,
    });

    // Store the config used for this generation
    canvasStore.update(canvas.id, { config_used: mergedConfig });

    onProgress?.('generating', `Creating "${autoTitle}"...`);

    try {
      // Generate the initial code — capture interaction_id for multi-turn edits
      const { html: generatedHtml, interactionId } = onChunk
        ? await this._generateStreaming(prompt, mergedConfig, null, onChunk, onThoughtStart, onThought, onToolStatus)
        : await this._generate(prompt, mergedConfig);
      let html = generatedHtml;
      canvasStore.update(canvas.id, { html, status: 'testing', attempts: 1, interaction_id: interactionId });
      console.log(`[Canvas] Stored interaction_id: ${interactionId} for canvas ${canvas.id}`);
      onProgress?.('testing', 'Validating code...');

      // Validate and auto-fix loop
      const maxAttempts = 3;
      let attempt = 1;
      let currentInteractionId = interactionId;

      while (attempt <= maxAttempts) {
        const errors = this._validateHtml(html);

        if (errors.length === 0) {
          // No errors — mark as ready
          canvasStore.update(canvas.id, { html, status: 'ready', error: null, interaction_id: currentInteractionId });
          onProgress?.('ready', `"${autoTitle}" is ready!`);
          return canvasStore.get(canvas.id);
        }

        if (attempt >= maxAttempts) {
          // Max attempts reached — serve what we have with warning
          canvasStore.update(canvas.id, {
            html,
            status: 'ready',
            error: `Validation warnings (served anyway): ${errors.join('; ')}`,
            interaction_id: currentInteractionId
          });
          onProgress?.('ready', `"${autoTitle}" ready (with minor warnings)`);
          return canvasStore.get(canvas.id);
        }

        // Try to fix — chain via interaction_id for context
        attempt++;
        canvasStore.update(canvas.id, { status: 'fixing', attempts: attempt });
        onProgress?.('fixing', `Fixing issues (attempt ${attempt}/${maxAttempts})...`);

        const fixResult = await this._fix(html, errors, prompt, mergedConfig, currentInteractionId);
        html = fixResult.html;
        currentInteractionId = fixResult.interactionId || currentInteractionId;
        canvasStore.update(canvas.id, { html, interaction_id: currentInteractionId });
      }

      return canvasStore.get(canvas.id);
    } catch (error) {
      console.error('[Canvas] Generation error:', error.message);
      canvasStore.update(canvas.id, {
        status: 'error',
        error: error.message
      });
      onProgress?.('error', `Failed: ${error.message}`);
      return canvasStore.get(canvas.id);
    }
  }

  /**
   * Edit an existing canvas app based on instructions.
   * Reads the current code and metadata, regenerates with edit instructions.
   * 
   * @param {Object} params
   * @param {string} params.canvasId - ID of the canvas to edit
   * @param {string} params.instructions - What to change/improve
   * @param {Object} [params.config] - Override default config
   * @param {Function} [params.onProgress] - Progress callback
   * @param {Function} [params.onThoughtStart] - Called when new thought summary block begins
   * @param {Function} [params.onThought] - Thought summary delta callback
   * @param {Function} [params.onToolStatus] - Tool execution status callback
   * @returns {Promise<Object>} The updated canvas app
   */
  async editApp({ canvasId, instructions, config = {}, onProgress, onChunk, onThoughtStart, onThought, onToolStatus }) {
    const mergedConfig = { ...CANVAS_DEFAULTS, ...config };

    // Get the existing canvas
    const existing = canvasStore.get(canvasId);
    if (!existing) {
      throw new Error(`Canvas not found: ${canvasId}`);
    }

    onProgress?.('generating', `Editing "${existing.title}"...`);

    // ── Snapshot current state as a version before applying edits ──
    const versions = existing.versions || [];
    if (existing.html && existing.status === 'ready') {
      versions.push({
        version: versions.length + 1,
        title: existing.title,
        html: existing.html,
        html_length: existing.html.length,
        timestamp: new Date().toISOString(),
      });
    }

    // Generate a new title that reflects the edit instructions
    const newTitle = this._generateEditTitle(existing.title, instructions);

    // Track edit history for context (individual instructions, no [Edit:] in prompt)
    const editHistory = existing.edit_history || [];
    editHistory.push({
      instructions,
      timestamp: new Date().toISOString(),
      previous_interaction_id: existing.interaction_id || null,
      config_used: mergedConfig,
    });

    canvasStore.update(canvasId, {
      status: 'generating',
      // Keep prompt as original — don't append [Edit:] markers
      title: newTitle,
      config_used: mergedConfig,
      edit_history: editHistory,
      versions,
    });

    // Determine if we can use multi-turn context
    const previousInteractionId = existing.interaction_id || null;
    const isMultiTurn = !!previousInteractionId;
    console.log(`[Canvas] Edit mode: ${isMultiTurn ? 'MULTI-TURN (chaining from ' + previousInteractionId + ')' : 'SINGLE-TURN (no previous interaction)'}`);

    try {
      let html, interactionId;

      if (isMultiTurn) {
        // Multi-turn: send only the edit instructions, Gemini has full context
        const editOnlyPrompt = `The user wants the following changes to the app:\n${instructions}\n\nApply the requested changes. Keep everything that works well, and only change/add/remove what's needed. Output the COMPLETE updated HTML file — no partial code, no placeholders. Start with <!DOCTYPE html> and end with </html>.`;
        const result = onChunk
          ? await this._generateStreaming(editOnlyPrompt, mergedConfig, previousInteractionId, onChunk, onThoughtStart, onThought, onToolStatus)
          : await this._generate(editOnlyPrompt, mergedConfig, previousInteractionId);
        html = result.html;
        interactionId = result.interactionId;
      } else {
        // Single-turn fallback: send full code + instructions (old behavior)
        const editPrompt = this._buildEditPrompt(existing, instructions);
        const result = onChunk
          ? await this._generateStreaming(editPrompt, mergedConfig, null, onChunk, onThoughtStart, onThought, onToolStatus)
          : await this._generate(editPrompt, mergedConfig);
        html = result.html;
        interactionId = result.interactionId;
      }

      // Try to extract a better title from the generated HTML's <title> tag
      const htmlTitleMatch = html.match(/<title[^>]*>([^<]+)<\/title>/i);
      if (htmlTitleMatch && htmlTitleMatch[1]) {
        const htmlTitle = htmlTitleMatch[1].trim();
        // Only use the HTML title if it's meaningful (not generic like "App" or empty)
        if (htmlTitle.length > 2 && htmlTitle.toLowerCase() !== 'app' && htmlTitle.toLowerCase() !== 'document') {
          canvasStore.update(canvasId, { title: htmlTitle });
        }
      }

      canvasStore.update(canvasId, { html, status: 'testing', attempts: (existing.attempts || 0) + 1, interaction_id: interactionId });
      onProgress?.('testing', 'Validating edited code...');

      // Get the final title (may have been updated from HTML)
      const finalTitle = canvasStore.get(canvasId)?.title || newTitle;
      let currentInteractionId = interactionId;

      // Validate and auto-fix loop
      const maxAttempts = 3;
      let attempt = 1;

      while (attempt <= maxAttempts) {
        const errors = this._validateHtml(html);

        if (errors.length === 0) {
          canvasStore.update(canvasId, { html, status: 'ready', error: null, interaction_id: currentInteractionId });
          onProgress?.('ready', `"${finalTitle}" has been updated!`);
          return canvasStore.get(canvasId);
        }

        if (attempt >= maxAttempts) {
          canvasStore.update(canvasId, {
            html,
            status: 'ready',
            error: `Validation warnings after edit (served anyway): ${errors.join('; ')}`,
            interaction_id: currentInteractionId
          });
          onProgress?.('ready', `"${finalTitle}" updated (with minor warnings)`);
          return canvasStore.get(canvasId);
        }

        attempt++;
        canvasStore.update(canvasId, { status: 'fixing', attempts: (existing.attempts || 0) + attempt });
        onProgress?.('fixing', `Fixing issues (attempt ${attempt}/${maxAttempts})...`);

        const fixResult = await this._fix(html, errors, (existing.original_prompt || existing.prompt) + '\n\nEdit: ' + instructions, mergedConfig, currentInteractionId);
        html = fixResult.html;
        currentInteractionId = fixResult.interactionId || currentInteractionId;
        canvasStore.update(canvasId, { html, interaction_id: currentInteractionId });
      }

      return canvasStore.get(canvasId);
    } catch (error) {
      console.error('[Canvas] Edit error:', error.message);

      // If multi-turn failed, log it — the next edit will fall back to single-turn
      if (isMultiTurn) {
        console.warn('[Canvas] Multi-turn edit failed, interaction chain may have expired. Next edit will use single-turn.');
        canvasStore.update(canvasId, { interaction_id: null });
      }

      canvasStore.update(canvasId, {
        status: 'error',
        error: error.message
      });
      onProgress?.('error', `Edit failed: ${error.message}`);
      return canvasStore.get(canvasId);
    }
  }

  /**
   * Build a prompt for editing an existing canvas app.
   * Includes the current code, original prompt, and edit instructions.
   */
  _buildEditPrompt(existingApp, instructions) {
    const parts = [];

    parts.push(`You previously built this web app titled "${existingApp.title}".`);

    if (existingApp.prompt) {
      parts.push(`\nOriginal request: "${existingApp.prompt}"`);
    }

    if (existingApp.html) {
      parts.push(`\nHere is the CURRENT complete code of the app:\n\`\`\`html\n${existingApp.html}\n\`\`\``);
    }

    parts.push(`\nThe user wants the following changes:\n${instructions}`);
    parts.push(`\nApply the requested changes to the existing code. Keep everything that works well, and only change/add/remove what's needed to fulfill the edit request. If the edit fundamentally changes the app's purpose or type, update the <title> tag accordingly. Output the COMPLETE updated HTML file — no partial code, no placeholders. Start with <!DOCTYPE html> and end with </html>.`);

    return parts.join('\n');
  }

  /**
 * Generate HTML using the Interactions API.
 * Supports multi-turn via optional previousInteractionId.
 * 
 * @param {string} prompt - The generation prompt
 * @param {Object} config - Generation config
 * @param {string} [previousInteractionId] - Chain to a previous interaction for multi-turn context
 * @returns {Promise<{html: string, interactionId: string}>} Generated HTML and interaction ID
 */
  async _generate(prompt, config, previousInteractionId = null) {
    console.log('[Canvas] Generating app for prompt:', prompt.substring(0, 100));
    if (previousInteractionId) {
      console.log('[Canvas] Chaining from previous interaction:', previousInteractionId);
    }

    const request = {
      model: config.model || CANVAS_DEFAULTS.model,
      input: prompt,
      system_instruction: CANVAS_SYSTEM_INSTRUCTION,
      generation_config: {
        temperature: config.temperature ?? CANVAS_DEFAULTS.temperature,
        max_output_tokens: config.max_output_tokens ?? CANVAS_DEFAULTS.max_output_tokens,
        thinking_level: config.thinking_level ?? CANVAS_DEFAULTS.thinking_level,
        thinking_summaries: config.thinking_summaries ?? CANVAS_DEFAULTS.thinking_summaries,
      },
      tools: [{ type: 'url_context' }],
    };

    // Multi-turn: chain to previous interaction for conversation context
    if (previousInteractionId) {
      request.previous_interaction_id = previousInteractionId;
    }

    const interaction = await this.client.interactions.create(request);

    // Extract text from outputs
    const textOutputs = interaction.outputs?.filter(o => o.type === 'text') || [];
    let html = textOutputs.map(o => o.text).join('');

    // Clean up — strip markdown fences if present
    html = this._cleanHtml(html);

    return { html, interactionId: interaction.id || null };
  }

  /**
   * Generate HTML using streaming Interactions API.
   * Same as _generate() but yields text deltas via onChunk callback.
   * 
   * @param {string} prompt - The generation prompt
   * @param {Object} config - Generation config
   * @param {string} [previousInteractionId] - Chain for multi-turn context
   * @param {Function} [onChunk] - Called with each text delta string
   * @param {Function} [onThoughtStart] - Called when a new thought summary block begins
   * @param {Function} [onThought] - Called with each thought summary delta
   * @param {Function} [onToolStatus] - Called with (status, toolType) for tool execution events
   * @returns {Promise<{html: string, interactionId: string}>}
   */
  async _generateStreaming(prompt, config, previousInteractionId = null, onChunk = null, onThoughtStart = null, onThought = null, onToolStatus = null) {
    console.log('[Canvas] Streaming generation for prompt:', prompt.substring(0, 100));
    if (previousInteractionId) {
      console.log('[Canvas] Chaining from previous interaction:', previousInteractionId);
    }

    const request = {
      model: config.model || CANVAS_DEFAULTS.model,
      input: prompt,
      system_instruction: CANVAS_SYSTEM_INSTRUCTION,
      stream: true,
      generation_config: {
        temperature: config.temperature ?? CANVAS_DEFAULTS.temperature,
        max_output_tokens: config.max_output_tokens ?? CANVAS_DEFAULTS.max_output_tokens,
        thinking_level: config.thinking_level ?? CANVAS_DEFAULTS.thinking_level,
        thinking_summaries: config.thinking_summaries ?? CANVAS_DEFAULTS.thinking_summaries,
      },
      tools: [{ type: 'url_context' }],
    };

    if (previousInteractionId) {
      request.previous_interaction_id = previousInteractionId;
    }

    let html = '';
    let interactionId = null;
    const stream = await this.client.interactions.create(request);

    for await (const chunk of stream) {
      // ── Detailed logging for every chunk from Interactions API ──
      const eventType = chunk.event_type;
      const contentType = chunk.content?.type || chunk.delta?.type || 'unknown';
      console.log(`[Canvas][Stream] event=${eventType} contentType=${contentType}`, JSON.stringify(chunk).substring(0, 300));

      if (chunk.event_type === 'content.delta') {
        if (chunk.delta?.type === 'text' && chunk.delta.text) {
          html += chunk.delta.text;
          onChunk?.(chunk.delta.text);
        } else if (chunk.delta?.type === 'thought' && chunk.delta.thought) {
          onThought?.(chunk.delta.thought);
        } else if (chunk.delta?.type === 'thought_summary' && chunk.delta.content?.text) {
          onThought?.(chunk.delta.content.text);
        } else {
          console.log(`[Canvas][Stream] Unhandled content.delta type: ${chunk.delta?.type}`, JSON.stringify(chunk.delta).substring(0, 200));
        }
      } else if (chunk.event_type === 'content.start') {
        const cType = chunk.content?.type;
        console.log(`[Canvas][Stream] content.start: type=${cType}`, JSON.stringify(chunk.content).substring(0, 300));
        if (cType === 'thought_summary') {
          // New thought summary block — notify start
          onThoughtStart?.();
        } else if (cType && cType !== 'text' && cType !== 'thought') {
          // Built-in tool starts (url_context etc.) — log only
          console.log(`[Canvas][Stream] Built-in tool START: ${cType}`);
        }
      } else if (chunk.event_type === 'content.stop') {
        const cType = chunk.content?.type;
        console.log(`[Canvas][Stream] content.stop: type=${cType}`, JSON.stringify(chunk.content).substring(0, 200));
        if (cType && cType !== 'text' && cType !== 'thought' && cType !== 'thought_summary') {
          console.log(`[Canvas][Stream] Built-in tool STOP: ${cType}`);
        }
      } else if (chunk.event_type === 'interaction.complete') {
        interactionId = chunk.interaction?.id || null;
        console.log(`[Canvas][Stream] Interaction complete: id=${interactionId}`);
      } else {
        console.log(`[Canvas][Stream] Other event: ${eventType}`, JSON.stringify(chunk).substring(0, 200));
      }
    }

    html = this._cleanHtml(html);
    return { html, interactionId };
  }

  /**
 * Fix HTML errors using the Interactions API.
 * Chains via previousInteractionId when available for contextual fixes.
 * 
 * @returns {Promise<{html: string, interactionId: string}>} Fixed HTML and interaction ID
 */
  async _fix(html, errors, originalPrompt, config, previousInteractionId = null) {
    console.log('[Canvas] Fixing errors:', errors);

    const fixPrompt = `The following HTML app was generated for this request: "${originalPrompt}"

However, it has these issues:
${errors.map((e, i) => `${i + 1}. ${e}`).join('\n')}

Here is the current code:
\`\`\`html
${html}
\`\`\`

Fix ALL the issues and output the COMPLETE corrected HTML file. Remember: output ONLY the HTML code, no markdown, no explanation.`;

    const request = {
      model: config.model || CANVAS_DEFAULTS.model,
      input: fixPrompt,
      system_instruction: CANVAS_SYSTEM_INSTRUCTION,
      generation_config: {
        temperature: 0.3, // Lower temperature for fixes
        max_output_tokens: config.max_output_tokens ?? CANVAS_DEFAULTS.max_output_tokens,
        thinking_level: config.thinking_level ?? CANVAS_DEFAULTS.thinking_level,
      },
      tools: [{ type: 'url_context' }],
    };

    // Chain to previous interaction for context
    if (previousInteractionId) {
      request.previous_interaction_id = previousInteractionId;
    }

    const interaction = await this.client.interactions.create(request);
    const textOutputs = interaction.outputs?.filter(o => o.type === 'text') || [];
    let fixed = textOutputs.map(o => o.text).join('');
    fixed = this._cleanHtml(fixed);

    return { html: fixed, interactionId: interaction.id || null };
  }

  /**
   * Stream-generate a canvas app (for SSE endpoint).
   * Yields progress events.
   */
  async *generateAppStream({ prompt, title, config = {} }) {
    const mergedConfig = { ...CANVAS_DEFAULTS, ...config };
    const autoTitle = title || this._generateTitle(prompt);

    const canvas = canvasStore.create({
      title: autoTitle,
      description: prompt,
      prompt,
    });

    // Store the config used for this stream generation
    canvasStore.update(canvas.id, { config_used: mergedConfig });

    yield { event: 'canvas.created', canvas_id: canvas.id, title: autoTitle };
    yield { event: 'canvas.status', status: 'generating', message: `Creating "${autoTitle}"...` };

    try {
      // Stream the generation
      const request = {
        model: mergedConfig.model || CANVAS_DEFAULTS.model,
        input: prompt,
        system_instruction: CANVAS_SYSTEM_INSTRUCTION,
        stream: true,
        generation_config: {
          temperature: mergedConfig.temperature ?? CANVAS_DEFAULTS.temperature,
          max_output_tokens: mergedConfig.max_output_tokens ?? CANVAS_DEFAULTS.max_output_tokens,
          thinking_level: mergedConfig.thinking_level ?? CANVAS_DEFAULTS.thinking_level,
          thinking_summaries: mergedConfig.thinking_summaries ?? CANVAS_DEFAULTS.thinking_summaries,
        },
      };

      let html = '';
      const stream = await this.client.interactions.create(request);

      for await (const chunk of stream) {
        // Extract text deltas
        if (chunk.outputs) {
          for (const output of chunk.outputs) {
            if (output.type === 'text' && output.text) {
              html += output.text;
              yield { event: 'canvas.delta', delta: output.text };
            }
          }
        }
      }

      html = this._cleanHtml(html);
      canvasStore.update(canvas.id, { html, status: 'testing', attempts: 1 });
      yield { event: 'canvas.status', status: 'testing', message: 'Validating...' };

      // Validate
      const errors = this._validateHtml(html);
      if (errors.length === 0) {
        canvasStore.update(canvas.id, { html, status: 'ready', error: null });
        yield { event: 'canvas.ready', canvas_id: canvas.id, title: autoTitle };
        return;
      }

      // Auto-fix loop
      for (let attempt = 2; attempt <= 3; attempt++) {
        canvasStore.update(canvas.id, { status: 'fixing', attempts: attempt });
        yield { event: 'canvas.status', status: 'fixing', message: `Fixing (attempt ${attempt}/3)...` };

        const fixResult = await this._fix(html, errors, prompt, mergedConfig);
        html = fixResult.html;
        canvasStore.update(canvas.id, { html });

        const newErrors = this._validateHtml(html);
        if (newErrors.length === 0) {
          canvasStore.update(canvas.id, { html, status: 'ready', error: null });
          yield { event: 'canvas.ready', canvas_id: canvas.id, title: autoTitle };
          return;
        }
      }

      // Serve anyway with warnings
      canvasStore.update(canvas.id, { html, status: 'ready', error: 'Minor validation warnings' });
      yield { event: 'canvas.ready', canvas_id: canvas.id, title: autoTitle };

    } catch (error) {
      console.error('[Canvas] Stream generation error:', error.message);
      canvasStore.update(canvas.id, { status: 'error', error: error.message });
      yield { event: 'canvas.error', canvas_id: canvas.id, message: error.message };
    }
  }

  /**
   * Validate HTML — basic structural + JS syntax checks.
   */
  _validateHtml(html) {
    const errors = [];

    if (!html || html.trim().length === 0) {
      errors.push('Empty HTML output');
      return errors;
    }

    // Check basic HTML structure
    if (!html.includes('<!DOCTYPE html>') && !html.includes('<!doctype html>')) {
      errors.push('Missing <!DOCTYPE html> declaration');
    }
    if (!html.includes('<html')) {
      errors.push('Missing <html> tag');
    }
    if (!html.includes('<head>') && !html.includes('<head ')) {
      errors.push('Missing <head> section');
    }
    if (!html.includes('<body>') && !html.includes('<body ')) {
      errors.push('Missing <body> section');
    }
    if (!html.includes('</html>')) {
      errors.push('Missing closing </html> tag');
    }

    // Extract and validate JavaScript
    const scriptRegex = /<script(?:\s[^>]*)?>([^]*?)<\/script>/gi;
    let match;
    while ((match = scriptRegex.exec(html)) !== null) {
      const scriptContent = match[1].trim();
      // Skip external scripts (src attribute) and Babel/JSX scripts
      if (match[0].includes('src=') || match[0].includes('type="text/babel"') || match[0].includes("type='text/babel'")) {
        continue;
      }
      if (scriptContent.length > 0) {
        try {
          new vm.Script(scriptContent, { filename: 'canvas-app.js' });
        } catch (syntaxError) {
          errors.push(`JavaScript syntax error: ${syntaxError.message}`);
        }
      }
    }

    return errors;
  }

  /**
   * Clean HTML output — remove markdown fences, trim.
   */
  _cleanHtml(html) {
    if (!html) return '';
    // Remove markdown code fences
    html = html.replace(/^```(?:html)?\s*\n?/i, '');
    html = html.replace(/\n?```\s*$/i, '');
    return html.trim();
  }

  /**
   * Generate a short title from the prompt.
   */
  _generateTitle(prompt) {
    if (!prompt) return 'Untitled App';
    // Take first 40 chars, cut at word boundary
    const truncated = prompt.length > 40
      ? prompt.substring(0, 40).replace(/\s+\S*$/, '') + '…'
      : prompt;
    return truncated.charAt(0).toUpperCase() + truncated.slice(1);
  }

  /**
   * Generate a new title after editing a canvas app.
   * Uses the edit instructions to create a title that reflects the updated app.
   * Falls back to instruction-based title if instructions describe a clear rename.
   */
  _generateEditTitle(existingTitle, instructions) {
    if (!instructions) return existingTitle;

    // Check if instructions explicitly mention making it into something new
    // e.g., "make it a reminder app", "convert to a calculator", "change to a todo list"
    const transformPatterns = [
      /(?:make|turn|convert|change|transform)\s+(?:it|this)\s+(?:into|to|into a|to a)\s+(?:a\s+)?(.+?)(?:\s+app)?$/i,
      /(?:make|create|build)\s+(?:it|this)\s+a\s+(.+?)(?:\s+app)?$/i,
      /(?:rename|retitle)\s+(?:it|this)?\s*(?:to|as)\s+["']?(.+?)["']?$/i,
    ];

    for (const pattern of transformPatterns) {
      const match = instructions.match(pattern);
      if (match && match[1]) {
        const newName = match[1].trim();
        // Capitalize first letter of each word
        const titled = newName.replace(/\b\w/g, c => c.toUpperCase());
        return titled.endsWith('App') ? titled : `${titled} App`;
      }
    }

    // Otherwise, generate title from the instructions
    const title = this._generateTitle(instructions);
    // If the generated title is too similar to instructions being just a verb phrase,
    // prepend the context from the existing title
    if (title.length < 15 && existingTitle) {
      return `${existingTitle} (edited)`;
    }
    return title;
  }
}
