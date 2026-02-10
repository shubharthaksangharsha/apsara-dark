/**
 * Apsara Dark — Interactions API Backend
 * 
 * Custom function tool definitions for the Interactions API.
 * These are sent as tools to the model so it can call them.
 * 
 * Format matches the Interactions API tool schema:
 *   { type: 'function', name, description, parameters }
 */

import { canvasStore } from '../canvas/canvas-store.js';
import { CanvasService, CANVAS_DEFAULTS } from '../canvas/canvas-service.js';
import { InterpreterService } from '../interpreter/interpreter-service.js';
import { interpreterStore } from '../interpreter/interpreter-store.js';

// Canvas service instance — initialized lazily
let canvasService = null;
let interpreterService = null;

function getCanvasService() {
  if (!canvasService) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (apiKey) {
      canvasService = new CanvasService(apiKey);
    }
  }
  return canvasService;
}

function getInterpreterService() {
  if (!interpreterService) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (apiKey) {
      interpreterService = new InterpreterService(apiKey);
    }
  }
  return interpreterService;
}

// ─── Function Tool Declarations ─────────────────────────────────────────────

export const FUNCTION_TOOLS = [
  {
    type: 'function',
    name: 'get_current_time',
    description: 'Returns the current date, time, timezone, and server uptime. Useful when the user asks for the current time or date.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    type: 'function',
    name: 'get_weather',
    description: 'Gets the current weather for a given location. Returns temperature, conditions, humidity, and wind.',
    parameters: {
      type: 'object',
      properties: {
        location: {
          type: 'string',
          description: 'The city name, e.g. "Paris", "New York, NY"',
        },
      },
      required: ['location'],
    },
  },
  {
    type: 'function',
    name: 'list_canvases',
    description: 'Lists all canvas apps created by Apsara Canvas. Returns their IDs, titles, descriptions, status, and creation dates. Use this when the user asks to see their canvas apps, list their creations, or check what apps exist.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    type: 'function',
    name: 'get_canvas_detail',
    description: 'Gets the full detail of a canvas app by ID, including the HTML source code, the original prompt, generation log (steps, timestamps, attempts), and metadata. Use this when the user wants to see the code of a canvas app, understand how it was built, or review the generation history.',
    parameters: {
      type: 'object',
      properties: {
        canvas_id: {
          type: 'string',
          description: 'The ID of the canvas app to retrieve. Get this from list_canvases.',
        },
      },
      required: ['canvas_id'],
    },
  },
  {
    type: 'function',
    name: 'edit_canvas',
    description: 'Edits and improves an existing canvas app. Reads the current code and metadata, then regenerates the app based on the edit instructions. Use this when the user asks to edit, update, improve, refine, fix, or modify an existing canvas app.',
    parameters: {
      type: 'object',
      properties: {
        canvas_id: {
          type: 'string',
          description: 'The ID of the canvas app to edit. Get this from list_canvases.',
        },
        instructions: {
          type: 'string',
          description: 'Detailed instructions on what to change, improve, add, or fix in the app.',
        },
      },
      required: ['canvas_id', 'instructions'],
    },
  },
  {
    type: 'function',
    name: 'run_code',
    description: 'Executes Python code using the Apsara Interpreter. Use this when the user asks to run Python code, compute, calculate, analyze data, or create visualizations.',
    parameters: {
      type: 'object',
      properties: {
        prompt: {
          type: 'string',
          description: 'A description of what code to write and execute.',
        },
        title: {
          type: 'string',
          description: 'A short title for this code session.',
        },
      },
      required: ['prompt'],
    },
  },
  {
    type: 'function',
    name: 'list_code_sessions',
    description: 'Lists all code execution sessions from the Apsara Interpreter. Returns their IDs, titles, status, and whether they have image outputs.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
  {
    type: 'function',
    name: 'get_code_session',
    description: 'Gets the full detail of a code execution session by ID, including the Python code, output, and any generated images.',
    parameters: {
      type: 'object',
      properties: {
        session_id: {
          type: 'string',
          description: 'The ID of the code session to retrieve.',
        },
      },
      required: ['session_id'],
    },
  },
];

// ─── Function Tool Handlers ─────────────────────────────────────────────────

/**
 * Execute a function tool by name with given arguments.
 * Returns the result string to send back to the model as function_result.
 */
export async function executeFunction(name, args = {}) {
  switch (name) {
    case 'get_current_time': {
      const now = new Date();
      const uptimeSeconds = process.uptime();
      const hours = Math.floor(uptimeSeconds / 3600);
      const minutes = Math.floor((uptimeSeconds % 3600) / 60);
      const seconds = Math.floor(uptimeSeconds % 60);
      return JSON.stringify({
        success: true,
        utc_time: now.toISOString(),
        local_time: now.toLocaleString(),
        timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        uptime: `${hours}h ${minutes}m ${seconds}s`,
      });
    }

    case 'get_weather': {
      // Mock weather — in production, call a real weather API
      const location = args.location || 'Unknown';
      return JSON.stringify({
        success: true,
        location,
        temperature: '22°C',
        conditions: 'Partly cloudy',
        humidity: '65%',
        wind: '12 km/h NW',
        note: 'This is mock data. Connect to a real weather API for production.',
      });
    }

    case 'list_canvases': {
      const summaries = canvasStore.getSummaries();
      return JSON.stringify({
        success: true,
        count: summaries.length,
        apps: summaries,
      });
    }

    case 'get_canvas_detail': {
      const canvasId = args.canvas_id;
      if (!canvasId) {
        return JSON.stringify({ success: false, error: 'canvas_id is required' });
      }
      const detail = canvasStore.getDetail(canvasId);
      if (!detail) {
        return JSON.stringify({ success: false, error: `Canvas not found: ${canvasId}` });
      }
      // Truncate HTML if very long to avoid blowing up context
      const truncatedHtml = detail.html && detail.html.length > 8000
        ? detail.html.substring(0, 8000) + `\n\n... [truncated — full code is ${detail.html_length} chars]`
        : detail.html;
      return JSON.stringify({
        success: true,
        app: {
          ...detail,
          html: truncatedHtml,
        },
      });
    }

    case 'edit_canvas': {
      const editCanvasId = args.canvas_id;
      const instructions = args.instructions;
      if (!editCanvasId) {
        return JSON.stringify({ success: false, error: 'canvas_id is required' });
      }
      if (!instructions) {
        return JSON.stringify({ success: false, error: 'instructions is required' });
      }
      const service = getCanvasService();
      if (!service) {
        return JSON.stringify({ success: false, error: 'Canvas service not available' });
      }
      try {
        const app = await service.editApp({ canvasId: editCanvasId, instructions });
        return JSON.stringify({
          success: true,
          canvas_id: app.id,
          title: app.title,
          status: app.status,
          message: app.status === 'ready'
            ? `Updated "${app.title}" successfully!`
            : `Edit had issues: ${app.error}`,
        });
      } catch (err) {
        return JSON.stringify({ success: false, error: err.message });
      }
    }

    case 'run_code': {
      const codePrompt = args.prompt;
      const codeTitle = args.title;
      if (!codePrompt) {
        return JSON.stringify({ success: false, error: 'prompt is required' });
      }
      const interpService = getInterpreterService();
      if (!interpService) {
        return JSON.stringify({ success: false, error: 'Interpreter service not available' });
      }
      try {
        const session = await interpService.runCode({ prompt: codePrompt, title: codeTitle });
        return JSON.stringify({
          success: true,
          session_id: session.id,
          title: session.title,
          status: session.status,
          code: session.code,
          output: session.output,
          image_count: session.images ? session.images.length : 0,
          message: session.status === 'completed'
            ? `Code executed successfully!`
            : `Code execution had issues: ${session.error}`,
        });
      } catch (err) {
        return JSON.stringify({ success: false, error: err.message });
      }
    }

    case 'list_code_sessions': {
      const summaries = interpreterStore.getSummaries();
      return JSON.stringify({
        success: true,
        count: summaries.length,
        sessions: summaries,
      });
    }

    case 'get_code_session': {
      const sessionId = args.session_id;
      if (!sessionId) {
        return JSON.stringify({ success: false, error: 'session_id is required' });
      }
      const detail = interpreterStore.getDetail(sessionId);
      if (!detail) {
        return JSON.stringify({ success: false, error: `Code session not found: ${sessionId}` });
      }
      return JSON.stringify({
        success: true,
        session: {
          ...detail,
          image_count: detail.images ? detail.images.length : 0,
          images: (detail.images || []).map((img, i) => ({
            index: i,
            mime_type: img.mime_type,
          })),
        },
      });
    }

    default:
      return JSON.stringify({ success: false, error: `Unknown function: ${name}` });
  }
}

/**
 * Get list of available function tool names.
 */
export function getFunctionNames() {
  return FUNCTION_TOOLS.map(t => t.name);
}
