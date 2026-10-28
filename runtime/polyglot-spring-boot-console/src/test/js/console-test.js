import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ConsoleApi, ConsoleRequestError } from '../../main/resources/META-INF/polyglot-console/api.js';
import { SessionHistory, snapshotModel } from '../../main/resources/META-INF/polyglot-console/model.js';
import { SnapshotPoller } from '../../main/resources/META-INF/polyglot-console/polling.js';

function metric(count, seconds, maximum = 0.01) {
  return { baseUnit: 'seconds', measurements: [
    { statistic: 'COUNT', value: count }, { statistic: 'TOTAL_TIME', value: seconds },
    { statistic: 'MAX', value: maximum },
  ] };
}
function snapshot(count = 10, seconds = 0.1, failures = 2, startTime = 'instance-a') {
  return { health: 'UP', pythonAvailable: true, instanceId: startTime,
    refreshIntervalMillis: 5000, runtime: [], configuration: [], invocations: [
      { language: 'python', outcome: 'success', count: count - failures, totalSeconds: seconds * 0.8, windowMaxSeconds: 0.1 },
      { language: 'python', outcome: 'failure', count: failures, totalSeconds: seconds * 0.2, windowMaxSeconds: 0.02 },
      { language: 'js', outcome: 'success', count: 999, totalSeconds: 999, windowMaxSeconds: 1 },
    ] };
}

test('matching Python tag scopes produce cumulative counts and mean, excluding JavaScript', () => {
  const model = snapshotModel(snapshot());
  assert.equal(model.totals.count, 10);
  assert.equal(model.totals.successRate, 80);
  assert.equal(model.totals.failures, 2);
  assert.ok(Math.abs(model.totals.meanMs - 10) < 1e-9);
  assert.equal(model.health, 'UP');
  assert.equal(model.pythonHealth, 'UP');
  assert.ok(!JSON.stringify(model).includes('/private/secret'));
});

test('missing and zero-call metrics are not fabricated', () => {
  const source = snapshot(0, 0, 0);
  assert.equal(snapshotModel(source).totals.meanMs, null);
  assert.equal(snapshotModel(source).totals.successRate, null);
  source.invocations = [];
  assert.equal(snapshotModel(source).totals, null);
  source.invocations = [{ language: 'python', outcome: 'success', count: 1, totalSeconds: 1 }];
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
  const source = snapshot(); source.invocations = [];
  history.add(snapshotModel(source), 2000);
  assert.equal(history.previous, null);
});

test('API fetches only its private data endpoint on the current application origin and context', async () => {
  const calls = [];
  const expected = snapshot();
  const api = new ConsoleApi(new URL('http://localhost:8545/app/ops/console/'), async (url, options) => {
    calls.push({ url, options });
    return { ok: true, json: async () => expected };
  });
  assert.deepEqual(await api.snapshot(new AbortController().signal), expected);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].url.href, 'http://localhost:8545/app/ops/console/data');
  assert.equal(calls[0].options.method, 'GET');
  assert.equal(calls[0].options.credentials, 'same-origin');
});

test('API reports unavailable and unauthorized responses without returning internal error details', async () => {
  for (const status of [404, 503, 401]) {
    const api = new ConsoleApi(new URL('http://localhost/'), async () => ({ ok: false, status }));
    await assert.rejects(api.snapshot(), ConsoleRequestError);
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
  const context = Object.fromEntries(['clearRect', 'beginPath', 'moveTo', 'lineTo', 'stroke', 'fillText', 'fillRect', 'setTransform', 'arc', 'closePath', 'fill']
    .map(name => [name, () => {}]));
  function element() {
    return { children: [], classList: { add() {} },
      append(...items) { this.children.push(...items); },
      setAttribute(name, value) { (this.attributes ??= {})[name] = value; },
      replaceChildren() { this.children = []; },
      getContext() { return context; }, width: 600, height: 190 };
  }
  globalThis.document = {
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
    createElement: element,
  };
  try {
    const { renderSnapshot, connection, format } = await import('../../main/resources/META-INF/polyglot-console/view.js');
    const model = snapshotModel(snapshot());
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('total').textContent, '10');
    assert.equal(nodes.get('scopes').children.length, 3);
    assert.equal(nodes.get('mean').textContent, '10 ms');
    assert.equal(nodes.get('jvm-values').children[1].textContent, 'Unavailable');
    model.jvm = { javaVersion: '25-test', runtime: '<script>unsafe</script>', vendor: 'Test vendor',
      uptimeSeconds: 120, heapUsedBytes: 1048576, heapCommittedBytes: 2097152, liveThreads: 42 };
    model.runtime = [['Python executor available', 'Yes']];
    model.pythonAvailable = false;
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('jvm-values').children[1].textContent, '25-test');
    assert.equal(nodes.get('jvm-values').children[3].textContent, '<script>unsafe</script>');
    assert.equal(nodes.get('jvm-values').children[7].textContent, '2 min');
    assert.equal(nodes.get('jvm-values').children[9].textContent, '1 MiB');
    assert.equal(nodes.get('jvm-values').children[13].textContent, '42');
    assert.equal(nodes.get('runtime-values').children[1].textContent, 'No');
    assert.equal(nodes.get('runtime-values').children[3].textContent, 'Yes');
    assert.equal(format(null), '—');
    renderSnapshot(model, [{ time: 1, rate: 3, meanMs: 2 }, { time: 2, rate: 0, meanMs: null }], { rate: 0, meanMs: null });
    assert.equal(nodes.get('interval-mean').textContent, '—');
    model.health = 'DOWN'; model.configuration = [['Flag', '<script>unsafe</script>']];
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('configuration').children[1].textContent, '<script>unsafe</script>');
    model.scopes = []; model.totals = null; model.pythonAvailable = null;
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('total').textContent, '—');
    assert.equal(nodes.get('scopes').children[0].children[0].colSpan, 7);
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
  const url = new URL('../../main/resources/META-INF/polyglot-console/bootstrap.js', import.meta.url);
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
    assert.deepEqual(await api.snapshot(), { status: 'UP' });
  } finally { globalThis.fetch = original; }
});

test('runtime view retains every supplied gauge and its language scope, including unavailable values', () => {
  const source = snapshot();
  source.runtime = [
    { name: 'polyglot.executor.enabled', language: 'python', unit: 'boolean', value: 1 },
    { name: 'polyglot.executor.source.cache.size', language: 'js', value: 2 },
    { name: 'polyglot.js.loaded.interfaces.count', language: 'js', value: null },
  ];
  const model = snapshotModel(source);
  assert.deepEqual(model.runtime, [
    ['Python executor available', 'Yes'], ['JavaScript source cache', 2], ['JavaScript loaded interfaces', null],
  ]);
  source.runtime[0].value = 0;
  assert.equal(snapshotModel(source).runtime[0][1], 'No');
  source.runtime[0].value = null;
  assert.equal(snapshotModel(source).runtime[0][1], null);
  source.configuration = [{name: 'Fail-fast', value: true}, {name: 'JavaScript enabled', value: false}];
  assert.deepEqual(snapshotModel(source).configuration, [['Fail-fast', 'Enabled'], ['JavaScript enabled', 'Disabled']]);
});

test('JVM units distinguish unknown readings, zero values and binary memory sizes', async () => {
  const { formatUptime, formatMemory } = await import('../../main/resources/META-INF/polyglot-console/view.js');
  for (const value of [null, undefined, -1, Number.NaN, Infinity]) {
    assert.equal(formatUptime(value), 'Unavailable');
    assert.equal(formatMemory(value), 'Unavailable');
  }
  assert.equal(formatUptime(0), '0 s');
  assert.equal(formatUptime(30), '30 s');
  assert.equal(formatUptime(90), '1.5 min');
  assert.equal(formatUptime(5400), '1.5 h');
  assert.equal(formatMemory(0), '0 MiB');
  assert.equal(formatMemory(1048576), '1 MiB');
  assert.equal(formatMemory(1073741824), '1 GiB');
});

test('dashboard initializes its controls and preserves polling, pause and hidden-tab behavior', async () => {
  const previous = { document: globalThis.document, window: globalThis.window, fetch: globalThis.fetch, Option: globalThis.Option };
  const nodes = new Map();
  const pageEvents = new Map();
  const windowEvents = new Map();
  const context = Object.fromEntries(['clearRect', 'beginPath', 'moveTo', 'lineTo', 'stroke', 'fillText', 'fillRect', 'setTransform', 'arc', 'closePath', 'fill'].map(name => [name, () => {}]));
  function element() {
    return { children: [], listeners: new Map(), options: [{value: '5000'}], value: '5000', width: 600, height: 230,
      classList: { add() {} }, append(...items) { this.children.push(...items); },
      setAttribute(name, value) { (this.attributes ??= {})[name] = value; },
      replaceChildren() { this.children = []; }, getContext() { return context; },
      addEventListener(name, listener) { this.listeners.set(name, listener); },
      add(option) { this.options.push(option); } };
  }
  globalThis.document = {
    baseURI: 'http://localhost/app/ops/console/', hidden: false,
    getElementById(id) { if (!nodes.has(id)) nodes.set(id, element()); return nodes.get(id); },
    createElement: element, addEventListener(name, listener) { pageEvents.set(name, listener); },
  };
  globalThis.window = { addEventListener(name, listener) { windowEvents.set(name, listener); } };
  globalThis.Option = function (label, value) { this.textContent = label; this.value = value; };
  let failed = false;
  globalThis.fetch = async () => ({ ok: !failed, status: failed ? 403 : 200,
    json: async () => ({ ...snapshot(), refreshIntervalMillis: 7000 }) });
  try {
    await import('../../main/resources/META-INF/polyglot-console/app.js');
    await new Promise(setImmediate);
    assert.equal(nodes.get('total').textContent, '10');
    assert.equal(nodes.get('refresh').value, '7000');
    assert.equal(nodes.get('connection').textContent, 'Live');
    nodes.get('pause').listeners.get('click')();
    assert.equal(nodes.get('pause').textContent, 'Resume');
    assert.equal(nodes.get('connection').textContent, 'Paused');
    nodes.get('pause').listeners.get('click')();
    await new Promise(setImmediate);
    document.hidden = true; pageEvents.get('visibilitychange')();
    assert.equal(nodes.get('connection').textContent, 'Paused');
    document.hidden = false; failed = true; pageEvents.get('visibilitychange')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('connection').textContent, 'Disconnected');
    assert.equal(nodes.get('notice').hidden, false);
    failed = false; nodes.get('refresh').value = '2000'; nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('connection').textContent, 'Live');
  } finally {
    windowEvents.get('pagehide')?.();
    Object.assign(globalThis, previous);
  }
});

test('outcome bars use complete matching language scopes and never infer missing outcomes', async () => {
  const { outcomeShare, format } = await import('../../main/resources/META-INF/polyglot-console/view.js');
  const scopes = snapshotModel(snapshot()).scopes;
  assert.equal(outcomeShare(scopes, scopes[0]), 80);
  assert.equal(outcomeShare(scopes, scopes[1]), 20);
  assert.equal(outcomeShare(scopes, scopes[2]), null);
  const empty = snapshotModel(snapshot(0, 0, 0)).scopes;
  assert.equal(outcomeShare(empty, empty[0]), null);
  assert.notEqual(format(0.00012), '0');
  assert.equal(format(0), '0');
});

test('chart time labels reflect the collected monotonic time range without invented history', async () => {
  const { timeLabels, axisMaximum } = await import('../../main/resources/META-INF/polyglot-console/charts.js');
  const origin = Date.UTC(2026, 9, 27, 12, 0, 0);
  const labels = timeLabels(0, 10000, 600, origin);
  assert.equal(labels[0].fraction, 0);
  assert.equal(labels.at(-1).fraction, 1);
  assert.ok(labels.length >= 2 && labels.length <= 6);
  assert.ok(labels.every(tick => /^\d{2}:\d{2}:\d{2}$/.test(tick.label)));
  assert.equal(timeLabels(500, 500, 300, origin).length, 1);
  assert.equal(timeLabels(0, 1, 600, origin).length, 1);
  assert.equal(axisMaximum([0]), 1);
  assert.ok(axisMaximum([1, 7, 9]) >= 9);
  assert.ok(axisMaximum([0.0001]) >= 0.0001);
});

test('Canvas uses device pixels, draws time labels and preserves gaps in latency samples', async () => {
  const { drawChart } = await import('../../main/resources/META-INF/polyglot-console/charts.js');
  const traces = [];
  const context = Object.fromEntries(['clearRect', 'beginPath', 'moveTo', 'lineTo', 'stroke', 'fillText', 'arc', 'closePath', 'fill', 'setTransform']
    .map(name => [name, (...args) => traces.push({ name, args })]));
  const canvas = { clientWidth: 600, clientHeight: 230, getContext: () => context };
  const previous = globalThis.devicePixelRatio;
  globalThis.devicePixelRatio = 2;
  try {
    drawChart(canvas, [
      { time: 0, meanMs: 10 }, { time: 1000, meanMs: null }, { time: 2000, meanMs: 20 },
    ], 'meanMs');
    assert.equal(canvas.width, 1200);
    assert.equal(canvas.height, 460);
    assert.deepEqual(traces.find(item => item.name === 'setTransform').args, [2, 0, 0, 2, 0, 0]);
    assert.equal(traces.filter(item => item.name === 'arc').length, 2);
    assert.equal(traces.filter(item => item.name === 'closePath').length, 0);
    assert.ok(traces.some(item => item.name === 'fillText' && String(item.args[0]).includes(':')));
    traces.length = 0;
    drawChart(canvas, [{ time: 0, rate: 1 }, { time: 2000, rate: 4 }], 'rate');
    assert.equal(traces.filter(item => item.name === 'closePath').length, 1);
    drawChart(canvas, [], 'rate');
    drawChart({getContext: () => null}, [], 'rate');
  } finally { globalThis.devicePixelRatio = previous; }
});
