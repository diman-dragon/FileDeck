// Экран «Очистка»: слой, состояние и перерисовка. Остальное — в соседних файлах.
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { DEFAULT_CLEANER } from './config.js';
import { cancelScan, updateProgress } from './scan.js';
import { bindEvents } from './events.js';
import { setupHtml } from './views/setup-view.js';
import { scanningHtml } from './views/scanning-view.js';
import { summaryHtml } from './views/summary-view.js';
import { detailHtml } from './views/detail-view.js';

export { DEFAULT_CLEANER };

/**
 * ctx: { roots, prefs, savePrefs, locate(path), onChange(), viewImage(entry) }
 * Общее состояние экрана хранится в объекте c и передаётся в функции соседних файлов.
 */
export function openCleaner(ctx) {
  const { prefs } = ctx;
  prefs.cleaner = Object.assign({}, DEFAULT_CLEANER, prefs.cleaner || {});
  const c = {
    ctx,
    o: prefs.cleaner,
    node: el('', 'layer-root layer-cleaner'),
    state: { view: 'setup', cat: null, result: null, sel: {}, progress: null, busy: false },
    secondary: ctx.roots.find((r) => !r.primary),
    primary: ctx.roots.find((r) => r.primary) || ctx.roots[0],
    offProgress: null,
    closed: false,
  };

  const closeLayer = pushLayer(c.node, { onBack: () => c.back() });
  c.close = () => {
    c.closed = true;
    c.offProgress && c.offProgress();
    closeLayer();
  };

  c.back = () => {
    if (c.state.view === 'detail') {
      c.state.view = 'summary';
      return c.draw(true);
    }
    if (c.state.view === 'scanning') return cancelScan();
    c.close();
  };

  c.draw = (resetScroll = false) => {
    if (c.closed) return;
    const y = resetScroll ? 0 : c.node.querySelector('.cl-body')?.scrollTop || 0;
    c.node.innerHTML = { setup: setupHtml, scanning: scanningHtml, summary: summaryHtml, detail: detailHtml }[c.state.view](c);
    const body = c.node.querySelector('.cl-body');
    if (body && c.state.view !== 'scanning') body.scrollTop = y;
    if (c.state.view === 'scanning') updateProgress(c);
  };

  bindEvents(c);
  c.draw();
}
