import { CL } from './plugin.js';
import { icon, folderGlyph } from './icons.js';
import { pushLayer, el, toast, confirmDialog } from './layers.js';
import { withTrashProgress } from './trash-progress.js';
import { escapeHtml, formatSize, formatDate, kindOf, KIND_ICON, parentOf } from './utils.js';

const plural = (n, [one, few, many]) => {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
};

/** Корзина: список, восстановление, удаление навсегда, очистка. ctx: { prefs, onChange() } */
export async function openTrash(ctx) {
  const node = el('', 'layer-root layer-trash');
  const days = ctx.prefs.trashDays || 30;
  const selected = new Set();
  let items = [];
  let bytes = 0;
  let loading = true;
  let closed = false;
  let busy = false; // идёт восстановление или удаление — повторные нажатия игнорируем
  const close = pushLayer(node, { onBack: () => { closed = true; close(); } });

  const left = (t) => Math.max(0, Math.ceil((t + days * 86400000 - Date.now()) / 86400000));
  const where = (orig) => parentOf(orig).replace(/^\/storage\/(emulated\/0|[^/]+)/, (m, g) => (g === 'emulated/0' ? 'Память' : 'SD')) || '/';

  const rowHtml = (t, i) => {
    const kind = kindOf({ name: t.name, dir: t.dir });
    const on = selected.has(t.id);
    const d = left(t.time);
    return `<div class="item cl-row${on ? ' selected' : ''}" data-i="${i}" data-id="${escapeHtml(t.id)}">
      <button class="chk${on ? ' on' : ''}" data-act="chk" aria-label="Выбрать">${icon('check', 16)}</button>
      <div class="lead k-${kind}">${kind === 'folder' ? folderGlyph(40) : `<span class="kind-ic">${icon(KIND_ICON[kind] || 'file', 22)}</span>`}</div>
      <div class="body"><div class="name">${escapeHtml(t.name)}</div>
        <div class="sub">${formatSize(t.size) || '0 Б'} · удалён ${formatDate(t.time)}</div>
        <div class="sub where">${escapeHtml(where(t.orig))} · осталось ${d} ${plural(d, ['день', 'дня', 'дней'])}</div></div>
    </div>`;
  };

  const draw = () => {
    if (closed) return;
    const n = selected.size;
    let body;
    if (loading) body = '<div class="state"><div class="spinner"></div></div>';
    else if (!items.length) body = `<div class="state"><div class="empty-glyph">${icon('trash', 44)}</div><h1>Корзина пуста</h1><p>Удалённые файлы хранятся здесь ${days} ${plural(days, ['день', 'дня', 'дней'])} и могут быть восстановлены.</p></div>`;
    else body = `<div class="list-note">${items.length} ${plural(items.length, ['объект', 'объекта', 'объектов'])} · ${formatSize(bytes) || '0 Б'} · хранение ${days} ${plural(days, ['день', 'дня', 'дней'])}</div><div class="items list">${items.map(rowHtml).join('')}</div>`;
    node.innerHTML = `
      <div class="cl-bar"><button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button><h1>Корзина</h1>
        ${items.length ? `<button class="btn ghost small" data-act="toggle-all">${n === items.length ? 'Снять всё' : 'Выбрать всё'}</button>` : ''}</div>
      <div class="cl-body list-body">${body}</div>
      <div class="cl-actions">${
        n > 0
          ? `<div class="two"><button class="btn primary big" data-act="restore">Восстановить · ${n}</button><button class="btn danger big" data-act="delete">Удалить</button></div>`
          : items.length
            ? `<button class="btn ghost big" data-act="empty">Очистить корзину</button>`
            : ''
      }</div>`;
  };

  async function load() {
    try {
      const res = await CL.trashList();
      items = res.items;
      bytes = res.bytes;
    } catch (e) {
      toast(e.message || 'Не удалось прочитать корзину', { error: true });
    }
    loading = false;
    for (const id of [...selected]) if (!items.some((t) => t.id === id)) selected.delete(id);
    draw();
  }

  node.addEventListener('click', async (ev) => {
    const act = ev.target.closest('[data-act]')?.dataset.act;
    const row = ev.target.closest('.cl-row');
    if (act === 'back') { closed = true; return close(); }
    if (act === 'toggle-all') {
      if (selected.size === items.length) selected.clear();
      else items.forEach((t) => selected.add(t.id));
      return draw();
    }
    if (act === 'restore') {
      if (busy) return;
      busy = true;
      try {
        const res = await withTrashProgress('Восстанавливаем…', () => CL.trashRestore({ ids: [...selected] }));
        const failed = res.failed?.length || 0;
        toast(`${res.cancelled ? 'Отменено. ' : ''}Восстановлено: ${res.done}${failed ? `, не удалось: ${failed}` : ''}`, { error: !!failed });
        selected.clear();
        ctx.onChange && ctx.onChange();
      } catch (e) { toast(e.message || 'Не удалось восстановить', { error: true }); }
      busy = false;
      return load();
    }
    if (act === 'delete' || act === 'empty') {
      const all = act === 'empty';
      const ok = await confirmDialog({
        title: all ? 'Очистить корзину?' : `Удалить навсегда (${selected.size})?`,
        text: 'Файлы будут удалены без возможности восстановления.',
        okText: all ? 'Очистить' : 'Удалить',
      });
      if (!ok || busy) return;
      busy = true;
      const expected = all ? items.length : selected.size;
      try {
        const res = await withTrashProgress(all ? 'Очищаем корзину…' : 'Удаляем…', () => (all ? CL.trashEmpty() : CL.trashDelete({ ids: [...selected] })));
        const failed = res.cancelled ? 0 : Math.max(0, expected - res.done);
        const freed = `освобождено ${formatSize(res.bytes) || '0 Б'}`;
        if (res.cancelled) toast(`Отменено. Удалено: ${res.done} · ${freed}`);
        else if (failed) toast(`Удалено: ${res.done}, не удалось: ${failed}`, { error: true, ms: 4200 });
        else toast(`Удалено: ${res.done} · ${freed}`);
        selected.clear();
        ctx.onChange && ctx.onChange();
      } catch (e) { toast(e.message || 'Не удалось удалить', { error: true }); }
      busy = false;
      return load();
    }
    if (!row) return;
    const id = row.dataset.id;
    selected.has(id) ? selected.delete(id) : selected.add(id);
    draw();
  });

  draw();
  load();
}
