// Универсальный экран настроек: шапка «назад + заголовок», прокручиваемое тело и разбор нажатий.
// Системная кнопка «Назад» закрывает верхний экран сама — все экраны живут в общем стеке слоёв.
import { icon } from '../icons/icon.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';

/**
 * title    — заголовок в шапке
 * ctx      — { prefs, save(), onChange() }
 * body()   — возвращает HTML тела; вызывается при каждой перерисовке
 * actions  — { имя: async () => {} } для кнопок с data-act="имя"
 * onUpdate — вызывается после любого изменения (чтобы родительский экран обновил подписи)
 * Возвращает { redraw, close }.
 */
export function openSubscreen({ title, ctx, body, actions = {}, onUpdate }) {
  const { prefs } = ctx;
  const node = el('', 'layer-root layer-settings');
  const close = pushLayer(node);

  const draw = () => {
    node.innerHTML = `
      <div class="st-bar">
        <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
        <h1>${title}</h1>
      </div>
      <div class="st-body">${body()}</div>`;
  };
  const redraw = () => {
    const y = node.querySelector('.st-body')?.scrollTop || 0;
    draw();
    node.querySelector('.st-body').scrollTop = y;
  };
  draw();

  node.addEventListener('click', async (ev) => {
    const t = ev.target.closest('[data-toggle],[data-set],[data-act]');
    if (!t) return;
    const d = t.dataset;
    if (d.act === 'back') return close();
    if (d.toggle) {
      prefs[d.toggle] = !prefs[d.toggle];
    } else if (d.set) {
      prefs[d.set] = d.num === 'true' ? Number(d.val) : d.val;
    } else {
      const run = actions[d.act];
      if (!run) return;
      await run();
      redraw();
      onUpdate?.();
      return;
    }
    ctx.save();
    ctx.onChange();
    redraw();
    onUpdate?.();
  });

  return { redraw, close };
}
