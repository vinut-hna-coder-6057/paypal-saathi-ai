import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createApp } from '../src/app.js';

process.env.PAYPAL_KB_DB_PATH = ':memory:';

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

async function request(port, method, path, body) {
  const payload = body === undefined ? null : JSON.stringify(body);

  const response = await fetch(`http://127.0.0.1:${port}${path}`, {
    method,
    headers: payload
      ? { 'Content-Type': 'application/json' }
      : undefined,
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

test('GET /health returns service metadata', async () => {
  const app = createApp();
  const { server, port } = await listen(app);

  try {
    const { status, json } = await request(port, 'GET', '/health');
    assert.equal(status, 200);
    assert.equal(json.status, 'ok');
    assert.equal(json.classifier, 'rules');
    assert.equal(json.modelIntegrated, true);
    assert.ok(Array.isArray(json.supportedScreenCategories));
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
    assert.deepEqual(
      Object.keys(ok.json).sort(),
      ['category', 'guidance', 'disclaimer', 'matchedBy', 'screenCategory'].sort()
    );

    const contextual = await request(port, 'POST', '/api/classify', {
      question: 'What should I check next?',
      screenCategory: 'review_payment'
    });
    assert.equal(contextual.status, 200);
    assert.equal(contextual.json.screenCategory, 'review_payment');
    assert.equal(contextual.json.category, 'general');
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
        source: 'accessibility',
        observedAt: Date.now() - 60_000
      }
    });
    assert.equal(stale.status, 200);
    assert.equal(stale.json.screenCategory, 'activity');
    assert.deepEqual(
      Object.keys(stale.json).sort(),
      ['category', 'guidance', 'disclaimer', 'matchedBy', 'screenCategory'].sort()
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
