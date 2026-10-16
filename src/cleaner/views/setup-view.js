// Экран настройки: где и что искать.
import { icon } from '../../icons/icon.js';
import { formatSize } from '../../format.js';
import { CATS } from '../config.js';
import { topbar, switchRow } from './parts.js';

export function setupHtml(c) {
  const { o, primary, secondary } = c;
  return `${topbar('Очистка')}
    <div class="cl-body">
      <div class="cl-hero"><div class="cl-hero-ic">${icon('sparkle', 30)}</div>
        <p>Найдём дубликаты, мусор, пустые папки и самые тяжёлые файлы. Ничего не удаляется без вашего подтверждения.</p></div>
      <h2 class="st-h">Где искать</h2>
      <div class="group">
        ${switchRow(o, 'primary', 'Память телефона', primary ? `${formatSize(primary.free)} свободно из ${formatSize(primary.total)}` : '', !primary)}
        ${secondary ? switchRow(o, 'sd', 'SD-карта', `${formatSize(secondary.free)} свободно из ${formatSize(secondary.total)}`) : ''}
      </div>
      <h2 class="st-h">Что искать</h2>
      <div class="group">${CATS.map((c) => switchRow(o, c.opt, c.title, c.hint)).join('')}</div>
      <h2 class="st-h">Дополнительно</h2>
      <div class="group">${switchRow(o, 'appFolders', 'Папки приложений', 'Android/media: медиа мессенджеров. Удаление может оставить пустые места в чатах')}</div>
    </div>
    <div class="cl-actions"><button class="btn primary big" data-act="scan">Сканировать</button></div>`;
}
