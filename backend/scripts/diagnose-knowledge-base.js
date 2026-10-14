import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { getEmbeddingModelName } from '../src/services/embeddingService.js';
import { createVectorStore } from '../src/services/vectorStore.js';

const backendRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..'
);
const databasePath = process.env.PAYPAL_KB_DB_PATH
  ? path.resolve(process.env.PAYPAL_KB_DB_PATH)
  : path.join(backendRoot, 'data', 'paypal-knowledge.sqlite');

function localModelTagsUrl() {
  const configured = process.env.OLLAMA_BASE_URL || 'http://127.0.0.1:11434';
  const url = new URL(configured);
  const hostname = url.hostname.replace(/^\[|\]$/g, '').toLowerCase();
  if (url.protocol !== 'http:'
      || !['127.0.0.1', 'localhost', '::1'].includes(hostname)
      || url.username
      || url.password
      || (url.pathname !== '/' && url.pathname !== '')
      || url.search
      || url.hash) {
    throw new Error('Ollama must use a local loopback endpoint.');
  }
  return new URL('/api/tags', url);
}

async function checkConfiguredModel() {
  const model = process.env.PAYPAL_LLM_MODEL || 'qwen2.5:3b-instruct';
  try {
    const response = await fetch(localModelTagsUrl(), {
      signal: AbortSignal.timeout(2_000),
      redirect: 'error'
    });
    if (!response.ok) {
      return { configuredModel: model, status: 'unavailable' };
    }
    const result = await response.json();
    const available = Array.isArray(result.models)
      && result.models.some(item => item.name === model
        || item.name?.startsWith(`${model}:`));
    return {
      configuredModel: model,
      status: available ? 'available' : 'not-installed'
    };
  } catch {
    return { configuredModel: model, status: 'unavailable' };
  }
}

async function diagnose() {
  let index;
  if (!fs.existsSync(databasePath)) {
    index = {
      status: 'missing',
      databasePath,
      documents: 0,
      chunks: 0,
      embeddingModel: getEmbeddingModelName(),
      lastIngestionAt: null
    };
  } else {
    const store = createVectorStore(databasePath);
    try {
      index = {
        ...store.diagnostics(getEmbeddingModelName()),
        databasePath
      };
    } finally {
      store.close();
    }
  }

  const model = await checkConfiguredModel();
  console.log(JSON.stringify({
    index,
    model,
    status: index.status === 'ok' && model.status === 'available'
      ? 'ready'
      : 'degraded'
  }, null, 2));
  if (index.status !== 'ok' || model.status !== 'available') {
    process.exitCode = 1;
  }
}

diagnose().catch(error => {
  console.error(`Knowledge-base diagnostics failed: ${error.message}`);
  process.exitCode = 1;
});
