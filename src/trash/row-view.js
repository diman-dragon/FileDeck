// Строка корзины: галочка, значок, имя, размер и сколько осталось хранить.
import { icon } from '../icons/icon.js';
import { folderGlyph } from '../icons/folder-glyph.js';
import { escapeHtml } from '../html.js';
import { formatSize, formatDate, plural } from '../format.js';
import { kindOf, KIND_ICON } from '../file-kinds.js';
import { parentOf, shortLocation } from '../path.js';

const DAY_MS = 86400000;
const daysLeft = (time, days) => Math.max(0, Math.ceil((time + days * DAY_MS - Date.now()) / DAY_MS));

export function rowHtml(t, i, on, days) {
  const kind = kindOf({ name: t.name, dir: t.dir });
  const d = daysLeft(t.time, days);
  return `<div class="item cl-row${on ? ' selected' : ''}" data-i="${i}" data-id="${escapeHtml(t.id)}">
    <button class="chk${on ? ' on' : ''}" data-act="chk" aria-label="Выбрать">${icon('check', 16)}</button>
    <div class="lead k-${kind}">${kind === 'folder' ? folderGlyph(40) : `<span class="kind-ic">${icon(KIND_ICON[kind] || 'file', 22)}</span>`}</div>
    <div class="body"><div class="name">${escapeHtml(t.name)}</div>
      <div class="sub">${formatSize(t.size) || '0 Б'} · удалён ${formatDate(t.time)}</div>
      <div class="sub where">${escapeHtml(shortLocation(parentOf(t.orig)))} · осталось ${d} ${plural(d, ['день', 'дня', 'дней'])}</div></div>
  </div>`;
}
