import test from 'node:test';
import assert from 'node:assert/strict';
import { classifyHelpQuestion } from '../src/services/questionClassifier.js';

test('classifies a send-money question', () => {
  const result = classifyHelpQuestion('How do I send money?');
  assert.equal(result.category, 'send_money');
  assert.match(result.guidance, /verify the recipient and amount/i);
  assert.equal(result.matchedBy, 'rules');
});

test('does not claim to verify a payment', () => {
  const result = classifyHelpQuestion('Where is my payment status?');
  assert.equal(result.category, 'activity');
  assert.match(result.guidance, /cannot verify transaction status/i);
});

test('provides generic guidance for unknown questions', () => {
  const result = classifyHelpQuestion('Where are the settings?');
  assert.equal(result.category, 'general');
  assert.equal(result.matchedBy, 'fallback');
});

test('security questions warn against sharing secrets', () => {
  const result = classifyHelpQuestion('What do I do with my OTP?');
  assert.equal(result.category, 'security');
  assert.match(result.guidance, /never tell this companion/i);
});

test('empty input falls back safely', () => {
  const result = classifyHelpQuestion('   ');
  assert.equal(result.category, 'general');
});

test('uses a known screen context for otherwise general questions', () => {
  const result = classifyHelpQuestion('What should I check next?', 'review_payment');

  assert.equal(result.category, 'general');
  assert.match(result.guidance, /may relate to reviewing a payment/i);
  assert.match(result.guidance, /does not approve or execute transactions/i);
});

test('unknown screen context is communicated as uncertain', () => {
  const result = classifyHelpQuestion('What should I check next?', 'unknown');

  assert.equal(result.category, 'general');
  assert.match(result.guidance, /don’t have enough information to answer/i);
});

test('question topic takes precedence when it differs from screen context', () => {
  const result = classifyHelpQuestion('How do I check payment status?', 'wallet');

  assert.equal(result.category, 'activity');
  assert.match(result.guidance, /cannot verify transaction status/i);
  assert.match(result.guidance, /may not match the reported screen context/i);
});

test('does not repeat sensitive text from the question', () => {
  const sensitiveText = 'uniquesecret987';
  const result = classifyHelpQuestion(
    `My password is ${sensitiveText}; what should I do?`,
    'security'
  );

  assert.equal(result.category, 'security');
  assert.ok(!JSON.stringify(result).includes(sensitiveText));
});

test('recognizes varied transaction-history wording with unknown screen context', () => {
  for (const question of [
    'Where are my old payments?',
    'Show me my previous transactions.',
    'How do I see my payment histroy?'
  ]) {
    const result = classifyHelpQuestion(question, 'unknown');
    assert.equal(result.category, 'activity');
    assert.equal(result.intent, 'transaction_history');
  }
});

test('recognizes password reset intent while on a Wallet screen', () => {
  const result = classifyHelpQuestion(
    'How do I reset my password?',
    'wallet'
  );
  assert.equal(result.category, 'security');
  assert.equal(result.intent, 'account_security');
  assert.match(result.guidance, /screen context/i);
});

test('does not infer sending intent from a generic occurrence of send', () => {
  const result = classifyHelpQuestion(
    'Can you send me to the Help Center?',
    'unknown'
  );
  assert.equal(result.category, 'general');
  assert.equal(result.matchedBy, 'fallback');
});

test('unsupported questions direct users to official Help without guessing', () => {
  const result = classifyHelpQuestion(
    'Can you tell me my current account balance?',
    'unknown'
  );
  assert.equal(result.category, 'general');
  assert.match(result.guidance, /official PayPal Help Center/i);
});

test('extracts likely intent for policy topics while retaining safe generic categories', () => {
  const cases = [
    ['How do I open a PayPal business account?', 'account_setup'],
    ['How do PayPal fees and currency conversion work?', 'fees_and_currency'],
    ['Why is my account limited?', 'account_limitations'],
    ['How do I receive payments?', 'receiving_payments'],
    ['Where do I report a dispute?', 'disputes']
  ];

  for (const [question, intent] of cases) {
    assert.equal(classifyHelpQuestion(question, 'unknown').intent, intent);
  }
});
