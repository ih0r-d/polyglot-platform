(() => {
  const notice = document.getElementById('notice');
  if (globalThis.location.protocol === 'file:') {
    notice.hidden = false;
    notice.textContent = 'Static preview. Open /polyglot-console/ on your running application for live metrics.';
    document.getElementById('connection').textContent = 'Not connected';
    return;
  }
  import('./app.js').catch(() => {
    notice.hidden = false;
    notice.textContent = 'Console scripts could not load. Check that the console profile is enabled and reload the page.';
  });
})();
