// Экран категории: список найденного с выбором.
import { icon } from '../../icons/icon.js';
import { formatSize } from '../../format.js';
import { CATS } from '../config.js';
import { groupRows, keeper, rowsOf } from '../rows.js';
import { selBytes } from '../selection.js';
import { topbar } from './parts.js';
import { rowHtml } from './row-view.js';

export function detailHtml(cl) {
  const { state } = cl;
  const c = CATS.find((x) => x.id === state.cat);
  const rows = rowsOf(state.result, c.id);
  const n = state.sel[c.id]?.size || 0;
  const bytes = selBytes(cl, c.id);
  const all = rows.length && n === rows.length;
  let body = '';
  if (!rows.length) body = '<div class="state"><div class="empty-glyph">' + icon('check', 40) + '</div><h1>Всё чисто</h1><p>В этой категории больше ничего нет.</p></div>';
  else if (c.group) {
    let idx = 0;
    body = [...groupRows(rows).values()].map((list, gi) => {
      const keep = keeper(list);
      const waste = list.filter((x) => x !== keep).reduce((a, x) => a + x.size, 0);
      const head = `<div class="grp-head"><b>${c.id === 'similar' ? 'Похожие' : 'Одинаковые'} · ${list.length}</b><span>можно освободить ${formatSize(waste)}</span></div>`;
      return head + list.map((r) => rowHtml(c, state, r, idx++, keep.path)).join('');
    }).join('');
  } else {
    body = rows.map((r, i) => rowHtml(c, state, r, i, null)).join('');
  }
  return `${topbar(c.title, rows.length ? `<button class="btn ghost small" data-act="toggle-all">${all ? 'Снять всё' : 'Выбрать всё'}</button>` : '')}
    <div class="cl-body list-body"><p class="cl-hint">${c.hint}</p><div class="items list">${body}</div></div>
    <div class="cl-actions">${n > 0 ? `<button class="btn danger big" data-act="delete">Удалить · ${n} · ${formatSize(bytes) || '0 Б'}</button>` : `<button class="btn ghost big" disabled>Ничего не выбрано</button>`}</div>`;
}
