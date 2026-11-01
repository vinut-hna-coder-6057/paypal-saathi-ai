import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { getServerConfig } from './serverConfig.js';
import { createBearerAuthMiddleware } from './middleware/auth.js';
import { healthRouter } from './routes/health.js';
import { classifyRouter } from './routes/classify.js';
import { guidanceRouter } from './routes/guidance.js';
import { notFoundHandler, errorHandler } from './middleware/errors.js';

export function createApp({
  environment = process.env,
  config = getServerConfig(environment),
  authMiddleware
} = {}) {
  const app = express();
  const authenticate = authMiddleware
    || (config.auth
      ? createBearerAuthMiddleware(config.auth)
      : (_req, _res, next) => next());

  const allowedOrigins = (environment.CORS_ORIGINS || 'http://localhost:3000')
    .split(',')
    .map(origin => origin.trim())
    .filter(Boolean);

  app.disable('x-powered-by');
  app.use(helmet());

  app.use(cors({
    origin(origin, callback) {
      if (!origin || allowedOrigins.includes(origin)) {
        return callback(null, true);
      }
      return callback(new Error('Origin not allowed'));
    },
    methods: ['GET', 'POST'],
    allowedHeaders: ['Content-Type', 'Authorization']
  }));

  app.use(rateLimit({
    windowMs: 60_000,
    limit: Number(environment.RATE_LIMIT_PER_MINUTE || 60),
    standardHeaders: 'draft-7',
    legacyHeaders: false
  }));

  app.use('/api', authenticate);
  app.use(express.json({ limit: '8kb', strict: true }));
  app.use('/health', healthRouter);
  app.use('/api/classify', classifyRouter);
  app.use('/api/guidance', guidanceRouter);
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
