import { ConsoleApi } from './api.js';
import { SessionHistory, snapshotModel } from './model.js';
import { SnapshotPoller } from './polling.js';
import { connection, renderSnapshot } from './view.js';

const api = new ConsoleApi(new URL('./', document.baseURI));
const history = new SessionHistory();
const pause = document.getElementById('pause');
const refresh = document.getElementById('refresh');
let paused = false;
let configured = false;

const poller = new SnapshotPoller(signal => api.snapshot(signal), snapshot => {
  const model = snapshotModel(snapshot);
  if (!configured) {
    const interval = snapshot.refreshIntervalMillis;
    let option = Array.from(refresh.options).find(item => Number(item.value) === interval);
    if (!option) {
      option = new Option(`${interval / 1000} seconds`, String(interval));
      refresh.add(option);
    }
    refresh.value = String(interval);
    poller.intervalMs = interval;
    configured = true;
  }
  const { reset, sample } = history.add(model, performance.now());
  renderSnapshot(model, history.samples, sample);
  let warning = model.totals ? '' : 'Python invocation metrics are unavailable, disabled or filtered. Values are not inferred as zero.';
  if (reset) warning = 'Meter reset or application restart detected. Session history has been cleared.';
  connection('Live', warning);
}, () => {
  connection('Disconnected',
    'Snapshot may be stale. Check application status and authorization. Retrying.');
});

function resume() {
  if (paused || document.hidden) {
    poller.stop(); connection('Paused');
  } else {
    poller.start(Number(refresh.value));
  }
}

pause.addEventListener('click', () => {
  paused = !paused;
  pause.textContent = paused ? 'Resume' : 'Pause';
  resume();
});
refresh.addEventListener('change', resume);
document.addEventListener('visibilitychange', resume);
window.addEventListener('pagehide', () => poller.stop());

resume();
