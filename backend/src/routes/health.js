import { Router } from 'express';

export const healthRouter = Router();

function sendStatus(status, res) {
  res.set('Cache-Control', 'no-store');
  return res.status(200).json({ status });
}

healthRouter.get('/', (_req, res) => sendStatus('ok', res));
healthRouter.get('/ready', (_req, res) => sendStatus('ready', res));
