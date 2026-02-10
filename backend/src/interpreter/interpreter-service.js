/**
 * Apsara Dark — Interpreter Service
 * 
 * Uses the Gemini Interactions API with code_execution built-in tool
 * to run Python code in a sandboxed environment.
 * 
 * Supports:
 * - Python code execution
 * - Image output (matplotlib, PIL, etc.)
 * - Streaming results
 * - Multi-turn code sessions
 * 
 * The Interactions API natively supports code_execution as a built-in tool.
 * When the model generates code, Gemini runs it server-side and returns:
 * - executable_code: the Python code that was generated & executed
 * - code_execution_result: stdout, stderr, and any generated images
 */

import { GoogleGenAI } from '@google/genai';
import { interpreterStore } from './interpreter-store.js';

// ─── Default Interpreter Config ─────────────────────────────────────────────

export const INTERPRETER_DEFAULTS = {
  model: 'gemini-3-flash-preview',
  max_output_tokens: 65536,
  thinking_level: 'high',
  thinking_summaries: 'auto',
  temperature: 0.7,
};

const INTERPRETER_SYSTEM_INSTRUCTION = `You are Apsara Interpreter — a Python code execution assistant.

Your mission: Write and execute Python code to solve problems, create visualizations, process data, and demonstrate programming concepts.

**Rules:**
1. Always write clean, well-commented Python code.
2. Use the code_execution tool to run your code — NEVER just show code in text.
3. For visualizations, use matplotlib and save plots:
   - import matplotlib.pyplot as plt
   - Create your plot
   - plt.savefig('output.png', dpi=150, bbox_inches='tight')
   - plt.show()
4. For data analysis, use pandas, numpy, and scipy as needed.
5. Handle errors gracefully — if code fails, explain why and fix it.
6. Keep code concise but readable.
7. When showing results, print them clearly with labels.
8. For mathematical computations, use sympy or scipy.
9. If the user asks to generate an image or visualization, ALWAYS use matplotlib or PIL.
10. After executing code, summarize the results clearly.

**Available Libraries:**
- numpy, pandas, scipy, sympy
- matplotlib, PIL/Pillow
- json, csv, math, statistics
- collections, itertools, functools
- datetime, re, string

**Output Format:**
- Always execute the code — don't just describe what it would do.
- Print results to stdout so they appear in the output.
- For images, save to files — they will be captured automatically.`;

export class InterpreterService {
  constructor(apiKey) {
    this.apiKey = apiKey;
    this.client = new GoogleGenAI({ apiKey });
  }

  /**
   * Run code via the Interactions API with code_execution tool.
   * 
   * @param {Object} params
   * @param {string} params.prompt - What to compute/visualize/analyze
   * @param {string} [params.title] - Session title
   * @param {Object} [params.config] - Override default config
   * @param {Function} [params.onProgress] - Progress callback (status, message)
   * @returns {Promise<Object>} The code session result
   */
  async runCode({ prompt, title, config = {}, onProgress }) {
    const mergedConfig = { ...INTERPRETER_DEFAULTS, ...config };

    // Create the session entry
    const autoTitle = title || this._generateTitle(prompt);
    const session = interpreterStore.create({
      title: autoTitle,
      prompt,
    });

    onProgress?.('running', `Executing code for "${autoTitle}"...`);

    try {
      // Build the request
      const request = {
        model: mergedConfig.model,
        input: prompt,
        system_instruction: INTERPRETER_SYSTEM_INSTRUCTION,
        tools: [{ type: 'code_execution' }],
      };

      // Generation config
      const genConfig = {};
      if (mergedConfig.temperature !== undefined) genConfig.temperature = mergedConfig.temperature;
      if (mergedConfig.max_output_tokens !== undefined) genConfig.max_output_tokens = mergedConfig.max_output_tokens;
      if (mergedConfig.thinking_level) genConfig.thinking_level = mergedConfig.thinking_level;
      if (mergedConfig.thinking_summaries) genConfig.thinking_summaries = mergedConfig.thinking_summaries;
      if (Object.keys(genConfig).length > 0) request.generation_config = genConfig;

      console.log('[Interpreter] Creating interaction:', {
        model: mergedConfig.model,
        prompt: prompt.substring(0, 100) + (prompt.length > 100 ? '...' : ''),
      });

      onProgress?.('running', 'Generating and executing code...');

      // Create the interaction
      const interaction = await this.client.interactions.create(request);

      // Extract results from the interaction
      const result = this._extractResults(interaction);

      // Update the session with results
      interpreterStore.update(session.id, {
        code: result.code || '',
        output: result.output || '',
        images: result.images || [],
        status: result.error ? 'error' : 'completed',
        error: result.error || null,
      });

      onProgress?.(result.error ? 'error' : 'completed',
        result.error ? `Error: ${result.error}` : `Code executed successfully`);

      return interpreterStore.get(session.id);

    } catch (error) {
      console.error('[Interpreter] Error:', error.message);
      interpreterStore.update(session.id, {
        status: 'error',
        error: error.message,
      });
      onProgress?.('error', error.message);
      return interpreterStore.get(session.id);
    }
  }

  /**
   * Extract code, output, and images from an interaction response.
   */
  _extractResults(interaction) {
    const result = {
      code: '',
      output: '',
      text: '',
      images: [],
      error: null,
    };

    if (!interaction || !interaction.outputs) {
      return result;
    }

    for (const output of interaction.outputs) {
      switch (output.type) {
        case 'code_execution_call':
          // The code that was generated and executed
          // Structure: { arguments: { code, language }, id, type }
          result.code += (result.code ? '\n\n' : '') + (output.arguments?.code || '');
          break;

        case 'executable_code':
          // Legacy/alternative format
          result.code += (result.code ? '\n\n' : '') + (output.code || output.text || '');
          break;

        case 'code_execution_result':
          // The result of executing the code (stdout/stderr)
          // Structure: { call_id, is_error, result, type }
          result.output += (result.output ? '\n' : '') + (output.result || output.output || output.text || '');
          if (output.is_error) {
            result.error = output.result || 'Code execution error';
          }
          break;

        case 'text':
          // Model's text explanation
          result.text += (result.text ? '\n' : '') + (output.text || '');
          break;

        case 'image':
          // Generated image (e.g., matplotlib plot)
          if (output.data) {
            result.images.push({
              data: output.data,
              mime_type: output.mime_type || 'image/png',
            });
          }
          break;

        case 'inline_data':
          // Alternative image format
          if (output.data || output.inline_data) {
            const imgData = output.inline_data || output;
            result.images.push({
              data: imgData.data || '',
              mime_type: imgData.mime_type || imgData.mimeType || 'image/png',
            });
          }
          break;

        case 'thought':
          // Skip thought outputs (just model reasoning)
          break;

        default:
          console.log(`[Interpreter] Unknown output type: ${output.type}`, JSON.stringify(output).substring(0, 200));
          break;
      }
    }

    // If there's text but no code output, the model might have answered without executing
    if (!result.code && result.text) {
      result.output = result.text;
    }

    // Deduplicate images — plt.savefig() + plt.show() can produce duplicate outputs.
    // savefig and show may produce slightly different renders, so we use multiple strategies:
    //   1. Exact match on first 200 chars of base64 (catches identical images)
    //   2. Size within 5% AND middle-segment match — catches same chart rendered at slightly
    //      different quality (savefig vs show) while avoiding false positives on genuinely
    //      different images that happen to be similar in size
    if (result.images.length > 1) {
      const kept = [result.images[0]];
      for (let i = 1; i < result.images.length; i++) {
        const img = result.images[i];
        const imgData = img.data || '';
        const imgLen = imgData.length;
        let isDuplicate = false;
        for (const prev of kept) {
          const prevData = prev.data || '';
          const prevLen = prevData.length;
          // Strategy 1: exact prefix match
          if (imgData.substring(0, 200) === prevData.substring(0, 200)) {
            isDuplicate = true; break;
          }
          // Strategy 2: size within 5% AND middle 100-char segment matches
          if (imgLen > 0 && prevLen > 0) {
            const ratio = imgLen / prevLen;
            if (ratio > 0.95 && ratio < 1.05) {
              const midA = imgData.substring(Math.floor(imgLen * 0.4), Math.floor(imgLen * 0.4) + 100);
              const midB = prevData.substring(Math.floor(prevLen * 0.4), Math.floor(prevLen * 0.4) + 100);
              if (midA === midB) { isDuplicate = true; break; }
            }
          }
        }
        if (!isDuplicate) kept.push(img);
      }
      result.images = kept;
    }

    return result;
  }

  /**
   * Generate a short title from the prompt.
   */
  _generateTitle(prompt) {
    const words = prompt.trim().split(/\s+/).slice(0, 6);
    let title = words.join(' ');
    if (prompt.trim().split(/\s+/).length > 6) title += '...';
    return title;
  }
}
