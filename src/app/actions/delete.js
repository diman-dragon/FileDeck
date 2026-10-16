// Удаление: в корзину или навсегда.
import { FM, TR } from '../../plugin.js';
import { toast } from '../../layers/toast.js';
import { confirmDialog } from '../../layers/confirm-dialog.js';
import { basename } from '../../path.js';
import { prefs } from '../prefs.js';
import { S, inSearch } from '../state.js';
import { refresh, refreshTrash } from '../storage.js';
import { render } from '../render.js';
import { report } from './report.js';

export async function deletePaths(paths, { permanent = false } = {}) {
  const names = paths.map(basename);
  const toTrash = prefs.useTrash && !permanent;
  const what = paths.length === 1 ? `«${names[0]}»` : `выбранное (${paths.length})`;
  const ok = !prefs.confirmDelete && toTrash ? true : await confirmDialog({
    title: toTrash ? `Переместить ${what} в корзину?` : `Удалить ${what} навсегда?`,
    text: toTrash ? `Файлы хранятся в корзине ${prefs.trashDays} дн., их можно восстановить.` : 'Файлы будут удалены без возможности восстановления.',
    okText: toTrash ? 'В корзину' : 'Удалить',
    danger: !toTrash,
  });
  if (!ok) return;
  try {
    const res = await (toTrash ? TR.move({ paths }) : FM.remove({ paths }));
    S.selection.clear();
    if (S.clip) S.clip.paths = S.clip.paths.filter((p) => !paths.includes(p));
    if (S.clip && !S.clip.paths.length) S.clip = null;
    report(res, toTrash ? 'В корзине' : 'Удалено');
    if (toTrash) refreshTrash();
  } catch (e) {
    toast(e.message || 'Не удалось удалить', { error: true });
  }
  if (inSearch()) S.search.results = S.search.results.filter((e) => !paths.includes(e.path));
  S.path ? refresh() : render();
}
