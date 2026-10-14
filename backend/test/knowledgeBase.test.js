import test from 'node:test';
import assert from 'node:assert/strict';
import {
  extractDocument,
  fetchPayPalHelpDocument,
  isAllowedByRobots
} from '../src/services/knowledgeBase.js';

const syntheticUrl =
  'https://www.paypal.com/in/cshelp/article/synthetic-fixture-help123';

test('extracts heading-aware chunks and source metadata from a synthetic HTML fixture', () => {
  const syntheticHtml = `
    <!doctype html>
    <html lang="en">
      <head><title>Synthetic fixture article</title></head>
      <body>
        <nav>Navigation must not be indexed.</nav>
        <main>
          <h1>Synthetic fixture article</h1>
          <p>This synthetic paragraph provides enough sample content for extraction.</p>
          <h2>Synthetic section heading</h2>
          <p>This synthetic section describes a made-up example for a unit test.</p>
          <ul><li>Another synthetic example item.</li></ul>
        </main>
        <script>synthetic secret marker should not be extracted</script>
      </body>
    </html>`;

  const document = extractDocument(syntheticHtml, syntheticUrl, 200);

  assert.equal(document.title, 'Synthetic fixture article');
  assert.equal(document.language, 'en');
  assert.equal(document.region, 'IN');
  assert.match(document.contentHash, /^[a-f0-9]{64}$/);
  assert.ok(document.chunks.some(
    chunk => chunk.heading === 'Synthetic section heading'
  ));
  assert.ok(document.chunks.every(chunk =>
    !chunk.text.includes('Navigation must not be indexed')
      && !chunk.text.includes('secret marker')
  ));
});

test('robots rules block disallowed paths and honor a more specific allow rule', () => {
  const robotsText = [
    'User-agent: *',
    'Disallow: /in/cshelp/',
    'Allow: /in/cshelp/article/public-'
  ].join('\n');

  assert.equal(
    isAllowedByRobots(
      'https://www.paypal.com/in/cshelp/article/private-help123',
      robotsText
    ),
    false
  );
  assert.equal(
    isAllowedByRobots(
      'https://www.paypal.com/in/cshelp/article/public-help123',
      robotsText
    ),
    true
  );
});

test('rejects malformed source URLs and HTML without usable article content', () => {
  for (const url of [
    'http://www.paypal.com/in/cshelp/article/article-help123',
    'https://example.com/in/cshelp/article/article-help123',
    'https://www.paypal.com/us/cshelp/article/article-help123'
  ]) {
    assert.throws(
      () => extractDocument('<html><body>synthetic</body></html>', url),
      /Only public PayPal India Help Center/
    );
  }
  assert.throws(
    () => extractDocument('<html><body><p>No title</p></body></html>', syntheticUrl),
    /did not contain a title/
  );
});

test('deduplicates repeated text and hashes changed document content', () => {
  const repeated = 'Synthetic repeated article text for duplicate detection.';
  const html = `<html lang="en"><head><title>Synthetic duplicate fixture</title></head><body><main><h1>Synthetic duplicate fixture</h1><p>${repeated}</p><h2>Another section</h2><p>${repeated}</p><p>Additional synthetic text makes this document suitable for indexing safely.</p></main></body></html>`;
  const original = extractDocument(html, syntheticUrl);
  const changed = extractDocument(
    html.replace('Additional synthetic text', 'Changed synthetic content'),
    syntheticUrl
  );
  const joined = original.chunks.map(chunk => chunk.text).join(' ');

  assert.equal(joined.split(repeated).length - 1, 1);
  assert.notEqual(changed.contentHash, original.contentHash);
});

test('refuses redirects outside the permitted PayPal Help path', async () => {
  const requests = [];
  const fetchImpl = async input => {
    const url = new URL(input);
    requests.push(url.href);
    if (url.pathname === '/robots.txt') {
      return new Response('User-agent: *\nAllow: /in/cshelp/article/');
    }
    return new Response(null, {
      status: 302,
      headers: { location: 'https://example.invalid/steal' }
    });
  };

  await assert.rejects(
    fetchPayPalHelpDocument(syntheticUrl, { fetchImpl }),
    /Only public PayPal India Help Center/
  );
  assert.equal(requests.length, 2);
  assert.ok(requests.every(url => new URL(url).hostname === 'www.paypal.com'));
});

test('rejects oversized Help responses and explicit access challenges', async () => {
  const robots = new Response('User-agent: *\nAllow: /in/cshelp/article/');
  const oversizedFetch = async input => {
    if (new URL(input).pathname === '/robots.txt') {
      return robots.clone();
    }
    return new Response('<html></html>', {
      headers: {
        'content-type': 'text/html',
        'content-length': String(3 * 1024 * 1024)
      }
    });
  };
  await assert.rejects(
    fetchPayPalHelpDocument(syntheticUrl, { fetchImpl: oversizedFetch }),
    /exceeded the import size limit/
  );

  const challengeFetch = async input => {
    if (new URL(input).pathname === '/robots.txt') {
      return robots.clone();
    }
    return new Response('<html><body>Access denied</body></html>', {
      headers: { 'content-type': 'text/html' }
    });
  };
  await assert.rejects(
    fetchPayPalHelpDocument(syntheticUrl, { fetchImpl: challengeFetch }),
    /access challenge/
  );
});
