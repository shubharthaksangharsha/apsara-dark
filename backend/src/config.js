import { TOOL_DECLARATIONS } from './tools.js';

/**
 * Apsara Dark — Gemini Live Backend
 * 
 * Default session configuration for Gemini Live API.
 * All values here can be overridden by the client at connection time.
 */

// Available voices for native audio output models (TTS voices)
export const AVAILABLE_VOICES = [
  'Puck',
  'Charon',
  'Kore',
  'Fenrir',
  'Aoede',
  'Leda',
  'Orus',
  'Zephyr',
];

// Available models
export const AVAILABLE_MODELS = [
  'gemini-2.5-flash-native-audio-preview-12-2025',
];

// Response modality — AUDIO only (TEXT not supported by native audio model)
export const MODALITIES = {
  AUDIO: 'AUDIO',
};

// Function response scheduling options
export const FUNCTION_RESPONSE_SCHEDULING = {
  INTERRUPT: 'INTERRUPT',
  WHEN_IDLE: 'WHEN_IDLE',
  SILENT: 'SILENT',
};

// Default session config — applied when client connects without overrides
export const DEFAULT_SESSION_CONFIG = {
  // Model to use
  model: 'gemini-2.5-flash-native-audio-preview-12-2025',

  // System instruction for Apsara personality
  systemInstruction: `You are Apsara, a helpful, friendly, and intelligent AI assistant created by Shubharthak. 
You speak naturally and conversationally. You are warm, concise, and to the point. 
When speaking, you keep responses short unless asked for detail.
You are assisting Shubharthak with his day-to-day tasks, ideas, and conversations.`,

  // Response modalities — AUDIO for voice, TEXT for text-only
  responseModalities: ['AUDIO'],

  // Voice configuration
  voice: 'Kore',

  // Temperature for generation (0.0 - 2.0)
  temperature: 0.7,

  // Enable context window compression for unlimited session length
  contextWindowCompression: {
    slidingWindow: {},
  },

  // Enable session resumption for auto-reconnect (can be disabled by client)
  sessionResumption: null,

  // Enable affective dialog (emotion-aware responses)
  enableAffectiveDialog: true,

  // Enable proactive audio (model decides when to respond)
  proactiveAudio: true,

  // Thinking configuration (dynamic thinking enabled by default)
  // Set thinkingBudget to 0 to disable, or a positive number to guide it
  thinkingBudget: null, // null = default dynamic thinking

  // Enable thought summaries
  includeThoughts: false,

  // Enable input audio transcription
  inputAudioTranscription: true,

  // Enable output audio transcription
  outputAudioTranscription: true,

  // Tools configuration
  tools: {
    googleSearch: false,
    functionCalling: true,
  },

  // Per-tool async/sync modes: { "tool_name": true/false }
  // true = NON_BLOCKING (async), false/missing = BLOCKING (sync)
  toolAsyncModes: {},

  // Function declarations (custom tools the model can call)
  functionDeclarations: TOOL_DECLARATIONS,
};

// Audio format constants
export const AUDIO = {
  INPUT_SAMPLE_RATE: 16000,
  INPUT_CHANNELS: 1,
  INPUT_BIT_DEPTH: 16,
  INPUT_MIME_TYPE: 'audio/pcm;rate=16000',
  OUTPUT_SAMPLE_RATE: 24000,
  OUTPUT_CHANNELS: 1,
  OUTPUT_BIT_DEPTH: 16,
};
