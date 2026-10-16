// Нажатия на экранах очистки.
import { FM } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { basename, parentOf } from '../path.js';
import { kindOf } from '../file-kinds.js';
import { rowsOf } from './rows.js';
import { cancelScan, startScan } from './scan.js';
import { deleteCategory, quickClean } from './remove.js';

export function bindEvents(c) {
  const { ctx, o, state, node } = c;
  node.addEventListener('click', (ev) => {
    if (state.busy) return;
    const opt = ev.target.closest('[data-opt]');
    if (opt) {
      o[opt.dataset.opt] = !o[opt.dataset.opt];
      return c.draw();
    }
    const catBtn = ev.target.closest('[data-cat]');
    if (catBtn) {
      state.cat = catBtn.dataset.cat;
      state.view = 'detail';
      return c.draw(true);
    }
    const act = ev.target.closest('[data-act]')?.dataset.act;
    const row = ev.target.closest('.cl-row');
    if (act === 'back') return c.back();
    if (act === 'scan' || act === 'rescan') {
      state.view = 'setup';
      return act === 'scan' ? startScan(c) : c.draw();
    }
    if (act === 'cancel-scan') return cancelScan();
    if (act === 'quick') return quickClean(c);
    if (act === 'delete') return deleteCategory(c, state.cat);
    if (act === 'toggle-all') {
      const rows = rowsOf(state.result, state.cat);
      const set = state.sel[state.cat];
      if (set.size === rows.length) set.clear();
      else rows.forEach((r) => set.add(r.path));
      return c.draw();
    }
    if (!row) return;
    const path = row.dataset.path;
    if (act === 'locate') {
      c.close();
      return ctx.locate(parentOf(path));
    }
    if (act === 'view') {
      const e = { path, name: basename(path), size: 0, mtime: 0 };
      if (kindOf(e) === 'image') return ctx.viewImage(e);
      return FM.open({ path }).catch((err) => toast(err.message, { error: true }));
    }
    const set = state.sel[state.cat];
    set.has(path) ? set.delete(path) : set.add(path);
    c.draw();
  });
}
