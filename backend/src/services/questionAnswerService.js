import {
  classifyHelpQuestion,
  inferQuestionIntent
} from './questionClassifier.js';
import {
  embedText,
  getEmbeddingModelName
} from './embeddingService.js';
import { getKnowledgeStore } from './vectorStore.js';
import {
  generateGroundedAnswer,
  ModelProviderError
} from './modelAdapter.js';
import { sanitizeScreenContext } from './screenContext.js';
import { selectScreenStep } from './screenAwareGuidance.js';
import { recordPipelineMetric } from './pipelineMetrics.js';

const DEFAULT_MINIMUM_SIMILARITY = 0.45;
const MAXIMUM_SCORE_GAP = 0.2;
const SUCCESS_DISCLAIMER =
  'Answer based on the cited PayPal Help Center documentation. Verify current details in PayPal; Saathi cannot access your account or verify transactions.';
const RULE_FALLBACK_NOTICE =
  'The local answer model is unavailable. This is general rule-based guidance, not a source-grounded answer.';
const INSUFFICIENT_EVIDENCE =
  'The available official PayPal Help documentation does not provide enough evidence to answer this. Please check the PayPal Help Center directly; I will not guess account-specific or transaction details.';
const RETRIEVAL_UNAVAILABLE =
  'The local verified PayPal Help index is unavailable right now, so I cannot provide a source-grounded answer. Please check the PayPal Help Center directly.';
const SENSITIVE_QUESTION =
  'For your safety, remove passwords, PINs, one-time codes, personal contact details, account identifiers, and payment amounts before asking. I did not send this question for retrieval or model generation.';
const ACCOUNT_ACCESS_LIMITATION =
  'I can’t access your PayPal account or verify balances, recipients, or payment outcomes. Check the status directly in the official PayPal app; do not share account details here.';

const CREDENTIAL_VALUE = /\b(password|passcode|pin|otp|verification code|security code|cvv|cvc|recovery code|card number|account number)\b\s*(?:is\s+|[:=]\s*)\S+/i;
const LONG_NUMBER = /(?<!\d)(?:\d[ -]?){13,19}(?!\d)/;
const CODE_VALUE = /\b(otp|verification code|security code|cvv|cvc|pin)\b\s*(?:is\s+|[:=]\s*)?\d{3,8}\b/i;
const EMAIL_ADDRESS = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i;
const PHONE_NUMBER = /(?:\+?\d[\d ()-]{8,}\d)/;
const SENSITIVE_AMOUNT =
  /(?:\b(?:amount|balance|payment amount|transfer amount)\b\s*(?:is\s+|[:=]\s*)?(?:₹|INR|USD|EUR|GBP)?\s*\d[\d,.]*|(?:₹|INR|USD|EUR|GBP|Rs\.?|रु\.?|\$|€|£)\s*\d[\d,.]*)/i;
const SENSITIVE_IDENTIFIER = /\b(account|transaction|reference|recipient)\s*(?:id|number|details?)\s*(?:is\s+|[:=]\s*)\S+/i;
const PRIVATE_ACCOUNT_LOOKUP = /\b(?:what(?:'s| is)|show(?: me)?|tell me|check|verify|confirm)\s+(?:my|the)\s+(?:current )?(?:balance|payment status|transaction status|account details|recipient details)\b|\b(?:did|has)\s+my\s+(?:payment|transaction)\s+(?:go through|succeed|complete)\b/i;
const NAVIGATION_TARGETS = {
  account_security: {
    goal: 'account security tasks',
    labels: ['Security', 'Settings', 'Help', 'Profile']
  },
  transaction_history: {
    goal: 'finding previous transactions',
    labels: ['Activity', 'Transactions', 'Payments']
  },
  refunds: {
    goal: 'finding refund information',
    labels: ['Activity', 'Transactions', 'Payments', 'Help']
  },
  disputes: {
    goal: 'finding dispute information',
    labels: ['Activity', 'Transactions', 'Payments', 'Help']
  },
  payment_status: {
    goal: 'finding payment information',
    labels: ['Activity', 'Transactions', 'Payments']
  },
  account_limitations: {
    goal: 'account limitation information',
    labels: ['Security', 'Settings', 'Help']
  },
  fees_and_currency: {
    goal: 'fee and currency information',
    labels: ['Payments', 'Help']
  },
  receiving_payments: {
    goal: 'receiving payment information',
    labels: ['Wallet', 'Payments', 'Activity', 'Help']
  },
  account_setup: {
    goal: 'account setup information',
    labels: ['Home', 'Profile', 'Settings', 'Help']
  },
  bank_linking: {
    goal: 'linking a bank account',
    labels: ['Wallet', 'Bank Accounts', 'Cards', 'Payments']
  },
  payment_sending: {
    goal: 'sending money',
    labels: ['Send', 'Payments', 'Wallet']
  },
  email_confirmation: {
    goal: 'email confirmation settings',
    labels: ['Security', 'Settings', 'Profile']
  }
};

export class RetrievalUnavailableError extends Error {
  constructor(cause) {
    super('Local documentation retrieval is unavailable.', { cause });
    this.name = 'RetrievalUnavailableError';
    this.code = 'RETRIEVAL_UNAVAILABLE';
  }
}

function containsLikelySensitiveValue(question) {
  return CREDENTIAL_VALUE.test(question)
    || LONG_NUMBER.test(question)
    || CODE_VALUE.test(question)
    || EMAIL_ADDRESS.test(question)
    || PHONE_NUMBER.test(question)
    || SENSITIVE_AMOUNT.test(question)
    || SENSITIVE_IDENTIFIER.test(question);
}

function getMinimumSimilarity() {
  const configured = Number(
    process.env.PAYPAL_RAG_MIN_SIMILARITY ?? DEFAULT_MINIMUM_SIMILARITY
  );
  if (!Number.isFinite(configured) || configured < 0 || configured > 1) {
    throw new RangeError('PAYPAL_RAG_MIN_SIMILARITY must be between 0 and 1.');
  }
  return configured;
}

function ruleResult(question, screenCategory) {
  return classifyHelpQuestion(question, screenCategory);
}

function screenNavigationGuidance(intent, screenCategory, safeScreenContext) {
  const target = NAVIGATION_TARGETS[intent?.id];
  if (!target) {
    return null;
  }

  const reliableContext = safeScreenContext
    && safeScreenContext.confidence >= 75
    && safeScreenContext.evidenceStrength !== 'insufficient'
    && !safeScreenContext.ambiguous;
  if (!reliableContext) {
    return `I can answer the task from the documentation, but the current screen is uncertain. Which navigation labels do you see?`;
  }

  const visibleLabels = new Set(safeScreenContext?.visibleLabels || []);
  const visibleLabel = target.labels.find(label =>
    visibleLabels.has(label)
  );
  if (visibleLabel) {
    return `From the current screen, “${visibleLabel}” is visible and may be a place to look for ${target.goal}. This is a suggested place to check, not a confirmed Android-app route.`;
  }

  if (screenCategory === 'unknown') {
    return `I can answer the task from the documentation, but the current screen is uncertain. Which navigation labels do you see?`;
  }

  return `I can answer the task from the documentation, but I cannot confirm the next in-app control from the current ${screenCategory} screen. Which navigation labels do you see?`;
}

async function retrieveRelevantEvidence(question, intent, signal) {
  const throwIfCancelled = () => {
    if (signal?.aborted) {
      const error = new Error('Question request was cancelled.');
      error.code = 'REQUEST_CANCELLED';
      throw error;
    }
  };

  throwIfCancelled();

  try {
    const store = getKnowledgeStore();
    const embeddingModel = getEmbeddingModelName();

    if (!store.embeddingCompatibility(embeddingModel).compatible) {
      throw new Error(
        'Knowledge index embeddings do not match the configured model.'
      );
    }

    if (store.countChunks(embeddingModel) === 0) {
      return [];
    }

    const retrievalText = intent
      ? `${question}\n${intent.retrievalTerms}`
      : question;

    const queryVector = await embedText(retrievalText);

    throwIfCancelled();

    return store.search(queryVector, 12, embeddingModel, retrievalText);
  } catch (error) {
    if (error?.code === 'REQUEST_CANCELLED') {
      throw error;
    }

    throw new RetrievalUnavailableError(error);
  }
}

function logPipelineFailure(stage, error) {
  const code = typeof error?.code === 'string'
    ? error.code
    : error?.name || 'UNKNOWN_ERROR';
  console.error(`[question-answer] ${stage} unavailable (${code})`);
}

function sourcesForEvidence(evidence) {
  const sources = new Map();
  for (const chunk of evidence) {
    const source = sources.get(chunk.url);
    if (!source) {
      sources.set(chunk.url, {
        ...chunk,
        heading: chunk.heading,
        text: chunk.text
      });
      continue;
    }

    if (!source.heading.split(' | ').includes(chunk.heading)) {
      source.heading += ` | ${chunk.heading}`;
    }
    if (!source.text.includes(chunk.text) && source.text.length < 2_000) {
      source.text += `\n${chunk.text}`.slice(0, 2_000 - source.text.length);
    }
  }
  return [...sources.values()].slice(0, 3);
}

function insufficientResult(category, guidance = INSUFFICIENT_EVIDENCE) {
  return {
    category,
    guidance,
    disclaimer: SUCCESS_DISCLAIMER,
    matchedBy: 'fallback'
  };
}

function withSources(answer, evidence) {
  const sources = sourcesForEvidence(evidence);
  if (sources.length === 0) {
    return answer;
  }

  const citations = sources
    .map((source, index) =>
      `[${index + 1}] ${source.title} — ${source.heading} (${source.url})`
    )
    .join('\n');
  return `${answer.trim()}\n\nSources:\n${citations}`;
}

function discloseWebScopedSteps(answer, evidence) {
  const hasWebScopedSteps = evidence.some(item =>
    /\b(?:on|from|using) (?:the )?web(?:\s+browser)?\b|\bwebsite\b/i
      .test(item.text)
  );
  if (!hasWebScopedSteps || /\b(?:Android|mobile) app\b/i.test(answer)) {
    return answer;
  }

  return `${answer}\n\nThe cited steps are for PayPal on the web; the source does not confirm the same navigation path in the Android app.`;
}

function validateGeneratedAnswer(answer, sourceCount) {
  if (typeof answer !== 'string' || !answer.trim() || answer.length > 2_000) {
    throw new ModelProviderError(
      'Local model returned an invalid answer size.',
      'MODEL_INVALID_RESPONSE'
    );
  }

  const citations = [...answer.matchAll(/\[(\d+)\]/g)]
    .map(match => Number(match[1]));
  if (citations.length === 0
      || citations.some(index => index < 1 || index > sourceCount)
      || /https?:\/\//i.test(answer)
      || /\b(i (?:verified|confirmed|checked) your account|your payment (?:was|has been) successful|i sent the payment)\b/i.test(answer)
      || CREDENTIAL_VALUE.test(answer)
      || LONG_NUMBER.test(answer)
      || CODE_VALUE.test(answer)
      || EMAIL_ADDRESS.test(answer)
      || PHONE_NUMBER.test(answer)
      || SENSITIVE_AMOUNT.test(answer)
      || SENSITIVE_IDENTIFIER.test(answer)) {
    throw new ModelProviderError(
      'Local model returned an unvalidated answer.',
      'MODEL_INVALID_RESPONSE'
    );
  }
  return answer.trim();
}

function modelReportsInsufficientEvidence(answer) {
  if (typeof answer !== 'string'
      || !/\b(?:evidence|excerpts?|documentation|sources?)\b.{0,140}\b(?:do not|does not|don't|doesn't|cannot|can't|insufficient|not enough|not directly)\b.{0,100}\b(?:answer|support|contain|provide)\b/i.test(answer)) {
    return false;
  }

  const containsActionableGuidance =
    /\b(?:you can (?:go|open|find|view|download|select)|how to (?:find|view|download|reset|link|send)|to (?:find|view|download|reset|link|send)|go to|click|select|choose|enter|follow the prompts)\b/i
      .test(answer);
  return !containsActionableGuidance;
}

export function createQuestionAnswerService({
  retrieve = retrieveRelevantEvidence,
  generate = generateGroundedAnswer,
  classify = ruleResult,
  minimumSimilarity = getMinimumSimilarity()
} = {}) {
  return async function answerQuestion({
    question,
    screenCategory = 'unknown',
    screenContext,
    signal
  }) {
    
const requestStartedAt = Date.now();

try {
  if (signal?.aborted) {
    throw new ModelProviderError(
      'Question request was cancelled.',
      'REQUEST_CANCELLED'
    );
  }

  const ruleAnswer = classify(question, screenCategory);

    const intent = inferQuestionIntent(question);
    const safeScreenContext = sanitizeScreenContext(
      screenContext,
      screenCategory
    );

    if (containsLikelySensitiveValue(question)) {
      recordPipelineMetric('fallbacks');
      return {
        category: ruleAnswer.category,
        guidance: SENSITIVE_QUESTION,
        disclaimer: ruleAnswer.disclaimer,
        matchedBy: 'fallback',
        nextStep: null
      };
    }

    if (typeof question === 'string'
        && PRIVATE_ACCOUNT_LOOKUP.test(question)) {
      recordPipelineMetric('fallbacks');
      return {
        category: ruleAnswer.category,
        guidance: ACCOUNT_ACCESS_LIMITATION,
        disclaimer: ruleAnswer.disclaimer,
        matchedBy: 'fallback',
        nextStep: null
      };
    }

    const nextStep = selectScreenStep({
      intent,
      screenCategory,
      screenContext: safeScreenContext
    });
    if (nextStep) {
      recordPipelineMetric('fallbacks');
      return {
        category: ruleAnswer.category,
        guidance: nextStep.instruction,
        disclaimer: ruleAnswer.disclaimer,
        matchedBy: 'screen_rules',
        nextStep
      };
    }

    let evidence;
    const retrievalStartedAt = Date.now();
    try {
      evidence = await retrieve(question, intent, signal);
      if (signal?.aborted) {
  const error = new Error('Question request was cancelled.');
  error.code = 'REQUEST_CANCELLED';
  throw error;
}
      recordPipelineMetric(
        'retrievals',
        Date.now() - retrievalStartedAt
      );
    } catch (error) {
      if (!(error instanceof RetrievalUnavailableError)) {
        throw error;
      }
      recordPipelineMetric('retrievalFailures');
      recordPipelineMetric('fallbacks');
      logPipelineFailure('retrieval', error);
      return insufficientResult(ruleAnswer.category, RETRIEVAL_UNAVAILABLE);
    }

    if (!Array.isArray(evidence)) {
      throw new TypeError('Evidence retriever must return an array.');
    }

    const scoredEvidence = evidence
      .filter(item => Number.isFinite(item.score)
        && Number.isFinite(item.semanticScore ?? item.score)
        && typeof item.text === 'string'
        && typeof item.url === 'string'
        && typeof item.title === 'string')
      .sort((left, right) => right.score - left.score);
    const bestScore = scoredEvidence[0]?.score ?? -1;
    const relevantEvidence = sourcesForEvidence(scoredEvidence.filter(item =>
      (item.semanticScore ?? item.score) >= minimumSimilarity
        && item.score >= bestScore - MAXIMUM_SCORE_GAP
    ).slice(0, 10));

    if (relevantEvidence.length === 0) {
      recordPipelineMetric('fallbacks');
      return insufficientResult(ruleAnswer.category);
    }

    const generationStartedAt = Date.now();
    try {
      const generated = await generate({
        question,
        intent: intent?.id ?? null,
        evidence: relevantEvidence,
        signal
      });
      if (modelReportsInsufficientEvidence(generated)) {
        recordPipelineMetric(
          'modelGenerations',
          Date.now() - generationStartedAt
        );
        recordPipelineMetric('fallbacks');
        return insufficientResult(ruleAnswer.category);
      }
      const validatedAnswer = discloseWebScopedSteps(
        validateGeneratedAnswer(
          generated,
          relevantEvidence.length
        ),
        relevantEvidence
      );
      if (validatedAnswer.length > 2_000) {
        throw new ModelProviderError(
          'Local model answer plus source-scope notice exceeded the size limit.',
          'MODEL_INVALID_RESPONSE'
        );
      }
      recordPipelineMetric(
        'modelGenerations',
        Date.now() - generationStartedAt
      );
      return {
        category: ruleAnswer.category,
        guidance: withSources([
          screenNavigationGuidance(intent, screenCategory, safeScreenContext),
          validatedAnswer
        ].filter(Boolean).join('\n\n'), relevantEvidence),
        disclaimer: SUCCESS_DISCLAIMER,
        matchedBy: 'model'
      };
   
    } catch (error) {
      if (error?.code === 'REQUEST_CANCELLED' || signal?.aborted) {
        const cancelledError = new Error('Question request was cancelled.');
        cancelledError.code = 'REQUEST_CANCELLED';
        throw cancelledError;
      }

      if (!(error instanceof ModelProviderError)) {
        throw error;
      }

      recordPipelineMetric('modelFailures');
      if (error.code === 'MODEL_TIMEOUT') {
        recordPipelineMetric('modelTimeouts');
      }
      recordPipelineMetric('fallbacks');
      logPipelineFailure('local model', error);
      return {
        category: ruleAnswer.category,
        guidance: `${RULE_FALLBACK_NOTICE}\n\n${ruleAnswer.guidance}`,
        disclaimer: ruleAnswer.disclaimer,
        matchedBy: 'rules'
      };
    }

    } finally {
      recordPipelineMetric('requests', Date.now() - requestStartedAt);
    }
  };
}

export const answerQuestion = createQuestionAnswerService();
