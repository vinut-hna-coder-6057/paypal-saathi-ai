
export function createShutdownHandler({
  server,
  closeStore,
  timeoutMs = 10_000,
  exit = code => {
    process.exitCode = code;
  },
  log = console.log,
  warn = console.warn,
  errorLog = console.error
}) {
  let isShuttingDown = false;
  let forceExitTimer;

  return function shutdown(signal) {
    if (isShuttingDown) {
      warn('Shutdown already in progress; ignoring repeated signal.');
      return;
    }

    isShuttingDown = true;
    log(`Received ${signal}. Shutting down gracefully...`);

    forceExitTimer = setTimeout(() => {
      errorLog('Forced shutdown after timeout.');
      exit(1);
    }, timeoutMs);

    forceExitTimer.unref?.();

try {
  server.close(error => {
    if (error) {
      clearTimeout(forceExitTimer);
      errorLog('HTTP server shutdown failed:', error.message);
      exit(1);
      return;
    }

    try {
      closeStore();
      log('HTTP server and knowledge store closed.');
      exit(0);
    } catch (cleanupError) {
      errorLog(
        'Knowledge store shutdown failed:',
        cleanupError.message
      );
      exit(1);
    } finally {
      clearTimeout(forceExitTimer);
    }
  });
} catch (error) {
  clearTimeout(forceExitTimer);
  errorLog('HTTP server shutdown failed:', error.message);
  exit(1);
}

  };
}

