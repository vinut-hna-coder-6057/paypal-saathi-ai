const ACTION_LABELS = new Map([
  'Send',
  'Request',
  'Add card or bank',
  'Go to Resolution Centre',
  'Continue',
  'Next'
].map(label => [label.toLowerCase(), label]));

function safeActionElements(screenContext) {
  return (screenContext?.actionableElements || []).flatMap(value => {
    const [label, vertical, horizontal] = value.split('|');
    const canonicalLabel = ACTION_LABELS.get(label.toLowerCase());
    if (!canonicalLabel) {
      return [];
    }
    return [{ label: canonicalLabel, vertical, horizontal }];
  });
}

function positionDescription(element) {
  if (!element) {
    return null;
  }
  const parts = [];
  if (element.vertical === 'top') {
    parts.push('top');
  } else if (element.vertical === 'bottom') {
    parts.push('bottom');
  } else {
    parts.push('middle');
  }
  if (element.horizontal === 'left') {
    parts.push('left');
  } else if (element.horizontal === 'right') {
    parts.push('right');
  } else {
    parts.push('center');
  }
  return parts.join(' ');
}

function makeStep({
  screenCategory,
  actionElements,
  instruction,
  expectedNextScreen,
  status
}) {
  return {
    goal: 'payment_sending',
    screenCategory,
    availableActions: actionElements,
    instruction,
    expectedNextScreen,
    status
  };
}

export function selectScreenStep({
  intent,
  screenCategory,
  screenContext
}) {
  if (intent?.id !== 'payment_sending') {
    return null;
  }

  const actionElements = safeActionElements(screenContext);
  if (screenCategory === 'security' || screenCategory === 'sign_in') {
    return makeStep({
      screenCategory,
      actionElements,
      instruction: 'Pause here. Enter passwords or verification codes only in the official PayPal app; never share them with Saathi.',
      expectedNextScreen: null,
      status: 'safe_pause'
    });
  }

  const classificationIsReliable = screenContext
    && screenContext.confidence >= 75
    && screenContext.evidenceStrength !== 'insufficient'
    && !screenContext.ambiguous;
  if (screenCategory === 'unknown' || !classificationIsReliable) {
    return makeStep({
      screenCategory: 'unknown',
      actionElements,
      instruction: 'I can’t identify this screen reliably. Please describe the buttons or headings you see in general terms; do not include account or payment details.',
      expectedNextScreen: null,
      status: 'need_user_description'
    });
  }

  if (screenCategory === 'home') {
    const sendAction = actionElements.find(
      element => element.label === 'Send'
    );
    if (!sendAction) {
      return makeStep({
        screenCategory,
        actionElements,
        instruction: 'I can’t confirm which visible control starts sending money. Please describe the general button labels you see; do not include personal or payment details.',
        expectedNextScreen: null,
        status: 'need_user_description'
      });
    }
    const position = positionDescription(sendAction);
    return makeStep({
      screenCategory,
      actionElements,
      instruction: `Let’s send money together. Look near the ${position} of your screen and tap the “${sendAction.label}” button.`,
      expectedNextScreen: 'recipient_selection',
      status: 'awaiting_user'
    });
  }

  const steps = {
    send_money: {
      instruction: 'Look for PayPal’s recipient selection controls. Choose the intended recipient in PayPal and verify their identity before continuing.',
      expectedNextScreen: 'recipient_selection',
      status: 'awaiting_user'
    },
    recipient_selection: {
      instruction: 'Choose the intended recipient using PayPal’s own recipient picker, and verify the displayed identity before continuing.',
      expectedNextScreen: 'amount_entry',
      status: 'awaiting_user'
    },
    amount_entry: {
      instruction: 'Enter the intended amount only in PayPal, then check the currency and any fees shown there. Do not send those details to Saathi.',
      expectedNextScreen: 'review_payment',
      status: 'awaiting_user'
    },
    review_payment: {
      instruction: 'Pause and check the recipient, amount, currency, and fees in PayPal yourself. Continue only if they are correct; Saathi will not approve or submit the payment.',
      expectedNextScreen: 'confirmation',
      status: 'awaiting_user'
    },
    confirmation: {
      instruction: 'Check the status displayed in PayPal. Saathi cannot verify whether a payment succeeded; this step-by-step guidance is complete.',
      expectedNextScreen: null,
      status: 'completed'
    }
  };
  const step = steps[screenCategory];
  if (step) {
    return makeStep({
      screenCategory,
      actionElements,
      ...step
    });
  }

  return makeStep({
    screenCategory,
    actionElements,
    instruction: 'I can’t confirm the next control on this screen. Please describe the visible button labels in general terms; do not include personal or payment details.',
    expectedNextScreen: null,
    status: 'need_user_description'
  });
}
