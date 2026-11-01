import { readFileSync } from 'node:fs';
import { createServer as createHttpServer } from 'node:http';
import { createServer as createHttpsServer } from 'node:https';
import { createApp } from './app.js';
import { getServerConfig } from './serverConfig.js';
import { closeKnowledgeStore } from './services/vectorStore.js';
import { createShutdownHandler } from './serverLifecycle.js';
const config = getServerConfig();
const app = createApp({ config });
const server = config.tls
  ? createHttpsServer({
      cert: readFileSync(config.tls.certFile),
      key: readFileSync(config.tls.keyFile)
    }, app)
  : createHttpServer(app);
const protocol = config.tls ? 'https' : 'http';

server.listen(config.port, config.host, () => {
  console.log(
    `PayPal Saathi API listening on ${protocol}://${config.host}:${config.port}`
  );
  console.log('Answers: local PayPal Help retrieval + Ollama, with safe rule-based fallback.');
});

const shutdown = createShutdownHandler({
  server,
  closeStore: closeKnowledgeStore
});

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));