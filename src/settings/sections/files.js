import { openSubscreen } from '../subscreen.js';
import { SORTS } from '../options.js';
import { choiceRow, toggleRow, group } from '../rows.js';

/** Файлы: сортировка, скрытые файлы, подтверждение удаления. */
export function openFiles(ctx, onUpdate) {
  const { prefs } = ctx;
  return openSubscreen({
    title: 'Файлы',
    ctx,
    onUpdate,
    body: () =>
      group(
        SORTS.map(([v, l]) => choiceRow('sortBy', v, l, '', prefs.sortBy === v)).join('') +
          toggleRow(prefs, 'desc', 'В обратном порядке', 'Новые и большие файлы сверху, Я → А')
      ) +
      group(
        toggleRow(prefs, 'hidden', 'Скрытые файлы', 'Показывать файлы и папки, имена которых начинаются с точки') +
          toggleRow(prefs, 'confirmDelete', 'Подтверждать удаление', 'Спрашивать перед тем, как удалить файлы навсегда')
      ),
  });
}
