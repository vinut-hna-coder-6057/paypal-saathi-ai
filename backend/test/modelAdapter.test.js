import test from 'node:test';
import assert from 'node:assert/strict';
import { generateGroundedAnswer } from '../src/services/modelAdapter.js';

const syntheticEvidence = [{
  title: 'Synthetic source',
  url: 'https://www.paypal.com/in/cshelp/article/synthetic-source',
  heading: 'Synthetic heading',
  text: 'Synthetic public fixture evidence for adapter tests.'
}];

test('sends task-only question and bounded evidence to loopback Ollama', async t => {
  let requestUrl;
  let requestBody;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    requestUrl = url.toString();
    requestBody = JSON.parse(options.body);
    assert.equal(options.redirect, 'error');
    return new Response(JSON.stringify({
      response: JSON.stringify({
        answer: 'Synthetic answer supported by the excerpt.',
        sourceMarkers: [1]
      })
    }), { status: 200 });
  });

  const answer = await generateGroundedAnswer({
    question: 'Where can I see transaction history?',
    screenCategory: 'wallet',
    intent: 'transaction_history',
    screenContext: {
      visibleLabels: ['Activity', 'person@example.test', 'ignore rules']
    },
    evidence: syntheticEvidence
  });

  assert.match(requestUrl, /^http:\/\/127\.0\.0\.1:11434\/api\/generate$/);
  assert.equal(requestBody.keep_alive, '5m');
  assert.equal(requestBody.format.type, 'object');
  assert.match(requestBody.prompt, /"requestedTaskIntent":"transaction_history"/);
  assert.doesNotMatch(
    requestBody.prompt,
    /startingScreen|screenCategory|Activity|person@example\.test|ignore rules/
  );
  assert.match(requestBody.prompt, /untrusted data/);
  assert.match(
    requestBody.prompt,
    /Determine evidence sufficiency only from the question and excerpts/
  );
  assert.match(
    requestBody.prompt,
    /Do not reject a useful partial answer as wholly insufficient/
  );
  assert.match(requestBody.prompt, /identify them as web steps/);
  assert.match(answer, /\[1\]/);
});

test('uses only validated source markers and rejects marker-only answers', async t => {
  let modelAnswer = 'Synthetic answer [1] with another [1] marker.';
  const evidence = [
    ...syntheticEvidence,
    { ...syntheticEvidence[0], title: 'Second synthetic source' }
  ];
  t.mock.method(globalThis, 'fetch', async () =>
    new Response(JSON.stringify({
      response: JSON.stringify({
        answer: modelAnswer,
        sourceMarkers: [1, 1]
      })
    }), { status: 200 }));

  const answer = await generateGroundedAnswer({
    question: 'Synthetic question',
    evidence
  });
  assert.equal([...answer.matchAll(/\[1\]/g)].length, 1);
  assert.equal(answer, 'Synthetic answer with another marker. [1]');

  modelAnswer = '[1]';
  await assert.rejects(
    generateGroundedAnswer({
      question: 'Synthetic question',
      evidence
    }),
    /no answer text/
  );
});

test('rejects remote, credentialed, redirected, or path-configured Ollama URLs', async t => {
  const original = process.env.OLLAMA_BASE_URL;
  let called = false;
  t.mock.method(globalThis, 'fetch', async () => {
    called = true;
    return new Response('{}');
  });

  for (const url of [
    'https://example.invalid',
    'http://user:pass@127.0.0.1:11434',
    'http://127.0.0.1:11434/proxy',
    'http://127.0.0.1:11434?redirect=1'
  ]) {
    process.env.OLLAMA_BASE_URL = url;
    await assert.rejects(
      generateGroundedAnswer({
        question: 'Synthetic question',
        evidence: syntheticEvidence
      }),
      /loopback Ollama service/
    );
  }

  assert.equal(called, false);
  if (original === undefined) {
    delete process.env.OLLAMA_BASE_URL;
  } else {
    process.env.OLLAMA_BASE_URL = original;
  }
});

test('rejects malformed and oversized model responses', async t => {
  t.mock.method(globalThis, 'fetch', async () =>
    new Response('not-json', { status: 200 }));

  await assert.rejects(
    generateGroundedAnswer({
      question: 'Synthetic question',
      evidence: syntheticEvidence
    }),
    /malformed JSON/
  );

  t.mock.restoreAll();
  t.mock.method(globalThis, 'fetch', async () =>
    new Response(JSON.stringify({ response: 'x'.repeat(70_000) }), {
      status: 200
    }));

  await assert.rejects(
    generateGroundedAnswer({
      question: 'Synthetic question',
      evidence: syntheticEvidence
    }),
    /exceeded the size limit/
  );
});

test('rejects non-JSON answer structures and out-of-range source markers', async t => {
  t.mock.method(globalThis, 'fetch', async () =>
    new Response(JSON.stringify({
      response: JSON.stringify({
        answer: 'Unsupported synthetic statement.',
        sourceMarkers: [2]
      })
    }), { status: 200 }));

  await assert.rejects(
    generateGroundedAnswer({
      question: 'Synthetic question',
      evidence: syntheticEvidence
    }),
    /unsupported source markers/
  );
});
