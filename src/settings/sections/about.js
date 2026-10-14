import { openSubscreen } from '../subscreen.js';
import { escapeHtml } from '../../utils.js';
import { group } from '../rows.js';

/** О приложении: логотип, версия, сборка. */
export function openAbout(ctx) {
  return openSubscreen({
    title: 'О приложении',
    ctx,
    body: () =>
      group(
        `<div class="about-logo"><span class="brand-d big" aria-hidden="true"></span></div>
         <div class="about-name">FileDeck</div>
         <div class="about-ver">Версия ${escapeHtml(ctx.version)} · сборка ${escapeHtml(String(ctx.build))}</div>
         <div class="about-by">Сделано в DataLaw</div>
         <div class="about-tech">Capacitor 8 · ${ctx.native ? 'Android' : 'веб-превью'}</div>`,
        'about'
      ),
  });
}
