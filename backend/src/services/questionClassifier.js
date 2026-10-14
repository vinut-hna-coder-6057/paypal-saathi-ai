import { isKnownCategory } from './screenCategories.js';

const intents = [
  {
    id: 'account_security',
    category: 'security',
    pattern: /\b(password|passcode|pin|otp|one[- ]time code|verification code|sign in|log in|reset credentials|account security)\b/i,
    retrievalTerms: 'PayPal password reset sign-in account security verification',
    guidance:
      'Enter security credentials only into the official PayPal app when you initiated the action. Never tell this companion your password, PIN, or one-time code.'
  },
  {
    id: 'transaction_history',
    category: 'activity',
    pattern: /\b(transaction history|payment history|payment status|recent payments?|previous payments?|past payments?|old payments?|older payments?|earlier payments?|previous transactions?|past transactions?|old transactions?|older transactions?|earlier transactions?|recent activity|past activity|earlier activity|view (?:my )?transactions?|see (?:my )?transactions?|find (?:my )?transactions?|transction history|transaction histroy|payment histroy)\b/i,
    retrievalTerms: 'PayPal Activity view previous transactions payment history recent payments',
    guidance:
      'Review the relevant transaction details and status shown in the official PayPal app. This companion cannot verify transaction status from your account.'
  },
  {
    id: 'refunds',
    category: 'activity',
    pattern: /\b(refund|refunded|return(?:ed)? (?:a )?payment|money back)\b/i,
    retrievalTerms: 'PayPal refund issue track returned payment refund status',
    guidance:
      'Check PayPal’s official Help Center for refund steps and review the transaction in the official app. This companion cannot issue or verify a refund.'
  },
  {
    id: 'disputes',
    category: 'activity',
    pattern: /\b(dispute|resolution centre|resolution center|unauthorized transaction|unauthorised transaction|chargeback)\b/i,
    retrievalTerms: 'PayPal Resolution Centre dispute report unauthorized transaction',
    guidance:
      'Use PayPal’s official Resolution Centre and follow the options shown there. This companion cannot file or decide a dispute.'
  },
  {
    id: 'payment_status',
    category: 'activity',
    pattern: /\b(pending payment|payment pending|payment on hold|payment unavailable|payment failed|failed payment|did (?:my )?payment|payment (?:go )?through)\b/i,
    retrievalTerms: 'PayPal payment status pending on hold failed unavailable',
    guidance:
      'Review the status shown in the official PayPal app. This companion cannot verify whether a payment went through or access your account.'
  },
  {
    id: 'account_limitations',
    category: 'general',
    pattern: /\b(account limitation|limited account|account limited|account restricted|remove (?:an? )?limitation|account access restricted)\b/i,
    retrievalTerms: 'PayPal account limitation restricted account steps Resolution Centre',
    guidance:
      'Review the notice in PayPal’s official Resolution Centre. This companion cannot inspect your account or determine why it is limited.'
  },
  {
    id: 'fees_and_currency',
    category: 'general',
    pattern: /\b(fees?|exchange rate|currency conversion|conversion rate|currency exchange)\b/i,
    retrievalTerms: 'PayPal fees currency conversion exchange rates India',
    guidance:
      'Check PayPal’s current fee and currency information for your country and transaction type. This companion cannot calculate account-specific fees.'
  },
  {
    id: 'receiving_payments',
    category: 'general',
    pattern: /\b(receive payments?|get paid|accept payments?|withdraw (?:money|funds))\b/i,
    retrievalTerms: 'PayPal India receive payments get paid withdraw funds',
    guidance:
      'Check PayPal’s official India Help Center for receiving and withdrawal requirements. This companion cannot inspect account eligibility or payment status.'
  },
  {
    id: 'account_setup',
    category: 'general',
    pattern: /\b(?:create|open|set up|setup|register)\s+(?:a\s+)?(?:(?:new|personal|business)\s+)*(?:paypal\s+)?(?:(?:personal|business)\s+)?account\b|\bsign up\b/i,
    retrievalTerms: 'PayPal India create account sign up setup',
    guidance:
      'Use the official PayPal app or website to review current account setup requirements. Do not share sign-in credentials with this companion.'
  },
  {
    id: 'bank_linking',
    category: 'wallet',
    pattern: /\b(bank account|link(?:ing)? (?:a )?bank|add (?:a )?bank|payment method|wallet|debit card|credit card|card)\b/i,
    retrievalTerms: 'PayPal Wallet link add bank account payment methods card',
    guidance:
      'Review payment-method options in the official PayPal app. Never share card numbers, passwords, PINs, or verification codes with this companion.'
  },
  {
    id: 'payment_sending',
    category: 'send_money',
    pattern: /\b(send(?:ing)? (?:money|a payment)|pay someone|make a payment|transfer money|choose a recipient|select a recipient|recipient selection|review (?:a )?payment|payment amount)\b/i,
    retrievalTerms: 'PayPal send money make payment recipient amount review',
    guidance:
      'Use the official PayPal app and follow the steps it currently displays. Verify the recipient and amount, including the currency and any fees, yourself before deciding whether to continue.'
  },
  {
    id: 'email_confirmation',
    category: 'security',
    pattern: /\b(confirm|verify) (?:my )?email(?: address)?\b/i,
    retrievalTerms: 'PayPal confirm verify email address account',
    guidance:
      'Use the email-confirmation flow shown in the official PayPal app. Do not share sign-in links or verification codes with this companion.'
  }
];

const contextualGuidance = {
  home:
    'I can’t tell which options are currently visible from this screen category alone. Describe your goal in general terms, and follow the instructions shown in the official PayPal app.',
  send_money:
    'This context may relate to sending a payment, but I can’t confirm the exact screen or transaction. Follow the instructions shown in the official PayPal app, and verify the recipient, amount, currency, and any fees yourself before deciding whether to continue.',
  recipient_selection:
    'This context may relate to choosing a recipient, but I can’t confirm who or what is displayed. Check the identity details provided by PayPal carefully before deciding whether to continue.',
  amount_entry:
    'This context may relate to entering a payment amount, but I can’t confirm what is displayed. Check the amount, currency, and any fees in the official PayPal app before deciding whether to continue.',
  review_payment:
    'This context may relate to reviewing a payment. Check the recipient, amount, currency, and any fees shown by PayPal. You make the final decision; this companion does not approve or execute transactions.',
  confirmation:
    'This context may relate to a payment confirmation, but it does not establish whether a payment succeeded. Rely on the status displayed by PayPal; this companion cannot verify transactions.',
  activity:
    'This context may relate to transaction activity. Review the details and status shown by PayPal; this companion cannot verify transactions in your account.',
  wallet:
    'This context may relate to payment methods. Review the options shown in the official PayPal app, and never share card numbers or security credentials with this companion.',
  security:
    'This context may relate to account security. Enter passwords or verification codes only in the official PayPal app when appropriate, and never share them with this companion.',
  sign_in:
    'This context may relate to signing in. Use only the official PayPal app for sign-in, and never share your password or verification codes with this companion.',
  unknown:
    'I can’t determine what is currently displayed from the available screen context. Describe your goal in general terms and follow the instructions shown in the official PayPal app.'
};

const questionContextGroups = {
  send_money: 'payment',
  wallet: 'wallet',
  activity: 'activity',
  security: 'security'
};

const screenContextGroups = {
  send_money: 'payment',
  recipient_selection: 'payment',
  amount_entry: 'payment',
  review_payment: 'payment',
  confirmation: 'payment',
  activity: 'activity',
  wallet: 'wallet',
  security: 'security',
  sign_in: 'security'
};

const DISCLAIMER =
  'Prototype guidance only. Verify details in PayPal yourself. This is rule-based help, not an AI model.';

function fallbackGuidance(screenCategory) {
  return screenCategory === 'unknown'
    ? 'I don’t have enough information to answer that safely. Ask about a PayPal task in general terms, or visit the official PayPal Help Center at https://www.paypal.com/in/cshelp.'
    : contextualGuidance[screenCategory];
}

export function inferQuestionIntent(question) {
  if (typeof question !== 'string' || !question.trim()) {
    return null;
  }
  return intents.find(intent => intent.pattern.test(question)) || null;
}

function isIntentScreenGroupMismatch(intent, screenCategory) {
  const questionGroup = intent && questionContextGroups[intent.category];
  const screenGroup = screenContextGroups[screenCategory];
  return Boolean(
    questionGroup && screenGroup && questionGroup !== screenGroup
  );
}

export function classifyHelpQuestion(question, screenCategory = 'unknown') {
  const context = isKnownCategory(screenCategory) ? screenCategory : 'unknown';

  if (typeof question !== 'string' || !question.trim()) {
    return {
      category: 'general',
      guidance: fallbackGuidance(context),
      disclaimer: DISCLAIMER,
      matchedBy: 'fallback'
    };
  }

  const intent = inferQuestionIntent(question);
  if (!intent) {
    return {
      category: 'general',
      guidance: fallbackGuidance(context),
      disclaimer: DISCLAIMER,
      matchedBy: 'fallback',
      intent: null
    };
  }

  const mismatchNotice = isIntentScreenGroupMismatch(intent, context)
    ? ' Your question may not match the reported screen context; I can’t verify what is currently displayed.'
    : '';

  return {
    category: intent.category,
    guidance: `${intent.guidance}${mismatchNotice}`,
    disclaimer: DISCLAIMER,
    matchedBy: 'rules',
    intent: intent.id
  };
}
