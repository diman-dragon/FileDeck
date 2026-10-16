// Базовый диалог с кнопками «Отмена» / «ОК» и необязательным полем ввода. Возвращает Promise.
import { escapeHtml } from '../html.js';
import { pushLayer } from './stack.js';
import { el } from './dom.js';

export function dialog({ title, body, okText, cancelText = 'Отмена', danger = false, withInput = null }) {
  return new Promise((resolve) => {
    const node = el(
      `<div class="scrim"></div>
       <form class="dialog" role="alertdialog" aria-modal="true">
         <h2 class="dialog-title">${escapeHtml(title)}</h2>
         ${body ? `<p class="dialog-body">${body}</p>` : ''}
         ${withInput ? `<input class="dialog-input${withInput.error ? ' invalid' : ''}" type="${withInput.type || 'text'}" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="${escapeHtml(withInput.placeholder || '')}" value="${escapeHtml(withInput.value || '')}" />` : ''}
         ${withInput && withInput.error ? `<p class="dialog-error">${escapeHtml(withInput.error)}</p>` : ''}
         <div class="dialog-actions">
           <button type="button" class="btn ghost" data-act="cancel">${escapeHtml(cancelText)}</button>
           <button type="submit" class="btn ${danger ? 'danger' : 'primary'}">${escapeHtml(okText)}</button>
         </div>
       </form>`,
      'layer-root layer-dialog'
    );
    let settled = false;
    const finish = (v) => {
      if (settled) return;
      settled = true;
      close();
      resolve(v);
    };
    const close = pushLayer(node, { onBack: () => finish(withInput ? null : false) });
    const input = node.querySelector('.dialog-input');
    node.querySelector('.scrim').addEventListener('click', () => finish(withInput ? null : false));
    node.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(withInput ? null : false));
    node.querySelector('form').addEventListener('submit', (e) => {
      e.preventDefault();
      finish(withInput ? input.value : true);
    });
    if (input) {
      setTimeout(() => {
        input.focus();
        const v = input.value;
        const dot = v.lastIndexOf('.');
        input.setSelectionRange(0, withInput.selectBase && dot > 0 ? dot : v.length);
      }, 220);
    }
  });
}
