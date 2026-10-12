export function notFoundHandler(_req, res) {
  res.status(404).json({ error: 'Not found' });
}

export function errorHandler(error, _req, res, _next) {
  if (error?.message === 'Origin not allowed') {
    return res.status(403).json({ error: 'Origin not allowed' });
  }

  return res.status(500).json({ error: 'Internal server error' });
}
