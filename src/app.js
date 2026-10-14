import { SystemBars, SystemBarsStyle } from '@capacitor/core';
import { App as CapApp } from '@capacitor/app';
import { FM, CL, fileUrl, isNative } from './plugin.js';
import { icon, folderGlyph } from './icons.js';
import { toast, sheet, confirmDialog, promptDialog, infoDialog, handleBack, hasLayers } from './layers.js';
import { openImageViewer, openEditor } from './viewer.js';
import { openSettings } from './settings/index.js';
import { openCleaner } from './cleaner.js';
import { openTrash } from './trash.js';
import { createArchiveFlow, openArchiveSheet } from './archive.js';
import {
  basename, parentOf, join, escapeHtml, formatSize, formatDate, kindOf, extOf, sortEntries,
  KIND_ICON, KIND_LABEL, isEditable, debounce, isArchive,
} from './utils.js';

const APP_VERSION = typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev';
const APP_BUILD = typeof __APP_BUILD__ === 'number' ? __APP_BUILD__ : 0;

// ------------------------------------------------------------------ состояние
const PREFS_KEY = 'filedeck.prefs.v1';
const BASE_PREFS = { view: 'list', sortBy: 'name', desc: false, hidden: false, theme: 'auto', confirmDelete: true, start: 'home', useTrash: true, trashDays: 30, cleaner: {} };
const prefs = Object.assign(
  {},
  BASE_PREFS,
  (() => { try { return JSON.parse(localStorage.getItem(PREFS_KEY)) || {}; } catch { return {}; } })()
);
if (prefs.theme === 'dark') prefs.theme = 'amoled'; // миграция со старой версии
const DEFAULTS = JSON.stringify(BASE_PREFS);
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
  lastIn: {},          // последняя открытая папка на каждом накопителе
  trash: { count: 0, bytes: 0 },
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
const resolveTheme = () => (prefs.theme === 'auto' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'amoled') : prefs.theme);
export function applyTheme() {
  const t = resolveTheme();
  document.documentElement.dataset.theme = t;
  if (isNative) SystemBars.setStyle({ style: t === 'light' ? SystemBarsStyle.Light : SystemBarsStyle.Dark }).catch(() => {});
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
  if (S.perm) {
    await loadRoots();
    CL.trashPurge({ days: prefs.trashDays || 30 }).catch(() => {}).then(refreshTrash);
    const first = prefs.start === 'primary' ? primaryRoot() : prefs.start === 'sd' ? S.roots.find((r) => !r.primary) : null;
    if (first && S.path === null && !S.started) {
      S.started = true;
      return go(first.path);
    }
    S.started = true;
  }
  render();
}

async function refreshTrash() {
  try {
    const r = await CL.trashList();
    S.trash = { count: r.count, bytes: r.bytes };
  } catch { /* корзины может ещё не быть */ }
  const b = $('trash-badge');
  if (b) b.textContent = trashLabel();
}
const trashLabel = () => (S.trash.count ? `${S.trash.count} · ${formatSize(S.trash.bytes) || '0 Б'}` : 'пусто');

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
  if (path) {
    const r = rootOf(path);
    if (r) S.lastIn[r.path] = path;
  }
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

function switchHtml() {
  const cur = S.path ? rootOf(S.path) : null;
  const active = cur ? (cur.primary ? 'mem' : 'sd') : '';
  const hasSd = S.roots.some((r) => !r.primary);
  const btn = (id, label, ic, dim) =>
    `<button class="seg${active === id ? ' on' : ''}${dim ? ' off' : ''}" data-act="switch" data-root="${id}" aria-pressed="${active === id}">${icon(ic, 17)}<span>${label}</span></button>`;
  return `<div class="switch" role="group" aria-label="Накопитель">${btn('mem', 'Память', 'drive', false)}${btn('sd', 'SD-карта', 'sd', S.perm && !hasSd)}</div>`;
}

function renderTopbar() {
  const n = S.selection.size;
  const lead = inSearch()
    ? `<button class="icon-btn" data-act="exit-search" aria-label="Закрыть поиск">${icon('back')}</button>`
    : S.path && S.perm
      ? `<button class="icon-btn" data-act="up" aria-label="Наверх">${icon('back')}</button>`
      : `<span class="brand" aria-label="DataLaw"><i class="brand-d"></i></span>`;
  const bar = `
    <div class="bar">
      ${lead}
      ${switchHtml()}
      ${S.perm ? `<button class="icon-btn" data-act="search" aria-label="Поиск">${icon('search')}</button>` : ''}
      <button class="icon-btn" data-act="settings" aria-label="Настройки">${icon('gear')}</button>
    </div>`;

  let sub = '';
  let crumbs = false;
  if (n > 0) {
    sub = `<div class="sub selbar">
      <button class="icon-btn" data-act="clear-sel" aria-label="Отменить выбор">${icon('close')}</button>
      <div class="sel-count"><b>${n}</b><span>${plural(n, ['выбран', 'выбрано', 'выбрано'])}</span></div>
      <button class="icon-btn" data-act="select-all" aria-label="Выбрать всё">${icon('selectAll')}</button>
    </div>`;
  } else if (inSearch()) {
    sub = `<div class="sub searchbar">
      <input id="search-input" type="search" inputmode="search" enterkeyhint="search" placeholder="${S.path ? 'Поиск в этой папке' : 'Поиск по памяти'}" value="${escapeHtml(S.search.q)}" autocomplete="off" />
      ${S.search.q ? `<button class="icon-btn" data-act="clear-query" aria-label="Очистить">${icon('close')}</button>` : ''}
    </div>`;
  } else if (S.path && S.perm) {
    crumbs = true;
    sub = `<div class="sub crumbbar">
      <nav class="crumbs" aria-label="Путь">${crumbsHtml()}</nav>
      <button class="icon-btn small" data-act="toggle-view" aria-label="Сменить вид">${icon(prefs.view === 'list' ? 'grid' : 'list', 20)}</button>
    </div>`;
  }
  topbar.className = `topbar${crumbs ? ' has-crumbs' : ''}${n > 0 ? ' selecting' : ''}`;
  topbar.innerHTML = bar + sub;
  const nav = topbar.querySelector('.crumbs');
  if (nav) nav.scrollLeft = nav.scrollWidth;
  const input = $('search-input');
  if (input && S.search && !S.search.q && document.activeElement !== input) setTimeout(() => input.focus(), 30);
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

function switchRoot(which) {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  const root = which === 'sd' ? S.roots.find((r) => !r.primary) : primaryRoot();
  if (!root) return toast('SD-карта не найдена. Вставьте карту и вернитесь в приложение', { error: true });
  const cur = S.path ? rootOf(S.path) : null;
  if (cur && cur.path === root.path && S.path !== root.path && !inSearch()) return go(root.path); // повторный тап — в корень
  go(S.lastIn[root.path] || root.path);
}

function openCleanerPage() {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  openCleaner({
    roots: S.roots,
    prefs,
    savePrefs,
    locate: (path) => go(path),
    viewImage: (e) => openImageViewer([e], 0),
    onChange: () => { refreshTrash(); S.path ? refresh() : loadRoots().then(() => render({ keepScroll: true })); },
  });
}

function openTrashPage() {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  openTrash({ prefs, onChange: () => { refreshTrash(); S.path ? refresh() : loadRoots().then(() => render({ keepScroll: true })); } });
}

function openSettingsPage() {
  openSettings({
    prefs,
    save: savePrefs,
    onChange: () => { applyTheme(); render({ keepScroll: true }); },
    version: APP_VERSION,
    build: APP_BUILD,
    native: isNative,
    hasAccess: () => !!S.perm,
    requestAccess: requestPermission,
    trashInfo: () => S.trash,
    openTrash: openTrashPage,
    openCleaner: openCleanerPage,
    reset: () => { Object.assign(prefs, JSON.parse(DEFAULTS)); savePrefs(); },
  });
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
      <div class="tools">
        <button class="tool main" data-act="open-cleaner">${icon('sparkle', 26)}<b>Очистка</b><small>дубликаты, мусор, пустые папки</small></button>
        <button class="tool" data-act="open-trash">${icon('trash', 26)}<b>Корзина</b><small id="trash-badge">${trashLabel()}</small></button>
      </div>
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
  const el = ev.target.closest('[data-act]');
  if (!el) return;
  ({
    switch: () => switchRoot(el.dataset.root),
    settings: openSettingsPage,
    'toggle-view': () => { prefs.view = prefs.view === 'list' ? 'grid' : 'list'; savePrefs(); render({ keepScroll: true }); },
    'clear-sel': () => { S.selection.clear(); render({ keepScroll: true }); },
    'select-all': selectAll,
    'exit-search': exitSearch,
    'clear-query': () => { S.search.q = ''; S.search.results = []; render(); $('search-input')?.focus(); },
    search: startSearch,
    up,
  })[el.dataset.act]?.();
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
  if (act === 'open-cleaner') return openCleanerPage();
  if (act === 'open-trash') return openTrashPage();
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
  if (isArchive(e.name)) return openArchiveSheet(e, { onDone: refresh, onLocate: go });
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

async function deletePaths(paths, { permanent = false } = {}) {
  const names = paths.map(basename);
  const toTrash = prefs.useTrash && !permanent;
  const what = paths.length === 1 ? `«${names[0]}»` : `выбранное (${paths.length})`;
  const ok = !prefs.confirmDelete && toTrash ? true : await confirmDialog({
    title: toTrash ? `Переместить ${what} в корзину?` : `Удалить ${what} навсегда?`,
    text: toTrash ? `Файлы хранятся в корзине ${prefs.trashDays} дн., их можно восстановить.` : 'Файлы будут удалены без возможности восстановления.',
    okText: toTrash ? 'В корзину' : 'Удалить',
    danger: !toTrash,
  });
  if (!ok) return;
  try {
    const res = await (toTrash ? CL.trashMove({ paths }) : FM.remove({ paths }));
    S.selection.clear();
    if (S.clip) S.clip.paths = S.clip.paths.filter((p) => !paths.includes(p));
    if (S.clip && !S.clip.paths.length) S.clip = null;
    report(res, toTrash ? 'В корзине' : 'Удалено');
    if (toTrash) refreshTrash();
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
  if (!sel.length) return;
  const res = await createArchiveFlow({ entries: sel, destDir: S.path || parentOf(sel[0].path) });
  if (res) {
    S.selection.clear();
    refresh();
  }
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
  items.push({ icon: 'archive', label: 'Добавить в архив…', hint: 'ZIP, 7z, TAR · пароль, уровень сжатия', onClick: zipSelected });
  if (one && isArchive(one.name)) items.push({ icon: 'unzip', label: 'Распаковать…', hint: 'Просмотр содержимого и выбор файлов', onClick: () => { S.selection.clear(); render({ keepScroll: true }); openArchiveSheet(one, { onDone: refresh, onLocate: go }); } });
  if (one && !one.dir) items.push({ icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(one) });
  if (one && isEditable(one)) items.push({ icon: 'doc', label: 'Открыть в редакторе', onClick: () => openEditor(one, { onSaved: refresh }) });
  if (one) items.push({ icon: 'info', label: 'Свойства', onClick: () => showProps(one) });
  if (prefs.useTrash) items.push({ divider: true }, { icon: 'trash', label: 'Удалить навсегда', danger: true, onClick: () => deletePaths(sel.map((e) => e.path), { permanent: true }) });
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
CapApp.addListener('resume', () => { if (!S.perm) checkPermission(); else { loadRoots().then(() => render({ keepScroll: true })); refreshTrash(); } });
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.perm === false) checkPermission();
});

export { init, hasLayers };
