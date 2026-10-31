const byId = id => document.getElementById(id);
const text = (id, value) => { byId(id).textContent = value; };

/** Render detached runtime readings, never inferred client or guest-CPU measurements. */
export function renderRuntimeMetrics(model, format) {
  const data = model.observations;
  text('runtime-active', format(data.active));
  text('runtime-waiting', format(data.waiting));
  text('runtime-wait', data.wait?.meanMs == null ? '—' : `${format(data.wait.meanMs)} ms`);
  text('runtime-execution', data.execution?.meanMs == null ? '—' : `${format(data.execution.meanMs)} ms`);
  text('runtime-completed', format(data.execution?.count));
  text('reload-success', format(data.reload?.successes));
  text('reload-failure', format(data.reload?.failures));
  text('reload-active', format(data.activeReloads));
  text('reload-evictions', format(data.reloadEvictions));
  text('reload-mean', data.reload?.meanMs == null ? '—' : `${format(data.reload.meanMs)} ms`);
  const target = byId('cache-scopes');
  target.replaceChildren();
  const columns = Array.from(byId('cache-columns').children, cell => cell.textContent);
  for (const cache of data.caches) {
    const row = document.createElement('tr');
    const category = {source: 'Source', module: 'Method-name module', instance: 'Instance'}[cache.cache];
    if (!category) continue;
    const values = [`${cache.language} · ${category}`, format(cache.hits), format(cache.misses),
      cache.hitRate === null ? '—' : `${format(cache.hitRate)}%`, format(cache.invalidatedEntries), format(cache.size)];
    for (const [index, value] of values.entries()) {
      const cell = document.createElement('td');
      cell.setAttribute('data-label', columns[index] ?? '');
      cell.textContent = value; row.append(cell);
    }
    target.append(row);
  }
  if (!target.children.length) {
    const row = document.createElement('tr'), cell = document.createElement('td');
    row.className = 'empty-row';
    cell.colSpan = 6; cell.textContent = 'Cache metrics unavailable';
    row.append(cell); target.append(row);
  }
}
