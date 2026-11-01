import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign as signJwt } from 'node:crypto';
import http from 'node:http';
import { createApp } from '../src/app.js';
import { createClassifyRouter } from '../src/routes/classify.js';
import express from 'express';
import { errorHandler } from '../src/middleware/errors.js';
process.env.PAYPAL_KB_DB_PATH = ':memory:';

const { publicKey, privateKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048
});
const publicKeyPem = publicKey.export({
  type: 'spki',
  format: 'pem'
}).toString();
const AUTH_ISSUER = 'https://identity.example.test/';
const AUTH_AUDIENCE = 'paypal-saathi-api';
const REQUIRED_SCOPE = 'saathi:guidance';

function protectedEnvironment() {
  return {
    NODE_ENV: 'production',
    HOST: '127.0.0.1',
    AUTH_PUBLIC_KEY_PEM: publicKeyPem,
    AUTH_ISSUER,
    AUTH_AUDIENCE
  };
}

function createAccessToken(overrides = {}) {
  const nowSeconds = Math.floor(Date.now() / 1_000);
  const header = Buffer.from(JSON.stringify({
    alg: 'RS256',
    typ: 'JWT'
  })).toString('base64url');
  const claims = Buffer.from(JSON.stringify({
    iss: AUTH_ISSUER,
    sub: 'test-user',
    aud: AUTH_AUDIENCE,
    iat: nowSeconds,
    exp: nowSeconds + 300,
    scope: REQUIRED_SCOPE,
    ...overrides
  })).toString('base64url');
  const signingInput = `${header}.${claims}`;
  const signature = signJwt(
    'RSA-SHA256',
    Buffer.from(signingInput),
    privateKey
  ).toString('base64url');
  return `${signingInput}.${signature}`;
}

function listen(app) {
  return new Promise((resolve, reject) => {
    const server = http.createServer(app);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      resolve({ server, port });
    });
    server.on('error', reject);
  });
}

async function request(port, method, path, body, authorization) {
  const payload = body === undefined ? null : JSON.stringify(body);
  const headers = {};
  if (payload !== null) {
    headers['Content-Type'] = 'application/json';
  }
  if (authorization) {
    headers.Authorization = authorization;
  }

  const response = await fetch(`http://127.0.0.1:${port}${path}`, {
    method,
    headers: Object.keys(headers).length > 0 ? headers : undefined,
    body: payload
  });

  const text = await response.text();
  let json = null;

  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = null;
  }

  return { status: response.status, json, text };
}

test('health and readiness endpoints return only minimal public status', async () => {
  const app = createApp({ environment: protectedEnvironment() });
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'GET', '/health');
    assert.equal(status, 200);
    assert.equal(json.status, 'ok');
    assert.deepEqual(Object.keys(json), ['status']);
    assert.equal(json.auth, undefined);
    assert.equal(json.configuration, undefined);
    assert.equal(json.pipelineMetrics, undefined);

    const readiness = await request(port, 'GET', '/health/ready');
    assert.equal(readiness.status, 200);
    assert.deepEqual(readiness.json, { status: 'ready' });
  } finally {
    server.close();
  }
});

test('protected API endpoints reject requests without authentication', async () => {
  const app = createApp({ environment: protectedEnvironment() });
  const { server, port } = await listen(app);

  try {
    for (const [path, body] of [
      ['/api/guidance', { screenCategory: 'home' }],
      ['/api/classify', { question: 'How do I send money?' }]
    ]) {
      const { status, json } = await request(port, 'POST', path, body);
      assert.equal(status, 401);
      assert.deepEqual(json, { error: 'Authentication required' });
    }
  } finally {
    server.close();
  }
});

test('protected API endpoints reject invalid and expired bearer tokens', async () => {
  const app = createApp({ environment: protectedEnvironment() });
  const { server, port } = await listen(app);
  const nowSeconds = Math.floor(Date.now() / 1_000);

  try {
    const invalidAuthorizations = [
      'Bearer not.a.valid-token',
      `Bearer ${createAccessToken({ aud: 'another-api' })}`,
      `Bearer ${createAccessToken({
        iss: 'https://untrusted.example.test/'
      })}`
    ];
    for (const authorization of invalidAuthorizations) {
      const invalid = await request(
        port,
        'POST',
        '/api/guidance',
        { screenCategory: 'home' },
        authorization
      );
      assert.equal(invalid.status, 401);
    }

    const [header, claims, signature] = createAccessToken().split('.');
    const invalidSignature = await request(
      port,
      'POST',
      '/api/guidance',
      { screenCategory: 'home' },
      `Bearer ${header}.${claims}.${signature.slice(0, -2)}aa`
    );
    assert.equal(invalidSignature.status, 401);

    const expiredToken = createAccessToken({
      iat: nowSeconds - 60,
      exp: nowSeconds - 1
    });
    const expired = await request(
      port,
      'POST',
      '/api/guidance',
      { screenCategory: 'home' },
      `Bearer ${expiredToken}`
    );
    assert.equal(expired.status, 401);
  } finally {
    server.close();
  }
});

test('valid bearer authentication authorizes guidance and RAG endpoints', async () => {
  const app = createApp({ environment: protectedEnvironment() });
  const { server, port } = await listen(app);
  const authorization = `Bearer ${createAccessToken()}`;

  try {
    const guidance = await request(
      port,
      'POST',
      '/api/guidance',
      { screenCategory: 'review_payment' },
      authorization
    );
    assert.equal(guidance.status, 200);
    assert.equal(guidance.json.category, 'review_payment');

    const answer = await request(
      port,
      'POST',
      '/api/classify',
      { question: 'How do I send money?' },
      authorization
    );
    assert.equal(answer.status, 200);
    assert.equal(answer.json.category, 'send_money');
  } finally {
    server.close();
  }
});

test('valid authentication without the guidance scope is forbidden', async () => {
  const app = createApp({ environment: protectedEnvironment() });
  const { server, port } = await listen(app);
  const token = createAccessToken({ scope: 'profile:read' });

  try {
    const response = await request(
      port,
      'POST',
      '/api/guidance',
      { screenCategory: 'home' },
      `Bearer ${token}`
    );
    assert.equal(response.status, 403);
    assert.deepEqual(response.json, { error: 'Insufficient permissions' });
  } finally {
    server.close();
  }
});

test('POST /api/guidance accepts canonical categories', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'POST', '/api/guidance', {
      screenCategory: 'review_payment'
    });

    assert.equal(status, 200);
    assert.equal(json.category, 'review_payment');
    assert.ok(Array.isArray(json.steps));
    assert.ok(json.safetyNote);
    assert.ok(json.title);
  } finally {
    server.close();
  }
});

test('unexpected server errors return a generic response without internal details', async () => {
  const app = express();

  app.get('/test-error', (_req, _res, next) => {
    const error = new Error(
      'sensitive internal database path C:\\internal\\secret.js'
    );
    next(error);
  });

  app.use((error, _req, res, _next) => {
    // Use the actual production error handler.
    return errorHandler(error, _req, res, _next);
  });

  const { server, port } = await listen(app);

  try {
    const response = await fetch(
      `http://127.0.0.1:${port}/test-error`
    );
    const text = await response.text();

    assert.equal(response.status, 500);
    assert.deepEqual(JSON.parse(text), {
      error: 'Internal server error'
    });
    assert.doesNotMatch(
      text,
      /sensitive internal database path|secret\.js|stack/i
    );
  } finally {
    server.closeAllConnections?.();
    await new Promise(resolve => server.close(resolve));
  }
});


test('POST /api/guidance rejects empty screenCategory', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'POST', '/api/guidance', {
      screenCategory: '   '
    });

    assert.equal(status, 400);
    assert.match(json.error, /screenCategory/i);
  } finally {
    server.close();
  }
});

test('POST /api/guidance rejects oversized screenCategory', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'POST', '/api/guidance', {
      screenCategory: 'x'.repeat(81)
    });

    assert.equal(status, 413);
    assert.match(json.error, /80 characters/i);
  } finally {
    server.close();
  }
});

test('POST /api/guidance maps unknown categories safely', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'POST', '/api/guidance', {
      screenCategory: 'totally_made_up'
    });

    assert.equal(status, 200);
    assert.equal(json.category, 'unknown');
    assert.ok(json.steps.length > 0);
  } finally {
    server.close();
  }
});

test('POST /api/classify validates and classifies questions', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const missing = await request(port, 'POST', '/api/classify', {});
    assert.equal(missing.status, 400);

    const empty = await request(port, 'POST', '/api/classify', {
      question: ''
    });
    assert.equal(empty.status, 400);

    const oversized = await request(port, 'POST', '/api/classify', {
      question: 'a'.repeat(501)
    });
    assert.equal(oversized.status, 413);

    const ok = await request(port, 'POST', '/api/classify', {
      question: 'How do I send money?'
    });
    assert.equal(ok.status, 200);
    assert.equal(ok.json.category, 'send_money');
    assert.equal(ok.json.screenCategory, 'unknown');
    assert.ok(ok.json.guidance);
    assert.ok(ok.json.disclaimer);
    assert.equal(ok.json.nextStep.status, 'need_user_description');
    assert.deepEqual(
      Object.keys(ok.json).sort(),
      [
        'category',
        'guidance',
        'disclaimer',
        'matchedBy',
        'screenCategory',
        'nextStep',
        'screenClassification'
      ].sort()
    );
    assert.equal(ok.json.screenClassification, null);

    const contextual = await request(port, 'POST', '/api/classify', {
      question: 'What should I check next?',
      screenCategory: 'review_payment'
    });
    assert.equal(contextual.status, 200);
    assert.equal(contextual.json.screenCategory, 'review_payment');
    assert.equal(contextual.json.category, 'general');
    assert.equal(contextual.json.nextStep, null);
    assert.match(
      contextual.json.guidance,
      /official PayPal Help documentation does not provide enough evidence/i
    );
  } finally {
    server.close();
  }
});

test('POST /api/classify rejects invalid screenCategory values', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    for (const screenCategory of ['', null, 42, {}, 'not_a_category', 'x'.repeat(501)]) {
      const { status, json } = await request(port, 'POST', '/api/classify', {
        question: 'What should I do?',
        screenCategory
      });

      assert.equal(status, 400);
      assert.match(json.error, /screenCategory/i);
    }
  } finally {
    server.close();
  }
});

test('POST /api/classify ignores stale context and rejects raw or oversized context', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const stale = await request(port, 'POST', '/api/classify', {
      question: 'How do I see transaction history?',
      screenCategory: 'activity',
      screenContext: {
        category: 'activity',
        visibleLabels: ['Activity'],
        confidence: 82,
        evidenceStrength: 'moderate',
        ambiguous: false,
        evidenceCodes: ['activity_heading'],
        source: 'accessibility',
        observedAt: Date.now() - 60_000
      }
    });
    assert.equal(stale.status, 200);
    assert.equal(stale.json.screenCategory, 'activity');
    assert.deepEqual(
      Object.keys(stale.json).sort(),
      [
        'category',
        'guidance',
        'disclaimer',
        'matchedBy',
        'screenCategory',
        'nextStep',
        'screenClassification'
      ].sort()
    );
    assert.doesNotMatch(stale.text, /Activity/);

    for (const screenContext of [
      {
        category: 'activity',
        visibleLabels: ['Activity'],
        source: 'accessibility',
        observedAt: Date.now(),
        rawText: 'synthetic raw accessibility text'
      },
      {
        category: 'activity',
        visibleLabels: Array(9).fill('Activity'),
        confidence: 82,
        evidenceStrength: 'moderate',
        ambiguous: false,
        evidenceCodes: ['activity_heading'],
        source: 'accessibility',
        observedAt: Date.now()
      }
    ]) {
      const invalid = await request(port, 'POST', '/api/classify', {
        question: 'How do I see transaction history?',
        screenCategory: 'activity',
        screenContext
      });

      assert.equal(invalid.status, 400);
      assert.doesNotMatch(invalid.text, /synthetic raw accessibility text/);
    }
  } finally {
    server.close();
  }
});

test('POST /api/classify returns a single position-aware sending step', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'POST', '/api/classify', {
      question: 'How do I send money?',
      screenCategory: 'home',
      screenContext: {
        category: 'home',
        visibleLabels: ['Send', 'Request', 'person@example.test'],
        actionableElements: [
          'Send|bottom|left',
          'Request|bottom|right',
          'person@example.test|middle|center'
        ],
        confidence: 82,
        evidenceStrength: 'moderate',
        ambiguous: false,
        evidenceCodes: ['home_action_combination'],
        source: 'accessibility',
        observedAt: Date.now()
      }
    });

    assert.equal(status, 200);
    assert.equal(json.matchedBy, 'screen_rules');
    assert.equal(json.nextStep.expectedNextScreen, 'recipient_selection');
    assert.equal(json.nextStep.status, 'awaiting_user');
    assert.deepEqual(json.screenClassification, {
      category: 'home',
      confidence: 82,
      evidenceStrength: 'moderate',
      ambiguous: false,
      evidenceCodes: ['home_action_combination'],
      actionableElements: [
        'Send|bottom|left',
        'Request|bottom|right'
      ]
    });
    assert.doesNotMatch(JSON.stringify(json), /person@example\.test/);
    assert.match(json.guidance, /bottom left/i);
    assert.match(json.guidance, /“Send” button/);
  } finally {
    server.close();
  }
});

test('POST /api/classify keeps sensitive question text out of its response', async () => {
  const app = createApp();
  const { server, port } = await listen(app);
  const sensitiveText = 'uniquesecret987';

  try {
    const { status, text, json } = await request(port, 'POST', '/api/classify', {
      question: `My password is ${sensitiveText}; what should I do?`,
      screenCategory: 'security'
    });

    assert.equal(status, 200);
    assert.equal(json.category, 'security');
    assert.ok(!text.includes(sensitiveText));
  } finally {
    server.close();
  }
});

test('POST /api/classify aborts the service when the client disconnects', async () => {
  const app = express();
  app.use(express.json());

  let receivedSignal;
  let markStarted;

  const started = new Promise(resolve => {
    markStarted = resolve;
  });

  const appRouter = createClassifyRouter(async ({ signal }) => {
    receivedSignal = signal;
    markStarted();

    return new Promise(() => {
      signal.addEventListener('abort', () => {
        // The request should be cancelled when the client disconnects.
      }, { once: true });
    });
  });

  app.use('/api/classify', appRouter);

  const { server, port } = await listen(app);
  const controller = new AbortController();

  try {
    const pendingRequest = fetch(
      `http://127.0.0.1:${port}/api/classify`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          question: 'How do I send money?'
        }),
        signal: controller.signal
      }
    ).catch(() => null);

    await started;

    controller.abort();

    await pendingRequest;

    // Allow the server to process the connection close event.
    await new Promise(resolve => setTimeout(resolve, 50));

    assert.ok(receivedSignal, 'the service should receive an AbortSignal');
    assert.equal(
      receivedSignal.aborted,
      true,
      'the service signal should be aborted after disconnect'
    );
  } finally {
    server.closeAllConnections?.();
    await new Promise(resolve => server.close(resolve));
  }
});


test('unknown routes return 404 JSON', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'GET', '/nope');
    assert.equal(status, 404);
    assert.equal(json.error, 'Not found');
  } finally {
    server.close();
  }
});

test('malformed JSON returns 400', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const response = await fetch(`http://127.0.0.1:${port}/api/guidance`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: '{not-json'
    });
    const json = await response.json();
    assert.equal(response.status, 400);
    assert.match(json.error, /Invalid JSON/i);
  } finally {
    server.close();
  }
});
