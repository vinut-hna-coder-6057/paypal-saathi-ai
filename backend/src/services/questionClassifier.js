const categories = [
  {
    category: 'send_money',
    terms: ['send money', 'send payment', 'pay someone', 'transfer money'],
    guidance: 'Open PayPal and look for its Send option. Follow the instructions shown in PayPal, verify the recipient and amount carefully, and make the final decision yourself.'
  },
  {
    category: 'wallet',
    terms: ['wallet', 'bank account', 'card', 'payment method'],
    guidance: 'Open the Wallet area in PayPal and review the options displayed. Never share card numbers, passwords, PINs, or verification codes with this companion.'
  },
  {
    category: 'activity',
    terms: ['activity', 'transaction history', 'recent payment', 'payment status'],
    guidance: 'Open Activity in PayPal and review the relevant transaction. This companion cannot verify transaction status from your account.'
  },
  {
    category: 'security',
    terms: ['password', 'otp', 'one-time code', 'verification code', 'pin', 'passcode'],
    guidance: 'Enter security credentials only into the official PayPal app when you initiated the action. Never tell this companion your password, PIN, or one-time code.'
  }
];

export function classifyHelpQuestion(question) {
  const normalized = question.toLowerCase();

  for (const item of categories) {
    if (item.terms.some(term => normalized.includes(term))) {
      return {
        category: item.category,
        guidance: item.guidance
      };
    }
  }

  return {
    category: 'general',
    guidance: 'Describe what you are trying to do in general terms. Do not include account numbers, recipient details, payment amounts, passwords, PINs, or verification codes.'
  };
}
