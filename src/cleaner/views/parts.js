// Общие кусочки экранов очистки: верхняя панель и строка-переключатель.
import { icon } from '../../icons/icon.js';
import { escapeHtml } from '../../html.js';

export const topbar = (title, right = '') => `
  <div class="cl-bar">
    <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
    <h1>${escapeHtml(title)}</h1>${right}
  </div>`;

export const switchRow = (o, key, label, hint, disabled) => `
  <button class="row" role="switch" aria-checked="${!!o[key]}" data-opt="${key}" ${disabled ? 'disabled' : ''}>
    <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
    <span class="sw ${o[key] ? 'on' : ''}"><i></i></span>
  </button>`;
