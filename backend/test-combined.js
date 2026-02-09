/**
 * Combined test: starts server inline, connects TEXT mode, logs full config.
 */
import { GeminiLiveSession } from './src/gemini-live-session.js';
import { DEFAULT_SESSION_CONFIG } from './src/config.js';
import dotenv from 'dotenv';
dotenv.config();

const apiKey = process.env.GEMINI_API_KEY;
if (!apiKey) {
  console.error('Missing GEMINI_API_KEY in .env');
  process.exit(1);
}

// Merge TEXT config overrides
const sessionConfig = {
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

const mergedConfig = { ...DEFAULT_SESSION_CONFIG, ...sessionConfig };
console.log('[TEST] Merged config:', JSON.stringify(mergedConfig, null, 2));

const session = new GeminiLiveSession(apiKey, sessionConfig, {
  onConnected: () => {
    console.log('\n✅ Connected! Sending text...');
    session.sendText('Hello, who are you? Reply in one sentence.');
  },
  onDisconnected: ({ reason }) => {
    console.log('\n❌ Disconnected:', reason);
    process.exit(reason ? 1 : 0);
  },
  onTextData: ({ text }) => {
    console.log('[TEXT]', text);
  },
  onThought: ({ text }) => {
    console.log('[THOUGHT]', text);
  },
  onTurnComplete: () => {
    console.log('\n✅ Turn complete — TEXT mode works!');
    session.disconnect();
    setTimeout(() => process.exit(0), 500);
  },
  onError: ({ type, message }) => {
    console.error('\n❌ Error:', type, message);
    process.exit(1);
  },
});

console.log('[TEST] Connecting...');
const ok = await session.connect();
if (!ok) {
  console.error('[TEST] Connection failed');
  process.exit(1);
}

// Timeout
setTimeout(() => {
  console.log('[TEST] Timeout');
  session.disconnect();
  process.exit(1);
}, 20000);
