// Окно прогресса длинной операции.
import { escapeHtml } from '../html.js';
import { pushLayer } from './stack.js';
import { el } from './dom.js';

/** Окно прогресса длинной операции. set(0..1, подпись); onCancel вызывается по кнопке «Отмена». */
export function progressDialog({ title, onCancel, cancelText = 'Отмена' }) {
  const node = el(
    `<div class="scrim"></div>
     <div class="dialog progress-dialog" role="alertdialog" aria-modal="true" aria-live="polite">
       <h2 class="dialog-title">${escapeHtml(title)}</h2>
       <div class="pbar"><i></i></div>
       <div class="pmeta"><span class="pcur">Подготовка…</span><span class="ppct">0%</span></div>
       <div class="dialog-actions"><button class="btn ghost" data-act="cancel">${escapeHtml(cancelText)}</button></div>
     </div>`,
    'layer-root layer-dialog'
  );
  let closed = false;
  const close = pushLayer(node, { onBack: () => cancel() });
  const bar = node.querySelector('.pbar i');
  const cur = node.querySelector('.pcur');
  const pct = node.querySelector('.ppct');
  function cancel() {
    if (closed) return;
    node.querySelector('[data-act="cancel"]').disabled = true;
    cur.textContent = 'Отменяем…';
    onCancel && onCancel();
  }
  node.querySelector('[data-act="cancel"]').addEventListener('click', cancel);
  return {
    set(fraction, text) {
      const f = Math.max(0, Math.min(1, fraction || 0));
      bar.style.width = `${(f * 100).toFixed(1)}%`;
      pct.textContent = `${Math.round(f * 100)}%`;
      if (text) cur.textContent = text.length > 60 ? '…' + text.slice(-58) : text;
    },
    close() {
      closed = true;
      close();
    },
  };
}
