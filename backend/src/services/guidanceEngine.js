import { SCREEN_CATEGORIES } from './screenCategories.js';

const guides = {
  home: {
    title: 'Understand your PayPal home screen',
    steps: [
      'Look at the options available on the current screen.',
      'Choose the feature that matches what you want to do.',
      'If you are unsure, ask Saathi before continuing.'
    ]
  },
  send_money: {
    title: 'Sending money safely',
    steps: [
      'Open the Send option in the official PayPal app.',
      'Follow PayPal\'s instructions to select the intended recipient.',
      'Verify the recipient and amount on PayPal before proceeding.',
      'Review all transaction details yourself before approving anything.'
    ]
  },
  recipient_selection: {
    title: 'Choose the intended recipient',
    steps: [
      'Find the recipient using PayPal\'s available search or selection options.',
      'Carefully verify the recipient\'s displayed identity.',
      'Do not rely on a name alone if other identifying details are available.',
      'Continue only when you are confident you selected the right person.'
    ]
  },
  amount_entry: {
    title: 'Enter and verify the amount',
    steps: [
      'Enter the amount directly in the official PayPal app.',
      'Check the currency and amount displayed on the screen.',
      'Review any displayed fees or conversion details.',
      'Do not continue if anything looks incorrect.'
    ]
  },
  review_payment: {
    title: 'Review before approving',
    steps: [
      'Verify the recipient shown by PayPal.',
      'Verify the amount and currency.',
      'Review any fees, exchange rates, or payment details shown.',
      'Make your own decision. Saathi will never approve the payment for you.'
    ]
  },
  confirmation: {
    title: 'Check the displayed payment status',
    steps: [
      'Read the status shown in the official PayPal app.',
      'If needed, open Activity to inspect the transaction.',
      'Do not assume a payment succeeded just because this screen was detected.',
      'Rely on PayPal\'s displayed transaction status, not Saathi\'s screen classification.'
    ]
  },
  activity: {
    title: 'Review transaction activity',
    steps: [
      'Open Activity in the official PayPal app.',
      'Select the relevant transaction yourself.',
      'Review its displayed status and details.',
      'Contact PayPal support if the status is unclear.'
    ]
  },
  wallet: {
    title: 'Review wallet options',
    steps: [
      'Open Wallet in the official PayPal app.',
      'Review the payment methods shown.',
      'Use only the official app to manage payment methods.',
      'Never share card numbers or security credentials with Saathi.'
    ]
  },
  security: {
    title: 'Protect your account',
    steps: [
      'Enter passwords or verification codes only in the official PayPal app when appropriate.',
      'Never share your password, PIN, or one-time verification code with Saathi.',
      'Do not follow unexpected requests to reveal security information.',
      'If you suspect account misuse, use PayPal\'s official security and support options.'
    ]
  },
  sign_in: {
    title: 'Sign in only through PayPal',
    steps: [
      'Sign in only inside the official PayPal app.',
      'Never enter your PayPal password into Saathi.',
      'If you did not open PayPal yourself, stop and verify the app icon and publisher.',
      'After signing in, return to Saathi only for general navigation help.'
    ]
  },
  unknown: {
    title: 'Let\'s identify what you need help with',
    steps: [
      'Read the options displayed in the official PayPal app.',
      'Describe your goal to Saathi in general terms.',
      'Do not include account numbers, recipient details, passwords, PINs, OTPs, or card information.',
      'If the screen looks ambiguous, verify recipient, amount, and fees yourself before continuing.'
    ]
  }
};

/**
 * Exact aliases and longer human-readable labels (including legacy Android wording).
 * More specific phrases are checked before broad ones.
 */
const exactAliases = {
  home: ['home', 'dashboard', 'main'],
  send_money: ['send_money', 'send money', 'send'],
  recipient_selection: [
    'recipient_selection',
    'recipient selection',
    'choose recipient',
    'select recipient',
    'contacts'
  ],
  amount_entry: ['amount_entry', 'amount entry', 'enter amount'],
  review_payment: [
    'review_payment',
    'review payment',
    'payment review',
    'review',
    'confirm details'
  ],
  confirmation: [
    'confirmation',
    'payment confirmation',
    'success',
    'payment success',
    'payment status'
  ],
  activity: ['activity', 'transaction history', 'transactions'],
  wallet: ['wallet', 'payment methods'],
  security: ['security', 'password', 'verification'],
  sign_in: ['sign_in', 'sign in', 'login', 'log in', 'welcome']
};

const phraseHints = [
  ['review_payment', [
    'payment review screen',
    'review your payment',
    'review payment',
    'confirm payment',
    'send payment',
    'pay now',
    'complete purchase'
  ]],
  ['security', [
    'sensitive / verification',
    'sensitive',
    'verification screen',
    'one-time password',
    'verification code',
    'security code',
    'confirm your identity'
  ]],
  ['sign_in', [
    'sign-in',
    'sign in',
    'welcome screen',
    'welcome to paypal'
  ]],
  ['amount_entry', [
    'amount entry may be visible',
    'enter amount',
    'how much'
  ]],
  ['recipient_selection', [
    'recipient selection',
    'choose recipient',
    'select recipient'
  ]],
  ['send_money', [
    'send money flow',
    'send money',
    'pay someone'
  ]],
  ['wallet', [
    'wallet screen',
    'banks and cards',
    'bank account'
  ]],
  ['activity', [
    'activity screen',
    'recent activity',
    'transactions'
  ]],
  ['home', [
    'home screen may be visible',
    'home screen',
    'dashboard'
  ]],
  ['confirmation', [
    'payment confirmation',
    'payment success'
  ]],
  ['unknown', [
    'not classified',
    'screen details unavailable',
    'paypal screen'
  ]]
];

function normalize(value) {
  return String(value)
    .trim()
    .toLowerCase()
    .replace(/[–—]/g, ' ')
    .replace(/-/g, '_')
    .replace(/\s+/g, ' ');
}

function resolveCategory(screenCategory) {
  const normalized = normalize(screenCategory);
  const underscored = normalized.replace(/\s+/g, '_');

  if (Object.prototype.hasOwnProperty.call(guides, underscored)) {
    return underscored;
  }

  for (const [category, values] of Object.entries(exactAliases)) {
    if (values.some(value => {
      const alias = normalize(value);
      return normalized === alias || underscored === alias.replace(/\s+/g, '_');
    })) {
      return category;
    }
  }

  for (const [category, phrases] of phraseHints) {
    if (phrases.some(phrase => normalized.includes(normalize(phrase)))) {
      return category;
    }
  }

  return 'unknown';
}

export function getGuidance(screenCategory) {
  if (typeof screenCategory !== 'string') {
    return {
      category: 'unknown',
      ...guides.unknown,
      matchedBy: 'fallback'
    };
  }

  const category = resolveCategory(screenCategory);
  const guide = guides[category] || guides.unknown;

  return {
    category,
    title: guide.title,
    steps: [...guide.steps],
    matchedBy: category === 'unknown' ? 'fallback' : 'rules'
  };
}

export function listSupportedCategories() {
  return SCREEN_CATEGORIES.filter(category => category !== 'unknown');
}

export { guides };
