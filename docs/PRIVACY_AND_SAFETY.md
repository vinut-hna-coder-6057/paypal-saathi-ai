# Privacy and payment safety

## Product stance

Saathi provides **guidance only**. It must never act as an autonomous payment agent.

## Protections implemented

| Control | Status |
|---------|--------|
| No password / OTP / PIN / card collection in Saathi UI | Enforced in copy, autofill disabled on question field, and likely credential/account/financial values are blocked before requests |
| Accessibility reads the configured PayPal package only and skips nodes marked as passwords | Implemented in `ScreenMonitorService` |
| Raw screen text not persisted, logged, or uploaded | Category is stored; only exact allow-listed navigation labels are broadcast and optionally sent with a recent question context |
| No automatic clicks or payment submission | Service is observe-only |
| No inference of payment success from category alone | Confirmation / review copy tells users to trust PayPal’s UI |
| Backend does not log request bodies with secrets | No body logging middleware |
| Client-facing errors omit stack traces | `middleware/errors.js` |
| Rate limiting + helmet + JSON size limit | `app.js` |
| Backup disabled | `allowBackup="false"` |
| Cleartext limited to loopback / emulator host | `network_security_config.xml` |

## Data minimized

- **On device:** last screen **category ID**, event type label, and update time in app-private SharedPreferences. Navigation labels are held only in memory.
- **Android to API:** a submitted help `question`, `screenCategory`, and optionally up to eight exact generic navigation labels with a timestamp. Raw screen text, descriptions, roles, and screenshots are not sent.
- **Host loopback:** questions that pass local sensitive-value checks, allow-listed labels, and retrieved public Help excerpts are sent to the configured local Ollama process for generation.
- **Not collected:** screenshots, full accessibility trees, PayPal auth tokens.

## Remaining limitations

- Accessible PayPal screen text may briefly exist in memory during local classification (recipient names, amounts, etc.). Password nodes and their descendants are skipped. Collection is bounded by depth, node count, and text length. Only category and exact generic navigation labels are retained for the current context.
- Help questions are sent to the configured API only when submitted. Likely credentials, email addresses, phone numbers, long identifiers, and explicitly stated balances/payment amounts are blocked before retrieval. Pattern checks cannot guarantee detection of every sensitive value.
- The backend does not persist questions. It embeds them locally, retrieves public PayPal Help excerpts from `backend/data/paypal-knowledge.sqlite`, and sends the question plus those excerpts only to a loopback Ollama service. The model endpoint is restricted to loopback; there is no third-party inference provider.
- The local vector database contains extracted official Help Center chunks and their vectors, not accessibility text or question history. The embedding model weights are downloaded from Hugging Face on first use; no question text is part of that download.
- The importer checks PayPal `robots.txt` and stops on access challenges. It does not bypass access controls. The operator must confirm local indexing is permitted.
- The local API has no authentication. Bind it to trusted networks only.
- Aggregate pipeline timings and error/fallback counts are exposed by `/health`; they do not include questions, labels, or answer text.
- OCR and screenshot/vision processing are not enabled: the existing service already receives accessibility events, and screenshots would unnecessarily broaden the sensitive data collected.
- Release HTTPS endpoint is a placeholder (`https://api.example.com`) until you deploy a real host.
- The floating overlay and accessibility behavior still require physical-device testing.

## Enabling / disabling accessibility

1. Open Saathi → **Open Accessibility Settings** and review the screen-monitoring disclosure.
2. Enable **PayPal Saathi AI Screen Monitor**.
3. To stop monitoring, disable that service in the same settings screen.

Only `com.paypal.android.p2pmobile` is listed in the accessibility service metadata.
