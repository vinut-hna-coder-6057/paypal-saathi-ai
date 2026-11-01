# On-device visual understanding

## Status: IMPLEMENTED, DEVICE VALIDATION PENDING

Accessibility remains the primary observation and action source. Android 11+ users may separately consent to a one-shot screenshot and on-device text recognition when the current PayPal screen is unknown or ambiguous. The opt-in can be revoked in Saathi at any time. It is unavailable on older Android versions and Android/PayPal may deny capture for secure windows.

The app bundles ML Kit Latin and Devanagari recognizers; no OCR model is downloaded at runtime. Recognized text is immediately reduced to a fixed allow-list of generic screen-heading hints. OCR cannot create controls, labels, or actions. Before combining hints, the service obtains a fresh PayPal accessibility hierarchy and requires the screen identity to match the observation that triggered capture. All navigation remains based on fresh accessibility nodes and the existing user-tap/revalidation policy.

Screenshots and raw OCR text are transient in memory only. They are not written to storage, diagnostic logs, app/backend requests, task persistence, or model input. The backend request omits screen context when a category was assisted by the visual fallback. Bitmap copies and screenshot buffers are released after recognition or failure. If consent is absent, capture fails, text has no allow-listed hint, or the screen changed, accessibility-only/unknown handling remains in effect.

This is not production certification. Physical-device checks must verify capture behavior, privacy disclosures, resource cleanup, Android secure-window behavior, and PayPal compatibility. The implementation uses English/Latin and Devanagari recognition only; Telugu visual recognition is not included. Voice is explicitly deferred.
