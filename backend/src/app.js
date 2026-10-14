import express from 'express';
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';
import { healthRouter } from './routes/health.js';
import { classifyRouter } from './routes/classify.js';
import { guidanceRouter } from './routes/guidance.js';
import { notFoundHandler, errorHandler } from './middleware/errors.js';

export function createApp() {
  const app = express();

  const allowedOrigins = (process.env.CORS_ORIGINS || 'http://localhost:3000')
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
    allowedHeaders: ['Content-Type']
  }));

  app.use(express.json({ limit: '8kb', strict: true }));

  app.use(rateLimit({
    windowMs: 60_000,
    limit: Number(process.env.RATE_LIMIT_PER_MINUTE || 60),
    standardHeaders: 'draft-7',
    legacyHeaders: false
  }));

  app.use('/health', healthRouter);
  app.use('/api/classify', classifyRouter);
  app.use('/api/guidance', guidanceRouter);
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}
