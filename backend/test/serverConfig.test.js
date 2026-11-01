import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync } from 'node:crypto';
import { getServerConfig } from '../src/serverConfig.js';

const { publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicKeyPem = publicKey.export({
  type: 'spki',
  format: 'pem'
}).toString();

function authEnvironment(overrides = {}) {
  return {
    AUTH_PUBLIC_KEY_PEM: publicKeyPem,
    AUTH_ISSUER: 'https://identity.example.test/',
    AUTH_AUDIENCE: 'paypal-saathi-api',
    ...overrides
  };
}

test('server defaults to loopback and port 3000', () => {
  assert.deepEqual(getServerConfig({}), {
    host: '127.0.0.1',
    port: 3000,
    auth: null,
    tls: null
  });
});

test('server honors explicit host and port configuration', () => {
  const config = getServerConfig(authEnvironment({
    HOST: '0.0.0.0',
    PORT: '3100'
  }));
  assert.equal(config.host, '0.0.0.0');
  assert.equal(config.port, 3100);
  assert.equal(config.auth.audience, 'paypal-saathi-api');
  assert.equal(config.tls, null);
});

test('server accepts explicit IPv4 and IPv6 interface addresses', () => {
  assert.equal(
    getServerConfig(authEnvironment({ HOST: '192.168.1.20' })).host,
    '192.168.1.20'
  );
  assert.equal(getServerConfig({ HOST: '::1' }).host, '::1');
});

test('server rejects non-loopback binding without authentication configuration', () => {
  for (const HOST of ['192.168.1.20', '0.0.0.0', '::']) {
    assert.throws(
      () => getServerConfig({ HOST }),
      /Authentication is required/
    );
  }
});

test('production requires authentication even when bound to loopback', () => {
  assert.throws(
    () => getServerConfig({ NODE_ENV: 'production' }),
    /Authentication is required/
  );
});

test('remote production requires TLS certificate and key files', () => {
  assert.throws(
    () => getServerConfig(authEnvironment({
      NODE_ENV: 'production',
      HOST: '0.0.0.0'
    })),
    /requires TLS_CERT_FILE and TLS_KEY_FILE/
  );

  const config = getServerConfig(authEnvironment({
    NODE_ENV: 'production',
    HOST: '0.0.0.0',
    TLS_CERT_FILE: '.\\certs\\server.crt',
    TLS_KEY_FILE: '.\\certs\\server.key'
  }));
  assert.deepEqual(config.tls, {
    certFile: '.\\certs\\server.crt',
    keyFile: '.\\certs\\server.key'
  });
});

test('server rejects incomplete authentication and TLS configuration', () => {
  assert.throws(
    () => getServerConfig({ AUTH_ISSUER: 'https://identity.example.test/' }),
    /must be configured together/
  );
  assert.throws(
    () => getServerConfig({ TLS_CERT_FILE: '.\\certs\\server.crt' }),
    /must be configured together/
  );
});

test('server rejects invalid authentication keys and non-HTTPS issuers', () => {
  assert.throws(
    () => getServerConfig(authEnvironment({
      AUTH_PUBLIC_KEY_PEM: 'not a public key'
    })),
    /AUTH_PUBLIC_KEY_PEM/
  );
  assert.throws(
    () => getServerConfig(authEnvironment({
      AUTH_ISSUER: 'http://identity.example.test/'
    })),
    /AUTH_ISSUER must be an HTTPS URL/
  );
});

test('server rejects non-IP and empty bind addresses', () => {
  assert.throws(
    () => getServerConfig({ HOST: 'localhost' }),
    /HOST must be an IPv4 or IPv6 address/
  );
  assert.throws(
    () => getServerConfig({ HOST: '' }),
    /HOST must be an IPv4 or IPv6 address/
  );
});

test('server rejects invalid ports', () => {
  for (const PORT of ['0', '65536', '3000.5', 'not-a-port']) {
    assert.throws(
      () => getServerConfig({ PORT }),
      /PORT must be an integer between 1 and 65535/
    );
  }
});
