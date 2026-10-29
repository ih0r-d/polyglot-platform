import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ConsoleApi, ConsoleRequestError } from '../../main/resources/META-INF/polyglot-console/js/api.js';
import { SessionHistory, snapshotModel } from '../../main/resources/META-INF/polyglot-console/js/model.js';
import { SnapshotPoller } from '../../main/resources/META-INF/polyglot-console/js/polling.js';

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
    ] };
}

function withJavaScript(source) {
  source.invocations.push(
    {language: 'js', outcome: 'success', count: 4, totalSeconds: 0.04, windowMaxSeconds: 0.02},
    {language: 'js', outcome: 'failure', count: 1, totalSeconds: 0.01, windowMaxSeconds: 0.01});
  return source;
}

test('matching Python tag scopes produce cumulative counts and mean, excluding JavaScript', () => {
  const model = snapshotModel(withJavaScript(snapshot()), 'python');
  assert.equal(model.totals.count, 10);
  assert.equal(model.totals.successRate, 80);
  assert.equal(model.totals.failures, 2);
  assert.ok(Math.abs(model.totals.meanMs - 10) < 1e-9);
  assert.equal(model.health, 'UP');
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

test('All, Python and JavaScript totals retain matching outcome and duration scopes', () => {
  const source = withJavaScript(snapshot());
  const all = snapshotModel(source);
  assert.equal(all.language, 'all');
  assert.deepEqual(all.languages, ['python', 'js']);
  assert.equal(all.totals.count, 15);
  assert.equal(all.totals.successes, 12);
  assert.equal(all.totals.failures, 3);
  assert.equal(all.totals.successRate, 80);
  assert.ok(Math.abs(all.totals.meanMs - 10) < 1e-9);
  const javascript = snapshotModel(source, 'js');
  assert.equal(javascript.totals.count, 5);
  assert.equal(javascript.totals.failures, 1);
  assert.equal(javascript.scopes.length, 2);
  assert.equal(snapshotModel(source, 'python').totals.count, 10);
  assert.equal(snapshotModel(snapshot(), 'js').language, 'all');
});

test('incomplete scopes keep observed failures visible without claiming complete totals', () => {
  const source = withJavaScript(snapshot());
  source.invocations.pop();
  const model = snapshotModel(source);
  assert.equal(model.totals, null);
  assert.equal(model.scopes.find(scope => scope.language === 'python' && scope.outcome === 'failure').count, 2);
  assert.equal(snapshotModel(source, 'python').totals.count, 10);
  source.invocations = snapshot().invocations;
  source.runtime = [{name: 'polyglot.executor.enabled', language: 'js', value: 1}];
  assert.equal(snapshotModel(source).totals, null);
  assert.deepEqual(snapshotModel(source).languages, ['python', 'js']);
  for (const value of [-1, Number.NaN, Infinity]) {
    const invalid = snapshot(); invalid.invocations[0].totalSeconds = value;
    assert.equal(snapshotModel(invalid).totals, null);
  }
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

test('per-language histories detect resets hidden by growth in another language', () => {
  const previous = withJavaScript(snapshot());
  for (const change of ['count', 'duration']) {
    const next = withJavaScript(snapshot(30, 0.5));
    if (change === 'count') next.invocations[2].count = 1;
    else next.invocations[2].totalSeconds = 0.001;
    const histories = new Map(['all', 'python', 'js'].map(language => [language, new SessionHistory()]));
    for (const [language, history] of histories) history.add(snapshotModel(previous, language), 1000);
    assert.equal(histories.get('all').add(snapshotModel(next), 2000).reset, true);
    assert.equal(histories.get('js').add(snapshotModel(next, 'js'), 2000).reset, true);
    assert.equal(histories.get('python').add(snapshotModel(next, 'python'), 2000).sample.rate, 20);
  }
  const history = new SessionHistory();
  history.add(snapshotModel(previous, 'python'), 1000);
  assert.equal(history.add(snapshotModel(previous, 'js'), 2000).reset, true);
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
    const { renderSnapshot, connection, format } = await import('../../main/resources/META-INF/polyglot-console/js/view.js');
    const model = snapshotModel(snapshot());
    document.getElementById('invocation-columns').children = ['Language', 'Outcome', 'Count',
      'Total time (s)', 'Mean (ms)', 'Window max (ms)', 'Share'].map(textContent => ({textContent}));
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('total').textContent, '10');
    assert.equal(nodes.get('scopes').children.length, 2);
    assert.equal(nodes.get('scopes').children[0].children[0].attributes['data-label'], 'Language');
    assert.equal(nodes.get('scopes').children[0].children[6].attributes['data-label'], 'Share');
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
    assert.equal(nodes.get('heap-summary').textContent, '1 MiB / 2 MiB');
    assert.equal(nodes.get('heap-usage').value, 50);
    assert.equal(nodes.get('heap-percent').textContent, '50%');
    assert.equal(nodes.get('jvm-threads').textContent, '42');
    assert.equal(nodes.get('runtime-values').children[1].textContent, 'No');
    assert.equal(nodes.get('runtime-values').children[3].textContent, 'Yes');
    assert.equal(format(null), '—');
    renderSnapshot(model, [{ time: 1, rate: 3, meanMs: 2 }, { time: 2, rate: 0, meanMs: null }], { rate: 0, meanMs: null });
    assert.equal(nodes.get('interval-mean').textContent, '—');
    model.health = 'DOWN';
    renderSnapshot(model, [], null);
    assert.equal(nodes.get('health').textContent, 'DOWN');
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
  const url = new URL('../../main/resources/META-INF/polyglot-console/js/bootstrap.js', import.meta.url);
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
    ['Python available', 'Yes'], ['JavaScript cached sources', 2], ['JavaScript bindings', null],
  ]);
  source.runtime[0].value = 0;
  assert.equal(snapshotModel(source).runtime[0][1], 'No');
  source.runtime[0].value = null;
  assert.equal(snapshotModel(source).runtime[0][1], null);
});

test('JVM units distinguish unknown readings, zero values and binary memory sizes', async () => {
  const { formatUptime, formatMemory } = await import('../../main/resources/META-INF/polyglot-console/js/view.js');
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
  function element(id = '') {
    return { children: [], listeners: new Map(), options: [{value: '5000'}], value: '5000', width: 600, height: 230,
      classList: { add() {} }, append(...items) { this.children.push(...items); },
      setAttribute(name, value) { (this.attributes ??= {})[name] = value; },
      replaceChildren(...items) { this.children = items; if (id === 'language-filter') this.options = items; }, getContext() { return context; },
      addEventListener(name, listener) { this.listeners.set(name, listener); },
      add(option) { this.options.push(option); } };
  }
  globalThis.document = {
    baseURI: 'http://localhost/app/ops/console/', hidden: false,
    getElementById(id) {
      if (!nodes.has(id)) {
        const node = element(id);
        if (id === 'language-filter') { node.options = [{value:'all'}]; node.value = 'all'; }
        nodes.set(id, node);
      }
      return nodes.get(id);
    },
    createElement: element, addEventListener(name, listener) { pageEvents.set(name, listener); },
  };
  globalThis.window = { addEventListener(name, listener) { windowEvents.set(name, listener); } };
  globalThis.Option = function (label, value) { this.textContent = label; this.value = value; };
  let failed = false;
  let source = snapshot();
  let requests = 0;
  globalThis.fetch = async () => {
    requests++;
    return { ok: !failed, status: failed ? 403 : 200,
      json: async () => ({ ...source, refreshIntervalMillis: 7000 }) };
  };
  try {
    await import('../../main/resources/META-INF/polyglot-console/js/app.js');
    await new Promise(setImmediate);
    assert.equal(nodes.get('total').textContent, '10');
    assert.equal(nodes.get('refresh').value, '7000');
    assert.equal(nodes.get('connection').textContent, 'Live');
    const filter = nodes.get('language-filter');
    assert.deepEqual(filter.options.map(option => option.value), ['all','python']);
    source = withJavaScript(snapshot());
    nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('total').textContent, '15');
    assert.deepEqual(filter.options.map(option => option.value), ['all','python','js']);
    source = withJavaScript(snapshot(20, 0.3));
    nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    const beforeFilter = requests;
    const freshness = nodes.get('freshness').textContent;
    filter.value = 'js'; filter.listeners.get('change')();
    assert.equal(nodes.get('total').textContent, '5');
    assert.equal(nodes.get('failure-count').textContent, '1');
    assert.equal(nodes.get('chart-scope').textContent, 'JavaScript');
    assert.equal(nodes.get('scopes').children.length, 2);
    filter.value = 'python'; filter.listeners.get('change')();
    assert.equal(nodes.get('total').textContent, '20');
    assert.notEqual(nodes.get('rate').textContent, '0 /s');
    assert.equal(nodes.get('freshness').textContent, freshness);
    assert.equal(requests, beforeFilter);
    nodes.get('pause').listeners.get('click')();
    assert.equal(nodes.get('pause').textContent, 'Resume');
    assert.equal(nodes.get('connection').textContent, 'Paused');
    filter.value = 'js'; filter.listeners.get('change')();
    assert.equal(nodes.get('total').textContent, '5');
    assert.equal(nodes.get('connection').textContent, 'Paused');
    nodes.get('pause').listeners.get('click')();
    await new Promise(setImmediate);
    document.hidden = true; pageEvents.get('visibilitychange')();
    assert.equal(nodes.get('connection').textContent, 'Paused');
    document.hidden = false; failed = true; pageEvents.get('visibilitychange')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('connection').textContent, 'Disconnected');
    filter.value = 'all'; filter.listeners.get('change')();
    assert.equal(nodes.get('connection').textContent, 'Disconnected');
    assert.equal(nodes.get('notice').hidden, false);
    failed = false; nodes.get('refresh').value = '2000'; nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('connection').textContent, 'Live');
    filter.value = 'js'; source = snapshot();
    nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    assert.equal(filter.value, 'all');
    assert.deepEqual(filter.options.map(option => option.value), ['all','python']);
    source = {invocations:[], runtime:[]};
    nodes.get('refresh').listeners.get('change')();
    await new Promise(setImmediate);
    assert.equal(nodes.get('total').textContent, '—');
    assert.equal(filter.disabled, true);
    assert.equal(nodes.get('notice').hidden, false);
    windowEvents.get('resize')();
  } finally {
    windowEvents.get('pagehide')?.();
    Object.assign(globalThis, previous);
  }
});

test('outcome bars use selected totals and never infer missing outcomes', async () => {
  const { outcomeShare, format } = await import('../../main/resources/META-INF/polyglot-console/js/view.js');
  const model = snapshotModel(snapshot());
  assert.equal(outcomeShare(model.scopes[0], model.totals), 80);
  assert.equal(outcomeShare(model.scopes[1], model.totals), 20);
  assert.equal(outcomeShare(model.scopes[0], null), null);
  assert.equal(outcomeShare({count: -1}, model.totals), null);
  assert.equal(outcomeShare({count: 11}, model.totals), null);
  const empty = snapshotModel(snapshot(0, 0, 0));
  assert.equal(outcomeShare(empty.scopes[0], empty.totals), null);
  const all = snapshotModel(withJavaScript(snapshot()));
  assert.ok(Math.abs(outcomeShare(all.scopes[0], all.totals) - 8 / 15 * 100) < 1e-9);
  assert.notEqual(format(0.00012), '0');
  assert.equal(format(0), '0');
});

test('heap utilization uses committed heap and rejects missing or inconsistent measurements', async () => {
  const { heapPercentage } = await import('../../main/resources/META-INF/polyglot-console/js/view.js');
  assert.equal(heapPercentage(50, 100), 50);
  assert.equal(heapPercentage(0, 100), 0);
  for (const [used, committed] of [[null,100],[1,null],[-1,100],[1,0],[101,100],[Infinity,100]]) {
    assert.equal(heapPercentage(used, committed), null);
  }
});

test('chart time labels reflect the collected monotonic time range without invented history', async () => {
  const { timeLabels, axisMaximum } = await import('../../main/resources/META-INF/polyglot-console/js/charts.js');
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
  const { drawChart } = await import('../../main/resources/META-INF/polyglot-console/js/charts.js');
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
