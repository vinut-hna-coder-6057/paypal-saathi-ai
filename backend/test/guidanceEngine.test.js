import test from 'node:test';
import assert from 'node:assert/strict';
import { getGuidance } from '../src/services/guidanceEngine.js';

test('returns guidance for the send money category', () => {
  const result = getGuidance('send_money');

  assert.equal(result.category, 'send_money');
  assert.ok(result.steps.length >= 3);
  assert.match(result.title, /Sending money/i);
});

test('supports a recipient selection alias', () => {
  const result = getGuidance('select recipient');

  assert.equal(result.category, 'recipient_selection');
});

test('review guidance reminds users to verify details', () => {
  const result = getGuidance('review_payment');

  assert.equal(result.category, 'review_payment');
  assert.ok(result.steps.some(step => /verify/i.test(step)));
});

test('maps legacy Android human-readable labels', () => {
  assert.equal(
    getGuidance('Payment review screen').category,
    'review_payment'
  );
  assert.equal(
    getGuidance('Send money flow may be visible').category,
    'send_money'
  );
  assert.equal(
    getGuidance('Sensitive / verification screen').category,
    'security'
  );
  assert.equal(
    getGuidance('Home screen may be visible').category,
    'home'
  );
  assert.equal(
    getGuidance('Amount entry may be visible').category,
    'amount_entry'
  );
  assert.equal(
    getGuidance('Wallet screen may be visible').category,
    'wallet'
  );
  assert.equal(
    getGuidance('Activity screen may be visible').category,
    'activity'
  );
  assert.equal(
    getGuidance('Sign-in / welcome screen').category,
    'sign_in'
  );
});

test('unknown screens receive safe fallback guidance', () => {
  const result = getGuidance('unrecognized_screen');

  assert.equal(result.category, 'unknown');
  assert.ok(result.steps.length > 0);
  assert.equal(result.matchedBy, 'fallback');
});

test('non-string input receives fallback guidance', () => {
  assert.equal(getGuidance(null).category, 'unknown');
});

test('confirmation guidance does not claim payment success', () => {
  const result = getGuidance('confirmation');
  assert.ok(result.steps.some(step => /do not assume/i.test(step)));
});
