import test from 'node:test';
import assert from 'node:assert/strict';
import { getServerConfig } from '../src/serverConfig.js';

test('server defaults to loopback and port 3000', () => {
  assert.deepEqual(getServerConfig({}), {
    host: '127.0.0.1',
    port: 3000
  });
});

test('server honors explicit host and port configuration', () => {
  assert.deepEqual(getServerConfig({
    HOST: '0.0.0.0',
    PORT: '3100'
  }), {
    host: '0.0.0.0',
    port: 3100
  });
});

test('server accepts explicit IPv4 and IPv6 interface addresses', () => {
  assert.equal(getServerConfig({ HOST: '192.168.1.20' }).host, '192.168.1.20');
  assert.equal(getServerConfig({ HOST: '::1' }).host, '::1');
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
