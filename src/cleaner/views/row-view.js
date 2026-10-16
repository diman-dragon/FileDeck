// Строка найденного файла: галочка, миниатюра, имя, подписи, кнопки.
import { icon } from '../../icons/icon.js';
import { folderGlyph } from '../../icons/folder-glyph.js';
import { fileUrl } from '../../file-url.js';
import { escapeHtml } from '../../html.js';
import { formatSize, formatDate } from '../../format.js';
import { basename, parentOf, shortLocation } from '../../path.js';
import { kindOf, KIND_ICON } from '../../file-kinds.js';
import { JUNK_LABEL } from '../config.js';

function thumbHtml(r) {
  const kind = kindOf({ name: basename(r.path), dir: r.dir });
  if (kind === 'folder') return folderGlyph(40);
  if (kind === 'image') return `<img src="${fileUrl(r.path)}" alt="" loading="lazy" decoding="async" draggable="false" />`;
  return `<span class="kind-ic">${icon(KIND_ICON[kind] || 'file', 22)}</span>`;
}

export function rowHtml(c, state, r, i, keepPath) {
  const on = state.sel[c.id]?.has(r.path);
  const kind = kindOf({ name: basename(r.path), dir: r.dir });
  const where = shortLocation(parentOf(r.path));
  let sub = '';
  if (c.id === 'junk') sub = `${JUNK_LABEL[r.kind] || 'Мусор'} · ${formatSize(r.size)}`;
  else if (c.id === 'apks') sub = `${r.version ? 'v' + r.version + ' · ' : ''}${formatSize(r.size)}${r.mtime ? ' · ' + formatDate(r.mtime) : ''}`;
  else if (c.id === 'emptyFolders') sub = 'пустая папка';
  else if (c.id === 'zeroFiles') sub = '0 Б';
  else sub = `${r.w ? r.w + '×' + r.h + ' · ' : ''}${formatSize(r.size)}${r.mtime ? ' · ' + formatDate(r.mtime) : ''}`;
  const title = c.id === 'apks' && r.label ? `${r.label}` : basename(r.path);
  return `<div class="item cl-row${on ? ' selected' : ''}" data-i="${i}" data-path="${escapeHtml(r.path)}">
    <button class="chk${on ? ' on' : ''}" data-act="chk" aria-label="Выбрать">${icon('check', 16)}</button>
    <div class="lead k-${kind}">${thumbHtml(r)}</div>
    <div class="body"><div class="name">${escapeHtml(title)}${keepPath === r.path ? ' <em class="keep">оставить</em>' : ''}</div>
      <div class="sub">${escapeHtml(sub)}</div><div class="sub where">${escapeHtml(where)}</div></div>
    <div class="cl-tools">
      ${r.dir ? '' : `<button class="icon-btn small" data-act="view" aria-label="Открыть">${icon('eye', 19)}</button>`}
      <button class="icon-btn small" data-act="locate" aria-label="Показать в папке">${icon('folder', 19)}</button>
    </div>
  </div>`;
}
