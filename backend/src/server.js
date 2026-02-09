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

// --- Environment ---
const PORT = parseInt(process.env.PORT || '3000', 10);
const HOST = process.env.HOST || '0.0.0.0';
const GEMINI_API_KEY = process.env.GEMINI_API_KEY;

if (!GEMINI_API_KEY || GEMINI_API_KEY === 'your_gemini_api_key_here') {
  console.error('❌ GEMINI_API_KEY is not set. Copy .env.example to .env and add your key.');
  process.exit(1);
}

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
  });
});

// --- HTTP + WebSocket server ---
const server = createServer(app);

const wss = new WebSocketServer({
  server,
  path: '/live',
  maxPayload: 10 * 1024 * 1024, // 10 MB max per message (for video frames)
});

wss.on('connection', (ws, req) => {
  const clientIP = req.socket.remoteAddress;
  console.log(`[Server] New WebSocket connection from ${clientIP}`);
  handleWebSocket(ws, GEMINI_API_KEY);
});

// --- Start ---
server.listen(PORT, HOST, () => {
  console.log('');
  console.log('  ╔═══════════════════════════════════════════╗');
  console.log('  ║       🌙 Apsara Dark — Live Backend       ║');
  console.log('  ╠═══════════════════════════════════════════╣');
  console.log(`  ║  HTTP:  http://${HOST}:${PORT}              ║`);
  console.log(`  ║  WS:    ws://${HOST}:${PORT}/live            ║`);
  console.log('  ║  Health: /health                          ║');
  console.log('  ║  Config: /config                          ║');
  console.log('  ╚═══════════════════════════════════════════╝');
  console.log('');
  console.log(`  Model: ${DEFAULT_SESSION_CONFIG.model}`);
  console.log(`  Voice: ${DEFAULT_SESSION_CONFIG.voice}`);
  console.log('');
});

// --- Graceful shutdown ---
process.on('SIGINT', () => {
  console.log('\n[Server] Shutting down...');
  wss.clients.forEach(ws => ws.close());
  server.close(() => {
    console.log('[Server] Goodbye.');
    process.exit(0);
  });
});

process.on('SIGTERM', () => {
  console.log('\n[Server] Shutting down...');
  wss.clients.forEach(ws => ws.close());
  server.close(() => {
    process.exit(0);
  });
});
