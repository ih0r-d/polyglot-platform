/** Local tab selection only: no routing, requests, timers or history persistence. */
export class ConsoleTabs {
  #buttons;
  #changed;
  #url;
  #navigation;
  #selected;

  get selected() { return this.#selected; }

  constructor(tablist, changed, url, navigation) {
    this.#buttons = Array.from(tablist.children);
    this.#changed = changed;
    this.#url = new URL(url);
    this.#navigation = navigation;
    for (const button of this.#buttons) {
      button.addEventListener('click', () => this.select(button.dataset.tab));
    }
    tablist.addEventListener('keydown', event => this.#move(event));
    this.#activate(this.#url.searchParams.get('tab'), false);
  }

  select(name) {
    if (!this.#buttons.some(button => button.dataset.tab === name) || name === this.selected) return;
    this.#activate(name, true);
    this.#changed();
  }

  #activate(name, updateUrl) {
    this.#selected = this.#buttons.some(button => button.dataset.tab === name) ? name : 'overview';
    for (const button of this.#buttons) {
      const active = button.dataset.tab === this.selected;
      button.setAttribute('aria-selected', String(active));
      button.tabIndex = active ? 0 : -1;
      document.getElementById(button.getAttribute('aria-controls')).hidden = !active;
    }
    if (updateUrl) {
      this.#url.searchParams.set('tab', this.selected);
      this.#navigation.replaceState(null, '', this.#url.href);
    }
  }

  #move(event) {
    const index = this.#buttons.indexOf(event.target);
    if (index < 0 || event.altKey || event.ctrlKey || event.metaKey) return;
    const count = this.#buttons.length;
    const destinations = {ArrowRight: (index + 1) % count, ArrowLeft: (index + count - 1) % count,
      Home: 0, End: count - 1};
    if (!Object.hasOwn(destinations, event.key)) return;
    const next = destinations[event.key];
    event.preventDefault();
    const button = this.#buttons[next];
    this.select(button.dataset.tab);
    button.focus();
  }
}
