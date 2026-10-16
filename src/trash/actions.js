// Действия корзины: восстановить выбранное, удалить выбранное, очистить всё.
import { TR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { confirmDialog } from '../layers/confirm-dialog.js';
import { formatSize } from '../format.js';
import { withTrashProgress } from './progress.js';

export async function restoreSelected(c) {
  if (c.busy) return;
  c.busy = true;
  try {
    const res = await withTrashProgress('Восстанавливаем…', () => TR.restore({ ids: [...c.selected] }));
    const failed = res.failed?.length || 0;
    toast(`${res.cancelled ? 'Отменено. ' : ''}Восстановлено: ${res.done}${failed ? `, не удалось: ${failed}` : ''}`, { error: !!failed });
    c.selected.clear();
    c.ctx.onChange && c.ctx.onChange();
  } catch (e) {
    toast(e.message || 'Не удалось восстановить', { error: true });
  }
  c.busy = false;
  return c.load();
}

/** all = true — очистить всю корзину, иначе удалить только выбранное. */
export async function deleteFromTrash(c, all) {
  const ok = await confirmDialog({
    title: all ? 'Очистить корзину?' : `Удалить навсегда (${c.selected.size})?`,
    text: 'Файлы будут удалены без возможности восстановления.',
    okText: all ? 'Очистить' : 'Удалить',
  });
  if (!ok || c.busy) return;
  c.busy = true;
  const expected = all ? c.items.length : c.selected.size;
  try {
    const res = await withTrashProgress(all ? 'Очищаем корзину…' : 'Удаляем…', () => (all ? TR.empty() : TR.remove({ ids: [...c.selected] })));
    const failed = res.cancelled ? 0 : Math.max(0, expected - res.done);
    const freed = `освобождено ${formatSize(res.bytes) || '0 Б'}`;
    if (res.cancelled) toast(`Отменено. Удалено: ${res.done} · ${freed}`);
    else if (failed) toast(`Удалено: ${res.done}, не удалось: ${failed}`, { error: true, ms: 4200 });
    else toast(`Удалено: ${res.done} · ${freed}`);
    c.selected.clear();
    c.ctx.onChange && c.ctx.onChange();
  } catch (e) {
    toast(e.message || 'Не удалось удалить', { error: true });
  }
  c.busy = false;
  return c.load();
}
