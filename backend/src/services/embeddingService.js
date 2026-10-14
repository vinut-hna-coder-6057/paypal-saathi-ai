import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { env, pipeline } from '@huggingface/transformers';

const backendRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..'
);

env.cacheDir = process.env.PAYPAL_TRANSFORMERS_CACHE
  || path.join(backendRoot, 'data', 'model-cache');

export const DEFAULT_EMBEDDING_MODEL = 'Xenova/all-MiniLM-L6-v2';

let extractor;
let extractorModel;

export function getEmbeddingModelName() {
  return process.env.PAYPAL_EMBEDDING_MODEL || DEFAULT_EMBEDDING_MODEL;
}

async function getExtractor() {
  const model = getEmbeddingModelName();

  if (!extractor || extractorModel !== model) {
    extractorModel = model;
    extractor = pipeline('feature-extraction', model, {
      device: 'cpu',
      dtype: 'q8'
    });
  }

  return extractor;
}

export async function embedText(text) {
  if (typeof text !== 'string' || !text.trim()) {
    throw new TypeError('Embedding input must be a non-empty string.');
  }

  const featureExtractor = await getExtractor();
  const output = await featureExtractor(text, {
    pooling: 'mean',
    normalize: true
  });
  const vector = Array.from(output.data);

  if (vector.length === 0 || vector.some(value => !Number.isFinite(value))) {
    throw new Error('Embedding model returned an invalid vector.');
  }

  return vector;
}
