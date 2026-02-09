/**
 * Quick test script for TEXT mode on the local backend.
 * Usage: node test-text-mode.js
 *
 * Starts a WebSocket connection to the local backend, sends a connect
 * with TEXT modality, then sends a text message and prints responses.
 */

import WebSocket from 'ws';

const WS_URL = 'ws://localhost:3001/live';

const config = {
  model: 'gemini-2.5-flash-native-audio-preview-12-2025',
  responseModalities: ['TEXT'],
  temperature: 0.7,
  includeThoughts: true,
  inputAudioTranscription: false,
  outputAudioTranscription: false,
  enableAffectiveDialog: false,
  proactiveAudio: false,
  contextWindowCompression: { slidingWindow: {} },
  tools: { googleSearch: false, functionCalling: false },
};

console.log('Connecting to', WS_URL, '...');

const ws = new WebSocket(WS_URL);

ws.on('open', () => {
  console.log('[WS] Connected, sending connect with TEXT config...');
  ws.send(JSON.stringify({ type: 'connect', config }));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data.toString());
  console.log('[<<]', msg.type, msg.type === 'text' ? msg.text : msg.type === 'thought' ? `(thought) ${msg.text}` : JSON.stringify(msg).slice(0, 200));

  // Once connected, send a test message
  if (msg.type === 'connected') {
    console.log('\n[>>] Sending text: "Hello, who are you? Reply in one sentence."');
    ws.send(JSON.stringify({ type: 'text', text: 'Hello, who are you? Reply in one sentence.' }));

    // Timeout — close after 15 seconds
    setTimeout(() => {
      console.log('\n[TEST] Timeout reached, disconnecting...');
      ws.send(JSON.stringify({ type: 'disconnect' }));
      setTimeout(() => {
        ws.close();
        process.exit(0);
      }, 1000);
    }, 15000);
  }

  // On turn_complete, we know the response is done
  if (msg.type === 'turn_complete') {
    console.log('\n✅ TEXT mode works! Turn completed successfully.');
    ws.send(JSON.stringify({ type: 'disconnect' }));
    setTimeout(() => {
      ws.close();
      process.exit(0);
    }, 1000);
  }

  if (msg.type === 'error') {
    console.error('\n❌ ERROR:', msg.message);
    ws.close();
    process.exit(1);
  }
});

ws.on('error', (err) => {
  console.error('[WS] Error:', err.message);
  process.exit(1);
});

ws.on('close', () => {
  console.log('[WS] Connection closed');
});
