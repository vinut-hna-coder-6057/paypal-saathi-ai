import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import Database from 'better-sqlite3';

const backendRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..'
);
const defaultDatabasePath = path.join(
  backendRoot,
  'data',
  'paypal-knowledge.sqlite'
);

function validateVector(vector) {
  if (!Array.isArray(vector)
      || vector.length === 0
      || vector.some(value => !Number.isFinite(value))) {
    throw new TypeError('Vector must contain finite numeric values.');
  }
}

const STOP_WORDS = new Set([
  'about', 'after', 'can', 'could', 'does', 'from', 'have', 'help', 'how',
  'into', 'is', 'my', 'the', 'their', 'this', 'what', 'when', 'where', 'which',
  'who', 'why', 'with', 'would', 'you', 'your'
]);

function tokens(value) {
  return new Set(
    value.toLowerCase().match(/[\p{L}\p{N}]{3,}/gu)
      ?.filter(token => !STOP_WORDS.has(token)) || []
  );
}

function lexicalSimilarity(queryTerms, heading, text) {
  if (queryTerms.size === 0) {
    return 0;
  }
  const headingTerms = tokens(heading);
  const documentTerms = tokens(text);
  let matchedWeight = 0;
  for (const term of queryTerms) {
    if (headingTerms.has(term)) {
      matchedWeight += 1.5;
    } else if (documentTerms.has(term)) {
      matchedWeight += 1;
    }
  }
  return Math.min(1, matchedWeight / (queryTerms.size * 1.5));
}

function cosineSimilarity(left, right) {
  if (left.length !== right.length) {
    return -1;
  }

  let dot = 0;
  let leftMagnitude = 0;
  let rightMagnitude = 0;

  for (let index = 0; index < left.length; index++) {
    dot += left[index] * right[index];
    leftMagnitude += left[index] * left[index];
    rightMagnitude += right[index] * right[index];
  }

  if (leftMagnitude === 0 || rightMagnitude === 0) {
    return -1;
  }

  return dot / Math.sqrt(leftMagnitude * rightMagnitude);
}

export function createVectorStore(databasePath = defaultDatabasePath) {
  if (databasePath !== ':memory:') {
    fs.mkdirSync(path.dirname(databasePath), { recursive: true });
  }

  const database = new Database(databasePath);
  database.pragma('journal_mode = WAL');
  database.pragma('foreign_keys = ON');
  database.exec(`
    CREATE TABLE IF NOT EXISTS documents (
      url TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      language TEXT NOT NULL,
      region TEXT NOT NULL,
      content_hash TEXT NOT NULL,
      embedding_model TEXT NOT NULL DEFAULT '',
      updated_at TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS chunks (
      id TEXT PRIMARY KEY,
      source_url TEXT NOT NULL REFERENCES documents(url) ON DELETE CASCADE,
      heading TEXT NOT NULL,
      text TEXT NOT NULL,
      embedding TEXT NOT NULL
    );

    CREATE INDEX IF NOT EXISTS chunks_source_url_idx
      ON chunks(source_url);

    CREATE TABLE IF NOT EXISTS index_metadata (
      key TEXT PRIMARY KEY,
      value TEXT NOT NULL
    );
  `);
  const documentColumns = database.pragma('table_info(documents)');
  if (!documentColumns.some(column => column.name === 'embedding_model')) {
    database.exec(
      "ALTER TABLE documents ADD COLUMN embedding_model TEXT NOT NULL DEFAULT ''"
    );
  }

  const compatibilityStatement = database.prepare(`
    SELECT
      COUNT(*) AS documents,
      COUNT(CASE WHEN embedding_model = ? THEN 1 END) AS compatibleDocuments
    FROM documents
  `);
  const countChunksStatement = database.prepare(`
    SELECT COUNT(*) AS count
    FROM chunks
    INNER JOIN documents ON documents.url = chunks.source_url
    WHERE (? IS NULL OR documents.embedding_model = ?)
  `);
  const diagnosticStatement = database.prepare(`
    SELECT
      COUNT(DISTINCT documents.url) AS documents,
      COUNT(chunks.id) AS chunks,
      COUNT(DISTINCT CASE
        WHEN documents.embedding_model = ? THEN documents.url
      END) AS compatibleDocuments,
      COUNT(CASE
        WHEN documents.embedding_model = ? THEN chunks.id
      END) AS compatibleChunks,
      GROUP_CONCAT(DISTINCT documents.embedding_model) AS embeddingModels
    FROM documents
    LEFT JOIN chunks ON chunks.source_url = documents.url
  `);
  const metadataStatement = database.prepare(
    'SELECT value FROM index_metadata WHERE key = ?'
  );
  const setMetadataStatement = database.prepare(`
    INSERT INTO index_metadata (key, value)
    VALUES (?, ?)
    ON CONFLICT(key) DO UPDATE SET value = excluded.value
  `);
  const getDocumentStatement =
      database.prepare(`
        SELECT content_hash AS contentHash, embedding_model AS embeddingModel
        FROM documents
        WHERE url = ?
      `);
  const deleteDocumentStatement =
      database.prepare('DELETE FROM documents WHERE url = ?');
  const insertDocumentStatement = database.prepare(`
    INSERT INTO documents (
      url, title, language, region, content_hash, embedding_model, updated_at
    )
    VALUES (
      @url, @title, @language, @region, @contentHash, @embeddingModel, @updatedAt
    )
  `);
  const insertChunkStatement = database.prepare(`
    INSERT INTO chunks (id, source_url, heading, text, embedding)
    VALUES (@id, @url, @heading, @text, @embedding)
  `);
  const allChunksStatement = database.prepare(`
    SELECT
      chunks.id,
      chunks.heading,
      chunks.text,
      chunks.embedding,
      documents.title,
      documents.url,
      documents.language,
      documents.region,
      documents.content_hash AS contentHash,
      documents.embedding_model AS embeddingModel
    FROM chunks
    INNER JOIN documents ON documents.url = chunks.source_url
    WHERE (? IS NULL OR documents.embedding_model = ?)
  `);

  const replaceDocument = database.transaction(document => {
    deleteDocumentStatement.run(document.url);
    insertDocumentStatement.run({
      url: document.url,
      title: document.title,
      language: document.language,
      region: document.region,
      contentHash: document.contentHash,
      embeddingModel: document.embeddingModel,
      updatedAt: new Date().toISOString()
    });

    for (const chunk of document.chunks) {
      insertChunkStatement.run({
        id: chunk.id,
        url: document.url,
        heading: chunk.heading,
        text: chunk.text,
        embedding: JSON.stringify(chunk.embedding)
      });
    }
  });

  return {
    countChunks(embeddingModel = null) {
      return countChunksStatement.get(
        embeddingModel,
        embeddingModel
      ).count;
    },

    embeddingCompatibility(embeddingModel) {
      const result = compatibilityStatement.get(embeddingModel);
      return {
        documents: result.documents,
        compatibleDocuments: result.compatibleDocuments,
        compatible: result.documents === result.compatibleDocuments
      };
    },

    diagnostics(embeddingModel) {
      const counts = diagnosticStatement.get(
        embeddingModel,
        embeddingModel
      );
      const integrity = database.pragma('quick_check', { simple: true });
      const status = integrity !== 'ok'
        ? 'degraded'
        : counts.documents !== counts.compatibleDocuments
          ? 'embedding-model-mismatch'
          : 'ok';
      return {
        status,
        integrity,
        documents: counts.documents,
        chunks: counts.chunks,
        compatibleDocuments: counts.compatibleDocuments,
        compatibleChunks: counts.compatibleChunks,
        embeddingModels: counts.embeddingModels
          ? counts.embeddingModels.split(',')
          : [],
        embeddingModel,
        lastIngestionAt:
          metadataStatement.get('last_ingestion_at')?.value ?? null
      };
    },

    recordIngestion(at = new Date().toISOString()) {
      setMetadataStatement.run('last_ingestion_at', at);
    },

    hasDocument(url, contentHash, embeddingModel) {
      const previous = getDocumentStatement.get(url);
      return previous?.contentHash === contentHash
        && previous?.embeddingModel === embeddingModel;
    },

    upsertDocument(document) {
      if (!document || typeof document.url !== 'string'
          || typeof document.title !== 'string'
          || typeof document.language !== 'string'
          || typeof document.region !== 'string'
          || typeof document.contentHash !== 'string'
          || typeof document.embeddingModel !== 'string'
          || !Array.isArray(document.chunks)
          || document.chunks.length === 0) {
        throw new TypeError('Document metadata and chunks are required.');
      }

      for (const chunk of document.chunks) {
        if (!chunk || typeof chunk.id !== 'string'
            || typeof chunk.heading !== 'string'
            || typeof chunk.text !== 'string') {
          throw new TypeError('Each chunk requires an id, heading, and text.');
        }
        validateVector(chunk.embedding);
      }

      const previous = getDocumentStatement.get(document.url);
      if (previous?.contentHash === document.contentHash
          && previous?.embeddingModel === document.embeddingModel) {
        return { changed: false, chunkCount: document.chunks.length };
      }

      replaceDocument(document);
      return { changed: true, chunkCount: document.chunks.length };
    },

    search(queryVector, limit = 5, embeddingModel = null, queryText = '') {
      validateVector(queryVector);
      if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
        throw new RangeError('Search limit must be between 1 and 50.');
      }
      const queryTerms = typeof queryText === 'string'
        ? tokens(queryText)
        : new Set();
      const seenContent = new Set();

      return allChunksStatement.all(embeddingModel, embeddingModel)
        .map(row => {
          const embedding = JSON.parse(row.embedding);
          validateVector(embedding);
          if (embedding.length !== queryVector.length) {
            throw new Error(
              'Stored and query embedding dimensions do not match.'
            );
          }
          const semanticScore = cosineSimilarity(queryVector, embedding);
          const lexicalScore = lexicalSimilarity(
            queryTerms,
            row.heading,
            row.text
          );
          return {
            id: row.id,
            heading: row.heading,
            text: row.text,
            title: row.title,
            url: row.url,
            language: row.language,
            region: row.region,
            contentHash: row.contentHash,
            embeddingModel: row.embeddingModel,
            semanticScore,
            lexicalScore,
            score: queryTerms.size === 0
              ? semanticScore
              : semanticScore * 0.85 + lexicalScore * 0.15
          };
        })
        .sort((left, right) => right.score - left.score)
        .filter(result => {
          if (result.semanticScore < 0) {
            return false;
          }
          const contentKey = `${result.url}\n${result.text.trim().toLowerCase()}`;
          if (seenContent.has(contentKey)) {
            return false;
          }
          seenContent.add(contentKey);
          return true;
        })
        .slice(0, limit);
    },

    close() {
      database.close();
    }
  };
}


let defaultStore;

export function getKnowledgeStore() {
  if (!defaultStore) {
    const configuredPath =
      process.env.PAYPAL_KB_DB_PATH || defaultDatabasePath;
    defaultStore = createVectorStore(configuredPath);
  }

  return defaultStore;
}

export function closeKnowledgeStore() {
  if (!defaultStore) {
    return;
  }

  const store = defaultStore;
  defaultStore = undefined;
  store.close();
}

