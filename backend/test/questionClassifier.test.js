import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyHelpQuestion } from '../src/services/questionClassifier.js';

test('classifies a send-money question', () => {
  const result = classifyHelpQuestion('How do I send money?');
  assert.equal(result.category, 'send_money');
  assert.match(result.guidance, /verify the recipient and amount/i);
});

test('does not claim to verify a payment', () => {
  const result = classifyHelpQuestion('Where is my payment status?');
  assert.equal(result.category, 'activity');
  assert.match(result.guidance, /cannot verify transaction status/i);
});

test('provides generic guidance for unknown questions', () => {
  const result = classifyHelpQuestion('Where are the settings?');
  assert.equal(result.category, 'general');
});
