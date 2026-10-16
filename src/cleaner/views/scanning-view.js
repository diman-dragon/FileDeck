// Экран сканирования: прогресс и отмена.
import { icon } from '../../icons/icon.js';
import { topbar } from './parts.js';

export function scanningHtml() {
  return `${topbar('Сканирование')}
    <div class="cl-body scanning">
      <div class="scan-ring">${icon('sparkle', 34)}</div>
      <div class="scan-phase">Просматриваем файлы…</div>
      <div class="pbar indeterminate"><i></i></div>
      <div class="scan-stats"><div><b class="scan-files">0</b><span>файлов</span></div><div><b class="scan-bytes">0 Б</b><span>просмотрено</span></div></div>
      <div class="scan-cur"></div>
    </div>
    <div class="cl-actions"><button class="btn ghost big" data-act="cancel-scan">Отмена</button></div>`;
}
