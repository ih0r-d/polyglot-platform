/** One request at a time; cancellation prevents stale responses after pause/reconfiguration. */
export class SnapshotPoller {
    #load;
    #onData;
    #onError;
    #generation = 0;
    #timer;
    #controller;
    intervalMs;

    constructor(load, onData, onError) {
        this.#load = load;
        this.#onData = onData;
        this.#onError = onError;
    }

    start(intervalMs) {
        this.stop();
        this.intervalMs = intervalMs;
        void this.#poll(this.#generation);
    }

    stop() {
        this.#generation += 1;
        clearTimeout(this.#timer);
        this.#controller?.abort();
    }

    async #poll(generation) {
        const controller = new AbortController();
        this.#controller = controller;
        const deadline = setTimeout(() => controller.abort(), 8000);
        try {
            const data = await this.#load(controller.signal);
            if (this.#generation === generation) this.#onData(data);
        } catch (error) {
            if (this.#generation === generation) this.#onError(error);
        } finally {
            clearTimeout(deadline);
            if (this.#generation === generation) {
                this.#timer = setTimeout(() => { void this.#poll(generation); }, this.intervalMs);
            }
        }
    }
}
