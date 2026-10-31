const byId = id => document.getElementById(id);
const text = (id, value) => { byId(id).textContent = value; };
const category = cache => ({source: 'Source', module: 'Method-name module', instance: 'Instance'})[cache];

function metricRows(id, columnsId, values, emptyMessage) {
  const target = byId(id);
  target.replaceChildren();
  const columns = Array.from(byId(columnsId).children, cell => cell.textContent);
  for (const valuesRow of values) {
    const row = document.createElement('tr');
    for (const [index, value] of valuesRow.entries()) {
      const cell = document.createElement('td');
      cell.setAttribute('data-label', columns[index] ?? '');
      cell.textContent = value;
      row.append(cell);
    }
    target.append(row);
  }
  if (!target.children.length) {
    const row = document.createElement('tr'), cell = document.createElement('td');
    row.className = 'empty-row';
    cell.colSpan = columns.length; cell.textContent = emptyMessage;
    row.append(cell); target.append(row);
  }
  return target;
}

function renderExecution(data, format) {
  text('runtime-active', format(data.active));
  text('runtime-waiting', format(data.waiting));
  text('runtime-completed', format(data.execution?.count));
  const measurements = [data.wait?.meanMs, data.execution?.meanMs];
  const maximum = Math.max(0.001, ...measurements.filter(Number.isFinite));
  for (const [index, name] of ['admission', 'execution'].entries()) {
    const value = measurements[index];
    text(index === 0 ? 'runtime-wait' : 'runtime-execution', value == null ? '—' : `${format(value)} ms`);
    const meter = byId(`${name}-meter`);
    meter.hidden = !Number.isFinite(value);
    meter.max = maximum;
    meter.value = value ?? 0;
    meter.setAttribute('aria-valuetext', value == null ? 'Unavailable' : `${format(value)} milliseconds`);
  }
}

function renderCaches(data, format) {
  const caches = data.caches.filter(cache => category(cache.cache));
  const target = metricRows('cache-scopes', 'cache-columns', caches.map(cache => [
    `${cache.language} · ${category(cache.cache)}`, format(cache.hits), format(cache.misses),
    cache.hitRate === null ? '—' : `${format(cache.hitRate)}%`, format(cache.invalidatedEntries), format(cache.size),
  ]), 'Cache metrics unavailable');
  for (const [index, cache] of caches.entries()) {
    if (cache.hitRate === null) continue;
    const progress = document.createElement('progress');
    progress.max = 100; progress.value = cache.hitRate;
    progress.className = 'success';
    progress.setAttribute('aria-label', `${cache.language} ${category(cache.cache)} hit rate`);
    const cell = target.children[index].children[3];
    cell.className = 'cache-rate'; cell.append(progress);
  }
}

function renderReloads(data, format) {
  text('reload-success', format(data.reload?.successes));
  text('reload-failure', format(data.reload?.failures));
  text('reload-active', format(data.activeReloads));
  text('reload-evictions', format(data.reloadEvictions));
  text('reload-mean', data.reload?.meanMs == null ? '—' : `${format(data.reload.meanMs)} ms`);
  text('reload-state', !data.reload ? 'Python reload metrics unavailable for this language scope.'
    : data.reload.count === 0 && data.activeReloads === 0 ? 'No reload activity recorded.' : 'Cumulative reload measurements');
  metricRows('reload-caches', 'reload-columns', data.caches.filter(cache => cache.language === 'python' && category(cache.cache))
    .map(cache => [category(cache.cache), format(cache.reloadInvalidatedEntries)]), 'Reload cache metrics unavailable');
}

/** Only update the visible diagnostics tab; values never represent inferred guest CPU time. */
export function renderRuntimeMetrics(model, format, tab) {
  const data = model.observations;
  if (tab === 'execution') renderExecution(data, format);
  if (tab === 'caching') renderCaches(data, format);
  if (tab === 'reloads') renderReloads(data, format);
}
