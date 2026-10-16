// Нижний лист с пунктами меню.
import { icon } from '../icons/icon.js';
import { escapeHtml } from '../html.js';
import { pushLayer } from './stack.js';
import { el } from './dom.js';

/**
 * items: [{ icon, label, hint, danger, active, onClick }] или { divider: true }
 */
export function sheet({ title, subtitle, items, onDismiss }) {
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
  let picked = false;
  const dismiss = () => {
    close();
    if (!picked && onDismiss) onDismiss();
  };
  const close = pushLayer(node, { onBack: dismiss });
  node.querySelector('.scrim').addEventListener('click', dismiss);
  node.querySelectorAll('.sheet-item').forEach((b) =>
    b.addEventListener('click', () => {
      const it = items[Number(b.dataset.i)];
      picked = true;
      close();
      setTimeout(() => it.onClick && it.onClick(), 120);
    })
  );
  return close;
}
