// Поиск по файлам.
import { FM } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { debounce } from '../debounce.js';
import { S, primaryRoot } from './state.js';
import { $ } from './dom-refs.js';
import { render } from './render.js';
import { renderContent } from './views/content.js';
import { renderDock } from './views/dock.js';

export function startSearch() {
  S.search = { q: '', results: [], loading: false, truncated: false };
  render();
  setTimeout(() => $('search-input')?.focus(), 50);
}

export function exitSearch() {
  S.search = null;
  S.selection.clear();
  render();
}

let searchSeq = 0;

const doSearch = debounce(async (q) => {
  const seq = ++searchSeq;
  if (!S.search) return;
  const base = S.path || primaryRoot()?.path;
  if (!q.trim() || !base) {
    S.search.results = [];
    S.search.loading = false;
    return render();
  }
  S.search.loading = true;
  renderContentOnly();
  try {
    const res = await FM.search({ path: base, query: q.trim(), limit: 400 });
    if (seq !== searchSeq || !S.search) return;
    S.search.results = res.entries;
    S.search.truncated = res.truncated;
  } catch (e) {
    toast(e.message || 'Поиск не удался', { error: true });
  }
  if (S.search) S.search.loading = false;
  renderContentOnly();
}, 380);

export function runSearch(q) {
  if (!S.search) return;
  S.search.q = q;
  doSearch(q);
}

function renderContentOnly() {
  renderContent();
  renderDock();
}
