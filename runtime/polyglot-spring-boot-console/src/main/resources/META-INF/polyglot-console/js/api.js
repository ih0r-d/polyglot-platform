export class ConsoleRequestError extends Error {
  constructor(status) {
    super(`Console data returned HTTP ${status}; check application access permissions.`);
    this.name = 'ConsoleRequestError';
  }
}

export class ConsoleApi {
  #url;
  #request;

  constructor(base, request = globalThis.fetch.bind(globalThis)) {
    this.#url = new URL('data', base);
    this.#request = request;
  }

  async snapshot(signal) {
    const response = await this.#request(this.#url, {
      method: 'GET', signal, credentials: 'same-origin', cache: 'no-store', redirect: 'error',
    });
    if (!response.ok) throw new ConsoleRequestError(response.status);
    return response.json();
  }
}
