const finite = value => Number.isFinite(value) && value >= 0;

/** Aggregate only complete, matching operation/outcome scopes. */
export function operationTotals(operations, languages, operation) {
  const scopes = operations.filter(value => value.operation === operation && languages.includes(value.language));
  if (!languages.length || scopes.length !== languages.length * 2) return null;
  let count = 0, seconds = 0, failures = 0;
  for (const language of languages) {
    for (const outcome of ['success', 'failure']) {
      const matching = scopes.filter(value => value.language === language && value.outcome === outcome);
      if (matching.length !== 1 || !finite(matching[0].count) || !finite(matching[0].totalSeconds)) return null;
      const value = matching[0];
      count += value.count; seconds += value.totalSeconds;
      if (outcome === 'failure') failures += value.count;
    }
  }
  return {count, failures, successes: count - failures, meanMs: count > 0 ? seconds / count * 1000 : null};
}

export function cacheHitRate(hits, misses) {
  return finite(hits) && finite(misses) && hits + misses > 0 ? hits / (hits + misses) * 100 : null;
}

function gaugeTotal(readings, languages, name) {
  if (!languages.length) return null;
  let total = 0;
  for (const language of languages) {
    const scope = readings.filter(value => value.name === name && value.language === language);
    if (scope.length !== 1 || !finite(scope[0].value)) return null;
    total += scope[0].value;
  }
  return total;
}

export function runtimeMetricsModel(snapshot, languages) {
  const operations = snapshot.operations ?? [];
  const readings = snapshot.runtime ?? [];
  const reloadLanguages = languages.filter(language => language === 'python');
  return {
    active: gaugeTotal(readings, languages, 'polyglot.runtime.invocations.active'),
    waiting: gaugeTotal(readings, languages, 'polyglot.runtime.invocations.waiting'),
    wait: operationTotals(operations, languages, 'invocation_wait'),
    execution: operationTotals(operations, languages, 'invocation_execution'),
    reload: operationTotals(operations, reloadLanguages, 'reload'),
    reloadEvictions: reloadEvictions(snapshot.caches ?? [], reloadLanguages),
    activeReloads: gaugeTotal(readings, reloadLanguages, 'polyglot.runtime.reloads.active'),
    caches: (snapshot.caches ?? []).filter(value => languages.includes(value.language)).map(value => ({
      ...value, hitRate: cacheHitRate(value.hits, value.misses),
    })),
  };
}

function reloadEvictions(caches, languages) {
  if (!languages.length) return null;
  let total = 0;
  for (const language of languages) {
    for (const category of ['source', 'module', 'instance']) {
      const matching = caches.filter(value => value.language === language && value.cache === category);
      if (matching.length !== 1 || !finite(matching[0].reloadInvalidatedEntries)) return null;
      total += matching[0].reloadInvalidatedEntries;
    }
  }
  return total;
}

export function operationCounts(snapshot, languages) {
  const labels = {source_load: 'source loads', script_evaluation: 'script evaluations',
    inline_evaluation: 'inline evaluations', contract_validation: 'binding validations', instance_creation: 'instance creations'};
  const rows = [];
  for (const language of languages) {
    for (const [operation, label] of Object.entries(labels)) {
      const totals = operationTotals(snapshot.operations ?? [], [language], operation);
      if (totals) rows.push([`${language === 'python' ? 'Python' : 'JavaScript'} ${label}`, totals.count]);
    }
  }
  return rows;
}
