import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { PAYPAL_INDIA_HELP_SOURCES } from '../src/data/paypalIndiaHelpSources.js';
import {
  embedText,
  getEmbeddingModelName
} from '../src/services/embeddingService.js';
import {
  extractDocument,
  fetchPayPalHelpDocument
} from '../src/services/knowledgeBase.js';
import { getKnowledgeStore } from '../src/services/vectorStore.js';

const scriptPath = fileURLToPath(import.meta.url);
const MAX_LOCAL_FILE_BYTES = 2 * 1024 * 1024;

function wait(milliseconds) {
  return new Promise(resolve => setTimeout(resolve, milliseconds));
}

export async function ingestPayPalHelpSources(
  args = process.argv.slice(2),
  dependencies = {}
) {
  const fetchDocument =
    dependencies.fetchDocument || fetchPayPalHelpDocument;
  const embed = dependencies.embed || embedText;
  const getStore = dependencies.getStore || getKnowledgeStore;
  const waitImpl = dependencies.wait || wait;
  const log = dependencies.log || console.log;

  if (!args.includes('--confirm-permitted')) {
    throw new Error(
      'Confirm that you may locally index these public pages with --confirm-permitted.'
    );
  }

  const manifestIndex = args.indexOf('--manifest');
  const manifestPath = manifestIndex >= 0 ? args[manifestIndex + 1] : null;
  if (manifestIndex >= 0 && !manifestPath) {
    throw new Error('--manifest requires a JSON manifest path.');
  }

  const requestedUrls = args.filter((argument, index) =>
    !argument.startsWith('--')
      && !(manifestIndex >= 0 && index === manifestIndex + 1)
  );
  const sourceUrls = requestedUrls.length > 0
    ? requestedUrls
    : PAYPAL_INDIA_HELP_SOURCES;
  if (manifestPath && requestedUrls.length > 0) {
    throw new Error('Use either --manifest or source URLs, not both.');
  }

  let localEntries;
  let manifestDirectory;
  if (manifestPath) {
    const resolvedManifest = path.resolve(manifestPath);
    manifestDirectory = path.dirname(resolvedManifest);
    const manifestText = await fs.readFile(resolvedManifest, 'utf8');
    localEntries = JSON.parse(manifestText);
    if (!Array.isArray(localEntries)
        || localEntries.length === 0
        || localEntries.length > 50) {
      throw new Error('Manifest must be a JSON array containing 1 to 50 entries.');
    }
  }

  const store = getStore();

  try {
    const documentCount = localEntries ? localEntries.length : sourceUrls.length;
    for (let index = 0; index < documentCount; index++) {
      if (index > 0) {
        await waitImpl(1_000);
      }

      let document;
      if (localEntries) {
        const entry = localEntries[index];
        if (!entry || typeof entry.file !== 'string'
            || typeof entry.url !== 'string'
            || !/\.(?:html?|xhtml)$/i.test(entry.file)) {
          throw new Error('Each manifest entry needs an HTML file and official URL.');
        }
        const filePath = path.resolve(manifestDirectory, entry.file);
        const relativeFilePath = path.relative(manifestDirectory, filePath);
        if (relativeFilePath.startsWith('..') || path.isAbsolute(relativeFilePath)) {
          throw new Error('Manifest files must be inside the manifest directory.');
        }
        const fileStat = await fs.stat(filePath);
        if (fileStat.size > MAX_LOCAL_FILE_BYTES) {
          throw new Error('Local Help Center HTML exceeded the import size limit.');
        }
        const html = await fs.readFile(filePath, 'utf8');
        document = extractDocument(html, entry.url);
      } else {
        document = await fetchDocument(sourceUrls[index]);
      }

      const embeddingModel = getEmbeddingModelName();
      if (store.hasDocument(
        document.url,
        document.contentHash,
        embeddingModel
      )) {
        store.recordIngestion();
        log(`Unchanged: ${document.title}`);
        continue;
      }

      const chunks = [];
      for (const chunk of document.chunks) {
        const embeddingInput = `${chunk.heading}\n${chunk.text}`;
        chunks.push({
          ...chunk,
          embedding: await embed(embeddingInput)
        });
      }

      const result = store.upsertDocument({
        ...document,
        embeddingModel,
        chunks
      });
      store.recordIngestion();
      const action = result.changed ? 'Indexed' : 'Unchanged';
      log(
        `${action}: ${document.title} (${result.chunkCount} chunks; ${document.language}-${document.region})`
      );
    }
  } finally {
    store.close();
  }
}

if (process.argv[1]
    && pathToFileURL(path.resolve(process.argv[1])).href
      === pathToFileURL(scriptPath).href) {
  ingestPayPalHelpSources().catch(error => {
    console.error(`Knowledge ingestion stopped: ${error.message}`);
    process.exitCode = 1;
  });
}
