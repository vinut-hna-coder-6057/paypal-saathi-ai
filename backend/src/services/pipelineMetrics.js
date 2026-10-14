const counters = {
  requests: 0,
  requestDurationMs: 0,
  retrievals: 0,
  retrievalDurationMs: 0,
  retrievalFailures: 0,
  modelGenerations: 0,
  modelGenerationDurationMs: 0,
  modelFailures: 0,
  modelTimeouts: 0,
  fallbacks: 0
};

export function recordPipelineMetric(name, durationMs = 0) {
  if (!Object.hasOwn(counters, name)) {
    throw new RangeError(`Unknown pipeline metric: ${name}`);
  }
  counters[name]++;
  if (durationMs > 0 && Number.isFinite(durationMs)) {
    const durationName = name === 'requests'
      ? 'requestDurationMs'
      : name === 'retrievals'
        ? 'retrievalDurationMs'
        : name === 'modelGenerations'
          ? 'modelGenerationDurationMs'
          : null;
    if (durationName) {
      counters[durationName] += Math.round(durationMs);
    }
  }
}

export function getPipelineMetrics() {
  return { ...counters };
}

export function resetPipelineMetrics() {
  for (const key of Object.keys(counters)) {
    counters[key] = 0;
  }
}
