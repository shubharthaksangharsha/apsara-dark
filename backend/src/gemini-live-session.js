/**
 * Apsara Dark — Gemini Live Backend
 * 
 * GeminiLiveSession: Manages a single Live API WebSocket session with Gemini.
 * Handles connection, audio/text/video streaming, tool calls, session resumption,
 * context compression, transcriptions, and all Live API features.
 */

import { GoogleGenAI, Modality } from '@google/genai';
import { DEFAULT_SESSION_CONFIG, AUDIO } from './config.js';

export class GeminiLiveSession {
  constructor(apiKey, sessionConfig = {}, callbacks = {}) {
    this.apiKey = apiKey;
    this.callbacks = callbacks;
    this.session = null;
    this.connected = false;
    this.resumptionHandle = null;

    // Merge user config over defaults
    this.config = { ...DEFAULT_SESSION_CONFIG, ...sessionConfig };

    // Initialize the Google GenAI client
    this.ai = new GoogleGenAI({
      apiKey: this.apiKey,
      httpOptions: { apiVersion: 'v1alpha' },
    });

    // Message queue for processing
    this.responseQueue = [];
    this.processing = false;
  }

  /**
   * Build the Gemini Live API config object from our session config.
   */
  _buildGeminiConfig() {
    const config = {};

    // Response modalities
    config.responseModalities = this.config.responseModalities.map(m => {
      if (m === 'AUDIO') return Modality.AUDIO;
      if (m === 'TEXT') return Modality.TEXT;
      return m;
    });

    // System instruction
    if (this.config.systemInstruction) {
      config.systemInstruction = this.config.systemInstruction;
    }

    // Voice / speech config — only for AUDIO modality
    const isAudioModality = this.config.responseModalities.includes('AUDIO');
    if (isAudioModality && this.config.voice) {
      config.speechConfig = {
        voiceConfig: {
          prebuiltVoiceConfig: {
            voiceName: this.config.voice,
          },
        },
      };
    }

    // Temperature
    if (this.config.temperature !== null && this.config.temperature !== undefined) {
      config.temperature = this.config.temperature;
    }

    // Context window compression
    if (this.config.contextWindowCompression) {
      config.contextWindowCompression = this.config.contextWindowCompression;
    }

    // Session resumption
    // Session resumption — only if explicitly enabled (non-null, non-undefined object)
    if (this.config.sessionResumption && typeof this.config.sessionResumption === 'object') {
      config.sessionResumption = this.config.sessionResumption;
      // If we have a resumption handle from a previous connection, use it
      if (this.resumptionHandle) {
        config.sessionResumption = { handle: this.resumptionHandle };
      }
    }

    // Affective dialog — only for AUDIO modality
    if (isAudioModality && this.config.enableAffectiveDialog) {
      config.enableAffectiveDialog = true;
    }

    // Proactive audio — only for AUDIO modality
    if (isAudioModality && this.config.proactiveAudio) {
      config.proactivity = { proactiveAudio: true };
    }

    // Thinking
    if (this.config.thinkingBudget !== null && this.config.thinkingBudget !== undefined) {
      config.thinkingConfig = { thinkingBudget: this.config.thinkingBudget };
    }

    // Thought summaries
    if (this.config.includeThoughts) {
      config.includeThoughts = true;
    }

    // Input audio transcription — only for AUDIO modality (requires audio input)
    if (isAudioModality && this.config.inputAudioTranscription) {
      config.inputAudioTranscription = {};
    }

    // Output audio transcription — only for AUDIO modality (requires audio output)
    if (isAudioModality && this.config.outputAudioTranscription) {
      config.outputAudioTranscription = {};
    }

    // Media resolution for video/image input
    if (this.config.mediaResolution) {
      const resolutionMap = {
        'LOW': 'MEDIA_RESOLUTION_LOW',
        'MEDIUM': 'MEDIA_RESOLUTION_MEDIUM',
        'HIGH': 'MEDIA_RESOLUTION_HIGH',
      };
      const mapped = resolutionMap[this.config.mediaResolution] || this.config.mediaResolution;
      config.mediaResolution = mapped;
      console.log('[GeminiLive] Media resolution:', mapped);
    }

    // Tools
    const tools = [];
    if (this.config.tools?.googleSearch) {
      tools.push({ googleSearch: {} });
    }
    if (this.config.tools?.functionCalling && this.config.functionDeclarations?.length > 0) {
      // Per-tool async/sync modes: { "tool_name": true/false }
      const toolAsyncModes = this.config.toolAsyncModes || {};
      const hasAnyAsync = Object.values(toolAsyncModes).some(v => v === true);

      let declarations = this.config.functionDeclarations.map(fd => {
        const isToolAsync = toolAsyncModes[fd.name] === true;
        if (isToolAsync) {
          return { ...fd, behavior: 'NON_BLOCKING' };
        }
        // Default: BLOCKING (omit behavior field, which defaults to blocking)
        const { behavior, ...rest } = fd;
        return rest;
      });

      // Log each tool's mode
      for (const fd of declarations) {
        console.log(`[GeminiLive]   Tool "${fd.name}" → ${fd.behavior || 'BLOCKING'}`);
      }

      tools.push({ functionDeclarations: declarations });
      console.log(`[GeminiLive] Per-tool async modes: ${JSON.stringify(toolAsyncModes)} (${hasAnyAsync ? 'some async' : 'all sync'})`);
    }
    if (tools.length > 0) {
      config.tools = tools;
    }

    console.log('[GeminiLive] Tools config — googleSearch:', this.config.tools?.googleSearch, '→ tools sent:', JSON.stringify(tools));

    return config;
  }

  /**
   * Connect to Gemini Live API.
   */
  async connect() {
    if (this.connected) {
      console.warn('[GeminiLive] Already connected, disconnecting first...');
      await this.disconnect();
    }

    const geminiConfig = this._buildGeminiConfig();

    console.log('[GeminiLive] Connecting to Gemini Live API...');
    console.log('[GeminiLive] Model:', this.config.model);
    console.log('[GeminiLive] Voice:', this.config.voice);
    console.log('[GeminiLive] Modalities:', this.config.responseModalities);
    console.log('[GeminiLive] Resumption handle:', this.resumptionHandle ? 'yes' : 'no');
    console.log('[GeminiLive] Full Gemini config:', JSON.stringify(geminiConfig, null, 2));

    try {
      this.session = await this.ai.live.connect({
        model: this.config.model,
        config: geminiConfig,
        callbacks: {
          onopen: () => {
            console.log('[GeminiLive] Connected to Gemini Live API');
            this.connected = true;
            this.callbacks.onConnected?.();
          },
          onmessage: (message) => {
            this._handleMessage(message);
          },
          onerror: (error) => {
            console.error('[GeminiLive] Error:', error.message);
            this.callbacks.onError?.({ type: 'gemini_error', message: error.message });
          },
          onclose: (event) => {
            const reason = event.reason || (this.connected ? 'server closed connection' : 'client disconnected');
            console.log('[GeminiLive] Connection closed:', reason, `(code: ${event.code || 'n/a'})`);
            this.connected = false;
            this.callbacks.onDisconnected?.({ reason });
          },
        },
      });

      return true;
    } catch (error) {
      console.error('[GeminiLive] Failed to connect:', error.message);
      this.callbacks.onError?.({ type: 'connection_failed', message: error.message });
      return false;
    }
  }

  /**
   * Handle incoming messages from Gemini Live API.
   */
  _handleMessage(message) {
    // Session resumption updates
    if (message.sessionResumptionUpdate) {
      const update = message.sessionResumptionUpdate;
      if (update.resumable && update.newHandle) {
        this.resumptionHandle = update.newHandle;
        console.log('[GeminiLive] Session resumption handle updated');
        this.callbacks.onSessionResumptionUpdate?.({
          resumable: update.resumable,
          hasHandle: true,
        });
      }
    }

    // GoAway — connection will terminate soon
    if (message.goAway) {
      console.warn('[GeminiLive] GoAway received, timeLeft:', message.goAway.timeLeft);
      this.callbacks.onGoAway?.({ timeLeft: message.goAway.timeLeft });
    }

    // Server content (audio, text, interruptions, turn complete, generation complete)
    if (message.serverContent) {
      const sc = message.serverContent;

      // Interruption
      if (sc.interrupted) {
        this.callbacks.onInterrupted?.();
        return;
      }

      // Generation complete
      if (sc.generationComplete) {
        this.callbacks.onGenerationComplete?.();
      }

      // Turn complete
      if (sc.turnComplete) {
        this.callbacks.onTurnComplete?.();
      }

      // Model turn — audio + text parts
      if (sc.modelTurn && sc.modelTurn.parts) {
        for (const part of sc.modelTurn.parts) {
          // Audio data
          if (part.inlineData && part.inlineData.data) {
            this.callbacks.onAudioData?.({
              data: part.inlineData.data, // base64 encoded PCM
              mimeType: part.inlineData.mimeType || 'audio/pcm;rate=24000',
            });
          }

          // Text data
          if (part.text) {
            if (part.thought) {
              // Only forward thoughts if includeThoughts is enabled
              if (this.config.includeThoughts) {
                this.callbacks.onThought?.({ text: part.text });
              }
            } else {
              this.callbacks.onTextData?.({ text: part.text });
            }
          }

          // Executable code (from code execution tool)
          if (part.executableCode) {
            this.callbacks.onExecutableCode?.({ code: part.executableCode.code });
          }

          // Code execution result
          if (part.codeExecutionResult) {
            this.callbacks.onCodeExecutionResult?.({ output: part.codeExecutionResult.output });
          }
        }
      }

      // Input transcription
      if (sc.inputTranscription && sc.inputTranscription.text) {
        this.callbacks.onInputTranscription?.({ text: sc.inputTranscription.text });
      }

      // Output transcription
      if (sc.outputTranscription && sc.outputTranscription.text) {
        this.callbacks.onOutputTranscription?.({ text: sc.outputTranscription.text });
      }
    }

    // Tool calls
    if (message.toolCall) {
      this.callbacks.onToolCall?.({
        functionCalls: message.toolCall.functionCalls,
      });
    }

    // Usage metadata
    if (message.usageMetadata) {
      this.callbacks.onUsageMetadata?.(message.usageMetadata);
    }
  }

  /**
   * Send real-time audio input (streaming from mic).
   * Data should be base64 encoded PCM audio.
   */
  sendAudio(base64AudioData) {
    if (!this.session || !this.connected) {
      // Silently discard — audio chunks arrive very fast, don't spam logs
      return;
    }

    this.session.sendRealtimeInput({
      audio: {
        data: base64AudioData,
        mimeType: AUDIO.INPUT_MIME_TYPE,
      },
    });
  }

  /**
   * Send real-time video input (streaming from camera).
   * Data should be base64 encoded image frame.
   */
  sendVideo(base64ImageData, mimeType = 'image/jpeg') {
    if (!this.session || !this.connected) {
      return;
    }

    this.session.sendRealtimeInput({
      video: {
        data: base64ImageData,
        mimeType: mimeType,
      },
    });
  }

  /**
   * Send text input with interruption support.
   * First sends a realtimeInput text signal to trigger activity detection (which interrupts
   * any ongoing model response), then sends the full text as clientContent with turnComplete.
   * This ensures text messages interrupt Apsara's speech just like voice does.
   */
  sendText(text) {
    if (!this.session || !this.connected) {
      console.warn('[GeminiLive] Cannot send text — not connected');
      return;
    }

    // Step 1: Send a realtimeInput text signal to trigger activity detection → interruption
    // This causes the server to detect user activity and send an 'interrupted' event,
    // which tells the client to stop audio playback immediately.
    try {
      this.session.sendRealtimeInput({ text });
    } catch (e) {
      console.warn('[GeminiLive] realtimeInput text not supported, falling back:', e.message);
    }

    // Step 2: Send the actual text as clientContent with turnComplete
    // clientContent interrupts server-side generation and submits the text as a full turn.
    this.session.sendClientContent({
      turns: text,
      turnComplete: true,
    });
  }

  /**
   * Send incremental context (for restoring session state).
   */
  sendContext(turns, turnComplete = false) {
    if (!this.session || !this.connected) {
      console.warn('[GeminiLive] Cannot send context — not connected');
      return;
    }

    this.session.sendClientContent({
      turns: turns,
      turnComplete: turnComplete,
    });
  }

  /**
   * Send tool (function) response back to the model.
   */
  sendToolResponse(functionResponses) {
    if (!this.session || !this.connected) {
      console.warn('[GeminiLive] Cannot send tool response — not connected');
      return;
    }

    this.session.sendToolResponse({ functionResponses });
  }

  /**
   * Signal end of audio stream (pause).
   */
  sendAudioStreamEnd() {
    if (!this.session || !this.connected) return;

    this.session.sendRealtimeInput({ audioStreamEnd: true });
  }

  /**
   * Update session config on-the-fly (reconnect required for most changes).
   */
  updateConfig(newConfig) {
    this.config = { ...this.config, ...newConfig };
  }

  /**
   * Disconnect from Gemini Live API.
   */
  async disconnect() {
    if (this.session) {
      console.log('[GeminiLive] Disconnecting (client requested)...');
      this.connected = false; // Set before close to avoid "unknown" in onclose
      try {
        this.session.close();
      } catch (e) {
        // Ignore close errors
      }
      this.session = null;
    }
    this.connected = false;
  }

  /**
   * Reconnect (with optional new config). Uses session resumption if available.
   */
  async reconnect(newConfig = null) {
    if (newConfig) {
      this.updateConfig(newConfig);
    }
    await this.disconnect();
    return await this.connect();
  }

  /**
   * Get current session state.
   */
  getState() {
    return {
      connected: this.connected,
      model: this.config.model,
      voice: this.config.voice,
      modalities: this.config.responseModalities,
      hasResumptionHandle: !!this.resumptionHandle,
      temperature: this.config.temperature,
      enableAffectiveDialog: this.config.enableAffectiveDialog,
      proactiveAudio: this.config.proactiveAudio,
      thinkingBudget: this.config.thinkingBudget,
      includeThoughts: this.config.includeThoughts,
      inputAudioTranscription: this.config.inputAudioTranscription,
      outputAudioTranscription: this.config.outputAudioTranscription,
      contextWindowCompression: !!this.config.contextWindowCompression,
      googleSearch: this.config.tools?.googleSearch || false,
      functionCalling: this.config.tools?.functionCalling || false,
    };
  }
}
