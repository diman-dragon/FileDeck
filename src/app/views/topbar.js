// Верхняя панель: переключатель накопителей, поиск, выделение, «хлебные крошки».
import { icon } from '../../icons/icon.js';
import { join } from '../../path.js';
import { escapeHtml } from '../../html.js';
import { plural } from '../../format.js';
import { prefs } from '../prefs.js';
import { S, inSearch, rootOf } from '../state.js';
import { $, topbar } from '../dom-refs.js';

function switchHtml() {
  const cur = S.path ? rootOf(S.path) : null;
  const active = cur ? (cur.primary ? 'mem' : 'sd') : '';
  const hasSd = S.roots.some((r) => !r.primary);
  const btn = (id, label, ic, dim) =>
    `<button class="seg${active === id ? ' on' : ''}${dim ? ' off' : ''}" data-act="switch" data-root="${id}" aria-pressed="${active === id}">${icon(ic, 17)}<span>${label}</span></button>`;
  return `<div class="switch" role="group" aria-label="Накопитель">${btn('mem', 'Память', 'drive', false)}${btn('sd', 'SD-карта', 'sd', S.perm && !hasSd)}</div>`;
}

export function renderTopbar() {
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
    sub = `<div class="subbar selbar">
      <button class="icon-btn" data-act="clear-sel" aria-label="Отменить выбор">${icon('close')}</button>
      <div class="sel-count"><b>${n}</b><span>${plural(n, ['выбран', 'выбрано', 'выбрано'])}</span></div>
      <button class="icon-btn" data-act="select-all" aria-label="Выбрать всё">${icon('selectAll')}</button>
    </div>`;
  } else if (inSearch()) {
    sub = `<div class="subbar searchbar">
      <input id="search-input" type="search" inputmode="search" enterkeyhint="search" placeholder="${S.path ? 'Поиск в этой папке' : 'Поиск по памяти'}" value="${escapeHtml(S.search.q)}" autocomplete="off" />
      ${S.search.q ? `<button class="icon-btn" data-act="clear-query" aria-label="Очистить">${icon('close')}</button>` : ''}
    </div>`;
  } else if (S.path && S.perm) {
    crumbs = true;
    sub = `<div class="subbar crumbbar">
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
