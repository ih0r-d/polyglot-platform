import { ConsoleApi } from './api.js';
import { SessionHistory, snapshotModel } from './model.js';
import { SnapshotPoller } from './polling.js';
import { connection, renderSnapshot } from './view.js';

const api = new ConsoleApi(new URL('./', document.baseURI));
const sessions = new Map(['all', 'python', 'js'].map(language => [language, new SessionHistory()]));
const states = new Map();
const pause = document.getElementById('pause');
const refresh = document.getElementById('refresh');
const languageFilter = document.getElementById('language-filter');
let paused = false;
let configured = false;
let connected = null;
let updatedAt = null;

function languages(available) {
  const values = ['all', ...available];
  if (Array.from(languageFilter.options).map(option => option.value).join() !== values.join()) {
    const selected = languageFilter.value;
    languageFilter.replaceChildren(...values.map(value =>
      new Option(({ all: 'All', python: 'Python', js: 'JavaScript' })[value], value)));
    languageFilter.value = values.includes(selected) ? selected : 'all';
  }
  languageFilter.disabled = available.length === 0;
}

function markGap() {
  const time = performance.now();
  for (const history of sessions.values()) history.gap(time);
  for (const state of states.values()) state.sample = null;
}

function renderSelected() {
  if (document.hidden) { connection('Paused'); return; }
  const selected = languageFilter.value || 'all';
  const state = states.get(selected);
  if (state) renderSnapshot(state.model, sessions.get(selected).samples, state.sample, updatedAt);
  if (paused || document.hidden) {
    connection('Paused');
  } else if (connected === null) {
    connection('Connecting…');
  } else if (!connected) {
    connection('Disconnected', 'Snapshot may be stale. Check application status and authorization. Retrying.');
  } else if (state) {
    let warning = state.model.totals ? '' : 'Selected invocation metrics are unavailable, disabled or incomplete. Values are not inferred as zero.';
    if (state.reset) warning = 'Meter reset or application restart detected. Session history has been cleared.';
    connection('Live', warning);
  }
}

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
  connected = true;
  updatedAt = Date.now();
  languages(model.languages);
  const time = performance.now();
  for (const [language, history] of sessions) {
    if (language !== 'all' && !model.languages.includes(language)) {
      history.clear(); states.delete(language);
    } else {
      const scoped = language === 'all' ? model : snapshotModel(snapshot, language);
      states.set(language, { model: scoped, ...history.add(scoped, time) });
    }
  }
  renderSelected();
}, () => {
  connected = false;
  markGap();
  renderSelected();
});

function resume() {
  if (paused || document.hidden) {
    poller.stop(); markGap(); renderSelected();
  } else {
    poller.start(Number(refresh.value));
  }
}

pause.addEventListener('click', () => {
  paused = !paused;
  pause.textContent = paused ? 'Resume' : 'Pause';
  pause.setAttribute('aria-pressed', String(paused));
  resume();
});
refresh.addEventListener('change', resume);
languageFilter.addEventListener('change', renderSelected);
document.addEventListener('visibilitychange', resume);
window.addEventListener('resize', renderSelected);
window.addEventListener('pagehide', () => poller.stop());

resume();
