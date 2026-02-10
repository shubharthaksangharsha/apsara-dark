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
      html: null,
      status: 'generating', // generating | testing | fixing | ready | error
      error: null,
      attempts: 0,
      created_at: now,
      updated_at: now,
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
    Object.assign(app, updates, { updated_at: new Date().toISOString() });
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
}

// Singleton store
export const canvasStore = new CanvasStore();
