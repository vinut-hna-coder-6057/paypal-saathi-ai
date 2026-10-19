
import test from 'node:test';
import assert from 'node:assert/strict';
import { createShutdownHandler } from '../src/serverLifecycle.js';

test('graceful shutdown closes the server and then the knowledge store', () => {
  const calls = [];
  const server = {
    close(callback) {
      calls.push('server.close');
      callback();
    }
  };

  const shutdown = createShutdownHandler({
    server,
    closeStore() {
      calls.push('closeStore');
    },
    exit(code) {
      calls.push(`exit:${code}`);
    },
    log() {},
    warn() {},
    errorLog() {}
  });

  shutdown('SIGTERM');

  assert.deepEqual(calls, [
    'server.close',
    'closeStore',
    'exit:0'
  ]);
});

test('repeated shutdown signals do not close the server twice', () => {
  let closeCalls = 0;
  const warnings = [];

  const server = {
    close(callback) {
      closeCalls += 1;
      callback();
    }
  };

  const shutdown = createShutdownHandler({
    server,
    closeStore() {},
    exit() {},
    log() {},
    warn(message) {
      warnings.push(message);
    },
    errorLog() {}
  });

  shutdown('SIGINT');
  shutdown('SIGTERM');

  assert.equal(closeCalls, 1);
  assert.equal(warnings.length, 1);
  assert.match(warnings[0], /already in progress/i);
});

test('server shutdown failure skips knowledge-store cleanup', () => {
  const calls = [];
  const server = {
    close(callback) {
      callback(new Error('simulated server failure'));
    }
  };

  const shutdown = createShutdownHandler({
    server,
    closeStore() {
      calls.push('closeStore');
    },
    exit(code) {
      calls.push(`exit:${code}`);
    },
    log() {},
    warn() {},
    errorLog() {}
  });

  shutdown('SIGTERM');

  assert.deepEqual(calls, ['exit:1']);
});

test('knowledge-store cleanup failure reports an unsuccessful shutdown', () => {
  const calls = [];
  const server = {
    close(callback) {
      callback();
    }
  };

  const shutdown = createShutdownHandler({
    server,
    closeStore() {
      throw new Error('simulated cleanup failure');
    },
    exit(code) {
      calls.push(`exit:${code}`);
    },
    log() {},
    warn() {},
    errorLog() {}
  });

  shutdown('SIGTERM');

  assert.deepEqual(calls, ['exit:1']);
});

test('shutdown timeout is configured and does not keep the process alive', () => {
  let timeoutCallback;
  let unrefCalled = false;

  const originalSetTimeout = globalThis.setTimeout;
  const originalClearTimeout = globalThis.clearTimeout;

  globalThis.setTimeout = callback => {
    timeoutCallback = callback;
    return {
      unref() {
        unrefCalled = true;
      }
    };
  };

  globalThis.clearTimeout = () => {};

  try {
    const server = {
      close() {
        // Simulate a server that never finishes closing.
      }
    };

    const errors = [];
    const exitCodes = [];

    const shutdown = createShutdownHandler({
      server,
      closeStore() {},
      timeoutMs: 1234,
      exit(code) {
        exitCodes.push(code);
      },
      log() {},
      warn() {},
      errorLog(message) {
        errors.push(message);
      }
    });

    shutdown('SIGTERM');

    assert.equal(typeof timeoutCallback, 'function');
    assert.equal(unrefCalled, true);

    timeoutCallback();

    assert.deepEqual(exitCodes, [1]);
    assert.ok(errors.some(message => /forced shutdown/i.test(message)));
  } finally {
    globalThis.setTimeout = originalSetTimeout;
    globalThis.clearTimeout = originalClearTimeout;
  }
});

test('synchronous server-close failure reports an unsuccessful shutdown', () => {
  const calls = [];

  const shutdown = createShutdownHandler({
    server: {
      close() {
        throw new Error('simulated synchronous close failure');
      }
    },
    closeStore() {
      calls.push('closeStore');
    },
    exit(code) {
      calls.push(`exit:${code}`);
    },
    log() {},
    warn() {},
    errorLog(message) {
      calls.push(message);
    }
  });

  shutdown('SIGTERM');

  assert.ok(
    calls.some(
      value =>
        typeof value === 'string' &&
        value.includes('HTTP server shutdown failed')
    )
  );
  assert.ok(calls.includes('exit:1'));
  assert.ok(!calls.includes('closeStore'));
});
