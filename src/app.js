import { SystemBars, SystemBarsStyle } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { FM, fileUrl, isNative } from './plugin.js';
import { icon, folderGlyph } from './icons.js';
import { toast, sheet, confirmDialog, promptDialog, infoDialog, handleBack, hasLayers } from './layers.js';
import { openImageViewer, openEditor } from './viewer.js';
import {
  basename, parentOf, join, escapeHtml, formatSize, formatDate, kindOf, extOf, sortEntries,
  KIND_ICON, KIND_LABEL, isEditable, debounce,
} from './utils.js';

// ------------------------------------------------------------------ состояние
const PREFS_KEY = 'filedeck.prefs.v1';
const prefs = Object.assign(
  { view: 'list', sortBy: 'name', desc: false, hidden: false, theme: 'auto' },
  (() => { try { return JSON.parse(localStorage.getItem(PREFS_KEY)) || {}; } catch { return {}; } })()
);
const savePrefs = () => { try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch { /* ignore */ } };

const S = {
  perm: null,          // null — проверяем, true/false
  roots: [],
  path: null,          // null — главный экран
  entries: [],
  loading: false,
  error: null,
  selection: new Set(),
  clip: null,          // { mode: 'copy' | 'move', paths: [] }
  search: null,        // { q, results, loading, truncated }
  chunk: 240,
  scrollMemo: new Map(),
};

const $ = (id) => document.getElementById(id);
const topbar = $('topbar');
const content = $('content');
const dock = $('dock');
const fab = $('fab');

const QUICK = [
  { name: 'Download', label: 'Загрузки', icon: 'download' },
  { name: 'DCIM', label: 'Камера', icon: 'camera' },
  { name: 'Pictures', label: 'Картинки', icon: 'image' },
  { name: 'Music', label: 'Музыка', icon: 'music' },
  { name: 'Movies', label: 'Видео', icon: 'video' },
  { name: 'Documents', label: 'Документы', icon: 'doc' },
];

const primaryRoot = () => S.roots.find((r) => r.primary) || S.roots[0];
const rootOf = (path) =>
  S.roots.filter((r) => path === r.path || path.startsWith(r.path + '/')).sort((a, b) => b.path.length - a.path.length)[0];
const inSearch = () => S.search !== null;

/** Список, который сейчас показан на экране. */
function currentList() {
  if (inSearch()) return S.search.results;
  const visible = prefs.hidden ? S.entries : S.entries.filter((e) => !e.name.startsWith('.'));
  return sortEntries(visible, { by: prefs.sortBy, desc: prefs.desc });
}
let viewCache = [];

// ------------------------------------------------------------------ тема
export function applyTheme() {
  const t = prefs.theme;
  if (t === 'auto') delete document.documentElement.dataset.theme;
  else document.documentElement.dataset.theme = t;
  const dark = t === 'dark' || (t === 'auto' && matchMedia('(prefers-color-scheme: dark)').matches);
  if (isNative) SystemBars.setStyle({ style: dark ? SystemBarsStyle.Dark : SystemBarsStyle.Light }).catch(() => {});
}

// ------------------------------------------------------------------ загрузка
async function init() {
  applyTheme();
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', applyTheme);
  render();
  await checkPermission();
}

async function checkPermission() {
  try {
    const { granted } = await FM.checkPermission();
    S.perm = granted;
  } catch {
    S.perm = false;
  }
  if (S.perm) await loadRoots();
  render();
}

async function loadRoots() {
  try {
    ({ roots: S.roots } = await FM.getRoots());
  } catch (e) {
    toast(e.message || 'Не удалось получить список накопителей', { error: true });
  }
}

async function requestPermission() {
  try {
    const { granted } = await FM.requestPermission();
    if (granted) await checkPermission();
  } catch (e) {
    toast(e.message || 'Не удалось запросить доступ', { error: true });
  }
}

async function load(path, { keepScroll = false } = {}) {
  S.loading = true;
  S.error = null;
  render({ keepScroll });
  try {
    const { entries } = await FM.list({ path });
    if (S.path !== path) return; // пользователь ушёл дальше
    S.entries = entries;
  } catch (e) {
    if (S.path !== path) return;
    S.entries = [];
    S.error = e.message || 'Не удалось открыть папку';
  }
  S.loading = false;
  S.chunk = 240;
  render({ keepScroll });
}

export function go(path) {
  if (S.path) S.scrollMemo.set(S.path, content.scrollTop);
  S.search = null;
  S.selection.clear();
  S.path = path;
  S.entries = [];
  if (path === null) {
    loadRoots().then(render);
    render();
    return;
  }
  load(path).then(() => {
    const y = S.scrollMemo.get(path);
    if (y != null) content.scrollTop = y;
  });
}

function up() {
  if (!S.path) return false;
  const root = rootOf(S.path);
  if (!root || S.path === root.path) go(null);
  else go(parentOf(S.path));
  return true;
}

const refresh = () => (S.path ? load(S.path, { keepScroll: true }) : loadRoots().then(render));

// ------------------------------------------------------------------ рендер
export function render({ keepScroll = false } = {}) {
  const scroll = content.scrollTop;
  renderTopbar();
  renderContent();
  renderDock();
  $('app').classList.toggle('selecting', S.selection.size > 0);
  const showFab = S.perm && S.path && !inSearch() && S.selection.size === 0 && !S.error;
  fab.innerHTML = icon('plus', 26);
  fab.classList.toggle('visible', !!showFab);
  fab.classList.toggle('lifted', !!S.clip);
  if (keepScroll) content.scrollTop = scroll;
  else if (S.loading) content.scrollTop = 0;
}

function renderTopbar() {
  const n = S.selection.size;
  if (n > 0) {
    topbar.className = 'topbar selecting';
    topbar.innerHTML = `
      <button class="icon-btn" data-act="clear-sel" aria-label="Отменить выбор">${icon('close')}</button>
      <div class="sel-count"><b>${n}</b><span>${plural(n, ['выбран', 'выбрано', 'выбрано'])}</span></div>
      <button class="icon-btn" data-act="select-all" aria-label="Выбрать всё">${icon('selectAll')}</button>`;
    return;
  }
  if (inSearch()) {
    topbar.className = 'topbar searching';
    topbar.innerHTML = `
      <button class="icon-btn" data-act="exit-search" aria-label="Назад">${icon('back')}</button>
      <input id="search-input" type="search" inputmode="search" enterkeyhint="search" placeholder="${S.path ? 'Поиск в этой папке' : 'Поиск по памяти'}" value="${escapeHtml(S.search.q)}" autocomplete="off" />
      ${S.search.q ? `<button class="icon-btn" data-act="clear-query" aria-label="Очистить">${icon('close')}</button>` : ''}`;
    return;
  }
  if (!S.path || !S.perm) {
    topbar.className = 'topbar home';
    topbar.innerHTML = `
      <div class="wordmark">${folderGlyph(26)}<span>FileDeck</span></div>
      <div class="spacer"></div>
      ${S.perm ? `<button class="icon-btn" data-act="search" aria-label="Поиск">${icon('search')}</button>` : ''}
      <button class="icon-btn" data-act="menu" aria-label="Меню">${icon('more')}</button>`;
    return;
  }
  topbar.className = 'topbar browse';
  topbar.innerHTML = `
    <button class="icon-btn" data-act="up" aria-label="Наверх">${icon('back')}</button>
    <nav class="crumbs" aria-label="Путь">${crumbsHtml()}</nav>
    <button class="icon-btn" data-act="search" aria-label="Поиск">${icon('search')}</button>
    <button class="icon-btn" data-act="menu" aria-label="Меню">${icon('more')}</button>`;
  const nav = topbar.querySelector('.crumbs');
  nav.scrollLeft = nav.scrollWidth;
}

function crumbsHtml() {
  const root = rootOf(S.path);
  const base = root ? root.path : '';
  const parts = S.path.slice(base.length).split('/').filter(Boolean);
  const label = root ? (root.primary ? 'Память' : 'SD-карта') : '/';
  let acc = base;
  const tabs = [`<button class="tab${parts.length ? '' : ' current'}" data-path="${escapeHtml(base || '/')}">${escapeHtml(label)}</button>`];
  parts.forEach((p, i) => {
    acc = join(acc, p);
    tabs.push(`<button class="tab${i === parts.length - 1 ? ' current' : ''}" data-path="${escapeHtml(acc)}">${escapeHtml(p)}</button>`);
  });
  return tabs.join('');
}

function renderContent() {
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

function homeHtml() {
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
      <h2 class="section">Быстрый доступ</h2>
      <div class="quick-grid">${quick}</div>
    </section>`;
}

function renderList() {
  viewCache = currentList();
  const list = viewCache;
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
    content.innerHTML = `<div class="state"><div class="empty-glyph">${folderGlyph(64, true)}</div><h1>Здесь пусто</h1><p>${hiddenCount ? 'В папке только скрытые файлы. Включите их показ в меню.' : 'Создайте папку или файл кнопкой «+», либо вставьте скопированное.'}</p></div>`;
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
      const more = viewCache.slice(from, S.chunk);
      container.insertAdjacentHTML('beforeend', more.map((e, i) => itemHtml(e, from + i, grid)).join(''));
      if (S.chunk >= viewCache.length) s.remove();
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

function renderDock() {
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

// ------------------------------------------------------------------ события
topbar.addEventListener('click', (ev) => {
  const tab = ev.target.closest('.tab');
  if (tab) return go(tab.dataset.path);
  const act = ev.target.closest('[data-act]')?.dataset.act;
  if (!act) return;
  ({
    'clear-sel': () => { S.selection.clear(); render({ keepScroll: true }); },
    'select-all': selectAll,
    'exit-search': exitSearch,
    'clear-query': () => { S.search.q = ''; S.search.results = []; render(); $('search-input')?.focus(); },
    search: startSearch,
    menu: openMenu,
    up,
  })[act]?.();
});

topbar.addEventListener('input', (ev) => {
  if (ev.target.id === 'search-input') runSearch(ev.target.value);
});

fab.addEventListener('click', () => {
  sheet({
    title: 'Создать',
    items: [
      { icon: 'folderPlus', label: 'Новая папка', onClick: () => createItem(true) },
      { icon: 'filePlus', label: 'Новый текстовый файл', onClick: () => createItem(false) },
    ],
  });
});

dock.addEventListener('click', (ev) => {
  const act = ev.target.closest('[data-act]')?.dataset.act;
  if (!act) return;
  ({
    copy: () => toClipboard('copy'),
    cut: () => toClipboard('move'),
    share: () => shareSelected(),
    delete: () => deletePaths([...S.selection]),
    'more-sel': moreForSelection,
    paste,
    'paste-cancel': () => { S.clip = null; render({ keepScroll: true }); },
  })[act]?.();
});

// жесты: обычный тап и долгое нажатие
let pressTimer = null;
let longFired = false;
let pressXY = [0, 0];
const entryFromEvent = (ev) => {
  const it = ev.target.closest('.item');
  return it ? viewCache[Number(it.dataset.i)] : null;
};
content.addEventListener('pointerdown', (ev) => {
  const e = entryFromEvent(ev);
  longFired = false;
  if (!e) return;
  pressXY = [ev.clientX, ev.clientY];
  pressTimer = setTimeout(() => {
    longFired = true;
    navigator.vibrate?.(12);
    toggleSelect(e.path);
  }, 380);
});
const cancelPress = () => { clearTimeout(pressTimer); pressTimer = null; };
content.addEventListener('pointermove', (ev) => {
  if (pressTimer && Math.hypot(ev.clientX - pressXY[0], ev.clientY - pressXY[1]) > 10) cancelPress();
});
['pointerup', 'pointercancel', 'pointerleave', 'scroll'].forEach((t) => content.addEventListener(t, cancelPress, { passive: true }));
content.addEventListener('contextmenu', (ev) => ev.preventDefault());

content.addEventListener('click', (ev) => {
  const actEl = ev.target.closest('[data-act]');
  const act = actEl?.dataset.act;
  if (act === 'grant') return requestPermission();
  if (act === 'up') return up();
  if (longFired) { longFired = false; return; }

  const pathBtn = ev.target.closest('[data-path]');
  if (pathBtn && !pathBtn.classList.contains('item')) return go(pathBtn.dataset.path);

  const e = entryFromEvent(ev);
  if (!e) return;
  if (act === 'tick' || S.selection.size > 0) return toggleSelect(e.path);
  openEntry(e);
});

// ------------------------------------------------------------------ выделение
function toggleSelect(path) {
  S.selection.has(path) ? S.selection.delete(path) : S.selection.add(path);
  render({ keepScroll: true });
}
function selectAll() {
  const all = viewCache;
  if (S.selection.size === all.length) S.selection.clear();
  else all.forEach((e) => S.selection.add(e.path));
  render({ keepScroll: true });
}
const selectedEntries = () => viewCache.filter((e) => S.selection.has(e.path));

// ------------------------------------------------------------------ открытие
function openEntry(e) {
  const kind = kindOf(e);
  if (e.dir) return go(e.path);
  if (kind === 'image') {
    const images = viewCache.filter((x) => kindOf(x) === 'image');
    return openImageViewer(images, Math.max(0, images.findIndex((x) => x.path === e.path)));
  }
  if (extOf(e.name) === 'zip') {
    return sheet({
      title: e.name,
      subtitle: formatSize(e.size),
      items: [
        { icon: 'unzip', label: 'Распаковать здесь', onClick: () => unzip(e) },
        { icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(e) },
      ],
    });
  }
  if (isEditable(e)) {
    return sheet({
      title: e.name,
      subtitle: formatSize(e.size),
      items: [
        { icon: 'edit', label: 'Открыть в редакторе', onClick: () => openEditor(e, { onSaved: refresh }) },
        { icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(e) },
      ],
    });
  }
  openExternal(e);
}
const openExternal = (e) => FM.open({ path: e.path }).catch((err) => toast(err.message, { error: true }));

// ------------------------------------------------------------------ действия
function toClipboard(mode) {
  S.clip = { mode, paths: [...S.selection] };
  const n = S.clip.paths.length;
  S.selection.clear();
  render({ keepScroll: true });
  toast(`${mode === 'move' ? 'Вырезано' : 'Скопировано'}: ${n}. Откройте нужную папку и нажмите «Вставить сюда».`, { ms: 3600 });
}

async function paste() {
  if (!S.clip || !S.path) return;
  const { mode, paths } = S.clip;
  toast(mode === 'move' ? 'Переносим…' : 'Копируем…', { ms: 600000 });
  try {
    const res = await (mode === 'move' ? FM.move : FM.copy)({ paths, dest: S.path });
    S.clip = null;
    report(res, mode === 'move' ? 'Перенесено' : 'Скопировано');
  } catch (e) {
    toast(e.message || 'Операция не удалась', { error: true });
  }
  refresh();
}

function report(res, verb) {
  const failed = res.failed?.length || 0;
  if (failed) toast(`${verb}: ${res.done}, не удалось: ${failed}. Нельзя вставлять папку саму в себя.`, { error: true, ms: 4200 });
  else toast(`${verb}: ${res.done}`);
}

async function deletePaths(paths) {
  const names = paths.map(basename);
  const ok = await confirmDialog({
    title: paths.length === 1 ? `Удалить «${names[0]}»?` : `Удалить выбранное (${paths.length})?`,
    text: 'Файлы будут удалены без возможности восстановления.',
  });
  if (!ok) return;
  try {
    const res = await FM.remove({ paths });
    S.selection.clear();
    if (S.clip) S.clip.paths = S.clip.paths.filter((p) => !paths.includes(p));
    if (S.clip && !S.clip.paths.length) S.clip = null;
    report(res, 'Удалено');
  } catch (e) {
    toast(e.message || 'Не удалось удалить', { error: true });
  }
  if (inSearch()) S.search.results = S.search.results.filter((e) => !paths.includes(e.path));
  S.path ? refresh() : render();
}

async function renameEntry(e) {
  const name = await promptDialog({ title: 'Переименовать', value: e.name, okText: 'Переименовать', selectBase: !e.dir });
  if (name == null || name.trim() === '' || name === e.name) return;
  try {
    await FM.rename({ path: e.path, name });
    S.selection.clear();
    toast('Переименовано');
  } catch (err) {
    toast(err.message || 'Не удалось переименовать', { error: true });
  }
  refresh();
}

async function createItem(isDir) {
  const name = await promptDialog({
    title: isDir ? 'Новая папка' : 'Новый файл',
    placeholder: isDir ? 'Название папки' : 'заметка.txt',
    value: isDir ? '' : 'Новый файл.txt',
    okText: 'Создать',
    selectBase: !isDir,
  });
  if (!name || !name.trim()) return;
  if (name.includes('/')) return toast('В имени нельзя использовать «/»', { error: true });
  try {
    const path = join(S.path, name.trim());
    await (isDir ? FM.mkdir : FM.createFile)({ path });
    toast(isDir ? 'Папка создана' : 'Файл создан');
  } catch (e) {
    toast(e.message || 'Не удалось создать', { error: true });
  }
  refresh();
}

async function shareSelected() {
  const paths = selectedEntries().filter((e) => !e.dir).map((e) => e.path);
  if (!paths.length) return toast('Поделиться можно только файлами, не папками', { error: true });
  try { await FM.share({ paths }); } catch (e) { toast(e.message, { error: true }); }
}

async function zipSelected() {
  const sel = selectedEntries();
  const def = sel.length === 1 ? sel[0].name.replace(/\.[^.]+$/, '') : 'Архив';
  const name = await promptDialog({ title: 'Создать архив', value: `${def}.zip`, okText: 'Архивировать', selectBase: true });
  if (!name || !name.trim()) return;
  toast('Архивируем…', { ms: 600000 });
  try {
    await FM.zip({ paths: sel.map((e) => e.path), dest: S.path || parentOf(sel[0].path), name: name.trim() });
    S.selection.clear();
    toast('Архив создан');
  } catch (e) {
    toast(e.message || 'Не удалось создать архив', { error: true });
  }
  refresh();
}

async function unzip(e) {
  toast('Распаковываем…', { ms: 600000 });
  try {
    await FM.unzip({ path: e.path, dest: parentOf(e.path) });
    S.selection.clear();
    toast('Распаковано');
  } catch (err) {
    toast(err.message || 'Не удалось распаковать', { error: true });
  }
  refresh();
}

async function showProps(e) {
  const kind = kindOf(e);
  const dlg = infoDialog({
    title: e.name,
    rows: [
      ['Тип', KIND_LABEL[kind] + (e.dir ? '' : extOf(e.name) ? ` (.${extOf(e.name)})` : '')],
      ['Путь', parentOf(e.path)],
      ['Размер', e.dir ? 'считаем…' : `${formatSize(e.size)} (${e.size.toLocaleString('ru')} Б)`],
      ...(e.dir ? [['Содержимое', 'считаем…']] : []),
      ['Изменён', formatDate(e.mtime, true)],
    ],
  });
  if (e.dir) {
    try {
      const r = await FM.size({ path: e.path });
      dlg.set('Размер', formatSize(r.size));
      dlg.set('Содержимое', `${r.files} ${plural(r.files, ['файл', 'файла', 'файлов'])}, ${r.folders} ${plural(r.folders, ['папка', 'папки', 'папок'])}`);
    } catch {
      dlg.set('Размер', 'не удалось посчитать');
      dlg.set('Содержимое', '—');
    }
  }
}

function moreForSelection() {
  const sel = selectedEntries();
  const one = sel.length === 1 ? sel[0] : null;
  const items = [];
  if (one) items.push({ icon: 'edit', label: 'Переименовать', onClick: () => renameEntry(one) });
  items.push({ icon: 'archive', label: 'Добавить в архив (.zip)', onClick: zipSelected });
  if (one && extOf(one.name) === 'zip') items.push({ icon: 'unzip', label: 'Распаковать здесь', onClick: () => unzip(one) });
  if (one && !one.dir) items.push({ icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(one) });
  if (one && isEditable(one)) items.push({ icon: 'doc', label: 'Открыть в редакторе', onClick: () => openEditor(one, { onSaved: refresh }) });
  if (one) items.push({ icon: 'info', label: 'Свойства', onClick: () => showProps(one) });
  sheet({ title: one ? one.name : `Выбрано: ${sel.length}`, items });
}

// ------------------------------------------------------------------ поиск
function startSearch() {
  S.search = { q: '', results: [], loading: false, truncated: false };
  render();
  setTimeout(() => $('search-input')?.focus(), 50);
}
function exitSearch() {
  S.search = null;
  S.selection.clear();
  render();
}
let searchSeq = 0;
const doSearch = debounce(async (q) => {
  const seq = ++searchSeq;
  if (!S.search) return;
  const base = S.path || primaryRoot()?.path;
  if (!q.trim() || !base) {
    S.search.results = [];
    S.search.loading = false;
    return render();
  }
  S.search.loading = true;
  renderContentOnly();
  try {
    const res = await FM.search({ path: base, query: q.trim(), limit: 400 });
    if (seq !== searchSeq || !S.search) return;
    S.search.results = res.entries;
    S.search.truncated = res.truncated;
  } catch (e) {
    toast(e.message || 'Поиск не удался', { error: true });
  }
  if (S.search) S.search.loading = false;
  renderContentOnly();
}, 380);
function runSearch(q) {
  if (!S.search) return;
  S.search.q = q;
  doSearch(q);
}
function renderContentOnly() {
  renderContent();
  renderDock();
}

// ------------------------------------------------------------------ меню
function openMenu() {
  const items = [];
  if (S.path && S.perm) {
    items.push(
      { icon: prefs.view === 'list' ? 'grid' : 'list', label: prefs.view === 'list' ? 'Показать сеткой' : 'Показать списком', onClick: () => { prefs.view = prefs.view === 'list' ? 'grid' : 'list'; savePrefs(); render({ keepScroll: true }); } },
      { icon: 'sort', label: 'Сортировка', hint: sortLabel(), onClick: openSortSheet },
      { icon: prefs.hidden ? 'eyeOff' : 'eye', label: prefs.hidden ? 'Скрыть скрытые файлы' : 'Показать скрытые файлы', onClick: () => { prefs.hidden = !prefs.hidden; savePrefs(); render({ keepScroll: true }); } },
      { icon: 'refresh', label: 'Обновить', onClick: refresh },
      { divider: true }
    );
  } else if (S.perm) {
    items.push({ icon: 'refresh', label: 'Обновить', onClick: refresh }, { divider: true });
  }
  items.push({ icon: 'theme', label: 'Тема', hint: { auto: 'Как в системе', dark: 'Тёмная', light: 'Светлая' }[prefs.theme], onClick: openThemeSheet });
  sheet({ items });
}

const SORTS = [['name', 'По имени'], ['date', 'По дате'], ['size', 'По размеру'], ['type', 'По типу']];
const sortLabel = () => `${SORTS.find((s) => s[0] === prefs.sortBy)[1].toLowerCase()}, ${prefs.desc ? 'по убыванию' : 'по возрастанию'}`;
function openSortSheet() {
  sheet({
    title: 'Сортировка',
    subtitle: 'Папки всегда идут первыми. Повторное нажатие меняет порядок.',
    items: SORTS.map(([key, label]) => ({
      label,
      hint: prefs.sortBy === key ? (prefs.desc ? 'по убыванию' : 'по возрастанию') : '',
      active: prefs.sortBy === key,
      onClick: () => {
        if (prefs.sortBy === key) prefs.desc = !prefs.desc;
        else { prefs.sortBy = key; prefs.desc = key === 'date' || key === 'size'; }
        savePrefs();
        render({ keepScroll: true });
      },
    })),
  });
}
function openThemeSheet() {
  sheet({
    title: 'Тема',
    items: [['auto', 'Как в системе'], ['dark', 'Тёмная'], ['light', 'Светлая']].map(([k, label]) => ({
      label, active: prefs.theme === k,
      onClick: () => { prefs.theme = k; savePrefs(); applyTheme(); },
    })),
  });
}

// ------------------------------------------------------------------ системное
function plural(n, [one, few, many]) {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

function onBackButton() {
  if (handleBack()) return;
  if (S.selection.size) { S.selection.clear(); return render({ keepScroll: true }); }
  if (inSearch()) return exitSearch();
  if (S.clip && !S.path) { /* буфер остаётся, просто выходим */ }
  if (up()) return;
  CapApp.exitApp();
}

CapApp.addListener('backButton', onBackButton);
// Возврат с экрана системных настроек — перепроверяем разрешение
CapApp.addListener('resume', () => { if (!S.perm) checkPermission(); });
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.perm === false) checkPermission();
});

export { init, hasLayers };
