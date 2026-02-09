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
  {
    name: 'calculate',
    description: 'Performs a basic arithmetic calculation. Supports addition, subtraction, multiplication, and division. Use this when the user asks for a math calculation.',
    parameters: {
      type: 'object',
      properties: {
        expression: {
          type: 'string',
          description: 'A simple arithmetic expression to evaluate, e.g. "2 + 3 * 4" or "100 / 7". Only supports +, -, *, / and parentheses.',
        },
      },
      required: ['expression'],
    },
  },
  {
    name: 'get_random_fact',
    description: 'Returns a random fun fact. Use this when the user asks for a fun fact, trivia, or something interesting.',
    parameters: {
      type: 'object',
      properties: {},
      required: [],
    },
  },
];

// ─── Tool Handlers ──────────────────────────────────────────────────────────

const FACTS = [
  'Honey never spoils. Archaeologists have found 3,000-year-old honey in Egyptian tombs that was still edible.',
  'Octopuses have three hearts and blue blood.',
  'A day on Venus is longer than its year.',
  'Bananas are berries, but strawberries are not.',
  'The Eiffel Tower can grow up to 6 inches taller during the summer due to thermal expansion.',
  'There are more possible iterations of a game of chess than there are atoms in the observable universe.',
  'A group of flamingos is called a "flamboyance".',
  'The shortest war in history lasted 38 to 45 minutes (between Britain and Zanzibar in 1896).',
  'Wombat poop is cube-shaped.',
  'The inventor of the Pringles can is buried in one.',
];

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

    case 'calculate': {
      const expression = args.expression || '';
      try {
        // Safe evaluation: only allow numbers, operators, parentheses, spaces, and decimal points
        if (!/^[\d\s+\-*/().]+$/.test(expression)) {
          return { success: false, error: 'Invalid expression. Only numbers and +, -, *, /, () are allowed.' };
        }
        // Use Function constructor for safe math evaluation
        const result = new Function(`return (${expression})`)();
        if (!isFinite(result)) {
          return { success: false, error: 'Result is not a finite number (division by zero?).' };
        }
        return { success: true, expression, result: Number(result.toFixed(10)) };
      } catch (e) {
        return { success: false, error: `Calculation error: ${e.message}` };
      }
    }

    case 'get_random_fact': {
      const fact = FACTS[Math.floor(Math.random() * FACTS.length)];
      return { success: true, fact };
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
