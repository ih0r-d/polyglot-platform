import { drawChart } from './charts.js';

const numberFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
export const format = value => {
  if (!Number.isFinite(value)) return '—';
  return value > 0 && value < 0.01 ? value.toPrecision(2) : numberFormat.format(value);
};
const byId = id => document.getElementById(id);
const text = (id, value) => { byId(id).textContent = value; };

export function formatUptime(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return 'Unavailable';
  if (seconds >= 3600) return `${format(seconds / 3600)} h`;
  return seconds >= 60 ? `${format(seconds / 60)} min` : `${format(seconds)} s`;
}

export function formatMemory(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return 'Unavailable';
  return bytes >= 1024 ** 3 ? `${format(bytes / 1024 ** 3)} GiB` : `${format(bytes / 1024 ** 2)} MiB`;
}

function availability(value) {
  if (value == null) return 'Unavailable';
  return value ? 'Yes' : 'No';
}

function rows(id, items) {
  const target = byId(id);
  target.replaceChildren();
  for (const [name, value] of items) {
    const term = document.createElement('dt');
    const detail = document.createElement('dd');
    term.textContent = name;
    detail.textContent = value ?? 'Unavailable';
    target.append(term, detail);
  }
}

function health(id, value) {
  const target = byId(id);
  target.textContent = value;
  target.className = 'status';
  if (value === 'UP') target.classList.add('up');
  if (['DOWN', 'OUT_OF_SERVICE'].includes(value)) target.classList.add('down');
}

export function outcomeShare(scopes, scope) {
  const matching = scopes.filter(item => item.language === scope.language);
  const success = matching.find(item => item.outcome === 'success');
  const failure = matching.find(item => item.outcome === 'failure');
  if (matching.length !== 2 || !Number.isFinite(success?.count) || !Number.isFinite(failure?.count)
      || success.count < 0 || failure.count < 0) return null;
  const total = success.count + failure.count;
  return total > 0 ? scope.count / total * 100 : null;
}

function invocationRow(scope, scopes) {
  const row = document.createElement('tr');
  const values = [scope.language, scope.outcome, format(scope.count), format(scope.seconds),
    scope.count > 0 && scope.seconds !== null ? format(scope.seconds / scope.count * 1000) : '—',
    scope.maximum === null ? '—' : format(scope.maximum * 1000)];
  for (const [index, value] of values.entries()) {
    const cell = document.createElement('td');
    const label = document.createElement('span');
    label.textContent = value;
    if (index === 1) label.className = 'outcome ' + (scope.outcome === 'success' ? 'success' : 'failure');
    cell.append(label); row.append(cell);
  }
  const cell = document.createElement('td');
  const container = document.createElement('div'); container.className = 'share-cell';
  const share = outcomeShare(scopes, scope);
  if (share !== null) {
    const progress = document.createElement('progress');
    progress.max = 100; progress.value = share;
    progress.className = scope.outcome === 'success' ? 'success' : 'failure';
    progress.setAttribute('aria-label', `${scope.language} ${scope.outcome}: ${format(share)}% within language`);
    container.append(progress);
  }
  const label = document.createElement('span'); label.className = 'share-value';
  label.textContent = share === null ? '—' : `${format(share)}%`;
  container.append(label); cell.append(container); row.append(cell);
  return row;
}

export function renderSnapshot(model, history, latest) {
  const totals = model.totals;
  text('total', format(totals?.count));
  text('success-count', format(totals?.successes));
  text('failure-count', format(totals?.failures));
  text('success-rate', totals?.successRate == null ? '—' : `${format(totals.successRate)}%`);
  text('mean', totals?.meanMs == null ? '—' : `${format(totals.meanMs)} ms`);
  text('rate', latest ? `${format(latest.rate)} /s` : '—');
  text('interval-mean', latest?.meanMs == null ? '—' : `${format(latest.meanMs)} ms`);
  health('health', model.health);
  health('python-state', model.pythonHealth);
  rows('runtime-values', [
    ['Python available at startup', availability(model.pythonAvailable)],
    ...model.runtime.map(([name, value]) => [name, typeof value === 'number' ? format(value) : value]),
  ]);
  rows('configuration', model.configuration);
  const jvm = model.jvm;
  rows('jvm-values', [
    ['Java', jvm.javaVersion], ['Runtime', jvm.runtime], ['Vendor', jvm.vendor],
    ['Uptime', formatUptime(jvm.uptimeSeconds)],
    ['Heap used', formatMemory(jvm.heapUsedBytes)],
    ['Heap committed', formatMemory(jvm.heapCommittedBytes)],
    ['Live platform threads', Number.isFinite(jvm.liveThreads) ? format(jvm.liveThreads) : 'Unavailable'],
  ]);
  const table = byId('scopes');
  table.replaceChildren();
  for (const scope of model.scopes) table.append(invocationRow(scope, model.scopes));
  if (model.scopes.length === 0) {
    const row = document.createElement('tr'); const cell = document.createElement('td');
    cell.colSpan = 7; cell.textContent = 'Invocation metrics unavailable, disabled or filtered.';
    row.append(cell); table.append(row);
  }
  drawChart(byId('throughput-chart'), history, 'rate');
  drawChart(byId('latency-chart'), history, 'meanMs');
  text('freshness', `Updated ${new Date().toLocaleTimeString()}`);
}

export function connection(message, warning = '') {
  text('connection', message);
  byId('connection').className = 'connection-state ' + (message === 'Live' ? 'live' : message === 'Paused' ? 'paused' : 'disconnected');
  const notice = byId('notice');
  notice.hidden = !warning;
  notice.textContent = warning;
}
