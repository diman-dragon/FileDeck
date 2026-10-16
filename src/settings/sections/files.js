// Блоки страницы «Файлы»: порядок списка и поведение.
import { SORTS } from '../options.js';
import { caption, choiceRow, toggleRow, group } from '../rows.js';

export const filesBlock = ({ prefs }) =>
  caption('Сортировка') +
  group(
    SORTS.map(([v, l]) => choiceRow('sortBy', v, l, '', prefs.sortBy === v)).join('') +
      toggleRow(prefs, 'desc', 'В обратном порядке', 'Новые и большие файлы сверху, Я → А')
  ) +
  caption('Поведение') +
  group(
    toggleRow(prefs, 'hidden', 'Скрытые файлы', 'Показывать файлы и папки, имена которых начинаются с точки') +
      toggleRow(prefs, 'confirmDelete', 'Подтверждать удаление', 'Спрашивать перед тем, как удалить файлы навсегда')
  );
