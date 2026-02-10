/**
 * Apsara Dark — Interactions API Backend
 * 
 * Barrel export for the interactions module.
 */

export { InteractionsService } from './interactions-service.js';
export { createInteractionsRouter } from './interactions-router.js';
export { handleInteractionsWebSocket } from './interactions-ws-handler.js';
export {
  INTERACTIONS_MODELS,
  INTERACTIONS_AGENTS,
  DEFAULT_MODEL,
  DEFAULT_GENERATION_CONFIG,
  DEFAULT_SYSTEM_INSTRUCTION,
  BUILTIN_TOOLS,
  THINKING_LEVELS,
} from './interactions-config.js';
export { FUNCTION_TOOLS, executeFunction, getFunctionNames } from './interactions-tools.js';
