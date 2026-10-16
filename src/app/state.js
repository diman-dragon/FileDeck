// Общее состояние приложения и небольшие функции для чтения из него.
import { sortEntries } from '../sort.js';
import { prefs } from './prefs.js';

export const S = {
  visible: [],         // записи текущего списка (для выделения)
  perm: null,          // null — проверяем, true/false
  roots: [],
  path: null,          // null — главный экран
  entries: [],
  loading: false,
  error: null,
  selection: new Set(),
  clip: null,          // { mode: 'copy' | 'move', paths: [] }
  search: null,        // { q, results, loading, truncated }
  chunk: 240,
  scrollMemo: new Map(),
  lastIn: {},          // последняя открытая папка на каждом накопителе
  history: [],         // пройденные места (path или null — главный экран), для системной кнопки «Назад»
  trash: { count: 0, bytes: 0 },
};

export const primaryRoot = () => S.roots.find((r) => r.primary) || S.roots[0];

export const rootOf = (path) =>
  S.roots.filter((r) => path === r.path || path.startsWith(r.path + '/')).sort((a, b) => b.path.length - a.path.length)[0];

export const inSearch = () => S.search !== null;

/** Список, который сейчас показан на экране. */
export function currentList() {
  if (inSearch()) return S.search.results;
  const visible = prefs.hidden ? S.entries : S.entries.filter((e) => !e.name.startsWith('.'));
  return sortEntries(visible, { by: prefs.sortBy, desc: prefs.desc });
}
