// Строки экранов настроек. Каждая функция возвращает HTML-строку.
import { icon } from '../icons.js';

/** Переключатель on/off для prefs[key]. */
export const toggleRow = (prefs, key, label, hint) => `
  <button class="row" role="switch" aria-checked="${!!prefs[key]}" data-toggle="${key}">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    <span class="sw ${prefs[key] ? 'on' : ''}"><i></i></span>
  </button>`;

/** Строка с радио-выбором одного значения prefs[key]. */
export const choiceRow = (key, value, label, hint, active) => `
  <button class="row" role="radio" aria-checked="${active}" data-set="${key}" data-val="${value}" data-num="${typeof value === 'number'}">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    <span class="radio ${active ? 'on' : ''}">${active ? icon('check', 16) : ''}</span>
  </button>`;

/** Сегментный переключатель: items = [[value, label, iconName]]. */
export const segmentRow = (prefs, key, items) => `
  <div class="segment" role="radiogroup">${items
    .map(
      ([v, l, ic]) =>
        `<button role="radio" aria-checked="${prefs[key] === v}" class="${prefs[key] === v ? 'on' : ''}" data-set="${key}" data-val="${v}">${icon(ic, 18)}<span>${l}</span></button>`
    )
    .join('')}</div>`;

/** Строка-кнопка со стрелкой: открывает подэкран или действие. */
export const linkRow = (act, label, hint) => `
  <button class="row" data-act="${act}">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    ${icon('chevron', 20)}
  </button>`;

/** Пункт главного меню: иконка, название, краткое текущее значение. */
export const menuRow = (act, ic, label, summary) => `
  <button class="row" data-act="${act}">
    <span class="row-ic">${icon(ic, 20)}</span>
    <span class="row-text"><span class="row-label">${label}</span>${summary ? `<small>${summary}</small>` : ''}</span>
    ${icon('chevron', 20)}
  </button>`;

/** Строка без нажатия: подпись + необязательный бейдж справа. */
export const infoRow = (label, hint, badgeHtml = '') => `
  <div class="row static">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    ${badgeHtml}
  </div>`;

export const group = (html, cls = '') => `<div class="group ${cls}">${html}</div>`;
