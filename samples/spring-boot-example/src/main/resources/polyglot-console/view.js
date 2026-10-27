const numberFormat = new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 });
export const format = value => Number.isFinite(value) ? numberFormat.format(value) : '—';
const byId = id => document.getElementById(id);
const text = (id, value) => { byId(id).textContent = value; };

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

function chart(id, samples, field, color) {
  const canvas = byId(id);
  const context = canvas.getContext('2d');
  if (!context) return;
  const width = canvas.width;
  const height = canvas.height;
  context.clearRect(0, 0, width, height);
  context.strokeStyle = '#e6e9eb';
  context.lineWidth = 1;
  for (let y = 20; y < height - 10; y += 40) {
    context.beginPath(); context.moveTo(0, y); context.lineTo(width, y); context.stroke();
  }
  const values = samples.map(sample => sample[field]).filter(Number.isFinite);
  if (values.length === 0) {
    context.fillStyle = '#5c6268'; context.font = '13px system-ui';
    context.fillText('Waiting for two snapshots with matching Timer data', 15, 95);
    return;
  }
  const maximum = Math.max(...values, 0.01) * 1.2;
  context.fillStyle = '#5c6268'; context.font = '10px system-ui';
  context.fillText(`${format(maximum)}`, 3, 13);
  context.fillText('0', 3, height - 3);
  context.strokeStyle = color; context.lineWidth = 2;
  const first = samples[0].time;
  const elapsed = Math.max(samples.at(-1).time - first, 1);
  let connected = false;
  context.beginPath();
  for (const sample of samples) {
    if (!Number.isFinite(sample[field])) { connected = false; continue; }
    const x = 25 + (sample.time - first) / elapsed * (width - 35);
    const y = height - 20 - sample[field] / maximum * (height - 45);
    if (connected) context.lineTo(x, y); else context.moveTo(x, y);
    connected = true;
    context.fillStyle = color; context.fillRect(x - 2, y - 2, 4, 4);
  }
  context.stroke();
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
    ['Python available', model.pythonAvailable === null ? 'Unavailable' : String(model.pythonAvailable)],
    ...model.runtime.map(([name, value]) => [name, value === null ? 'Unavailable' : format(value)]),
  ]);
  rows('configuration', model.configuration);
  rows('jvm', model.jvm.length ? model.jvm : [['JVM metadata', 'Unavailable']]);
  const table = byId('scopes');
  table.replaceChildren();
  for (const scope of model.scopes) {
    const row = document.createElement('tr');
    const values = [scope.language, scope.outcome, format(scope.count), format(scope.seconds),
      scope.count > 0 && scope.seconds !== null ? format(scope.seconds / scope.count * 1000) : '—',
      scope.maximum === null ? '—' : format(scope.maximum * 1000)];
    for (const value of values) {
      const cell = document.createElement('td'); cell.textContent = value; row.append(cell);
    }
    table.append(row);
  }
  if (model.scopes.length === 0) {
    const row = document.createElement('tr'); const cell = document.createElement('td');
    cell.colSpan = 6; cell.textContent = 'Invocation metrics unavailable, disabled or filtered.';
    row.append(cell); table.append(row);
  }
  chart('throughput-chart', history, 'rate', '#096a49');
  chart('latency-chart', history, 'meanMs', '#6450a1');
  text('freshness', `Updated ${new Date().toLocaleTimeString()}`);
}

export function connection(message, warning = '') {
  text('connection', message);
  const notice = byId('notice');
  notice.hidden = !warning;
  notice.textContent = warning;
}

