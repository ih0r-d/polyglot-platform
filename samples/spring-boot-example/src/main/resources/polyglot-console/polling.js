/** One request at a time; cancellation prevents stale responses after pause/reconfiguration. */
export class SnapshotPoller {
  constructor(load, onData, onError) {
    this.load = load;
    this.onData = onData;
    this.onError = onError;
    this.generation = 0;
  }

  start(intervalMs) {
    this.stop();
    this.intervalMs = intervalMs;
    const generation = this.generation;
    this.poll(generation);
  }

  stop() {
    this.generation += 1;
    clearTimeout(this.timer);
    this.controller?.abort();
  }

  async poll(generation) {
    const controller = new AbortController();
    this.controller = controller;
    const deadline = setTimeout(() => controller.abort(), 8000);
    try {
      const data = await this.load(controller.signal);
      if (this.generation === generation) this.onData(data);
    } catch (error) {
      if (this.generation === generation) this.onError(error);
    } finally {
      clearTimeout(deadline);
      if (this.generation === generation) {
        this.timer = setTimeout(() => this.poll(generation), this.intervalMs);
      }
    }
  }
}
