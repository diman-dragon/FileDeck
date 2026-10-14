import { openSubscreen } from '../subscreen.js';
import { TRASH_DAYS } from '../options.js';
import { choiceRow, toggleRow, linkRow, group } from '../rows.js';
import { trashSummary } from '../summaries.js';

/** Корзина и очистка: срок хранения, переходы в корзину и в глубокую очистку. */
export function openTrashSettings(ctx, onUpdate) {
  const { prefs } = ctx;
  return openSubscreen({
    title: 'Корзина и очистка',
    ctx,
    onUpdate,
    actions: {
      'open-trash': async () => ctx.openTrash(),
      'open-cleaner': async () => ctx.openCleaner(),
    },
    body: () =>
      group(
        toggleRow(prefs, 'useTrash', 'Использовать корзину', 'Удалённые файлы можно восстановить в течение срока хранения') +
          (prefs.useTrash
            ? TRASH_DAYS.map(([v, l]) => choiceRow('trashDays', v, 'Хранить ' + l, '', Number(prefs.trashDays) === v)).join('')
            : '')
      ) +
      group(
        linkRow('open-trash', 'Открыть корзину', trashSummary(ctx)) +
          linkRow('open-cleaner', 'Глубокая очистка', 'Дубликаты, похожие фото, мусор, пустые папки')
      ),
  });
}
