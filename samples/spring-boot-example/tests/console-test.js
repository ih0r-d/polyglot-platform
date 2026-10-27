import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ConsoleApi, ConsoleRequestError } from '../src/main/resources/polyglot-console/api.js';
import { SessionHistory, snapshotModel } from '../src/main/resources/polyglot-console/model.js';
import { SnapshotPoller } from '../src/main/resources/polyglot-console/polling.js';

function metric(count, seconds, maximum = 0.01) {
  return { baseUnit: 'seconds', measurements: [
    { statistic: 'COUNT', value: count }, { statistic: 'TOTAL_TIME', value: seconds },
    { statistic: 'MAX', value: maximum },
  ] };
}
function snapshot(count = 10, seconds = 0.1, failures = 2, startTime = 100) {
  return { health: { status: { code: 'UP' }, components: { polyglot: { health: { status: { code: 'UNKNOWN' } } } } },
    info: { python: { available: true, resourcesPath: '/private/secret' }, core: { enabled: true } },
    gauges: { 'process.start.time': { measurements: [{ statistic: 'VALUE', value: startTime }] } },
    timers: [
      { language: 'python', outcome: 'success', metric: metric(count - failures, seconds * 0.8) },
      { language: 'python', outcome: 'failure', metric: metric(failures, seconds * 0.2) },
      { language: 'js', outcome: 'success', metric: metric(999, 999) },
    ] };
}

test('matching Python tag scopes produce cumulative counts and mean, excluding JavaScript', () => {
  const model = snapshotModel(snapshot());
  assert.equal(model.totals.count, 10);
  assert.equal(model.totals.successRate, 80);
  assert.equal(model.totals.failures, 2);
  assert.ok(Math.abs(model.totals.meanMs - 10) < 1e-9);
  assert.equal(model.health, 'UP');
  assert.equal(model.pythonHealth, 'UNKNOWN');
  assert.ok(!JSON.stringify(model).includes('/private/secret'));
});

test('absent, incomplete, unsupported units and zero-call metrics are not fabricated', () => {
  const source = snapshot(0, 0, 0);
  assert.equal(snapshotModel(source).totals.meanMs, null);
  assert.equal(snapshotModel(source).totals.successRate, null);
  source.timers[0].metric = null;
  assert.equal(snapshotModel(source).totals, null);
  source.timers = [];
  assert.equal(snapshotModel(source).totals, null);
  source.timers = [{ language: 'python', outcome: 'success', metric: { ...metric(2, 1), baseUnit: 'unknown' } }];
  assert.equal(snapshotModel(source).scopes[0].seconds, null);
});

test('timer units normalize without inventing MAX and invalid numbers are rejected', () => {
  const source = snapshot();
  source.timers[0].metric.baseUnit = 'milliseconds';
  source.timers[0].metric.measurements = [
    { statistic: 'COUNT', value: 4 }, { statistic: 'TOTAL_TIME', value: 20 },
  ];
  const scope = snapshotModel(source).scopes[0];
  assert.equal(scope.seconds, 0.02);
  assert.equal(scope.maximum, null);
  source.timers[0].metric.measurements[0].value = Number.NaN;
  assert.equal(snapshotModel(source).totals, null);
});

test('bounded history derives rates and interval means, with gaps for idle latency', () => {
  const history = new SessionHistory(2);
  assert.equal(history.add(snapshotModel(snapshot()), 0).sample, null);
  const delta = history.add(snapshotModel(snapshot(20, 0.3)), 2000).sample;
  assert.equal(delta.rate, 5);
  assert.ok(Math.abs(delta.meanMs - 20) < 1e-9);
  assert.equal(history.add(snapshotModel(snapshot(20, 0.3)), 4000).sample.meanMs, null);
  history.add(snapshotModel(snapshot(30, 0.5)), 6000);
  assert.equal(history.samples.length, 2);
});

test('counter resets, outcome resets, restart and missing meters clear history', () => {
  for (const next of [snapshot(1, 0.01, 0), snapshot(20, 0.4, 1), snapshot(20, 0.4, 4, 200)]) {
    const history = new SessionHistory();
    history.add(snapshotModel(snapshot()), 1000);
    assert.equal(history.add(snapshotModel(next), 2000).reset, true);
    assert.equal(history.samples.length, 0);
  }
  const history = new SessionHistory();
  history.add(snapshotModel(snapshot()), 0);
  const source = snapshot(); source.timers = [];
  history.add(snapshotModel(source), 2000);
  assert.equal(history.previous, null);
});

test('API only performs same-origin GETs with exact language/outcome filters', async () => {
  const calls = [];
  const api = new ConsoleApi(new URL('http://localhost:8080/sample/'), async (url, options) => {
    calls.push({ url, options });
    let data = {};
    if (url.pathname.endsWith('/metrics')) data = { names: ['polyglot.client.invocations', 'polyglot.executor.source.cache.size'] };
    if (url.pathname.endsWith('polyglot.client.invocations')) data = { ...metric(10, 1), availableTags: [{ tag: 'language', values: ['python', 'js'] }] };
    return { ok: true, status: 200, json: async () => data };
  });
  const result = await api.snapshot(new AbortController().signal);
  assert.equal(result.timers.length, 4);
  assert.deepEqual(calls.filter(call => call.url.searchParams.has('tag') && call.url.pathname.endsWith('invocations'))
    .map(call => call.url.searchParams.getAll('tag')), [
    ['language:python', 'outcome:success'], ['language:python', 'outcome:failure'],
    ['language:js', 'outcome:success'], ['language:js', 'outcome:failure'],
  ]);
  assert.ok(calls.every(call => call.url.pathname.startsWith('/sample/actuator/')));
  assert.ok(calls.every(call => call.options.method === 'GET'));
  assert.ok(calls.every(call => call.options.credentials === 'same-origin'));
  assert.ok(calls.find(call => call.url.pathname.endsWith('source.cache.size')).url.searchParams.get('tag') === 'language:python');
});

test('API tolerates missing endpoints and DOWN health, sanitizes access failures', async () => {
  for (const status of [404, 503, 401]) {
    const api = new ConsoleApi(new URL('http://localhost/'), async () => ({
      ok: false, status, json: async () => ({ status: 'DOWN' }),
    }));
    if (status === 404) assert.equal(await api.json('actuator/health'), null);
    if (status === 503) assert.deepEqual(await api.json('actuator/health'), { status: 'DOWN' });
    if (status === 401) await assert.rejects(api.json('actuator/health'), ConsoleRequestError);
  }
});

test('poller does not overlap and discards in-flight responses after stop', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let resolve;
  let calls = 0;
  const results = [];
  const poller = new SnapshotPoller(() => { calls++; return new Promise(done => { resolve = done; }); },
    value => results.push(value), error => assert.fail(error.message));
  poller.start(1000);
  t.mock.timers.tick(5000);
  assert.equal(calls, 1);
  poller.stop(); resolve('stale');
  await Promise.resolve(); await Promise.resolve();
  assert.deepEqual(results, []);
  t.mock.timers.tick(10000);
  assert.equal(calls, 1);
});

test('poller retries failures, refreshes at the selected interval and cancels on pause', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let calls = 0;
  let errors = 0;
  const values = [];
  const poller = new SnapshotPoller(async () => {
    calls++;
    if (calls === 1) throw new ConsoleRequestError(503);
    return calls;
  }, value => values.push(value), () => { errors++; });
  poller.start(2000);
  await Promise.resolve(); await Promise.resolve();
  assert.equal(errors, 1);
  t.mock.timers.tick(1999); assert.equal(calls, 1);
  t.mock.timers.tick(1);
  await Promise.resolve(); await Promise.resolve();
  assert.deepEqual(values, [2]);
  poller.stop(); t.mock.timers.tick(2000);
  assert.equal(calls, 2);
});

test('rendering handles real snapshot models, missing values and chart gaps without HTML injection', async () => {
  const nodes = new Map();
  const context = Object.fromEntries(['clearRect', 'beginPath', 'moveTo', 'lineTo', 'stroke', 'fillText', 'fillRect']
    .map(name => [name, () => {}]));
  function element() {
    return { children: [], classList: { add() {} },
      append(...items) { this.children.push(...items); },
      replaceChildren() { this.children = []; },
      getContext() { return context; }, width: 600, height: 190 };
  }
  globalThis.document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
    createElement: element,
  };
  try {
    const { renderSnapshot, connection, format } = await import('../src/main/resources/polyglot-console/view.js');
    const model = snapshotModel(snapshot());
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('total').textContent, '10');
    assert.equal(nodes.get('scopes').children.length, 3);
    assert.equal(nodes.get('mean').textContent, '10 ms');
    assert.equal(format(null), '—');
    renderSnapshot(model, [{ time: 1, rate: 3, meanMs: 2 }, { time: 2, rate: 0, meanMs: null }], { rate: 0, meanMs: null });
    assert.equal(nodes.get('interval-mean').textContent, '—');
    model.health = 'DOWN'; model.jvm = [['vendor', '<script>unsafe</script>']];
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('jvm').children[1].textContent, '<script>unsafe</script>');
    model.scopes = []; model.totals = null; model.pythonAvailable = null;
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('total').textContent, '—');
    assert.equal(nodes.get('scopes').children[0].children[0].colSpan, 6);
    connection('Disconnected', 'Stale values');
    assert.equal(nodes.get('notice').hidden, false);
    connection('Live'); assert.equal(nodes.get('notice').hidden, true);
  } finally {
    delete globalThis.document;
  }
});

test('file preview has actionable HTTP guidance without loading modules or making requests', async () => {
  const { readFile } = await import('node:fs/promises');
  const { runInNewContext } = await import('node:vm');
  const nodes = { notice: { hidden: true }, connection: {} };
  const url = new URL('../src/main/resources/polyglot-console/bootstrap.js', import.meta.url);
  runInNewContext(await readFile(url, 'utf8'), {
    location: { protocol: 'file:' }, document: { getElementById: id => nodes[id] },
  }, { filename: url.pathname });
  assert.equal(nodes.notice.hidden, false);
  assert.match(nodes.notice.textContent, /Static preview/);
  assert.equal(nodes.connection.textContent, 'Not connected');
});

test('default browser fetch retains its Window receiver', async () => {
  const original = globalThis.fetch;
  globalThis.fetch = async function () {
    assert.equal(this, globalThis);
    return { ok: true, status: 200, json: async () => ({ status: 'UP' }) };
  };
  try {
    const api = new ConsoleApi(new URL('http://localhost/'));
    assert.deepEqual(await api.json('actuator/health'), { status: 'UP' });
  } finally { globalThis.fetch = original; }
});
