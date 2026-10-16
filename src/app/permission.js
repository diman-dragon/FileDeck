// Проверка и запрос доступа ко всем файлам.
import { FM, TR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { prefs } from './prefs.js';
import { S, primaryRoot } from './state.js';
import { loadRoots, refreshTrash } from './storage.js';
import { go } from './navigation.js';
import { render } from './render.js';

export async function checkPermission() {
  try {
    const { granted } = await FM.checkPermission();
    S.perm = granted;
  } catch {
    S.perm = false;
  }
  if (S.perm) {
    await loadRoots();
    TR.purge({ days: prefs.trashDays || 30 }).catch(() => {}).then(refreshTrash);
    const first = prefs.start === 'primary' ? primaryRoot() : prefs.start === 'sd' ? S.roots.find((r) => !r.primary) : null;
    if (first && S.path === null && !S.started) {
      S.started = true;
      return go(first.path, { replace: true }); // стартовый экран — дно истории
    }
    S.started = true;
  }
  render();
}

export async function requestPermission() {
  try {
    const { granted } = await FM.requestPermission();
    if (granted) await checkPermission();
  } catch (e) {
    toast(e.message || 'Не удалось запросить доступ', { error: true });
  }
}
