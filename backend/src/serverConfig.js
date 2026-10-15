import { isIP } from 'node:net';

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

  return {
    port,
    host
  };
}
