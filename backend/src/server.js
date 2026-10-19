import { createApp } from './app.js';
import { getServerConfig } from './serverConfig.js';
import { closeKnowledgeStore } from './services/vectorStore.js';
import { createShutdownHandler } from './serverLifecycle.js';
const app = createApp();
const { host, port } = getServerConfig();

const server = app.listen(port, host, () => {
  console.log(`PayPal Saathi API listening on http://${host}:${port}`);
  console.log('Answers: local PayPal Help retrieval + Ollama, with safe rule-based fallback.');
});

let isShuttingDown = false;
const shutdown = createShutdownHandler({
  server,
  closeStore: closeKnowledgeStore
});

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));