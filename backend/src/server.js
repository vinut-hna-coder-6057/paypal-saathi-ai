import { createApp } from './app.js';

const app = createApp();
const port = Number(process.env.PORT || 3000);
const host = process.env.HOST || '0.0.0.0';

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
