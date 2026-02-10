/**
 * Apsara Dark — Interpreter Router
 * 
 * Express router for Interpreter API HTTP endpoints.
 * 
 * Endpoints:
 *   GET  /api/interpreter              — List all code sessions
 *   GET  /api/interpreter/:id          — Get a code session detail
 *   POST /api/interpreter              — Run code (create a new session)
 *   DELETE /api/interpreter/:id        — Delete a code session
 *   GET  /api/interpreter/:id/images   — Get images from a session
 *   GET  /api/interpreter/:id/images/:idx — Get a specific image
 */

import { Router } from 'express';
import { InterpreterService } from './interpreter-service.js';
import { interpreterStore } from './interpreter-store.js';

export function createInterpreterRouter(apiKey) {
  const router = Router();
  const service = new InterpreterService(apiKey);

  // ─── GET /api/interpreter — list all sessions ─────────────────────────────
  router.get('/', (req, res) => {
    const summaries = interpreterStore.getSummaries();
    res.json({
      count: summaries.length,
      sessions: summaries,
    });
  });

  // ─── GET /api/interpreter/:id — get session detail ────────────────────────
  router.get('/:id', (req, res) => {
    const detail = interpreterStore.getDetail(req.params.id);
    if (!detail) {
      return res.status(404).json({ error: 'Session not found' });
    }
    res.json(detail);
  });

  // ─── POST /api/interpreter — run code ─────────────────────────────────────
  router.post('/', async (req, res) => {
    const { prompt, title, config } = req.body;
    if (!prompt) {
      return res.status(400).json({ error: 'prompt is required' });
    }
    try {
      const session = await service.runCode({ prompt, title, config });
      res.json(session);
    } catch (error) {
      console.error('[Interpreter Router] Error:', error.message);
      res.status(500).json({ error: error.message });
    }
  });

  // ─── DELETE /api/interpreter/:id — delete session ─────────────────────────
  router.delete('/:id', (req, res) => {
    const deleted = interpreterStore.delete(req.params.id);
    if (!deleted) {
      return res.status(404).json({ error: 'Session not found' });
    }
    res.json({ success: true });
  });

  // ─── GET /api/interpreter/:id/images — list images ────────────────────────
  router.get('/:id/images', (req, res) => {
    const session = interpreterStore.get(req.params.id);
    if (!session) {
      return res.status(404).json({ error: 'Session not found' });
    }
    res.json({
      count: session.images ? session.images.length : 0,
      images: (session.images || []).map((img, i) => ({
        index: i,
        mime_type: img.mime_type,
        url: `/api/interpreter/${req.params.id}/images/${i}`,
      })),
    });
  });

  // ─── GET /api/interpreter/:id/images/:idx — serve a specific image ────────
  router.get('/:id/images/:idx', (req, res) => {
    const session = interpreterStore.get(req.params.id);
    if (!session) {
      return res.status(404).json({ error: 'Session not found' });
    }
    const idx = parseInt(req.params.idx);
    if (isNaN(idx) || idx < 0 || !session.images || idx >= session.images.length) {
      return res.status(404).json({ error: 'Image not found' });
    }
    const img = session.images[idx];
    const buffer = Buffer.from(img.data, 'base64');
    res.setHeader('Content-Type', img.mime_type || 'image/png');
    res.setHeader('Content-Length', buffer.length);
    res.send(buffer);
  });

  return router;
}
