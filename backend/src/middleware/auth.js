import { createPublicKey, verify as verifySignature } from 'node:crypto';

const REQUIRED_SCOPE = 'saathi:guidance';
const MAX_TOKEN_LENGTH = 8_192;
const MAX_TOKEN_LIFETIME_SECONDS = 3_600;

export function validateRsaPublicKey(publicKeyPem) {
  if (typeof publicKeyPem !== 'string'
      || !/-----BEGIN (?:RSA )?PUBLIC KEY-----/.test(publicKeyPem)) {
    throw new TypeError('AUTH_PUBLIC_KEY_PEM must contain an RSA public key.');
  }

  let publicKey;
  try {
    publicKey = createPublicKey(publicKeyPem);
  } catch {
    throw new TypeError('AUTH_PUBLIC_KEY_PEM is not a valid public key.');
  }

  if (publicKey.asymmetricKeyType !== 'rsa'
      || (publicKey.asymmetricKeyDetails?.modulusLength ?? 0) < 2_048) {
    throw new TypeError(
      'AUTH_PUBLIC_KEY_PEM must be an RSA key with at least 2048 bits.'
    );
  }

  return publicKey;
}

function decodeJsonSegment(segment) {
  if (!/^[A-Za-z0-9_-]+$/.test(segment)) {
    throw new Error('Malformed token segment.');
  }

  const parsed = JSON.parse(Buffer.from(segment, 'base64url').toString('utf8'));
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Malformed token JSON.');
  }
  return parsed;
}

function tokenHasRequiredClaims(claims, { issuer, audience, nowSeconds }) {
  if (claims.iss !== issuer
      || typeof claims.sub !== 'string'
      || !claims.sub.trim()
      || claims.sub.length > 256
      || !Number.isSafeInteger(claims.iat)
      || !Number.isSafeInteger(claims.exp)
      || claims.iat > nowSeconds
      || claims.exp <= nowSeconds
      || claims.exp <= claims.iat
      || claims.exp - claims.iat > MAX_TOKEN_LIFETIME_SECONDS) {
    return false;
  }

  if (claims.nbf !== undefined
      && (!Number.isSafeInteger(claims.nbf) || claims.nbf > nowSeconds)) {
    return false;
  }

  const tokenAudiences = Array.isArray(claims.aud)
    ? claims.aud
    : [claims.aud];
  return tokenAudiences.includes(audience);
}

function rejectAuthentication(res) {
  res.set('WWW-Authenticate', 'Bearer realm="paypal-saathi"');
  return res.status(401).json({ error: 'Authentication required' });
}

function rejectAuthorization(res) {
  res.set(
    'WWW-Authenticate',
    `Bearer realm="paypal-saathi", error="insufficient_scope", scope="${REQUIRED_SCOPE}"`
  );
  return res.status(403).json({ error: 'Insufficient permissions' });
}

export function createBearerAuthMiddleware({
  publicKeyPem,
  issuer,
  audience,
  now = Date.now
}) {
  const publicKey = validateRsaPublicKey(publicKeyPem);
  if (typeof issuer !== 'string' || !issuer.trim()
      || typeof audience !== 'string' || !audience.trim()) {
    throw new TypeError('A trusted token issuer and audience are required.');
  }

  return function authenticateApiRequest(req, res, next) {
    const authorization = req.get('authorization') || '';
    const match = /^Bearer ([^\s]+)$/i.exec(authorization);
    if (!match || match[1].length > MAX_TOKEN_LENGTH) {
      return rejectAuthentication(res);
    }

    try {
      const segments = match[1].split('.');
      if (segments.length !== 3 || segments.some(segment => !segment)) {
        return rejectAuthentication(res);
      }

      const [encodedHeader, encodedClaims, encodedSignature] = segments;
      const header = decodeJsonSegment(encodedHeader);
      const claims = decodeJsonSegment(encodedClaims);
      if (header.alg !== 'RS256'
          || (header.typ !== undefined
              && !['jwt', 'at+jwt'].includes(String(header.typ).toLowerCase()))
          || header.crit !== undefined
          || (header.b64 !== undefined && header.b64 !== true)) {
        return rejectAuthentication(res);
      }

      if (!/^[A-Za-z0-9_-]+$/.test(encodedSignature)) {
        return rejectAuthentication(res);
      }
      const signature = Buffer.from(encodedSignature, 'base64url');
      const signingInput = `${encodedHeader}.${encodedClaims}`;
      if (!verifySignature(
        'RSA-SHA256',
        Buffer.from(signingInput),
        publicKey,
        signature
      )) {
        return rejectAuthentication(res);
      }

      const nowSeconds = Math.floor(now() / 1_000);
      if (!tokenHasRequiredClaims(claims, {
        issuer,
        audience,
        nowSeconds
      })) {
        return rejectAuthentication(res);
      }

      const scopes = typeof claims.scope === 'string'
        ? claims.scope.split(/\s+/)
        : Array.isArray(claims.scope)
          ? claims.scope
          : [];
      if (!scopes.includes(REQUIRED_SCOPE)) {
        return rejectAuthorization(res);
      }

      req.auth = Object.freeze({ subject: claims.sub });
      return next();
    } catch {
      return rejectAuthentication(res);
    }
  };
}
