/**
 * Apsara Dark — Interactions API Backend
 * 
 * Custom function tool definitions for the Interactions API.
 * These are sent as tools to the model so it can call them.
 * 
 * Format matches the Interactions API tool schema:
 *   { type: 'function', name, description, parameters }
 */

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
