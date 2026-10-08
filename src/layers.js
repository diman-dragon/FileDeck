// Слои поверх интерфейса: листы, диалоги, полноэкранные окна, тосты.
// Все слои живут в одном стеке, поэтому системная кнопка «Назад» закрывает верхний.
import { icon } from './icons.js';
import { escapeHtml } from './utils.js';

const root = () => document.getElementById('layer');
const stack = [];

export function pushLayer(node, { onBack } = {}) {
  const entry = { node, onBack, closed: false };
  entry.close = () => {
    if (entry.closed) return;
    entry.closed = true;
    const i = stack.indexOf(entry);
    if (i >= 0) stack.splice(i, 1);
    node.classList.remove('open');
    node.classList.add('closing');
    setTimeout(() => node.remove(), 200);
  };
  root().appendChild(node);
  stack.push(entry);
  requestAnimationFrame(() => requestAnimationFrame(() => node.classList.add('open')));
  return entry.close;
}

/** true — «Назад» обработана слоем. */
export function handleBack() {
  const top = stack[stack.length - 1];
  if (!top) return false;
  if (top.onBack) top.onBack();
  else top.close();
  return true;
}
export const hasLayers = () => stack.length > 0;

function el(html, cls) {
  const d = document.createElement('div');
  d.className = cls;
  d.innerHTML = html;
  return d;
}

// ------------------------------------------------------------------ toast
let toastTimer;
export function toast(message, { error = false, ms = 2600 } = {}) {
  let t = document.getElementById('toast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'toast';
    t.setAttribute('role', 'status');
    document.body.appendChild(t);
  }
  t.textContent = message;
  t.className = 'toast show' + (error ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), ms);
}

// ------------------------------------------------------------------ bottom sheet
/**
 * items: [{ icon, label, hint, danger, active, onClick }] или { divider: true }
 */
export function sheet({ title, subtitle, items }) {
  const rows = items
    .map((it, i) =>
      it.divider
        ? '<div class="sheet-divider"></div>'
        : `<button class="sheet-item${it.danger ? ' danger' : ''}${it.active ? ' active' : ''}" data-i="${i}">
            <span class="sheet-ic">${it.icon ? icon(it.icon, 22) : ''}</span>
            <span class="sheet-label">${escapeHtml(it.label)}${it.hint ? `<small>${escapeHtml(it.hint)}</small>` : ''}</span>
            ${it.active ? `<span class="sheet-check">${icon('check', 20)}</span>` : ''}
          </button>`
    )
    .join('');
  const node = el(
    `<div class="scrim"></div>
     <div class="sheet" role="dialog" aria-modal="true">
       <div class="sheet-grip"></div>
       ${title ? `<div class="sheet-head"><div class="sheet-title">${escapeHtml(title)}</div>${subtitle ? `<div class="sheet-sub">${escapeHtml(subtitle)}</div>` : ''}</div>` : ''}
       <div class="sheet-body">${rows}</div>
     </div>`,
    'layer-root layer-sheet'
  );
  const close = pushLayer(node);
  node.querySelector('.scrim').addEventListener('click', close);
  node.querySelectorAll('.sheet-item').forEach((b) =>
    b.addEventListener('click', () => {
      const it = items[Number(b.dataset.i)];
      close();
      setTimeout(() => it.onClick && it.onClick(), 120);
    })
  );
  return close;
}

// ------------------------------------------------------------------ диалоги
function dialog({ title, body, okText, cancelText = 'Отмена', danger = false, withInput = null }) {
  return new Promise((resolve) => {
    const node = el(
      `<div class="scrim"></div>
       <form class="dialog" role="alertdialog" aria-modal="true">
         <h2 class="dialog-title">${escapeHtml(title)}</h2>
         ${body ? `<p class="dialog-body">${body}</p>` : ''}
         ${withInput ? `<input class="dialog-input" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" placeholder="${escapeHtml(withInput.placeholder || '')}" value="${escapeHtml(withInput.value || '')}" />` : ''}
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

export const confirmDialog = ({ title, text, okText = 'Удалить', danger = true }) =>
  dialog({ title, body: text ? escapeHtml(text) : '', okText, danger });

export const promptDialog = ({ title, value = '', placeholder = '', okText = 'Готово', selectBase = false }) =>
  dialog({ title, okText, withInput: { value, placeholder, selectBase } });

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

export { el };
