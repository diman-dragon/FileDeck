// Главный экран: накопители, быстрый доступ, инструменты.
import { icon } from '../../icons/icon.js';
import { join } from '../../path.js';
import { escapeHtml } from '../../html.js';
import { formatSize } from '../../format.js';
import { S, primaryRoot } from '../state.js';
import { trashLabel } from '../storage.js';

const QUICK = [
  { name: 'Download', label: 'Загрузки', icon: 'download' },
  { name: 'DCIM', label: 'Камера', icon: 'camera' },
  { name: 'Pictures', label: 'Картинки', icon: 'image' },
  { name: 'Music', label: 'Музыка', icon: 'music' },
  { name: 'Movies', label: 'Видео', icon: 'video' },
  { name: 'Documents', label: 'Документы', icon: 'doc' },
];

export function homeHtml() {
  const cards = S.roots
    .map((r) => {
      const used = Math.max(0, r.total - r.free);
      const pct = r.total ? Math.round((used / r.total) * 100) : 0;
      const ticks = Array.from({ length: 28 }, (_, i) => `<i class="${(i + 1) / 28 <= pct / 100 ? 'on' : ''}"></i>`).join('');
      return `
        <button class="storage ${pct > 90 ? 'low' : ''}" data-path="${escapeHtml(r.path)}">
          <div class="storage-top">
            <span class="storage-ic">${icon(r.primary ? 'drive' : 'sd', 22)}</span>
            <span class="storage-name">${escapeHtml(r.name)}</span>
            <span class="storage-pct">${pct}%</span>
          </div>
          <div class="ticks" aria-hidden="true">${ticks}</div>
          <div class="storage-foot"><b>${formatSize(r.free)}</b> свободно из ${formatSize(r.total)}</div>
        </button>`;
    })
    .join('');
  const root = primaryRoot();
  const quick = root
    ? QUICK.map(
        (q) => `
        <button class="quick" data-path="${escapeHtml(join(root.path, q.name))}">
          <span class="quick-ic">${icon(q.icon, 22)}</span>
          <span>${q.label}</span>
        </button>`
      ).join('')
    : '';
  return `
    <section class="home">
      <div class="storages">${cards || '<div class="muted pad">Накопители не найдены</div>'}</div>
      <div class="tools">
        <button class="tool main" data-act="open-cleaner">${icon('sparkle', 26)}<b>Очистка</b><small>дубликаты, мусор, пустые папки</small></button>
        <button class="tool" data-act="open-trash">${icon('trash', 26)}<b>Корзина</b><small id="trash-badge">${trashLabel()}</small></button>
      </div>
      <h2 class="section">Быстрый доступ</h2>
      <div class="quick-grid">${quick}</div>
    </section>`;
}
