export function notFoundHandler(_req, res) {
  res.status(404).json({ error: 'Not found' });
}

export function errorHandler(error, _req, res, _next) {
  if (error?.message === 'Origin not allowed') {
    return res.status(403).json({ error: 'Origin not allowed' });
  }

  if (error?.type === 'entity.parse.failed') {
    return res.status(400).json({ error: 'Invalid JSON body' });
  }

  if (error?.type === 'entity.too.large') {
    return res.status(413).json({ error: 'Request body too large' });
  }

  // Never expose stack traces or internal details to clients.
  return res.status(500).json({ error: 'Internal server error' });
}
