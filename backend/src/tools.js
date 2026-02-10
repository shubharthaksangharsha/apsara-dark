/**
 * Apsara Dark — Backend Tools
 * 
 * Custom function declarations and handlers for Gemini Live API tool calling.
 * Each tool has a declaration (schema) and a handler function.
 */

import { CanvasService } from './canvas/canvas-service.js';
import { canvasStore } from './canvas/canvas-store.js';
import { InterpreterService } from './interpreter/interpreter-service.js';
import { interpreterStore } from './interpreter/interpreter-store.js';
import { GoogleGenAI } from '@google/genai';

// Canvas service instance — initialized lazily when API key is available
let canvasService = null;
let interpreterService = null;
let geminiApiKey = null;

export function initCanvasService(apiKey) {
  canvasService = new CanvasService(apiKey);
  interpreterService = new InterpreterService(apiKey);
  geminiApiKey = apiKey;
}

// ─── Tool Declarations (sent to Gemini so it knows what it can call) ────────

export const TOOL_DECLARATIONS = [
  {
    name: 'get_server_info',
    description: 'Returns current server information including date/time, timezone, server uptime, and Node.js version. Useful when the user asks for the current time, date, or server status.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    name: 'apsara_canvas',
    description: 'Creates a web application using HTML, CSS, and JavaScript (or React). Use this when the user asks you to create, build, make, or design an app, website, game, tool, dashboard, calculator, or any interactive web application. Apsara Canvas will generate the full code, validate it, auto-fix any errors, and serve it. The app will appear in "My Canvas" for the user to view.',
    parameters: {
      type: 'object',
      properties: {
        prompt: {
          type: 'string',
          description: 'A detailed description of the web app to create. Include features, layout, color preferences, and functionality details.',
        },
        title: {
          type: 'string',
          description: 'A short, descriptive title for the app (e.g., "Todo App", "Weather Dashboard"). If not provided, one will be auto-generated.',
        },
      },
      required: ['prompt'],
    },
  },
  {
    name: 'list_canvases',
    description: 'Lists all canvas apps created by Apsara Canvas. Returns their IDs, titles, descriptions, status, and creation dates. Use this when the user asks to see, list, or check their canvas apps, their creations, or what apps exist.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    name: 'get_canvas_detail',
    description: 'Gets the full detail of a canvas app by ID, including the HTML source code, the original prompt, generation log (steps, timestamps, attempts), and metadata. Use this when the user wants to see the code of a canvas app, understand how it was built, or review the generation history.',
    parameters: {
      type: 'object',
      properties: {
        canvas_id: {
          type: 'string',
          description: 'The ID of the canvas app to retrieve details for. Get this from list_canvases.',
        },
      },
      required: ['canvas_id'],
    },
  },
  {
    name: 'edit_canvas',
    description: 'Edits and improves an existing canvas app. Reads the current code and metadata, then regenerates the app based on the edit instructions. Use this when the user asks to edit, update, improve, refine, fix, change, or modify an existing canvas app. You MUST call list_canvases first to get the canvas_id, then call this tool with the canvas_id and the edit instructions.',
    parameters: {
      type: 'object',
      properties: {
        canvas_id: {
          type: 'string',
          description: 'The ID of the canvas app to edit. Get this from list_canvases.',
        },
        instructions: {
          type: 'string',
          description: 'Detailed instructions on what to change, improve, add, or fix in the app. Be specific about the desired changes.',
        },
      },
      required: ['canvas_id', 'instructions'],
    },
  },
  {
    name: 'run_code',
    description: 'Executes Python code using the Apsara Interpreter. Use this when the user asks to run, execute, compute, calculate, analyze data, create a chart/plot/visualization, or write Python code. The code is executed in a sandboxed Python environment with access to numpy, pandas, matplotlib, scipy, sympy, and PIL. The results (including any generated images) will be saved in "My Code" for the user to review.',
    parameters: {
      type: 'object',
      properties: {
        prompt: {
          type: 'string',
          description: 'A description of what code to write and execute. Be specific about the computation, analysis, or visualization needed.',
        },
        title: {
          type: 'string',
          description: 'A short title for this code session (e.g., "Fibonacci Sequence", "Stock Price Chart"). Auto-generated if not provided.',
        },
      },
      required: ['prompt'],
    },
  },
  {
    name: 'list_code_sessions',
    description: 'Lists all code execution sessions from the Apsara Interpreter. Returns their IDs, titles, status, and whether they have image outputs. Use this when the user asks to see their code history, past computations, or executed scripts.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    name: 'get_code_session',
    description: 'Gets the full detail of a code execution session by ID, including the Python code, output, any generated images, and execution log. Use this when the user wants to see the code, output, or images from a past session.',
    parameters: {
      type: 'object',
      properties: {
        session_id: {
          type: 'string',
          description: 'The ID of the code session to retrieve. Get this from list_code_sessions.',
        },
      },
      required: ['session_id'],
    },
  },
  {
    name: 'edit_code',
    description: 'Edits and re-runs an existing code session with new instructions. Reads the previous code, prompt, and output, then generates updated code based on the edit instructions and executes it. Use this when the user asks to edit, update, fix, modify, or improve code they previously ran. You MUST call list_code_sessions first to get the session_id, then call this tool with the session_id and the edit instructions.',
    parameters: {
      type: 'object',
      properties: {
        session_id: {
          type: 'string',
          description: 'The ID of the code session to edit. Get this from list_code_sessions.',
        },
        instructions: {
          type: 'string',
          description: 'Detailed instructions on what to change, fix, add, or improve in the code. Be specific about the desired changes.',
        },
      },
      required: ['session_id', 'instructions'],
    },
  },
  {
    name: 'url_context',
    description: 'Fetches and analyzes content from one or more URLs using the Gemini URL Context tool. Use this when the user asks you to read, summarize, analyze, compare, or extract information from web pages, articles, documentation, or any publicly accessible URL. Supports HTML pages, PDFs, images, JSON, and plain text. Can process up to 20 URLs per request.',
    parameters: {
      type: 'object',
      properties: {
        prompt: {
          type: 'string',
          description: 'The question or instruction about the URL content. Include the URL(s) in this prompt, e.g. "Summarize https://example.com" or "Compare these two articles: https://url1.com and https://url2.com".',
        },
        title: {
          type: 'string',
          description: 'A short title describing this URL context request (e.g., "Wikipedia Summary", "Recipe Comparison"). Auto-generated if not provided.',
        },
      },
      required: ['prompt'],
    },
  },
];

// ─── Tool Handlers ──────────────────────────────────────────────────────────

/**
 * Execute a tool by name with given arguments.
 * Returns the result object to send back to Gemini.
 * 
 * NOTE: Some tools (like apsara_canvas) are async. The ws-handler
 * checks isAsyncTool() and handles them differently.
 */
export function executeTool(name, args = {}) {
  switch (name) {
    case 'get_server_info': {
      const now = new Date();
      const uptimeSeconds = process.uptime();
      const hours = Math.floor(uptimeSeconds / 3600);
      const minutes = Math.floor((uptimeSeconds % 3600) / 60);
      const seconds = Math.floor(uptimeSeconds % 60);

      return {
        success: true,
        server_time: now.toISOString(),
        local_time: now.toLocaleString(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        uptime: `${hours}h ${minutes}m ${seconds}s`,
        node_version: process.version,
        platform: process.platform,
      };
    }

    case 'list_canvases': {
      const summaries = canvasStore.getSummaries();
      return {
        success: true,
        count: summaries.length,
        apps: summaries,
        message: summaries.length === 0
          ? 'No canvas apps have been created yet.'
          : `Found ${summaries.length} canvas app(s).`,
      };
    }

    case 'get_canvas_detail': {
      const canvasId = args.canvas_id;
      if (!canvasId) {
        return { success: false, error: 'canvas_id is required' };
      }
      const detail = canvasStore.getDetail(canvasId);
      if (!detail) {
        return { success: false, error: `Canvas not found: ${canvasId}` };
      }
      // Truncate HTML if very long to avoid blowing up Gemini context
      const truncatedHtml = detail.html && detail.html.length > 8000
        ? detail.html.substring(0, 8000) + `\n\n... [truncated — full code is ${detail.html_length} chars]`
        : detail.html;
      return {
        success: true,
        app: {
          ...detail,
          html: truncatedHtml,
        },
      };
    }

    case 'list_code_sessions': {
      const summaries = interpreterStore.getSummaries();
      return {
        success: true,
        count: summaries.length,
        sessions: summaries,
        message: summaries.length === 0
          ? 'No code sessions have been created yet.'
          : `Found ${summaries.length} code session(s).`,
      };
    }

    case 'get_code_session': {
      const sessionId = args.session_id;
      if (!sessionId) {
        return { success: false, error: 'session_id is required' };
      }
      const detail = interpreterStore.getDetail(sessionId);
      if (!detail) {
        return { success: false, error: `Code session not found: ${sessionId}` };
      }
      // Truncate code/output if very long
      const truncatedCode = detail.code && detail.code.length > 8000
        ? detail.code.substring(0, 8000) + `\n\n... [truncated — full code is ${detail.code.length} chars]`
        : detail.code;
      const truncatedOutput = detail.output && detail.output.length > 4000
        ? detail.output.substring(0, 4000) + `\n\n... [truncated — full output is ${detail.output.length} chars]`
        : detail.output;
      return {
        success: true,
        session: {
          ...detail,
          code: truncatedCode,
          output: truncatedOutput,
          image_count: detail.images ? detail.images.length : 0,
          // Don't send full base64 images through Gemini — just URLs
          images: (detail.images || []).map((img, i) => ({
            index: i,
            mime_type: img.mime_type,
            url: `/api/interpreter/${detail.id}/images/${i}`,
          })),
        },
      };
    }

    default:
      return { success: false, error: `Unknown tool: ${name}` };
  }
}

/**
 * Execute the apsara_canvas tool asynchronously.
 * This is called separately because it takes time to generate the app.
 * 
 * @param {Object} args - { prompt, title }
 * @param {Function} onProgress - Callback for progress updates sent to client
 * @param {Object} interactionConfig - Config overrides (model, temperature, etc.) from session
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeCanvasTool(args = {}, onProgress, interactionConfig = {}) {
  if (!canvasService) {
    return { success: false, error: 'Canvas service not initialized' };
  }

  const { prompt, title } = args;
  if (!prompt) {
    return { success: false, error: 'prompt is required' };
  }

  try {
    const app = await canvasService.generateApp({
      prompt,
      title,
      config: interactionConfig,
      onProgress: (status, message) => {
        console.log(`[Canvas Tool] ${status}: ${message}`);
        onProgress?.(status, message);
      },
    });

    return {
      success: true,
      canvas_id: app.id,
      title: app.title,
      status: app.status,
      error: app.error || null,
      message: app.status === 'ready'
        ? `I've created "${app.title}" for you! You can find it in My Canvas.`
        : `There was an issue creating the app: ${app.error}`,
      render_url: `/api/canvas/${app.id}/render`,
    };
  } catch (error) {
    console.error('[Canvas Tool] Error:', error.message);
    return { success: false, error: error.message };
  }
}

/**
 * Execute the edit_canvas tool asynchronously.
 * Reads existing canvas code and applies edit instructions.
 * 
 * @param {Object} args - { canvas_id, instructions }
 * @param {Function} onProgress - Callback for progress updates sent to client
 * @param {Object} interactionConfig - Config overrides (model, temperature, etc.) from session
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeCanvasEditTool(args = {}, onProgress, interactionConfig = {}) {
  if (!canvasService) {
    return { success: false, error: 'Canvas service not initialized' };
  }

  const { canvas_id, instructions } = args;
  if (!canvas_id) {
    return { success: false, error: 'canvas_id is required' };
  }
  if (!instructions) {
    return { success: false, error: 'instructions is required' };
  }

  try {
    const app = await canvasService.editApp({
      canvasId: canvas_id,
      instructions,
      config: interactionConfig,
      onProgress: (status, message) => {
        console.log(`[Canvas Edit Tool] ${status}: ${message}`);
        onProgress?.(status, message);
      },
    });

    return {
      success: true,
      canvas_id: app.id,
      title: app.title,
      status: app.status,
      error: app.error || null,
      message: app.status === 'ready'
        ? `I've updated "${app.title}" for you! Check it in My Canvas.`
        : `There was an issue editing the app: ${app.error}`,
      render_url: `/api/canvas/${app.id}/render`,
    };
  } catch (error) {
    console.error('[Canvas Edit Tool] Error:', error.message);
    return { success: false, error: error.message };
  }
}

/**
 * Check if a tool requires async execution (takes significant time).
 */
export function isLongRunningTool(name) {
  return name === 'apsara_canvas' || name === 'edit_canvas' || name === 'run_code' || name === 'edit_code' || name === 'url_context';
}

/**
 * Execute the run_code tool asynchronously.
 * Uses the Interpreter service to run Python code via Interactions API.
 * 
 * @param {Object} args - { prompt, title }
 * @param {Function} onProgress - Callback for progress updates sent to client
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeInterpreterTool(args = {}, onProgress, interactionConfig = {}) {
  if (!interpreterService) {
    return { success: false, error: 'Interpreter service not initialized' };
  }

  const { prompt, title } = args;
  if (!prompt) {
    return { success: false, error: 'prompt is required' };
  }

  try {
    const session = await interpreterService.runCode({
      prompt,
      title,
      config: interactionConfig,
      onProgress: (status, message) => {
        console.log(`[Interpreter Tool] ${status}: ${message}`);
        onProgress?.(status, message);
      },
    });

    // Build image URLs for client
    const imageUrls = (session.images || []).map((img, i) => ({
      index: i,
      mimeType: img.mime_type,
      url: `/api/interpreter/${session.id}/images/${i}`,
    }));

    return {
      success: true,
      session_id: session.id,
      title: session.title,
      status: session.status,
      code: session.code || '',
      output: session.output || '',
      image_count: imageUrls.length,
      images: imageUrls,
      error: session.error || null,
      message: session.status === 'completed'
        ? `Code executed successfully! ${imageUrls.length > 0 ? `Generated ${imageUrls.length} image(s).` : ''} Check "My Code" for details.`
        : `Code execution had an issue: ${session.error}`,
    };
  } catch (error) {
    console.error('[Interpreter Tool] Error:', error.message);
    return { success: false, error: error.message };
  }
}

/**
 * Get the list of tool names available.
 */
export function getToolNames() {
  return TOOL_DECLARATIONS.map(t => t.name);
}

/**
 * Execute the edit_code tool asynchronously.
 * Reads the existing code session and re-runs with edit instructions.
 * 
 * @param {Object} args - { session_id, instructions }
 * @param {Function} onProgress - Callback for progress updates
 * @param {Object} interactionConfig - Config overrides from session
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeCodeEditTool(args = {}, onProgress, interactionConfig = {}) {
  if (!interpreterService) {
    return { success: false, error: 'Interpreter service not initialized' };
  }

  const { session_id, instructions } = args;
  if (!session_id) {
    return { success: false, error: 'session_id is required' };
  }
  if (!instructions) {
    return { success: false, error: 'instructions is required' };
  }

  // Verify session exists
  const existing = interpreterStore.getDetail(session_id);
  if (!existing) {
    return { success: false, error: `Code session not found: ${session_id}` };
  }

  try {
    // Use editCode — updates the SAME session instead of creating a new one
    const session = await interpreterService.editCode({
      sessionId: session_id,
      instructions,
      config: interactionConfig,
      onProgress: (status, message) => {
        console.log(`[Code Edit Tool] ${status}: ${message}`);
        onProgress?.(status, message);
      },
    });

    // Build image URLs for client
    const imageUrls = (session.images || []).map((img, i) => ({
      index: i,
      mimeType: img.mime_type,
      url: `/api/interpreter/${session.id}/images/${i}`,
    }));

    return {
      success: true,
      session_id: session.id,           // Same ID as the original session
      original_session_id: session_id,
      title: session.title,
      status: session.status,
      code: session.code || '',
      output: session.output || '',
      image_count: imageUrls.length,
      images: imageUrls,
      error: session.error || null,
      message: session.status === 'completed'
        ? `Code updated and executed successfully! ${imageUrls.length > 0 ? `Generated ${imageUrls.length} image(s).` : ''} Check "My Code" for details.`
        : `Code edit had an issue: ${session.error}`,
    };
  } catch (error) {
    console.error('[Code Edit Tool] Error:', error.message);
    return { success: false, error: error.message };
  }
}

/**
 * Execute the url_context tool using the Interactions API.
 * Fetches and analyzes content from URLs.
 * 
 * @param {Object} args - { prompt, title }
 * @param {Function} onProgress - Callback for progress updates sent to client
 * @param {Object} interactionConfig - Config overrides (model, temperature, etc.) from session
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeUrlContextTool(args = {}, onProgress, interactionConfig = {}) {
  if (!geminiApiKey) {
    return { success: false, error: 'Gemini API key not available' };
  }

  const { prompt, title } = args;
  if (!prompt) {
    return { success: false, error: 'prompt is required' };
  }

  const autoTitle = title || prompt.substring(0, 60).replace(/\n/g, ' ').trim() + '...';

  onProgress?.('running', `Fetching URL content for "${autoTitle}"...`);

  try {
    const client = new GoogleGenAI({ apiKey: geminiApiKey });

    // Merge interaction config with defaults
    const model = interactionConfig.model || 'gemini-2.5-flash';
    const temperature = interactionConfig.temperature !== undefined ? interactionConfig.temperature : 0.7;
    const maxOutputTokens = interactionConfig.max_output_tokens || 65536;
    const thinkingLevel = interactionConfig.thinking_level || 'high';
    const thinkingSummaries = interactionConfig.thinking_summaries || 'auto';

    onProgress?.('fetching', `Using ${model} with URL context tool...`);

    // Build the Interactions API request with url_context built-in tool
    const request = {
      model,
      input: prompt,
      tools: [{ type: 'url_context' }],
      system_instruction: `You are Apsara, a helpful AI assistant. When analyzing URL content, provide clear, detailed, and well-structured responses. Use markdown formatting for readability. Extract key information, summarize accurately, and cite sources when relevant.`,
      generation_config: {
        temperature,
        max_output_tokens: maxOutputTokens,
        thinking_level: thinkingLevel,
        thinking_summaries: thinkingSummaries,
      },
    };

    onProgress?.('processing', `Fetching and analyzing URL content...`);

    const interaction = await client.interactions.create(request);

    // Extract results
    let resultText = '';
    let urlMetadata = [];
    let thoughts = [];

    if (interaction.outputs) {
      for (const output of interaction.outputs) {
        if (output.type === 'text') {
          resultText += output.text;
        } else if (output.type === 'thought') {
          if (output.summary) {
            thoughts.push(output.summary);
          }
        }
      }
    }

    // Extract URL context metadata if available
    // The metadata is typically in the candidates/outputs
    if (interaction.candidates && interaction.candidates[0]) {
      const candidate = interaction.candidates[0];
      if (candidate.urlContextMetadata?.urlMetadata) {
        urlMetadata = candidate.urlContextMetadata.urlMetadata.map(m => ({
          url: m.retrievedUrl || m.retrieved_url,
          status: m.urlRetrievalStatus || m.url_retrieval_status,
        }));
      }
    }

    onProgress?.('completed', `URL context analysis complete`);

    return {
      success: true,
      title: autoTitle,
      text: resultText,
      url_metadata: urlMetadata,
      urls_fetched: urlMetadata.length,
      thoughts: thoughts.length > 0 ? thoughts : undefined,
      usage: interaction.usage || null,
      message: resultText
        ? `Successfully analyzed URL content. ${urlMetadata.length > 0 ? `Fetched ${urlMetadata.length} URL(s).` : ''}`
        : 'No content could be extracted from the URL(s).',
    };
  } catch (error) {
    console.error('[URL Context Tool] Error:', error.message);
    onProgress?.('error', `URL context failed: ${error.message}`);
    return { success: false, error: error.message };
  }
}
