/**
 * Canonical screen-category labels used by Android and this API.
 * These are rule-based (non-AI) identifiers today and the contract
 * for a future model that predicts the same labels.
 */
export const SCREEN_CATEGORIES = Object.freeze([
  'home',
  'send_money',
  'recipient_selection',
  'amount_entry',
  'review_payment',
  'confirmation',
  'activity',
  'wallet',
  'security',
  'sign_in',
  'unknown'
]);

export function isKnownCategory(category) {
  return SCREEN_CATEGORIES.includes(category);
}
