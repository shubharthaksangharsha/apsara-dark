/**
 * Apsara Dark — Interactions API Backend
 * 
 * Configuration for the Interactions API endpoints.
 * Defines available models, default generation config, tool definitions, etc.
 */

// ─── Supported Models for Interactions API ──────────────────────────────────
export const INTERACTIONS_MODELS = [
  { id: 'gemini-2.5-pro', name: 'Gemini 2.5 Pro', type: 'model' },
  { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash', type: 'model' },
  { id: 'gemini-2.5-flash-lite', name: 'Gemini 2.5 Flash Lite', type: 'model' },
  { id: 'gemini-3-pro-preview', name: 'Gemini 3 Pro Preview', type: 'model' },
  { id: 'gemini-3-flash-preview', name: 'Gemini 3 Flash Preview', type: 'model' },
];

export const INTERACTIONS_AGENTS = [
  { id: 'deep-research-pro-preview-12-2025', name: 'Deep Research Preview', type: 'agent' },
];

// Default model for text interactions
export const DEFAULT_MODEL = 'gemini-3-flash-preview';

// ─── Default Generation Config ──────────────────────────────────────────────
export const DEFAULT_GENERATION_CONFIG = {
  temperature: 0.7,
  max_output_tokens: 8192,
  thinking_level: 'high',       // high | medium | low | minimal
  thinking_summaries: 'auto',   // auto | none
};

// ─── System Instruction ─────────────────────────────────────────────────────
export const DEFAULT_SYSTEM_INSTRUCTION = `You are Apsara, a helpful, friendly, and intelligent AI assistant created by Shubharthak. 
You are warm, concise, and to the point. When answering, you keep responses short unless asked for detail.
You format your responses in clean markdown when appropriate.
You are assisting Shubharthak with his day-to-day tasks, ideas, and conversations.`;

// ─── Built-in Tool Types ────────────────────────────────────────────────────
export const BUILTIN_TOOLS = {
  GOOGLE_SEARCH: { type: 'google_search' },
  CODE_EXECUTION: { type: 'code_execution' },
  URL_CONTEXT: { type: 'url_context' },
};

// ─── Thinking Levels ────────────────────────────────────────────────────────
export const THINKING_LEVELS = ['minimal', 'low', 'medium', 'high'];
