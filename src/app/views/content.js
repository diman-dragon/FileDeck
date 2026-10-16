// Выбор того, что показать в области содержимого.
import { icon } from '../../icons/icon.js';
import { S, inSearch } from '../state.js';
import { content } from '../dom-refs.js';
import { homeHtml } from './home.js';
import { renderList } from './file-list.js';

export function renderContent() {
  if (S.perm === null) {
    content.innerHTML = `<div class="state"><div class="spinner"></div></div>`;
    return;
  }
  if (!S.perm) {
    content.innerHTML = `
      <div class="state perm">
        <div class="perm-badge">${icon('lock', 34)}</div>
        <h1>Нужен доступ ко всем файлам</h1>
        <p>FileDeck показывает и меняет файлы на устройстве. Android просит выдать это разрешение отдельно: включите переключатель на следующем экране и вернитесь в приложение.</p>
        <button class="btn primary big" data-act="grant">Выдать доступ</button>
      </div>`;
    return;
  }
  if (!S.path) {
    if (inSearch()) return renderList();
    content.innerHTML = homeHtml();
    return;
  }
  renderList();
}
