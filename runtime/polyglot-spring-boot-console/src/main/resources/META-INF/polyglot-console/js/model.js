import { operationCounts, runtimeMetricsModel } from './runtime-model.js';

export function invocationTotals(scopes, languages) {
    if (!languages.length || scopes.length !== languages.length * 2) return null;
    let successes = 0;
    let failures = 0;
    let seconds = 0;
    for (const language of languages) {
        const success = scopes.find(scope => scope.language === language && scope.outcome === 'success');
        const failure = scopes.find(scope => scope.language === language && scope.outcome === 'failure');
        if ([success?.count, failure?.count, success?.seconds, failure?.seconds]
            .some(value => !Number.isFinite(value) || value < 0)) return null;
        successes += success.count;
        failures += failure.count;
        seconds += success.seconds + failure.seconds;
    }
    const count = successes + failures;
    return {
        count, seconds, successes, failures,
        successRate: count > 0 ? successes / count * 100 : null,
        meanMs: count > 0 ? seconds / count * 1000 : null,
    };
}

function runtimeLabel(name, language) {
    const labels = {
        'polyglot.executor.configured.count': 'Configured executors',
        'polyglot.executor.available.count': 'Available executors',
        'polyglot.startup.duration': 'Startup hook (ms)',
        'polyglot.executor.enabled': 'bean present',
        'polyglot.executor.source.cache.size': 'cached sources',
        'polyglot.runtime.executors.closed': 'executor closures',
        'polyglot.python.bound.interfaces.count': 'bindings (DEFAULT)',
        'polyglot.python.instance.cache.size': 'instances (DEFAULT)',
        'polyglot.js.loaded.interfaces.count': 'bindings',
    };
    const prefix = {python: 'Python ', js: 'JavaScript '}[language] ?? '';
    return prefix + (labels[name] ?? name);
}

export function snapshotModel(snapshot, selected = 'all') {
    const allScopes = (snapshot.invocations ?? []).map(item => ({
        language: item.language, outcome: item.outcome, count: item.count,
        seconds: item.totalSeconds, maximum: item.windowMaxSeconds,
    }));
    const readings = snapshot.runtime ?? [];
    const languages = ['python', 'js'].filter(language =>
        allScopes.some(scope => scope.language === language) || readings.some(item => item.language === language));
    const language = languages.includes(selected) ? selected : 'all';
    const scopes = allScopes.filter(scope => language === 'all' || scope.language === language);
    const included = language === 'all' ? languages : [language];
    return {
        language, languages, scopes, totals: invocationTotals(scopes, included),
        health: snapshot.health,
        pythonAvailable: snapshot.pythonAvailable ?? null,
        startTime: snapshot.instanceId,
        runtime: [...readings.filter(item => !item.name.startsWith('polyglot.runtime.') || item.name === 'polyglot.runtime.executors.closed')
            .map(item => [runtimeLabel(item.name, item.language),
                item.unit === 'boolean' ? ({0: 'No', 1: 'Yes'}[item.value] ?? null) : (item.value ?? null)]),
            ...operationCounts(snapshot, languages)],
        configuration: (snapshot.configuration ?? []).filter(setting => typeof setting.value === 'boolean')
            .map(setting => [setting.name, setting.value ? 'Yes' : 'No']),
        jvm: snapshot.jvm ?? {},
        observations: runtimeMetricsModel(snapshot, included),
    };
}

/** Omit cache sizes only when that same language/category is already visible in Caching. */
export function diagnosticsRows(model, tab) {
    const visible = new Set();
    if (tab === 'caching') {
        for (const cache of model.observations.caches) {
            if (!Number.isFinite(cache.size)) continue;
            if (cache.cache === 'source') visible.add(runtimeLabel('polyglot.executor.source.cache.size', cache.language));
            if (cache.cache === 'instance') visible.add(runtimeLabel('polyglot.python.instance.cache.size', cache.language));
        }
    }
    return model.runtime.filter(([name]) => !visible.has(name));
}

/** Bounded, browser-session samples. No persistence and no invented historical data. */
export class SessionHistory {
    constructor(limit = 120) {
        this.limit = limit;
        this.samples = [];
        this.previous = null;
        this.last = null;
    }

    clear() {
        this.samples = [];
        this.previous = null;
        this.last = null;
    }

    gap(time) {
        this.previous = null;
        if (this.samples.length && this.samples.at(-1).rate !== null) {
            this.samples.push({time, rate: null, meanMs: null});
            if (this.samples.length > this.limit) this.samples.shift();
        }
    }

    add(model, time) {
        if (!model.totals) {
            this.gap(time);
            return {reset: false, sample: null};
        }
        const current = {
            time, startTime: model.startTime, language: model.language,
            scopes: model.scopes.map(scope => ({...scope})), ...model.totals
        };
        const previous = this.previous;
        const last = this.last;
        const reset = Boolean(last && (
            (current.startTime != null && last.startTime != null && current.startTime !== last.startTime)
            || current.count < last.count || current.seconds < last.seconds
            || current.language !== last.language || scopeReset(last.scopes, current.scopes)
            || current.successes < last.successes || current.failures < last.failures));
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
        this.last = current;
        return {reset, sample};
    }
}

function scopeReset(previous, current) {
    return previous.length !== current.length || previous.some(before => {
        const after = current.find(scope => scope.language === before.language && scope.outcome === before.outcome);
        return !after || after.count < before.count || after.seconds < before.seconds;
    });
}
