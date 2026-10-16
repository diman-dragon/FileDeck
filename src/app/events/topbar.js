// Нажатия в верхней панели.
import { prefs, savePrefs } from '../prefs.js';
import { S } from '../state.js';
import { $, topbar } from '../dom-refs.js';
import { go, switchRoot, up } from '../navigation.js';
import { render } from '../render.js';
import { openSettingsPage } from '../pages.js';
import { selectAll } from '../selection.js';
import { exitSearch, runSearch, startSearch } from '../search.js';

topbar.addEventListener('click', (ev) => {
  const tab = ev.target.closest('.tab');
  if (tab) return go(tab.dataset.path);
  const el = ev.target.closest('[data-act]');
  if (!el) return;
  ({
    switch: () => switchRoot(el.dataset.root),
    settings: openSettingsPage,
    'toggle-view': () => { prefs.view = prefs.view === 'list' ? 'grid' : 'list'; savePrefs(); render({ keepScroll: true }); },
    'clear-sel': () => { S.selection.clear(); render({ keepScroll: true }); },
    'select-all': selectAll,
    'exit-search': exitSearch,
    'clear-query': () => { S.search.q = ''; S.search.results = []; render(); $('search-input')?.focus(); },
    search: startSearch,
    up,
  })[el.dataset.act]?.();
});

topbar.addEventListener('input', (ev) => {
  if (ev.target.id === 'search-input') runSearch(ev.target.value);
});
