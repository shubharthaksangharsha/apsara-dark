/**
 * Apsara Dark — Gemini Live Backend
 * 
 * Main server entry point.
 * - Express HTTP server for health checks & config endpoint
 * - WebSocket server for real-time Gemini Live API relay
 * 
 * Single-user design — no auth layer, just API key on the server.
 */

import 'dotenv/config';
import express from 'express';
import { createServer } from 'http';
import { WebSocketServer } from 'ws';
import { handleWebSocket } from './ws-handler.js';
import { AVAILABLE_VOICES, AVAILABLE_MODELS, DEFAULT_SESSION_CONFIG, AUDIO } from './config.js';
import { TOOL_DECLARATIONS, initCanvasService } from './tools.js';
import { createInteractionsRouter } from './interactions/interactions-router.js';
import { handleInteractionsWebSocket } from './interactions/interactions-ws-handler.js';
import { DEFAULT_MODEL } from './interactions/interactions-config.js';
import { createCanvasRouter } from './canvas/canvas-router.js';

// --- Environment ---
const PORT = parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY || GEMINI_API_KEY === 'your_gemini_api_key_here') {
  console.error('❌ GEMINI_API_KEY is not set. Copy .env.example to .env and add your key.');
  process.exit(1);
}

// Initialize Canvas service with API key
initCanvasService(GEMINI_API_KEY);

// --- Express app (health & config endpoints) ---
const app = express();
app.use(express.json());

// Health check
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    service: 'apsara-dark-backend',
    timestamp: new Date().toISOString(),
  });
});

// Config endpoint — returns available options for the Live Settings panel
app.get('/config', (req, res) => {
  res.json({
    voices: AVAILABLE_VOICES,
    models: AVAILABLE_MODELS,
    defaults: {
      model: DEFAULT_SESSION_CONFIG.model,
      voice: DEFAULT_SESSION_CONFIG.voice,
      temperature: DEFAULT_SESSION_CONFIG.temperature,
      responseModalities: DEFAULT_SESSION_CONFIG.responseModalities,
      enableAffectiveDialog: DEFAULT_SESSION_CONFIG.enableAffectiveDialog,
      proactiveAudio: DEFAULT_SESSION_CONFIG.proactiveAudio,
      thinkingBudget: DEFAULT_SESSION_CONFIG.thinkingBudget,
      includeThoughts: DEFAULT_SESSION_CONFIG.includeThoughts,
      inputAudioTranscription: DEFAULT_SESSION_CONFIG.inputAudioTranscription,
      outputAudioTranscription: DEFAULT_SESSION_CONFIG.outputAudioTranscription,
      contextWindowCompression: !!DEFAULT_SESSION_CONFIG.contextWindowCompression,
      googleSearch: DEFAULT_SESSION_CONFIG.tools.googleSearch,
      functionCalling: DEFAULT_SESSION_CONFIG.tools.functionCalling,
      systemInstruction: DEFAULT_SESSION_CONFIG.systemInstruction,
    },
    audio: AUDIO,
    tools: TOOL_DECLARATIONS.map(t => ({ name: t.name, description: t.description })),
  });
});

// --- Interactions API (text/chat endpoints) ---
app.use('/api/interactions', createInteractionsRouter(GEMINI_API_KEY));

// --- Canvas API (app generation endpoints) ---
app.use('/api/canvas', createCanvasRouter(GEMINI_API_KEY));

// --- HTTP + WebSocket server ---
const server = createServer(app);

// WebSocket for Live API (real-time audio/video)
const wssLive = new WebSocketServer({
  server,
  path: '/live',
  maxPayload: 10 * 1024 * 1024, // 10 MB max per message (for video frames)
});

wssLive.on('connection', (ws, req) => {
  const clientIP = req.socket.remoteAddress;
  console.log(`[Server] New Live WebSocket connection from ${clientIP}`);
  handleWebSocket(ws, GEMINI_API_KEY);
});

// WebSocket for Interactions API (text chat)
const wssInteractions = new WebSocketServer({
  server,
  path: '/chat',
  maxPayload: 5 * 1024 * 1024, // 5 MB max per message
});

wssInteractions.on('connection', (ws, req) => {
  const clientIP = req.socket.remoteAddress;
  console.log(`[Server] New Interactions WebSocket connection from ${clientIP}`);
  handleInteractionsWebSocket(ws, GEMINI_API_KEY);
});

// --- Start ---
server.listen(PORT, HOST, () => {
  console.log('');
  console.log('  ╔═══════════════════════════════════════════════════╗');
  console.log('  ║         🌙 Apsara Dark — Backend Server          ║');
  console.log('  ╠═══════════════════════════════════════════════════╣');
  console.log(`  ║  HTTP:     http://${HOST}:${PORT}                    ║`);
  console.log('  ║                                                   ║');
  console.log('  ║  Live API (audio/video):                          ║');
  console.log(`  ║    WS:     ws://${HOST}:${PORT}/live                  ║`);
  console.log('  ║    Config: GET /config                            ║');
  console.log('  ║                                                   ║');
  console.log('  ║  Interactions API (text/chat):                    ║');
  console.log(`  ║    REST:   POST /api/interactions                 ║`);
  console.log(`  ║    Stream: POST /api/interactions/stream          ║`);
  console.log(`  ║    WS:     ws://${HOST}:${PORT}/chat                  ║`);
  console.log('  ║    Config: GET /api/interactions/config           ║');
  console.log('  ║                                                   ║');
  console.log('  ║  Health:   GET /health                            ║');
  console.log('  ║                                                   ║');
  console.log('  ║  Canvas API (app generation):                     ║');
  console.log(`  ║    REST:   /api/canvas                            ║`);
  console.log(`  ║    Render: /api/canvas/:id/render                 ║`);
  console.log('  ╚═══════════════════════════════════════════════════╝');
  console.log('');
  console.log(`  Live Model: ${DEFAULT_SESSION_CONFIG.model}`);
  console.log(`  Live Voice: ${DEFAULT_SESSION_CONFIG.voice}`);
  console.log(`  Chat Model: ${DEFAULT_MODEL}`);
  console.log('');
});

// --- Graceful shutdown ---
process.on('SIGINT', () => {
  console.log('\n[Server] Shutting down...');
  wssLive.clients.forEach(ws => ws.close());
  wssInteractions.clients.forEach(ws => ws.close());
  server.close(() => {
    console.log('[Server] Goodbye.');
    process.exit(0);
  });
});

process.on('SIGTERM', () => {
  console.log('\n[Server] Shutting down...');
  wssLive.clients.forEach(ws => ws.close());
  wssInteractions.clients.forEach(ws => ws.close());
  server.close(() => {
    process.exit(0);
  });
});
