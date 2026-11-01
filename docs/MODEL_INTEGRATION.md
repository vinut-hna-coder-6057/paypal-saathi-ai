# Local RAG and model integration

The backend question-answer route now uses a local retrieval-augmented generation pipeline. Android screen classification remains rule-based and does not send accessibility text to a model.

## Runtime path

1. `POST /api/classify` validates the submitted question, screen category, and optional recent screen context.
2. `questionClassifier.js` identifies a conservative question intent and adds relevant query terms when available.
3. `embeddingService.js` embeds the question plus that intent expansion using a local CPU Transformers.js model.
4. `vectorStore.js` ranks persistent SQLite vectors with a semantic score and a small heading/content lexical boost. Screen category and labels never filter retrieval.
5. `questionAnswerService.js` discards stale context, rejects sensitive input and weak evidence, adds a cautious navigation hint from matching fresh allow-listed labels, validates generated output, and uses the existing rule-based fallback for model errors.
6. `modelAdapter.js` sends only the question, intent, and retrieved excerpts to a loopback-only Ollama API. Screen category and navigation labels do not reach the model, so they cannot change task interpretation or evidence sufficiency.
7. The existing response fields are preserved. Grounded answers include source titles, headings, and official URLs in `guidance`.

The answer model returns supported task steps. The API adds a possible starting point only when a fresh visible label matches the task; it explicitly avoids claiming that the label opens the destination. When the current screen is unknown or no relevant label is visible, guidance still answers from the documentation and asks which navigation labels are visible if an app-specific next step is needed. Help articles that describe web procedures are labelled as web-only rather than presented as confirmed Android-app paths. Partial evidence is retained when it answers a useful part of the question, with its limitation stated.

## Models and storage

| Purpose | Default | Configuration |
|---------|---------|---------------|
| Local answer generation | `qwen2.5:3b-instruct` through Ollama | `PAYPAL_LLM_MODEL` |
| Local embeddings | `Xenova/all-MiniLM-L6-v2` through Transformers.js on CPU | `PAYPAL_EMBEDDING_MODEL` |
| Ollama endpoint | `http://127.0.0.1:11434` | `OLLAMA_BASE_URL` (loopback only) |
| Persistent index | `backend/data/paypal-knowledge.sqlite` | `PAYPAL_KB_DB_PATH` |
| Similarity floor | `0.45` cosine similarity | `PAYPAL_RAG_MIN_SIMILARITY` |
| Model timeout | `45000` ms | `PAYPAL_LLM_TIMEOUT_MS` |
| Model retention | `5m` | `PAYPAL_OLLAMA_KEEP_ALIVE` |

The local vector database stores official-document chunks, source title, URL, language, region, content hash, embedding model ID, and embedding values. It does not store user questions, screen text, or generated answers. Updating the embedding model causes documents to be re-indexed separately rather than comparing incompatible vectors.

The installed Qwen model and a relevant indexed Help excerpt were checked with local generation prompts. In this environment, cold prompt generation took about 6.5 seconds and a warm prompt about 0.4 seconds; these are not end-to-end Android timings. A five-minute retention period was selected to reduce repeated load latency. Set `PAYPAL_OLLAMA_KEEP_ALIVE=0` to unload after each answer if memory pressure warrants it. The Android `/api/classify` read timeout remains 55 seconds; health and screen-guidance requests retain their shorter timeout.

## Knowledge ingestion

`backend/src/data/paypalIndiaHelpSources.js` contains a small set of official PayPal India Help Center article URLs. Run the importer from `backend`:

```powershell
npm run ingest:paypal -- --confirm-permitted
```

The fetcher checks PayPal `robots.txt`, accepts only HTTPS PayPal India Help article URLs, restricts redirects to the same host and allowed path, caps downloads, and waits between pages. It stops if robots rules disallow a page or an access challenge is returned. It does not crawl, solve challenges, or bypass access restrictions. The allow-list includes sending/receiving, bank linking, email confirmation, password reset, refund, payment hold, and account-limitation topics; only pages successfully fetched and processed are indexed.

For permitted HTML files obtained separately, create a JSON manifest in the same directory:

```json
[
  {
    "file": "send-help.html",
    "url": "https://www.paypal.com/in/cshelp/article/how-do-i-send-payments-help293"
  }
]
```

Then run:

```powershell
npm run ingest:paypal -- --confirm-permitted --manifest .\sources.json
```

The importer extracts article headings and text, creates bounded heading-aware chunks, computes a content hash, and skips unchanged documents for the current embedding model. Raw HTML is not stored in the vector database.

## Evidence and fallbacks

- The answer model receives the question, a rule-derived intent, and the top relevant documentation excerpts. It receives neither screen category nor navigation labels nor raw accessibility text. The API uses fresh allow-listed labels separately to form a cautious, non-authoritative navigation hint.
- The Android service extracts category-relevant text transiently, but sends only a category and a small set of exact generic navigation labels. Labels and timestamps are bounded and discarded from persisted screen state. The service continues to filter the exact PayPal package and skip password nodes.
- An optional, separately consented Android 11+ local OCR fallback can contribute only a fixed generic screen hint after an unknown/ambiguous observation. Raw OCR text and screenshots are not included in API/model requests or stored; visual-assisted requests omit the structured accessibility context. It cannot generate controls or actions. See [ON_DEVICE_VISUAL_UNDERSTANDING.md](./ON_DEVICE_VISUAL_UNDERSTANDING.md). Physical PayPal device/version/locale validation remains outstanding.
- The current screen category never filters out otherwise relevant question evidence.
- Below-threshold or absent evidence returns an explicit documentation-insufficient answer and does not invoke the LLM. A model's insufficiency phrase does not discard a response that also contains supported task steps.
- Retrieval unavailable returns an explicit local-index unavailable response.
- Ollama unavailable or timed out uses the existing rule-based topic answer, clearly labeled as non-source-grounded.
- Questions that appear to contain credential values bypass embedding and generation. Request bodies are not logged or persisted.
- Email addresses, phone numbers, long account/card identifiers, and explicitly stated balances/payment amounts are also blocked before retrieval or model generation.
- Generated output must be bounded, cite an available evidence marker, avoid unsupported URLs and sensitive values, and not claim account/transaction verification; invalid output falls back.
- At most one model call runs at a time; competing requests fall back safely. Calls use bounded timeouts/output. The Ollama base URL is loopback-only and HTTP redirects are rejected.
- `POST /api/classify` retains `category`, `guidance`, `disclaimer`, `matchedBy`, and `screenCategory`.

Run `npm run diagnostics:knowledge` from `backend` to check document/chunk counts, embedding-model compatibility, SQLite integrity, last successful ingestion, and whether the configured Ollama model is installed. Public `/health` and `/health/ready` expose only minimal status values; they do not reveal pipeline metrics or optional dependency configuration.

The model is not an account agent. It must not infer transaction status, approve payments, click controls, select recipients, or repeat credential values. Verify any actionable detail against the official PayPal app and cited Help Center page.

## Validation

`npm test` covers the API contract, source parsing/redirect/size/challenge decisions, repeatable and partial ingestion, persistent hybrid retrieval, question-topic precedence across multiple starting screens, screen-label-only navigation hints, web-path disclosure, sensitive-input bypass, stale screen context, model response validation, retrieval errors, model unavailability, timeout, and rule-based fallback. The embedding and local Ollama provider can be smoke-tested locally without real account data.
