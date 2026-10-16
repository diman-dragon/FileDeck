// Блок страницы «Файлы»: корзина и глубокая очистка.
import { formatSize } from '../../format.js';
import { TRASH_DAYS } from '../options.js';
import { caption, choiceRow, toggleRow, linkRow, group } from '../rows.js';

const trashSummary = (ctx) => {
  const t = ctx.trashInfo ? ctx.trashInfo() : null;
  return t && t.count ? `${t.count} · ${formatSize(t.bytes) || '0 Б'}` : 'Пусто';
};

export const trashBlock = (ctx) => {
  const { prefs } = ctx;
  return (
    caption('Корзина и очистка') +
    group(
      toggleRow(prefs, 'useTrash', 'Использовать корзину', 'Удалённые файлы можно восстановить в течение срока хранения') +
        (prefs.useTrash
          ? TRASH_DAYS.map(([v, l]) => choiceRow('trashDays', v, 'Хранить ' + l, '', Number(prefs.trashDays) === v)).join('')
          : '')
    ) +
    group(linkRow('open-trash', 'Открыть корзину', trashSummary(ctx)) + linkRow('open-cleaner', 'Глубокая очистка', 'Дубликаты, похожие фото, мусор, пустые папки'))
  );
};
