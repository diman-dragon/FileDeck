// Список и сетка файлов с подгрузкой порциями.
import { fileUrl } from '../../file-url.js';
import { icon } from '../../icons/icon.js';
import { folderGlyph } from '../../icons/folder-glyph.js';
import { parentOf, extOf } from '../../path.js';
import { escapeHtml } from '../../html.js';
import { formatSize, formatDate, plural } from '../../format.js';
import { kindOf, KIND_ICON } from '../../file-kinds.js';
import { prefs } from '../prefs.js';
import { S, currentList, inSearch, rootOf } from '../state.js';
import { $, content } from '../dom-refs.js';

export function renderList() {
  S.visible = currentList();
  const list = S.visible;
  let head = '';
  if (inSearch()) {
    const s = S.search;
    if (s.loading) head = `<div class="list-note"><div class="spinner small"></div>Ищем «${escapeHtml(s.q)}»…</div>`;
    else if (s.q && !list.length) {
      content.innerHTML = `<div class="state"><div class="empty-glyph">${icon('search', 40)}</div><h1>Ничего не найдено</h1><p>По запросу «${escapeHtml(s.q)}» файлов нет. Проверьте написание или поищите выше по дереву.</p></div>`;
      return;
    } else if (!s.q) {
      content.innerHTML = `<div class="state"><div class="empty-glyph">${icon('search', 40)}</div><p>Введите часть имени файла или папки.</p></div>`;
      return;
    } else head = `<div class="list-note">${list.length} ${plural(list.length, ['результат', 'результата', 'результатов'])}${s.truncated ? ' · показаны первые, уточните запрос' : ''}</div>`;
  } else if (S.loading) {
    content.innerHTML = `<div class="state"><div class="spinner"></div></div>`;
    return;
  } else if (S.error) {
    content.innerHTML = `<div class="state"><div class="empty-glyph">${icon('lock', 40)}</div><h1>Папка недоступна</h1><p>${escapeHtml(S.error)}</p><button class="btn ghost" data-act="up">Назад</button></div>`;
    return;
  } else if (!list.length) {
    const hiddenCount = S.entries.length;
    content.innerHTML = `<div class="state"><div class="empty-glyph">${folderGlyph(64, true)}</div><h1>Здесь пусто</h1><p>${hiddenCount ? 'В папке только скрытые файлы. Включите их показ в настройках (шестерёнка).' : 'Создайте папку или файл кнопкой «+», либо вставьте скопированное.'}</p></div>`;
    return;
  }

  const grid = prefs.view === 'grid' && !inSearch();
  const shown = list.slice(0, S.chunk);
  content.innerHTML = `${head}<div class="items ${grid ? 'grid' : 'list'}">${shown.map((e, i) => itemHtml(e, i, grid)).join('')}</div>${list.length > shown.length ? '<div id="sentinel" class="sentinel"><div class="spinner small"></div></div>' : ''}`;
  observeSentinel();
}

let observer;

function observeSentinel() {
  observer?.disconnect();
  const s = $('sentinel');
  if (!s) return;
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries[0].isIntersecting) return;
      observer.disconnect();
      const container = content.querySelector('.items');
      const grid = container.classList.contains('grid');
      const from = container.children.length;
      S.chunk += 240;
      const more = S.visible.slice(from, S.chunk);
      container.insertAdjacentHTML('beforeend', more.map((e, i) => itemHtml(e, from + i, grid)).join(''));
      if (S.chunk >= S.visible.length) s.remove();
      else observeSentinel();
    },
    { root: content, rootMargin: '600px' }
  );
  observer.observe(s);
}

function leadHtml(e, kind, big) {
  if (kind === 'folder') return folderGlyph(big ? 56 : 42);
  if (kind === 'image') {
    return `<img src="${fileUrl(e.path)}" alt="" loading="lazy" decoding="async" draggable="false" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'ext',textContent:'${escapeHtml(extOf(e.name)).toUpperCase().slice(0, 4)}'}))" />`;
  }
  return `<span class="kind-ic">${icon(KIND_ICON[kind] || 'file', big ? 28 : 22)}</span><span class="ext">${escapeHtml(extOf(e.name)).slice(0, 4)}</span>`;
}

function subHtml(e) {
  if (e.dir) {
    const c = e.count ?? 0;
    return `${c} ${plural(c, ['элемент', 'элемента', 'элементов'])}`;
  }
  return `${formatSize(e.size)} · ${formatDate(e.mtime)}`;
}

function itemHtml(e, i, grid) {
  const kind = kindOf(e);
  const sel = S.selection.has(e.path);
  const sub = inSearch() ? escapeHtml(parentOf(e.path).replace(rootOf(e.path)?.path || '', '') || '/') : subHtml(e);
  return `
    <div class="item${sel ? ' selected' : ''}${e.name.startsWith('.') ? ' dim' : ''}" data-i="${i}" role="button" tabindex="0">
      <div class="lead k-${kind}">${leadHtml(e, kind, grid)}</div>
      <div class="body">
        <div class="name">${escapeHtml(e.name)}</div>
        <div class="sub">${sub}</div>
      </div>
      <div class="tick" data-act="tick">${icon('check', 16)}</div>
    </div>`;
}
