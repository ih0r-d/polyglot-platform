export function pythonTotals(scopes) {
  const success = scopes.find(scope => scope.language === 'python' && scope.outcome === 'success');
  const failure = scopes.find(scope => scope.language === 'python' && scope.outcome === 'failure');
  if ([success?.count, failure?.count, success?.seconds, failure?.seconds]
    .some(value => value === null || value === undefined)) return null;
  const count = success.count + failure.count;
  const seconds = success.seconds + failure.seconds;
  return {
    count, seconds, successes: success.count, failures: failure.count,
    successRate: count > 0 ? success.count / count * 100 : null,
    meanMs: count > 0 ? seconds / count * 1000 : null,
  };
}

function runtimeLabel(name, language) {
  const labels = {
    'polyglot.executor.configured.count': 'Configured executors',
    'polyglot.executor.available.count': 'Available executors',
    'polyglot.startup.duration': 'Recorded initialization (ms)',
    'polyglot.executor.enabled': 'executor available (1/0)',
    'polyglot.executor.source.cache.size': 'source cache',
    'polyglot.python.bound.interfaces.count': 'DEFAULT bound interfaces',
    'polyglot.python.instance.cache.size': 'DEFAULT instance cache',
    'polyglot.js.loaded.interfaces.count': 'loaded interfaces',
  };
  const prefix = { python: 'Python ', js: 'JavaScript ' }[language] ?? '';
  return prefix + (labels[name] ?? name);
}

export function snapshotModel(snapshot) {
  const scopes = (snapshot.invocations ?? []).map(item => ({
    language: item.language, outcome: item.outcome, count: item.count,
    seconds: item.totalSeconds, maximum: item.windowMaxSeconds,
  }));
  const readings = snapshot.runtime ?? [];
  return {
    scopes, totals: pythonTotals(scopes), health: snapshot.health, pythonHealth: snapshot.health,
    pythonAvailable: snapshot.pythonAvailable ?? null,
    startTime: snapshot.instanceId,
    runtime: readings.map(item => [runtimeLabel(item.name, item.language), item.value ?? null]),
    configuration: (snapshot.configuration ?? []).map(item => [item.name, String(item.value)]),
  };
}

/** Bounded, browser-session samples. No persistence and no invented historical data. */
export class SessionHistory {
  constructor(limit = 120) {
    this.limit = limit;
    this.samples = [];
    this.previous = null;
  }

  clear() {
    this.samples = [];
    this.previous = null;
  }

  add(model, time) {
    if (!model.totals) {
      this.clear();
      return { reset: false, sample: null };
    }
    const current = { time, startTime: model.startTime, ...model.totals };
    const previous = this.previous;
    const reset = Boolean(previous && (
      (current.startTime !== null && previous.startTime !== null && current.startTime !== previous.startTime)
      || current.count < previous.count || current.seconds < previous.seconds
      || current.successes < previous.successes || current.failures < previous.failures));
    if (reset) this.clear();
    let sample = null;
    if (previous && !reset && time > previous.time) {
      const calls = current.count - previous.count;
      sample = {
        time, rate: calls * 1000 / (time - previous.time),
        meanMs: calls > 0 ? (current.seconds - previous.seconds) * 1000 / calls : null,
      };
      this.samples.push(sample);
      if (this.samples.length > this.limit) this.samples.shift();
    }
    this.previous = current;
    return { reset, sample };
  }
}
