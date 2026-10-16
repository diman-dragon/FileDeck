// Строки экранов настроек. Каждая функция возвращает HTML-строку.
import { icon } from '../icons/icon.js';

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

/** Строка-кнопка со стрелкой: открывает экран или запускает действие. */
export const linkRow = (act, label, hint) => `
  <button class="row" data-act="${act}">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    ${icon('chevron', 20)}
  </button>`;

/** Строка без нажатия: подпись + необязательный бейдж справа. */
export const infoRow = (label, hint, badgeHtml = '') => `
  <div class="row static">
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    ${badgeHtml}
  </div>`;

export const group = (html, cls = '') => `<div class="group ${cls}">${html}</div>`;

/** Подзаголовок над группой строк. */
export const caption = (text) => `<h2 class="st-h">${text}</h2>`;
