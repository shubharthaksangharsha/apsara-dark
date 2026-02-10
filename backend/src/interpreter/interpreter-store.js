/**
 * Apsara Dark — Interpreter Store
 * 
 * In-memory store for code execution sessions.
 * Each session has: id, title, code, output, images, status, timestamps.
 * 
 * Similar to canvas-store but for Python code execution results.
 */

import { randomUUID } from 'crypto';

class InterpreterStore {
  constructor() {
    /** @type {Map<string, CodeSession>} */
    this.sessions = new Map();
  }

  /**
   * Create a new code execution session.
   */
  create({ title, prompt, code }) {
    const id = randomUUID();
    const now = new Date().toISOString();
    const session = {
      id,
      title: title || 'Untitled Code',
      prompt: prompt || '',
      original_prompt: prompt || '',   // Never changes — the first prompt that created this session
      code: code || '',
      output: null,          // stdout/stderr text output
      images: [],             // Array of { data: base64, mime_type: string }
      previous_code: null,             // Stores the code before last edit
      previous_output: null,           // Stores the output before last edit
      edit_instructions: null,         // The last edit instruction applied
      edit_count: 0,                   // How many times this session was edited
      status: 'running',      // running | completed | error
      error: null,
      created_at: now,
      updated_at: now,
      execution_log: [
        { step: 'created', timestamp: now, message: 'Code session created' }
      ],
    };
    this.sessions.set(id, session);
    return session;
  }

  /**
   * Get a session by ID.
   */
  get(id) {
    return this.sessions.get(id) || null;
  }

  /**
   * Get all sessions, sorted by creation date (newest first).
   */
  getAll() {
    return Array.from(this.sessions.values())
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  }

  /**
   * Update a session.
   */
  update(id, updates) {
    const session = this.sessions.get(id);
    if (!session) return null;
    const now = new Date().toISOString();
    // Auto-log status changes
    if (updates.status && updates.status !== session.status) {
      if (!session.execution_log) session.execution_log = [];
      session.execution_log.push({
        step: updates.status,
        timestamp: now,
        message: updates.error || `Status changed to ${updates.status}`,
      });
    }
    Object.assign(session, updates, { updated_at: now });
    return session;
  }

  /**
   * Delete a session.
   */
  delete(id) {
    return this.sessions.delete(id);
  }

  /**
   * Get summary list (for the Android app's "My Code" screen).
   */
  getSummaries() {
    return this.getAll().map(s => ({
      id: s.id,
      title: s.title,
      prompt: s.prompt,
      status: s.status,
      has_images: s.images && s.images.length > 0,
      image_count: s.images ? s.images.length : 0,
      created_at: s.created_at,
    }));
  }

  /**
   * Get full detail for a session (includes code, output, images).
   */
  getDetail(id) {
    const session = this.sessions.get(id);
    if (!session) return null;
    return {
      id: session.id,
      title: session.title,
      prompt: session.prompt,
      original_prompt: session.original_prompt || session.prompt,
      code: session.code,
      output: session.output,
      images: session.images || [],
      previous_code: session.previous_code || null,
      previous_output: session.previous_output || null,
      edit_instructions: session.edit_instructions || null,
      edit_count: session.edit_count || 0,
      status: session.status,
      error: session.error,
      created_at: session.created_at,
      updated_at: session.updated_at,
      execution_log: session.execution_log || [],
    };
  }
}

// Singleton store
export const interpreterStore = new InterpreterStore();
