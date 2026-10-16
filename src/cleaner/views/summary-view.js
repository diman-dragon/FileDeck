// Экран результатов: сколько можно освободить и по категориям.
import { icon } from '../../icons/icon.js';
import { formatSize, plural } from '../../format.js';
import { CATS, USAGE } from '../config.js';
import { countOf, rowsOf, wasteOf } from '../rows.js';
import { safeBytes } from '../selection.js';
import { topbar } from './parts.js';

export function summaryHtml(c) {
  const { o, state } = c;
  const r = state.result;
  const bar = USAGE.filter(([k]) => r.usage[k]?.bytes).map(([k, , c]) => `<i style="flex:${r.usage[k].bytes};background:${c}"></i>`).join('');
  const legend = USAGE.filter(([k]) => r.usage[k]?.bytes).sort((a, b) => r.usage[b[0]].bytes - r.usage[a[0]].bytes).slice(0, 5)
    .map(([k, l, c]) => `<span><i style="background:${c}"></i>${l} <b>${formatSize(r.usage[k].bytes)}</b></span>`).join('');
  const safe = safeBytes(c);
  const cards = CATS.filter((x) => o[x.opt]).map((x) => {
    const n = countOf(r, x.id);
    const bytes = x.id === 'largest' ? rowsOf(r, x.id).reduce((a, y) => a + y.size, 0) : wasteOf(r, x.id);
    return `<button class="cat-card${n ? '' : ' clean'}" data-cat="${x.id}" ${n ? '' : 'disabled'}>
      <span class="cat-ic">${icon(n ? x.icon : 'check', 22)}</span>
      <span class="cat-text"><b>${x.title}</b><small>${n ? `${n} ${x.group ? plural(n, ['группа', 'группы', 'групп']) : plural(n, ['объект', 'объекта', 'объектов'])}${bytes ? ' · ' + formatSize(bytes) : ''}` : 'Ничего не найдено'}</small></span>
      ${n ? icon('chevron', 20) : ''}
    </button>`;
  }).join('');
  return `${topbar('Результаты', `<button class="icon-btn" data-act="rescan" aria-label="Сканировать заново">${icon('refresh')}</button>`)}
    <div class="cl-body">
      <div class="cl-total"><small>Можно безопасно освободить</small><b>${formatSize(safe) || '0 Б'}</b>
        <span>Просмотрено ${r.stats.files.toLocaleString('ru')} ${plural(r.stats.files, ['файл', 'файла', 'файлов'])} · ${formatSize(r.stats.bytes)} · ${(r.stats.ms / 1000).toFixed(1)} с</span></div>
      <div class="usage"><div class="usage-bar">${bar}</div><div class="usage-legend">${legend}</div></div>
      <h2 class="st-h">Найдено</h2>
      <div class="cats">${cards || '<div class="muted pad">Включите хотя бы одну категорию</div>'}</div>
    </div>
    <div class="cl-actions">${safe > 0 ? `<button class="btn primary big" data-act="quick">Быстрая очистка · ${formatSize(safe)}</button>` : `<button class="btn ghost big" data-act="rescan">Сканировать заново</button>`}</div>`;
}
