// Загрузка накопителей, содержимого папки и состояния корзины.
import { FM, TR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { formatSize } from '../format.js';
import { S } from './state.js';
import { $ } from './dom-refs.js';
import { render } from './render.js';

export async function refreshTrash() {
  try {
    const r = await TR.list();
    S.trash = { count: r.count, bytes: r.bytes };
  } catch { /* корзины может ещё не быть */ }
  const b = $('trash-badge');
  if (b) b.textContent = trashLabel();
}

export const trashLabel = () => (S.trash.count ? `${S.trash.count} · ${formatSize(S.trash.bytes) || '0 Б'}` : 'пусто');

export async function loadRoots() {
  try {
    ({ roots: S.roots } = await FM.getRoots());
  } catch (e) {
    toast(e.message || 'Не удалось получить список накопителей', { error: true });
  }
}

export async function load(path, { keepScroll = false } = {}) {
  S.loading = true;
  S.error = null;
  render({ keepScroll });
  try {
    const { entries } = await FM.list({ path });
    if (S.path !== path) return; // пользователь ушёл дальше
    S.entries = entries;
  } catch (e) {
    if (S.path !== path) return;
    S.entries = [];
    S.error = e.message || 'Не удалось открыть папку';
  }
  S.loading = false;
  S.chunk = 240;
  render({ keepScroll });
}

export const refresh = () => (S.path ? load(S.path, { keepScroll: true }) : loadRoots().then(render));
