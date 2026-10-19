import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {
  createVectorStore,
  closeKnowledgeStore,
  getKnowledgeStore
} from '../src/services/vectorStore.js';
test('SQLite vector store persists metadata, ranks vectors, and upserts idempotently', async () => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'paypal-kb-'));
  const databasePath = path.join(directory, 'knowledge.sqlite');
  const document = {
    url: 'https://www.paypal.com/in/cshelp/article/synthetic-help123',
    title: 'Synthetic vector-store fixture',
    language: 'en',
    region: 'IN',
    contentHash: 'fixture-hash-v1',
    embeddingModel: 'synthetic-embedding-v1',
    chunks: [
      {
        id: 'chunk-send',
        heading: 'Synthetic send heading',
        text: 'Synthetic send-payment documentation.',
        embedding: [1, 0]
      },
      {
        id: 'chunk-account',
        heading: 'Synthetic account heading',
        text: 'Synthetic account documentation.',
        embedding: [0, 1]
      }
    ]
  };

  try {
    const firstStore = createVectorStore(databasePath);
    assert.equal(firstStore.upsertDocument(document).changed, true);
    assert.equal(firstStore.upsertDocument(document).changed, false);
    firstStore.recordIngestion('2026-10-14T12:00:00.000Z');
    const indexStatus = firstStore.diagnostics('synthetic-embedding-v1');
    assert.equal(indexStatus.status, 'ok');
    assert.equal(indexStatus.documents, 1);
    assert.equal(indexStatus.chunks, 2);
    assert.equal(indexStatus.compatibleChunks, 2);
    assert.equal(indexStatus.lastIngestionAt, '2026-10-14T12:00:00.000Z');
    const incompatibleStatus =
      firstStore.diagnostics('other-embedding-model');
    assert.equal(incompatibleStatus.compatibleChunks, 0);
    assert.equal(incompatibleStatus.status, 'embedding-model-mismatch');
    assert.equal(firstStore.countChunks(), 2);
    assert.equal(firstStore.countChunks('synthetic-embedding-v1'), 2);
    assert.equal(firstStore.countChunks('other-embedding-model'), 0);
    firstStore.close();

    const reopenedStore = createVectorStore(databasePath);
    const [bestMatch] = reopenedStore.search(
      [0.9, 0.1],
      2,
      'synthetic-embedding-v1'
    );
    assert.equal(bestMatch.id, 'chunk-send');
    assert.equal(bestMatch.title, 'Synthetic vector-store fixture');
    assert.equal(bestMatch.language, 'en');
    assert.equal(bestMatch.region, 'IN');
    assert.equal(bestMatch.contentHash, 'fixture-hash-v1');
    assert.equal(
      reopenedStore.search([0.9, 0.1], 2, 'other-embedding-model').length,
      0
    );
    reopenedStore.close();
  } finally {
    await fs.rm(directory, {
      recursive: true,
      force: true,
      maxRetries: 5,
      retryDelay: 100
    });
  }
});

test('hybrid search boosts matching headings and rejects incompatible dimensions', () => {
  const store = createVectorStore(':memory:');
  try {
    store.upsertDocument({
      url: 'https://www.paypal.com/in/cshelp/article/synthetic-hybrid-help123',
      title: 'Synthetic hybrid retrieval fixture',
      language: 'en',
      region: 'IN',
      contentHash: 'hybrid-content-v1',
      embeddingModel: 'synthetic-hybrid-v1',
      chunks: [
        {
          id: 'synthetic-send-heading',
          heading: 'Synthetic sending payments',
          text: 'Synthetic payment instructions.',
          embedding: [1, 0]
        },
        {
          id: 'synthetic-history-heading',
          heading: 'Synthetic transaction history',
          text: 'Synthetic activity and older payment records.',
          embedding: [0.9, 0.1]
        }
      ]
    });

    const results = store.search(
      [1, 0],
      2,
      'synthetic-hybrid-v1',
      'transaction history'
    );
    assert.equal(results[0].id, 'synthetic-history-heading');
    assert.ok(results[0].score > results[0].semanticScore);
    assert.throws(
      () => store.search([1, 0, 0], 2, 'synthetic-hybrid-v1'),
      /dimensions do not match/
    );
  } finally {
    store.close();
  }
});

test('search suppresses duplicate chunk content within a document', () => {
  const store = createVectorStore(':memory:');
  try {
    store.upsertDocument({
      url: 'https://www.paypal.com/in/cshelp/article/synthetic-duplicate-help123',
      title: 'Synthetic duplicate fixture',
      language: 'en',
      region: 'IN',
      contentHash: 'duplicate-content-v1',
      embeddingModel: 'synthetic-duplicate-v1',
      chunks: [
        {
          id: 'synthetic-duplicate-one',
          heading: 'Synthetic section one',
          text: 'Identical synthetic public text.',
          embedding: [1, 0]
        },
        {
          id: 'synthetic-duplicate-two',
          heading: 'Synthetic section two',
          text: 'Identical synthetic public text.',
          embedding: [0.99, 0.01]
        }
      ]
    });

    const results = store.search([1, 0], 10, 'synthetic-duplicate-v1');
    assert.equal(results.length, 1);
  } finally {
    store.close();
  }
});

test('closing an uninitialized knowledge store is safe', () => {
  assert.doesNotThrow(() => closeKnowledgeStore());
});

