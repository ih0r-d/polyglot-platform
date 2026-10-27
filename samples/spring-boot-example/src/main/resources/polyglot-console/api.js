const TIMER = 'polyglot.client.invocations';
const GAUGES = [
  'polyglot.executor.available.count', 'polyglot.executor.configured.count',
  'polyglot.executor.enabled', 'polyglot.executor.source.cache.size',
  'polyglot.python.bound.interfaces.count', 'polyglot.python.instance.cache.size',
  'polyglot.startup.duration', 'process.start.time', 'jvm.info',
];

export class ConsoleRequestError extends Error {
  constructor(status) {
    super(`Endpoint returned HTTP ${status}; check local Actuator exposure and access permissions.`);
    this.name = 'ConsoleRequestError';
  }
}

export class ConsoleApi {
  constructor(base, request = globalThis.fetch.bind(globalThis)) {
    this.base = base;
    this.request = request;
  }

  async json(path, signal) {
    const response = await this.request(new URL(path, this.base), {
      method: 'GET', signal, credentials: 'same-origin', cache: 'no-store', redirect: 'error',
    });
    if (response.status === 404) return null;
    // Health DOWN returns 503 with a usable snapshot, not a transport failure.
    if (!response.ok && !(path === 'actuator/health' && response.status === 503)) {
      throw new ConsoleRequestError(response.status);
    }
    return response.json();
  }

  async snapshot(signal) {
    const [health, info, directory] = await Promise.all([
      this.json('actuator/health', signal), this.json('actuator/info', signal),
      this.json('actuator/metrics', signal),
    ]);
    const names = new Set(directory?.names ?? []);
    const gauges = Object.fromEntries(await Promise.all(GAUGES.filter(name => names.has(name))
      .map(async name => {
        const filter = ['polyglot.executor.source.cache.size', 'polyglot.executor.enabled'].includes(name)
          ? '?tag=language:python' : '';
        return [name, await this.json(`actuator/metrics/${name}${filter}`, signal)];
      })));
    const timer = names.has(TIMER) ? await this.json(`actuator/metrics/${TIMER}`, signal) : null;
    const languages = timer?.availableTags?.find(tag => tag.tag === 'language')?.values ?? [];
    const timers = await Promise.all(languages.filter(language => ['python', 'js'].includes(language))
      .flatMap(language => ['success', 'failure'].map(async outcome => {
        const query = new URLSearchParams();
        query.append('tag', `language:${language}`);
        query.append('tag', `outcome:${outcome}`);
        return { language, outcome, metric: await this.json(`actuator/metrics/${TIMER}?${query}`, signal) };
      })));
    return { health, info, gauges, timers };
  }

}
