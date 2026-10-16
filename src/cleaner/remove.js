// Удаление найденного: в корзину или навсегда, быстрая очистка.
import { FM, TR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { sheet } from '../layers/sheet.js';
import { confirmDialog } from '../layers/confirm-dialog.js';
import { formatSize, plural } from '../format.js';
import { CATS } from './config.js';
import { groupRows, purgeResult, rowsOf } from './rows.js';
import { safeBytes, safePaths } from './selection.js';

export async function removePaths(c, paths, bytes, label) {
  const { ctx, state } = c;
  const { prefs } = ctx;
  if (!paths.length) return;
  const useTrash = prefs.useTrash;
  const days = prefs.trashDays || 30;
  const describe = `${paths.length} ${plural(paths.length, ['объект', 'объекта', 'объектов'])} · ${formatSize(bytes) || '0 Б'}`;
  const choose = () =>
    new Promise((resolve) => {
      if (!useTrash) {
        confirmDialog({ title: `Удалить навсегда?`, text: `${label}: ${describe}. Восстановить будет нельзя.`, okText: 'Удалить' }).then((ok) => resolve(ok ? 'permanent' : null));
        return;
      }
      sheet({
        title: label,
        subtitle: describe,
        onDismiss: () => resolve(null),
        items: [
          { icon: 'trash', label: 'Переместить в корзину', hint: `Хранится ${days} ${plural(days, ['день', 'дня', 'дней'])}, можно восстановить`, onClick: () => resolve('trash') },
          { icon: 'close', label: 'Удалить навсегда', hint: 'Сразу освободит место, без восстановления', danger: true, onClick: () => resolve('permanent') },
        ],
      });
    });
  const mode = await choose();
  if (!mode) return;
  state.busy = true;
  toast(mode === 'trash' ? 'Переносим в корзину…' : 'Удаляем…', { ms: 600000 });
  try {
    const res = await (mode === 'trash' ? TR.move({ paths }) : FM.remove({ paths }));
    const failed = res.failed?.length || 0;
    const okPaths = paths.filter((p) => !(res.failed || []).includes(p));
    purgeResult(state.result, state.sel, okPaths);
    toast(
      mode === 'trash'
        ? `В корзине: ${res.done}${failed ? `, не удалось: ${failed}` : ''}. Место освободится после очистки корзины.`
        : `Удалено: ${res.done}${failed ? `, не удалось: ${failed}` : ''} · освобождено ${formatSize(bytes)}`,
      { error: failed > 0, ms: 4200 }
    );
  } catch (e) {
    toast(e.message || 'Не удалось удалить', { error: true });
  }
  state.busy = false;
  ctx.onChange && ctx.onChange();
  c.draw();
}

export async function deleteCategory(c, catId) {
  const { state } = c;
  const cat = CATS.find((c) => c.id === catId);
  const rows = rowsOf(state.result, catId).filter((r) => state.sel[catId]?.has(r.path));
  if (!rows.length) return;
  if (cat.group) {
    let full = 0;
    groupRows(rowsOf(state.result, catId)).forEach((list) => { if (list.every((r) => state.sel[catId].has(r.path))) full++; });
    if (full > 0) {
      const ok = await confirmDialog({
        title: 'Выбраны все копии',
        text: `В ${full} ${plural(full, ['группе', 'группах', 'группах'])} отмечены все экземпляры — файл исчезнет совсем. Продолжить?`,
        okText: 'Продолжить',
      });
      if (!ok) return;
    }
  }
  await removePaths(c, rows.map((r) => r.path), rows.reduce((a, r) => a + r.size, 0), cat.title);
}

export async function quickClean(c) {
  const paths = safePaths(c);
  if (!paths.length) return toast('Безопасной очистки не нашлось');
  await removePaths(c, paths, safeBytes(c), 'Быстрая очистка');
}
