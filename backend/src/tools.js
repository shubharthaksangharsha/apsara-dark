/**
 * Apsara Dark — Backend Tools
 * 
 * Custom function declarations and handlers for Gemini Live API tool calling.
 * Each tool has a declaration (schema) and a handler function.
 */

import { CanvasService } from './canvas/canvas-service.js';
import { canvasStore } from './canvas/canvas-store.js';

// Canvas service instance — initialized lazily when API key is available
let canvasService = null;

export function initCanvasService(apiKey) {
  canvasService = new CanvasService(apiKey);
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
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeCanvasTool(args = {}, onProgress) {
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
 * @returns {Promise<Object>} Result to send back to Gemini
 */
export async function executeCanvasEditTool(args = {}, onProgress) {
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
  return name === 'apsara_canvas' || name === 'edit_canvas';
}

/**
 * Get the list of tool names available.
 */
export function getToolNames() {
  return TOOL_DECLARATIONS.map(t => t.name);
}
