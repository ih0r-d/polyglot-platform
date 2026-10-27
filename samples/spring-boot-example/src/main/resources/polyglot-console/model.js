export function measurement(metric, statistic) {
  const value = metric?.measurements?.find(item => item.statistic === statistic)?.value;
  return Number.isFinite(value) && value >= 0 ? value : null;
}

function timerScope({ language, outcome, metric }) {
  const factor = { seconds: 1, milliseconds: 0.001, nanoseconds: 1e-9 }[metric?.baseUnit];
  const count = measurement(metric, 'COUNT');
  const total = measurement(metric, 'TOTAL_TIME');
  const maximum = measurement(metric, 'MAX');
  return {
    language, outcome, count,
    seconds: factor && total !== null ? total * factor : null,
    maximum: factor && maximum !== null ? maximum * factor : null,
  };
}

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

function status(value) {
  return typeof value === 'string' ? value : value?.code ?? 'UNKNOWN';
}

export function snapshotModel(snapshot) {
  const { info, health, gauges, timers } = snapshot;
  const scopes = timers.map(timerScope);
  const pythonHealth = health?.components?.polyglot;
  const jvm = gauges['jvm.info']?.availableTags ?? [];
  return {
    scopes, totals: pythonTotals(scopes),
    health: status(health?.status),
    pythonHealth: status((pythonHealth?.health ?? pythonHealth)?.status),
    pythonAvailable: typeof info?.python?.available === 'boolean' ? info.python.available : null,
    startTime: measurement(gauges['process.start.time'], 'VALUE'),
    runtime: [
      ['Configured executors', measurement(gauges['polyglot.executor.configured.count'], 'VALUE')],
      ['Available executors', measurement(gauges['polyglot.executor.available.count'], 'VALUE')],
      ['Python source cache', measurement(gauges['polyglot.executor.source.cache.size'], 'VALUE')],
      ['DEFAULT bound interfaces', measurement(gauges['polyglot.python.bound.interfaces.count'], 'VALUE')],
      ['DEFAULT instance cache', measurement(gauges['polyglot.python.instance.cache.size'], 'VALUE')],
      ['Recorded initialization (ms)', measurement(gauges['polyglot.startup.duration'], 'VALUE')],
    ],
    jvm: jvm.filter(tag => ['runtime', 'vendor', 'version'].includes(tag.tag))
      .map(tag => [tag.tag, tag.values.join(', ')]),
    // Allowlist only. Never render resourcesPath, arbitrary metadata or raw /info JSON.
    configuration: [
      ['Core enabled', info?.core?.enabled], ['Fail-fast', info?.core?.failFast],
      ['Python enabled', info?.python?.enabled], ['Safe defaults', info?.python?.safeDefaults],
      ['Warmup on startup', info?.python?.warmupOnStartup], ['JavaScript enabled', info?.js?.enabled],
    ].map(([name, value]) => [name, typeof value === 'boolean' ? String(value) : 'Unavailable']),
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
