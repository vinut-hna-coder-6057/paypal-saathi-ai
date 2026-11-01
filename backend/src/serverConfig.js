import { isIP } from 'node:net';
import { validateRsaPublicKey } from './middleware/auth.js';

function isLoopbackAddress(host) {
  if (isIP(host) === 4) {
    return host.split('.').map(Number)[0] === 127;
  }
  return host.toLowerCase() === '::1';
}

export function getServerConfig(environment = process.env) {
  const host = environment.HOST === undefined
    ? '127.0.0.1'
    : environment.HOST.trim();
  const port = environment.PORT === undefined
    ? 3000
    : Number(environment.PORT);

  if (!host || isIP(host) === 0) {
    throw new TypeError(
      'HOST must be an IPv4 or IPv6 address; use 0.0.0.0 or :: only to explicitly listen on all interfaces of that address family.'
    );
  }
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new RangeError('PORT must be an integer between 1 and 65535.');
  }

  const loopbackOnly = isLoopbackAddress(host);
  const authPublicKeyPem = environment.AUTH_PUBLIC_KEY_PEM
    ?.replace(/\\n/g, '\n')
    .trim();
  const authIssuer = environment.AUTH_ISSUER?.trim();
  const authAudience = environment.AUTH_AUDIENCE?.trim();
  const authValues = [authPublicKeyPem, authIssuer, authAudience];
  const authConfigured = authValues.every(Boolean);
  const authPartiallyConfigured =
    authValues.some(Boolean) && !authConfigured;

  if (authPartiallyConfigured) {
    throw new TypeError(
      'AUTH_PUBLIC_KEY_PEM, AUTH_ISSUER, and AUTH_AUDIENCE must be configured together.'
    );
  }

  const authRequired =
    environment.NODE_ENV === 'production' || !loopbackOnly;
  if (authRequired && !authConfigured) {
    throw new TypeError(
      'Authentication is required for production or non-loopback binding; configure AUTH_PUBLIC_KEY_PEM, AUTH_ISSUER, and AUTH_AUDIENCE.'
    );
  }

  let auth = null;
  if (authConfigured) {
    validateRsaPublicKey(authPublicKeyPem);
    let issuerUrl;
    try {
      issuerUrl = new URL(authIssuer);
    } catch {
      throw new TypeError('AUTH_ISSUER must be an HTTPS URL.');
    }
    if (issuerUrl.protocol !== 'https:'
        || issuerUrl.username
        || issuerUrl.password
        || issuerUrl.hash) {
      throw new TypeError('AUTH_ISSUER must be an HTTPS URL.');
    }
    auth = {
      publicKeyPem: authPublicKeyPem,
      issuer: authIssuer,
      audience: authAudience
    };
  }

  const tlsCertFile = environment.TLS_CERT_FILE?.trim();
  const tlsKeyFile = environment.TLS_KEY_FILE?.trim();
  if (Boolean(tlsCertFile) !== Boolean(tlsKeyFile)) {
    throw new TypeError(
      'TLS_CERT_FILE and TLS_KEY_FILE must be configured together.'
    );
  }
  if (environment.NODE_ENV === 'production'
      && !loopbackOnly
      && !tlsCertFile) {
    throw new TypeError(
      'Remote production binding requires TLS_CERT_FILE and TLS_KEY_FILE.'
    );
  }

  return {
    port,
    host,
    auth,
    tls: tlsCertFile
      ? { certFile: tlsCertFile, keyFile: tlsKeyFile }
      : null
  };
}
