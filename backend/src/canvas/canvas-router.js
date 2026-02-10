/**
 * Apsara Dark — Canvas API Router
 * 
 * REST endpoints for creating, listing, viewing, and serving canvas apps.
 * 
 * Endpoints:
 *   GET    /api/canvas              — List all canvas apps (summaries)
 *   GET    /api/canvas/config       — Get canvas default config
 *   POST   /api/canvas/create       — Create a new canvas app (non-streaming)
 *   POST   /api/canvas/create/stream — Create a new canvas app (SSE streaming)
 *   GET    /api/canvas/:id          — Get canvas app details
 *   GET    /api/canvas/:id/render   — Serve the canvas HTML (for iframe/WebView)
 *   DELETE /api/canvas/:id          — Delete a canvas app
 */

import { Router } from 'express';
import { CanvasService, CANVAS_DEFAULTS } from './canvas-service.js';
import { canvasStore } from './canvas-store.js';

export function createCanvasRouter(apiKey) {
  const router = Router();
  const service = new CanvasService(apiKey);

  // ─── GET /api/canvas/config ───────────────────────────────────────────────
  router.get('/config', (req, res) => {
    res.json({
      defaults: CANVAS_DEFAULTS,
      models: [
        { id: 'gemini-3-flash-preview', name: 'Gemini 3 Flash Preview' },
        { id: 'gemini-3-pro-preview', name: 'Gemini 3 Pro Preview' },
        { id: 'gemini-2.5-flash', name: 'Gemini 2.5 Flash' },
        { id: 'gemini-2.5-pro', name: 'Gemini 2.5 Pro' },
      ],
      thinking_levels: ['minimal', 'low', 'medium', 'high'],
    });
  });

  // ─── GET /api/canvas ──────────────────────────────────────────────────────
  router.get('/', (req, res) => {
    res.json({
      apps: canvasStore.getSummaries(),
    });
  });

  // ─── POST /api/canvas/create ──────────────────────────────────────────────
  // Non-streaming creation
  // Body: { prompt: string, title?: string, config?: { model, max_output_tokens, ... } }
  router.post('/create', async (req, res) => {
    const { prompt, title, config } = req.body;

    if (!prompt) {
      return res.status(400).json({ error: true, message: 'prompt is required' });
    }

    try {
      const app = await service.generateApp({ prompt, title, config });
      res.json({ success: true, app });
    } catch (error) {
      console.error('[Canvas] Create error:', error.message);
      res.status(500).json({ error: true, message: error.message });
    }
  });

  // ─── POST /api/canvas/create/stream ───────────────────────────────────────
  // SSE streaming creation
  router.post('/create/stream', async (req, res) => {
    const { prompt, title, config } = req.body;

    if (!prompt) {
      return res.status(400).json({ error: true, message: 'prompt is required' });
    }

    res.setHeader('Content-Type', 'text/event-stream');
    res.setHeader('Cache-Control', 'no-cache');
    res.setHeader('Connection', 'keep-alive');
    res.setHeader('X-Accel-Buffering', 'no');
    res.flushHeaders();

    try {
      const stream = service.generateAppStream({ prompt, title, config });

      for await (const event of stream) {
        res.write(`data: ${JSON.stringify(event)}\n\n`);
      }

      res.write(`data: [DONE]\n\n`);
      res.end();
    } catch (error) {
      console.error('[Canvas] Stream create error:', error.message);
      res.write(`data: ${JSON.stringify({ event: 'canvas.error', message: error.message })}\n\n`);
      res.end();
    }
  });

  // ─── GET /api/canvas/:id ──────────────────────────────────────────────────
  // Returns full canvas detail including code, prompt, and generation log
  router.get('/:id', (req, res) => {
    const app = canvasStore.getDetail(req.params.id);
    if (!app) {
      return res.status(404).json({ error: true, message: 'Canvas not found' });
    }
    res.json({ app });
  });

  // ─── GET /api/canvas/:id/render ───────────────────────────────────────────
  // Serves the raw HTML for iframe/WebView embedding
  router.get('/:id/render', (req, res) => {
    const app = canvasStore.get(req.params.id);
    if (!app) {
      return res.status(404).send('<html><body><h1>Canvas not found</h1></body></html>');
    }
    if (!app.html) {
      const statusPage = `<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body { background: #0D0D0D; color: #9E9E9E; font-family: system-ui; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
  .status { text-align: center; }
  .spinner { width: 32px; height: 32px; border: 3px solid #333; border-top-color: #B388FF; border-radius: 50%; animation: spin 1s linear infinite; margin: 0 auto 16px; }
  @keyframes spin { to { transform: rotate(360deg); } }
  h2 { color: #E8E8E8; font-size: 18px; margin: 0 0 8px; }
  p { font-size: 14px; margin: 0; }
</style></head><body>
<div class="status">
  <div class="spinner"></div>
  <h2>${app.status === 'error' ? 'Error' : 'Generating...'}</h2>
  <p>${app.status === 'error' ? (app.error || 'Unknown error') : 'Your app is being created by Apsara Canvas'}</p>
</div></body></html>`;
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      return res.send(statusPage);
    }

    // Serve with permissive headers for iframe embedding
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.setHeader('X-Frame-Options', 'ALLOWALL');
    res.removeHeader('X-Frame-Options'); // Allow iframe embedding
    res.setHeader('Content-Security-Policy', "frame-ancestors *");

    // Inject viewport meta tag if missing — ensures mobile-friendly rendering
    // even for apps generated before the mobile-first system instruction was added
    let html = app.html;
    if (html && !html.includes('name="viewport"') && !html.includes("name='viewport'")) {
      html = html.replace(
        /<head([^>]*)>/i,
        `<head$1>\n<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">`
      );
    }
    // Also inject a mobile-fix CSS reset if not present
    if (html && !html.includes('overflow-x: hidden') && !html.includes('overflow-x:hidden')) {
      html = html.replace(
        /<head([^>]*)>/i,
        `<head$1>\n<style>*, *::before, *::after { box-sizing: border-box; } body { margin: 0; overflow-x: hidden; }</style>`
      );
    }
    res.send(html);
  });

  // ─── DELETE /api/canvas/:id ───────────────────────────────────────────────
  router.delete('/:id', (req, res) => {
    const exists = canvasStore.get(req.params.id);
    if (!exists) {
      return res.status(404).json({ error: true, message: 'Canvas not found' });
    }
    canvasStore.delete(req.params.id);
    res.json({ success: true, message: 'Canvas deleted' });
  });

  return router;
}
