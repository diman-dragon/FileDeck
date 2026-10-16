// Экран настроек: вкладки «Общие / Файлы / О нас», страницы переключаются свайпом.
import { icon } from '../icons/icon.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { PAGES } from './pages.js';
import { tabsHtml, bindPager } from './tabs.js';
import { createActions } from './actions.js';

/**
 * ctx: { prefs, save(), onChange(), version, build, native, hasAccess(), requestAccess(),
 *        trashInfo(), openTrash(), openCleaner(), reset() }
 */
export function openSettings(ctx) {
  const { prefs } = ctx;
  const node = el('', 'layer-root layer-settings');
  const close = pushLayer(node);
  const actions = createActions(ctx);

  node.innerHTML = `
    <div class="st-bar">
      <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
      <h1>Настройки</h1>
    </div>
    ${tabsHtml(PAGES)}
    <div class="st-pager">
      ${PAGES.map((p) => `<section class="st-page" data-page="${p.id}" role="tabpanel"><div class="st-inner"></div></section>`).join('')}
    </div>`;

  // перерисовка меняет только содержимое страниц: положение пейджера и прокрутка сохраняются
  const inners = [...node.querySelectorAll('.st-inner')];
  const draw = () => PAGES.forEach((p, i) => (inners[i].innerHTML = p.html(ctx)));
  draw();
  bindPager(node);

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
      return draw();
    }
    ctx.save();
    ctx.onChange();
    draw();
  });

  return close;
}
