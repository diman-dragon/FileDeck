// Нижняя панель действий над выделенными файлами.
import { icon } from '../../icons/icon.js';
import { S, inSearch } from '../state.js';
import { dock } from '../dom-refs.js';

export function renderDock() {
  const n = S.selection.size;
  if (n > 0) {
    dock.className = 'dock actions';
    dock.innerHTML = `
      <button data-act="copy">${icon('copy')}<span>Копировать</span></button>
      <button data-act="cut">${icon('cut')}<span>Вырезать</span></button>
      <button data-act="share">${icon('share')}<span>Поделиться</span></button>
      <button data-act="delete" class="danger">${icon('trash')}<span>Удалить</span></button>
      <button data-act="more-sel">${icon('more')}<span>Ещё</span></button>`;
    return;
  }
  if (S.clip && S.path && S.perm && !inSearch()) {
    const c = S.clip.paths.length;
    dock.className = 'dock paste';
    dock.innerHTML = `
      <div class="paste-info">${icon(S.clip.mode === 'move' ? 'cut' : 'copy', 18)}<span>${S.clip.mode === 'move' ? 'Перенести' : 'Скопировать'}: <b>${c}</b></span></div>
      <button class="btn ghost small" data-act="paste-cancel">Отмена</button>
      <button class="btn primary small" data-act="paste">Вставить сюда</button>`;
    return;
  }
  dock.className = 'dock hidden';
  dock.innerHTML = '';
}
