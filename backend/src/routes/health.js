import { Router } from 'express';

export const healthRouter = Router();

healthRouter.get('/', (_req, res) => {
  res.status(200).json({
    status: 'ok',
    service: 'paypal-saathi-ai-api',
    version: '0.1.0'
  });
});
