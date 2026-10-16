// Окно «ключ — значение» (свойства файла). set(key, value) обновляет строку на лету.
import { escapeHtml } from '../html.js';
import { pushLayer } from './stack.js';
import { el } from './dom.js';

export function infoDialog({ title, rows }) {
  const html = rows
    .map(([k, v]) => `<div class="kv"><span>${escapeHtml(k)}</span><b data-k="${escapeHtml(k)}">${escapeHtml(v)}</b></div>`)
    .join('');
  const node = el(
    `<div class="scrim"></div>
     <div class="dialog info" role="dialog" aria-modal="true">
       <h2 class="dialog-title">${escapeHtml(title)}</h2>
       <div class="kvs">${html}</div>
       <div class="dialog-actions"><button class="btn primary" data-act="ok">Закрыть</button></div>
     </div>`,
    'layer-root layer-dialog'
  );
  const close = pushLayer(node);
  node.querySelector('.scrim').addEventListener('click', close);
  node.querySelector('[data-act="ok"]').addEventListener('click', close);
  return {
    close,
    set(key, value) {
      const b = node.querySelector(`b[data-k="${CSS.escape(key)}"]`);
      if (b) b.textContent = value;
    },
  };
}
