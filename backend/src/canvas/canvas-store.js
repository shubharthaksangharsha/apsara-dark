/**
 * Apsara Dark — Canvas Store
 * 
 * In-memory store for canvas apps created by Apsara Canvas.
 * Each canvas has: id, title, description, html content, status, timestamps.
 * 
 * In production, replace with a proper database.
 */

import { randomUUID } from 'crypto';

class CanvasStore {
  constructor() {
    /** @type {Map<string, CanvasApp>} */
    this.apps = new Map();
  }

  /**
   * Create a new canvas app entry.
   */
  create({ title, description, prompt }) {
    const id = randomUUID();
    const now = new Date().toISOString();
    const app = {
      id,
      title: title || 'Untitled App',
      description: description || '',
      prompt: prompt || '',
      original_prompt: prompt || '',   // Never changes — the first prompt that created this app
      html: null,
      status: 'generating', // generating | testing | fixing | ready | error
      error: null,
      attempts: 0,
      config_used: null,               // Config used for generation/last edit (model, temperature, etc.)
      created_at: now,
      updated_at: now,
      // Generation log — tracks each step of the generation process
      generation_log: [
        { step: 'created', timestamp: now, message: 'Canvas entry created' }
      ],
    };
    this.apps.set(id, app);
    return app;
  }

  /**
   * Get a canvas app by ID.
   */
  get(id) {
    return this.apps.get(id) || null;
  }

  /**
   * Get all canvas apps, sorted by creation date (newest first).
   */
  getAll() {
    return Array.from(this.apps.values())
      .sort((a, b) => new Date(b.created_at) - new Date(a.created_at));
  }

  /**
   * Update a canvas app.
   */
  update(id, updates) {
    const app = this.apps.get(id);
    if (!app) return null;
    const now = new Date().toISOString();
    // Auto-log status changes
    if (updates.status && updates.status !== app.status) {
      if (!app.generation_log) app.generation_log = [];
      app.generation_log.push({
        step: updates.status,
        timestamp: now,
        message: updates.error || `Status changed to ${updates.status}`,
        attempts: updates.attempts || app.attempts,
      });
    }
    // Track edit history — increment when prompt is updated (edit instruction appended)
    if (updates.prompt && updates.prompt !== app.prompt && updates.status === 'generating') {
      if (!app.edit_count) app.edit_count = 0;
      app.edit_count++;
    }
    Object.assign(app, updates, { updated_at: now });
    return app;
  }

  /**
   * Delete a canvas app.
   */
  delete(id) {
    return this.apps.delete(id);
  }

  /**
   * Get summary list (for the Android app's "My Canvas" screen).
   */
  getSummaries() {
    return this.getAll().map(app => ({
      id: app.id,
      title: app.title,
      description: app.description,
      status: app.status,
      created_at: app.created_at,
    }));
  }

  /**
   * Get full detail for a canvas app (includes code, prompt, generation log).
   */
  getDetail(id) {
    const app = this.apps.get(id);
    if (!app) return null;
    return {
      id: app.id,
      title: app.title,
      description: app.description,
      prompt: app.prompt,
      original_prompt: app.original_prompt || app.prompt,
      status: app.status,
      error: app.error,
      attempts: app.attempts,
      html: app.html,
      html_length: app.html ? app.html.length : 0,
      created_at: app.created_at,
      updated_at: app.updated_at,
      generation_log: app.generation_log || [],
      edit_count: app.edit_count || 0,
      config_used: app.config_used || null,
    };
  }
}

// Singleton store
export const canvasStore = new CanvasStore();
