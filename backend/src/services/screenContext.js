const MAX_CONTEXT_AGE_MS = 15_000;
const MAX_FUTURE_SKEW_MS = 5_000;
const MAX_NAVIGATION_LABELS = 8;
const MAX_ACTIONABLE_ELEMENTS = 8;
const MAX_LABEL_LENGTH = 40;
const verticalPositions = new Set(['top', 'middle', 'bottom']);
const horizontalPositions = new Set(['left', 'center', 'right']);
const evidenceStrengths = new Set(['strong', 'moderate', 'insufficient']);
const evidenceCodes = new Set([
  'unrelated_package',
  'insufficient_accessibility',
  'conflicting_screen_signals',
  'authentication_prompt',
  'sign_in_prompt',
  'review_prompt',
  'confirmation_prompt',
  'amount_prompt',
  'recipient_prompt',
  'home_action_combination',
  'send_flow_heading',
  'wallet_heading',
  'activity_heading',
  'home_heading'
]);
const categoryEvidence = new Map([
  ['home', new Set(['home_action_combination', 'home_heading'])],
  ['send_money', new Set(['send_flow_heading'])],
  ['recipient_selection', new Set(['recipient_prompt'])],
  ['amount_entry', new Set(['amount_prompt'])],
  ['review_payment', new Set(['review_prompt'])],
  ['confirmation', new Set(['confirmation_prompt'])],
  ['activity', new Set(['activity_heading'])],
  ['wallet', new Set(['wallet_heading'])],
  ['security', new Set(['authentication_prompt'])],
  ['sign_in', new Set(['sign_in_prompt'])],
  ['unknown', new Set([
    'unrelated_package',
    'insufficient_accessibility',
    'conflicting_screen_signals'
  ])]
]);
const classifierMetadataKeys = [
  'confidence',
  'evidenceStrength',
  'ambiguous',
  'evidenceCodes'
];

const safeLabels = new Map([
  'activity',
  'wallet',
  'home',
  'send',
  'request',
  'add card or bank',
  'go to resolution centre',
  'continue',
  'next',
  'payments',
  'transactions',
  'security',
  'settings',
  'help',
  'profile',
  'notifications',
  'cards',
  'bank accounts',
  'more',
  'menu',
  'back'
].map(label => [label, label.replace(/\b\w/g, character => character.toUpperCase())]));

export function normalizeNavigationLabels(labels) {
  if (!Array.isArray(labels)) {
    return [];
  }

  const result = [];
  for (const label of labels.slice(0, MAX_NAVIGATION_LABELS)) {
    if (typeof label !== 'string' || label.length > MAX_LABEL_LENGTH) {
      continue;
    }
    const canonical = safeLabels.get(label.trim().toLowerCase());
    if (canonical && !result.includes(canonical)) {
      result.push(canonical);
    }
  }
  return result;
}

export function normalizeEvidenceCodes(codes) {
  if (!Array.isArray(codes)) {
    return [];
  }
  return [...new Set(codes.filter(code =>
    typeof code === 'string' && evidenceCodes.has(code)
  ))].slice(0, 4);
}

export function normalizeActionableElements(elements) {
  if (!Array.isArray(elements)) {
    return [];
  }

  const result = [];
  for (const element of elements.slice(0, MAX_ACTIONABLE_ELEMENTS)) {
    if (typeof element !== 'string' || element.length > 80) {
      continue;
    }
    const parts = element.split('|');
    if (parts.length !== 3) {
      continue;
    }
    const label = safeLabels.get(parts[0].trim().toLowerCase());
    if (!label
        || !verticalPositions.has(parts[1])
        || !horizontalPositions.has(parts[2])) {
      continue;
    }
    const normalized = `${label}|${parts[1]}|${parts[2]}`;
    if (!result.includes(normalized)) {
      result.push(normalized);
    }
  }
  return result;
}

export function sanitizeScreenContext(
  context,
  screenCategory,
  now = Date.now()
) {
  if (context === undefined || context === null) {
    return null;
  }
  if (typeof context !== 'object' || Array.isArray(context)) {
    throw new TypeError('screenContext must be an object.');
  }

  const allowedKeys = new Set([
    'category',
    'visibleLabels',
    'actionableElements',
    'confidence',
    'evidenceStrength',
    'ambiguous',
    'evidenceCodes',
    'source',
    'observedAt'
  ]);
  if (Object.keys(context).some(key => !allowedKeys.has(key))
      || context.category !== screenCategory
      || context.source !== 'accessibility'
      || !Array.isArray(context.visibleLabels)
      || context.visibleLabels.length > MAX_NAVIGATION_LABELS
      || (context.actionableElements !== undefined
        && (!Array.isArray(context.actionableElements)
          || context.actionableElements.length > MAX_ACTIONABLE_ELEMENTS))
      || (context.confidence !== undefined
        && (!Number.isInteger(context.confidence)
          || context.confidence < 0
          || context.confidence > 100))
      || (context.evidenceStrength !== undefined
        && !evidenceStrengths.has(context.evidenceStrength))
      || (context.ambiguous !== undefined
        && typeof context.ambiguous !== 'boolean')
      || (context.evidenceCodes !== undefined
        && (!Array.isArray(context.evidenceCodes)
          || context.evidenceCodes.length > 4
          || context.evidenceCodes.some(code =>
            typeof code !== 'string' || !evidenceCodes.has(code)
          )))
      || !Number.isSafeInteger(context.observedAt)) {
    throw new TypeError('screenContext has an invalid shape.');
  }

  const metadataFieldsPresent = classifierMetadataKeys.filter(key =>
    Object.hasOwn(context, key)
  ).length;
  if (metadataFieldsPresent !== 0
      && metadataFieldsPresent !== classifierMetadataKeys.length) {
    throw new TypeError('screenContext has an invalid shape.');
  }

  const confidence = context.confidence ?? 0;
  const strength = context.evidenceStrength ?? 'insufficient';
  const ambiguous = context.ambiguous ?? true;
  const normalizedEvidenceCodes = normalizeEvidenceCodes(
    context.evidenceCodes ?? []
  );
  if ((metadataFieldsPresent > 0
        && (normalizedEvidenceCodes.length === 0
          || normalizedEvidenceCodes.some(code =>
            !categoryEvidence.get(screenCategory)?.has(code)
          )))
      || (screenCategory === 'unknown' && !ambiguous)) {
    throw new TypeError('screenContext has an invalid shape.');
  }

  const age = now - context.observedAt;
  if (age > MAX_CONTEXT_AGE_MS || age < -MAX_FUTURE_SKEW_MS) {
    return null;
  }

  return {
    category: screenCategory,
    visibleLabels: normalizeNavigationLabels(context.visibleLabels),
    actionableElements: normalizeActionableElements(
      context.actionableElements ?? []
    ),
    confidence,
    evidenceStrength: strength,
    ambiguous,
    evidenceCodes: normalizedEvidenceCodes,
    source: 'accessibility',
    observedAt: context.observedAt
  };
}
