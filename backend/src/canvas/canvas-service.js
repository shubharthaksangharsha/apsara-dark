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
- Use \`* { box-sizing: border-box; }\` and \`body { margin: 0; overflow-x: hidden; }\``;

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
   * @returns {Promise<Object>} The canvas app
   */
  async generateApp({ prompt, title, config = {}, onProgress }) {
    const mergedConfig = { ...CANVAS_DEFAULTS, ...config };
    
    // Create the canvas entry
    const autoTitle = title || this._generateTitle(prompt);
    const canvas = canvasStore.create({
      title: autoTitle,
      description: prompt,
      prompt,
    });

    onProgress?.('generating', `Creating "${autoTitle}"...`);

    try {
      // Generate the initial code
      let html = await this._generate(prompt, mergedConfig);
      canvasStore.update(canvas.id, { html, status: 'testing', attempts: 1 });
      onProgress?.('testing', 'Validating code...');

      // Validate and auto-fix loop
      const maxAttempts = 3;
      let attempt = 1;

      while (attempt <= maxAttempts) {
        const errors = this._validateHtml(html);
        
        if (errors.length === 0) {
          // No errors — mark as ready
          canvasStore.update(canvas.id, { html, status: 'ready', error: null });
          onProgress?.('ready', `"${autoTitle}" is ready!`);
          return canvasStore.get(canvas.id);
        }

        if (attempt >= maxAttempts) {
          // Max attempts reached — serve what we have with warning
          canvasStore.update(canvas.id, { 
            html, 
            status: 'ready', 
            error: `Validation warnings (served anyway): ${errors.join('; ')}` 
          });
          onProgress?.('ready', `"${autoTitle}" ready (with minor warnings)`);
          return canvasStore.get(canvas.id);
        }

        // Try to fix
        attempt++;
        canvasStore.update(canvas.id, { status: 'fixing', attempts: attempt });
        onProgress?.('fixing', `Fixing issues (attempt ${attempt}/${maxAttempts})...`);

        html = await this._fix(html, errors, prompt, mergedConfig);
        canvasStore.update(canvas.id, { html });
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
   * Generate HTML using the Interactions API (streaming, collected).
   */
  async _generate(prompt, config) {
    console.log('[Canvas] Generating app for prompt:', prompt.substring(0, 100));

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
    };

    const interaction = await this.client.interactions.create(request);
    
    // Extract text from outputs
    const textOutputs = interaction.outputs?.filter(o => o.type === 'text') || [];
    let html = textOutputs.map(o => o.text).join('');

    // Clean up — strip markdown fences if present
    html = this._cleanHtml(html);

    return html;
  }

  /**
   * Fix HTML errors using the Interactions API.
   */
  async _fix(html, errors, originalPrompt, config) {
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
    };

    const interaction = await this.client.interactions.create(request);
    const textOutputs = interaction.outputs?.filter(o => o.type === 'text') || [];
    let fixed = textOutputs.map(o => o.text).join('');
    fixed = this._cleanHtml(fixed);

    return fixed;
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

        html = await this._fix(html, errors, prompt, mergedConfig);
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
}
