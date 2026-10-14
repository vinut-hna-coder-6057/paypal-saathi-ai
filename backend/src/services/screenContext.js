const MAX_CONTEXT_AGE_MS = 15_000;
const MAX_FUTURE_SKEW_MS = 5_000;
const MAX_NAVIGATION_LABELS = 8;
const MAX_LABEL_LENGTH = 32;

const safeLabels = new Map([
  'activity',
  'wallet',
  'home',
  'send',
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
    'source',
    'observedAt'
  ]);
  if (Object.keys(context).some(key => !allowedKeys.has(key))
      || context.category !== screenCategory
      || context.source !== 'accessibility'
      || !Array.isArray(context.visibleLabels)
      || context.visibleLabels.length > MAX_NAVIGATION_LABELS
      || !Number.isSafeInteger(context.observedAt)) {
    throw new TypeError('screenContext has an invalid shape.');
  }

  const age = now - context.observedAt;
  if (age > MAX_CONTEXT_AGE_MS || age < -MAX_FUTURE_SKEW_MS) {
    return null;
  }

  return {
    category: screenCategory,
    visibleLabels: normalizeNavigationLabels(context.visibleLabels),
    source: 'accessibility',
    observedAt: context.observedAt
  };
}
