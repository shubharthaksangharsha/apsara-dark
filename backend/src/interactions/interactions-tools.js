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
];

// ─── Function Tool Handlers ─────────────────────────────────────────────────

/**
 * Execute a function tool by name with given arguments.
 * Returns the result string to send back to the model as function_result.
 */
export function executeFunction(name, args = {}) {
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
