/**
 * Apsara Dark — Backend Tools
 * 
 * Custom function declarations and handlers for Gemini Live API tool calling.
 * Each tool has a declaration (schema) and a handler function.
 */

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
];

// ─── Tool Handlers ──────────────────────────────────────────────────────────

/**
 * Execute a tool by name with given arguments.
 * Returns the result object to send back to Gemini.
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

    default:
      return { success: false, error: `Unknown tool: ${name}` };
  }
}

/**
 * Get the list of tool names available.
 */
export function getToolNames() {
  return TOOL_DECLARATIONS.map(t => t.name);
}
