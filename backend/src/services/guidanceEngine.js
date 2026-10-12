const guides = {
  home: {
    title: "Understand your PayPal home screen",
    steps: [
      "Look at the options available on the current screen.",
      "Choose the feature that matches what you want to do.",
      "If you are unsure, ask Saathi before continuing."
    ]
  },
  send_money: {
    title: "Sending money safely",
    steps: [
      "Open the Send option in the official PayPal app.",
      "Follow PayPal's instructions to select the intended recipient.",
      "Verify the recipient and amount on PayPal before proceeding.",
      "Review all transaction details yourself before approving anything."
    ]
  },
  recipient_selection: {
    title: "Choose the intended recipient",
    steps: [
      "Find the recipient using PayPal's available search or selection options.",
      "Carefully verify the recipient's displayed identity.",
      "Do not rely on a name alone if other identifying details are available.",
      "Continue only when you are confident you selected the right person."
    ]
  },
  amount_entry: {
    title: "Enter and verify the amount",
    steps: [
      "Enter the amount directly in the official PayPal app.",
      "Check the currency and amount displayed on the screen.",
      "Review any displayed fees or conversion details.",
      "Do not continue if anything looks incorrect."
    ]
  },
  review_payment: {
    title: "Review before approving",
    steps: [
      "Verify the recipient shown by PayPal.",
      "Verify the amount and currency.",
      "Review any fees, exchange rates, or payment details shown.",
      "Make your own decision. Saathi will never approve the payment for you."
    ]
  },
  confirmation: {
    title: "Check the displayed payment status",
    steps: [
      "Read the status shown in the official PayPal app.",
      "If needed, open Activity to inspect the transaction.",
      "Do not assume a payment succeeded just because this screen was detected.",
      "Rely on PayPal's displayed transaction status, not Saathi's screen classification."
    ]
  },
  activity: {
    title: "Review transaction activity",
    steps: [
      "Open Activity in the official PayPal app.",
      "Select the relevant transaction yourself.",
      "Review its displayed status and details.",
      "Contact PayPal support if the status is unclear."
    ]
  },
  wallet: {
    title: "Review wallet options",
    steps: [
      "Open Wallet in the official PayPal app.",
      "Review the payment methods shown.",
      "Use only the official app to manage payment methods.",
      "Never share card numbers or security credentials with Saathi."
    ]
  },
  security: {
    title: "Protect your account",
    steps: [
      "Enter passwords or verification codes only in the official PayPal app when appropriate.",
      "Never share your password, PIN, or one-time verification code with Saathi.",
      "Do not follow unexpected requests to reveal security information.",
      "If you suspect account misuse, use PayPal's official security and support options."
    ]
  },
  unknown: {
    title: "Let's identify what you need help with",
    steps: [
      "Read the options displayed in the official PayPal app.",
      "Describe your goal to Saathi in general terms.",
      "Do not include account numbers, recipient details, passwords, PINs, OTPs, or card information."
    ]
  }
};

const aliases = {
  home: ["home", "dashboard", "main"],
  send_money: ["send_money", "send money", "send"],
  recipient_selection: [
    "recipient_selection", "recipient selection", "choose recipient",
    "select recipient", "contacts"
  ],
  amount_entry: ["amount_entry", "amount entry", "enter amount"],
  review_payment: [
    "review_payment", "review payment", "payment review",
    "review", "confirm details"
  ],
  confirmation: [
    "confirmation", "payment confirmation", "success",
    "payment success", "payment status"
  ],
  activity: ["activity", "transaction history", "transactions"],
  wallet: ["wallet", "payment methods"],
  security: ["security", "login", "password", "verification"]
};

export function getGuidance(screenCategory) {
  if (typeof screenCategory !== "string") {
    return { category: "unknown", ...guides.unknown };
  }

  const normalized = screenCategory
    .trim()
    .toLowerCase()
    .replace(/-/g, "_");

  for (const [category, values] of Object.entries(aliases)) {
    if (
      normalized === category ||
      values.some(value => normalized === value.toLowerCase().replace(/-/g, "_"))
    ) {
      return { category, ...guides[category] };
    }
  }

  return { category: "unknown", ...guides.unknown };
}
