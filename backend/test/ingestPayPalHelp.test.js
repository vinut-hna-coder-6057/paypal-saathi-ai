import test from 'node:test';
import assert from 'node:assert/strict';
import { ingestPayPalHelpSources } from '../scripts/ingest-paypal-help.js';

const firstUrl =
  'https://www.paypal.com/in/cshelp/article/synthetic-ingest-one-help123';
const secondUrl =
  'https://www.paypal.com/in/cshelp/article/synthetic-ingest-two-help456';

function syntheticDocument(url) {
  return {
    url,
    title: 'Synthetic ingestion fixture',
    language: 'en',
    region: 'IN',
    contentHash: 'synthetic-content-hash',
    chunks: [{
      id: 'synthetic-chunk',
      heading: 'Synthetic heading',
      text: 'Synthetic fixture content for importer tests.'
    }]
  };
}

function fakeStore({ unchanged = false } = {}) {
  return {
    upserted: [],
    recorded: 0,
    closed: false,
    hasDocument() {
      return unchanged;
    },
    upsertDocument(document) {
      this.upserted.push(document);
      return { changed: true, chunkCount: document.chunks.length };
    },
    recordIngestion() {
      this.recorded++;
    },
    close() {
      this.closed = true;
    }
  };
}

const importerDependencies = store => ({
  getStore: () => store,
  embed: async () => [0.1, 0.2, 0.3],
  wait: async () => {},
  log: () => {}
});

test('unchanged documents are not embedded again', async () => {
  const store = fakeStore({ unchanged: true });
  let fetched = 0;
  let embedded = 0;

  await ingestPayPalHelpSources(
    ['--confirm-permitted', firstUrl],
    {
      ...importerDependencies(store),
      fetchDocument: async url => {
        fetched++;
        return syntheticDocument(url);
      },
      embed: async () => {
        embedded++;
        return [0.1, 0.2, 0.3];
      }
    }
  );

  assert.equal(fetched, 1);
  assert.equal(embedded, 0);
  assert.equal(store.recorded, 1);
  assert.equal(store.upserted.length, 0);
  assert.equal(store.closed, true);
});

test('partial ingestion failure preserves completed pages and closes the store', async () => {
  const store = fakeStore();
  await assert.rejects(
    ingestPayPalHelpSources(
      ['--confirm-permitted', firstUrl, secondUrl],
      {
        ...importerDependencies(store),
        fetchDocument: async url => {
          if (url === secondUrl) {
            throw new Error('Synthetic source unavailable');
          }
          return syntheticDocument(url);
        }
      }
    ),
    /Synthetic source unavailable/
  );

  assert.equal(store.upserted.length, 1);
  assert.equal(store.recorded, 1);
  assert.equal(store.closed, true);
});

test('failed embedding never replaces an existing indexed document', async () => {
  const store = fakeStore();
  await assert.rejects(
    ingestPayPalHelpSources(
      ['--confirm-permitted', firstUrl],
      {
        ...importerDependencies(store),
        fetchDocument: async url => syntheticDocument(url),
        embed: async () => {
          throw new Error('Synthetic embedding failure');
        }
      }
    ),
    /Synthetic embedding failure/
  );

  assert.equal(store.upserted.length, 0);
  assert.equal(store.recorded, 0);
  assert.equal(store.closed, true);
});
