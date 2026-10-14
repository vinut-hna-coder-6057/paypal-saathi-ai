import { createHash } from 'node:crypto';
import { load } from 'cheerio';

const PAYPAL_HOST = 'www.paypal.com';
const PAYPAL_HELP_PATH = '/in/cshelp/article/';
const MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
const USER_AGENT =
  'PayPalSaathiKnowledgeImporter/1.0 (+local private help index; respects robots.txt)';
let robotsPromise;

function normalizeWhitespace(text) {
  return text.replace(/\s+/g, ' ').trim();
}

function assertPermittedUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('Source URL must be an absolute HTTPS PayPal Help URL.');
  }

  if (url.protocol !== 'https:'
      || url.hostname.toLowerCase() !== PAYPAL_HOST
      || !url.pathname.startsWith(PAYPAL_HELP_PATH)
      || url.username
      || url.password) {
    throw new Error(
      'Only public PayPal India Help Center article URLs are accepted.'
    );
  }

  return url;
}

function parseRobotsRules(robotsText) {
  const groups = [];
  let current = null;

  for (const rawLine of robotsText.split(/\r?\n/)) {
    const line = rawLine.split('#', 1)[0].trim();
    if (!line) {
      continue;
    }

    const separator = line.indexOf(':');
    if (separator < 0) {
      continue;
    }

    const key = line.slice(0, separator).trim().toLowerCase();
    const value = line.slice(separator + 1).trim();

    if (key === 'user-agent') {
      if (!current || current.hasRules) {
        current = { agents: [], rules: [], hasRules: false };
        groups.push(current);
      }
      current.agents.push(value.toLowerCase());
      continue;
    }

    if ((key === 'allow' || key === 'disallow')
        && current
        && current.agents.includes('*')
        && value) {
      current.rules.push({ allow: key === 'allow', path: value });
      current.hasRules = true;
    }
  }

  return groups
    .filter(group => group.agents.includes('*'))
    .flatMap(group => group.rules);
}

function robotsRuleMatches(rulePath, requestPath) {
  const escaped = rulePath
    .replace(/[.+?^${}()|[\]\\]/g, '\\$&')
    .replace(/\*/g, '.*')
    .replace(/\\\$$/, '$');
  return new RegExp(`^${escaped}`).test(requestPath);
}

export function isAllowedByRobots(url, robotsText) {
  const parsedUrl = url instanceof URL ? url : new URL(url);
  const requestPath = `${parsedUrl.pathname}${parsedUrl.search}`;
  const matches = parseRobotsRules(robotsText)
    .filter(rule => robotsRuleMatches(rule.path, requestPath))
    .sort((left, right) => right.path.length - left.path.length);

  if (matches.length === 0) {
    return true;
  }

  const longestRules = matches.filter(
    rule => rule.path.length === matches[0].path.length
  );
  return longestRules.some(rule => rule.allow);
}

async function getRobotsText(fetchImpl, timeoutMs) {
  if (!robotsPromise) {
    robotsPromise = (async () => {
      const response = await fetchImpl(`https://${PAYPAL_HOST}/robots.txt`, {
        headers: { 'User-Agent': USER_AGENT },
        signal: AbortSignal.timeout(timeoutMs)
      });

      if (!response.ok) {
        throw new Error(`Could not verify PayPal robots.txt (HTTP ${response.status}).`);
      }

      return response.text();
    })().catch(error => {
      robotsPromise = undefined;
      throw error;
    });
  }

  return robotsPromise;
}

async function readLimitedResponse(response) {
  const contentLength = Number(response.headers.get('content-length') || 0);
  if (contentLength > MAX_RESPONSE_BYTES) {
    throw new Error('PayPal Help article exceeded the import size limit.');
  }

  if (!response.body) {
    return '';
  }

  const chunks = [];
  let byteCount = 0;
  for await (const chunk of response.body) {
    byteCount += chunk.length;
    if (byteCount > MAX_RESPONSE_BYTES) {
      throw new Error('PayPal Help article exceeded the import size limit.');
    }
    chunks.push(chunk);
  }

  return Buffer.concat(chunks).toString('utf8');
}

async function fetchPermittedPage(url, fetchImpl, robotsText, timeoutMs) {
  let currentUrl = url;

  for (let redirects = 0; redirects <= 5; redirects++) {
    currentUrl = assertPermittedUrl(currentUrl.href);
    if (!isAllowedByRobots(currentUrl, robotsText)) {
      throw new Error('PayPal robots.txt disallows this Help article URL.');
    }

    const response = await fetchImpl(currentUrl, {
      redirect: 'manual',
      headers: {
        'Accept': 'text/html',
        'User-Agent': USER_AGENT
      },
      signal: AbortSignal.timeout(timeoutMs)
    });

    if ([301, 302, 303, 307, 308].includes(response.status)) {
      const location = response.headers.get('location');
      if (!location || redirects === 5) {
        throw new Error('PayPal Help article exceeded the redirect limit.');
      }
      currentUrl = new URL(location, currentUrl);
      continue;
    }

    if (!response.ok) {
      throw new Error(`PayPal Help article returned HTTP ${response.status}.`);
    }

    const contentType = response.headers.get('content-type') || '';
    if (!contentType.toLowerCase().includes('text/html')) {
      throw new Error('PayPal Help article did not return HTML.');
    }

    const html = await readLimitedResponse(response);
    if (/captcha challenge|verify (?:that )?you are human|access denied|request blocked/i.test(html)) {
      throw new Error(
        'PayPal returned an access challenge; import stopped without attempting to bypass it.'
      );
    }

    return { html, url: currentUrl.href };
  }

  throw new Error('PayPal Help article exceeded the redirect limit.');
}

export function extractDocument(html, sourceUrl, maxChunkChars = 900) {
  assertPermittedUrl(sourceUrl);

  if (typeof html !== 'string' || !html.trim()) {
    throw new TypeError('HTML content is required.');
  }
  if (!Number.isInteger(maxChunkChars) || maxChunkChars < 200) {
    throw new RangeError('Chunk size must be at least 200 characters.');
  }

  const $ = load(html);
  const root = $('main').first().length
    ? $('main').first()
    : $('article').first().length
      ? $('article').first()
      : $('body');

  root.find(
    'script, style, noscript, svg, iframe, form, nav, header, footer, button, [aria-hidden="true"]'
  ).remove();

  const title = normalizeWhitespace(
    $('meta[property="og:title"]').attr('content')
      || root.find('h1').first().text()
      || $('title').text()
  );
  if (!title) {
    throw new Error('PayPal Help article did not contain a title.');
  }

  const sections = [];
  let currentHeading = title;
  let currentSection = { heading: currentHeading, blocks: [] };
  const seenBlocks = new Set();
  const selectors = 'h1, h2, h3, h4, p, li, td';

  for (const element of root.find(selectors).toArray()) {
    const node = $(element);
    const tag = element.tagName?.toLowerCase();
    const text = normalizeWhitespace(node.text());

    if (/^h[1-4]$/.test(tag)) {
      if (currentSection.blocks.length > 0) {
        sections.push(currentSection);
      }
      currentHeading = text || title;
      currentSection = { heading: currentHeading, blocks: [] };
      continue;
    }

    if (tag === 'li' && node.parents('li').length > 0) {
      continue;
    }
    if (text.length >= 2 && !seenBlocks.has(text)) {
      seenBlocks.add(text);
      currentSection.blocks.push(text);
    }
  }

  if (currentSection.blocks.length > 0) {
    sections.push(currentSection);
  }

  const normalizedContent = sections
    .map(section => `${section.heading}\n${section.blocks.join('\n')}`)
    .join('\n\n')
    .trim();
  if (normalizedContent.length < 80) {
    throw new Error('PayPal Help article contains insufficient extractable text.');
  }

  const chunks = [];
  for (const section of sections) {
    let currentText = '';
    const pushChunk = () => {
      if (!currentText) {
        return;
      }
      const text = currentText.trim();
      chunks.push({
        id: createHash('sha256')
          .update(`${sourceUrl}\n${section.heading}\n${text}`)
          .digest('hex'),
        heading: section.heading,
        text
      });
      currentText = '';
    };

    for (const block of section.blocks) {
      let remaining = block;
      while (remaining.length > maxChunkChars) {
        let splitAt = remaining.lastIndexOf(' ', maxChunkChars);
        if (splitAt < Math.floor(maxChunkChars * 0.6)) {
          splitAt = maxChunkChars;
        }
        const part = remaining.slice(0, splitAt).trim();
        if (currentText
            && currentText.length + part.length + 1 > maxChunkChars) {
          pushChunk();
        }
        currentText = currentText ? `${currentText} ${part}` : part;
        pushChunk();
        remaining = remaining.slice(splitAt).trim();
      }

      if (currentText
          && currentText.length + block.length + 1 > maxChunkChars) {
        pushChunk();
      }
      currentText = currentText ? `${currentText} ${remaining}` : remaining;
    }
    pushChunk();
  }

  const parsedUrl = new URL(sourceUrl);
  const rawLanguage = $('html').attr('lang') || 'en';
  const language = rawLanguage.toLowerCase().startsWith('en') ? 'en' : rawLanguage;

  return {
    url: sourceUrl,
    title,
    language,
    region: parsedUrl.pathname.startsWith('/in/') ? 'IN' : 'unknown',
    contentHash: createHash('sha256')
      .update(normalizedContent)
      .digest('hex'),
    chunks
  };
}

export async function fetchPayPalHelpDocument(
  sourceUrl,
  { fetchImpl = fetch, timeoutMs = 15_000 } = {}
) {
  const permittedUrl = assertPermittedUrl(sourceUrl);
  const robotsText = await getRobotsText(fetchImpl, timeoutMs);
  const fetched = await fetchPermittedPage(
    permittedUrl,
    fetchImpl,
    robotsText,
    timeoutMs
  );
  return extractDocument(fetched.html, fetched.url);
}
