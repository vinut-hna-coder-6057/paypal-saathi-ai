import { createApp } from './app.js';
import { getServerConfig } from './serverConfig.js';

const app = createApp();
const { host, port } = getServerConfig();

const server = app.listen(port, host, () => {
  console.log(`PayPal Saathi API listening on http://${host}:${port}`);
  console.log('Answers: local PayPal Help retrieval + Ollama, with safe rule-based fallback.');
});

function shutdown(signal) {
  console.log(`Received ${signal}. Shutting down gracefully...`);
  server.close(() => {
    console.log('HTTP server closed.');
    process.exit(0);
  });

  setTimeout(() => {
    console.error('Forced shutdown after timeout.');
    process.exit(1);
  }, 10_000).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
