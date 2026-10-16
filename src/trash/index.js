// Экран «Корзина»: слой, загрузка списка и нажатия. ctx: { prefs, onChange() }
import { TR } from '../plugin.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { toast } from '../layers/toast.js';
import { trashHtml } from './screen-view.js';
import { restoreSelected, deleteFromTrash } from './actions.js';

export async function openTrash(ctx) {
  const c = {
    ctx,
    node: el('', 'layer-root layer-trash'),
    days: ctx.prefs.trashDays || 30,
    selected: new Set(),
    items: [],
    bytes: 0,
    loading: true,
    closed: false,
    busy: false, // идёт восстановление или удаление — повторные нажатия игнорируем
  };
  const closeLayer = pushLayer(c.node, { onBack: () => close() });
  const close = () => {
    c.closed = true;
    closeLayer();
  };

  c.draw = () => {
    if (!c.closed) c.node.innerHTML = trashHtml(c);
  };

  c.load = async () => {
    try {
      const res = await TR.list();
      c.items = res.items;
      c.bytes = res.bytes;
    } catch (e) {
      toast(e.message || 'Не удалось прочитать корзину', { error: true });
    }
    c.loading = false;
    for (const id of [...c.selected]) if (!c.items.some((t) => t.id === id)) c.selected.delete(id);
    c.draw();
  };

  c.node.addEventListener('click', async (ev) => {
    const act = ev.target.closest('[data-act]')?.dataset.act;
    const row = ev.target.closest('.cl-row');
    if (act === 'back') return close();
    if (act === 'toggle-all') {
      if (c.selected.size === c.items.length) c.selected.clear();
      else c.items.forEach((t) => c.selected.add(t.id));
      return c.draw();
    }
    if (act === 'restore') return restoreSelected(c);
    if (act === 'delete' || act === 'empty') return deleteFromTrash(c, act === 'empty');
    if (!row) return;
    const id = row.dataset.id;
    c.selected.has(id) ? c.selected.delete(id) : c.selected.add(id);
    c.draw();
  });

  c.draw();
  c.load();
}
