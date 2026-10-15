import test from 'node:test';
import assert from 'node:assert/strict';
import { selectScreenStep } from '../src/services/screenAwareGuidance.js';
import { inferQuestionIntent } from '../src/services/questionClassifier.js';

const sendIntent = inferQuestionIntent('How do I send money?');
const reliableContext = {
  confidence: 82,
  evidenceStrength: 'moderate',
  ambiguous: false,
  evidenceCodes: ['home_action_combination']
};

test('selects a single send action only from a labeled actionable control', () => {
  const step = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'home',
    screenContext: {
      ...reliableContext,
      actionableElements: ['Send|bottom|left']
    }
  });

  assert.equal(step.goal, 'payment_sending');
  assert.equal(step.expectedNextScreen, 'recipient_selection');
  assert.equal(step.status, 'awaiting_user');
  assert.match(step.instruction, /bottom left/i);
  assert.match(step.instruction, /“Send” button/);
  assert.deepEqual(step.availableActions, [
    { label: 'Send', vertical: 'bottom', horizontal: 'left' }
  ]);
});

test('asks for a description when the home screen lacks an actionable Send control', () => {
  const step = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'home',
    screenContext: {
      ...reliableContext,
      visibleLabels: ['Send'],
      actionableElements: []
    }
  });

  assert.equal(step.status, 'need_user_description');
  assert.equal(step.expectedNextScreen, null);
  assert.match(step.instruction, /can’t confirm/i);
});

test('uncertain screens ask for a general description without guessing', () => {
  const step = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'unknown',
    screenContext: null
  });

  test('weak or ambiguous category metadata cannot produce a specific step', () => {
    for (const screenContext of [
      {
        ...reliableContext,
        confidence: 74,
        actionableElements: ['Send|bottom|left']
      },
      {
        ...reliableContext,
        ambiguous: true,
        actionableElements: ['Send|bottom|left']
      }
    ]) {
      const step = selectScreenStep({
        intent: sendIntent,
        screenCategory: 'home',
        screenContext
      });

      assert.equal(step.screenCategory, 'unknown');
      assert.equal(step.status, 'need_user_description');
      assert.equal(step.expectedNextScreen, null);
    }
  });

  assert.equal(step.status, 'need_user_description');
  assert.equal(step.expectedNextScreen, null);
  assert.match(step.instruction, /describe the buttons or headings/i);
});

test('sensitive screens safely pause a sending flow', () => {
  const step = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'security',
    screenContext: {
      actionableElements: ['Continue|middle|center']
    }
  });

  assert.equal(step.status, 'safe_pause');
  assert.equal(step.expectedNextScreen, null);
  assert.match(step.instruction, /never share them with Saathi/i);
});

test('advances one expected screen at a time and stops at completion', () => {
  const recipientStep = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'recipient_selection',
    screenContext: { ...reliableContext, actionableElements: [] }
  });
  const amountStep = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'amount_entry',
    screenContext: { ...reliableContext, actionableElements: [] }
  });
  const completionStep = selectScreenStep({
    intent: sendIntent,
    screenCategory: 'confirmation',
    screenContext: { ...reliableContext, actionableElements: [] }
  });

  assert.equal(recipientStep.expectedNextScreen, 'amount_entry');
  assert.equal(recipientStep.status, 'awaiting_user');
  assert.equal(amountStep.expectedNextScreen, 'review_payment');
  assert.equal(amountStep.status, 'awaiting_user');
  assert.equal(completionStep.expectedNextScreen, null);
  assert.equal(completionStep.status, 'completed');
});
