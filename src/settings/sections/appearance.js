import { openSubscreen } from '../subscreen.js';
import { THEMES, VIEWS } from '../options.js';
import { choiceRow, segmentRow, group } from '../rows.js';

/** Внешний вид: тема и вид списка. */
export function openAppearance(ctx, onUpdate) {
  const { prefs } = ctx;
  return openSubscreen({
    title: 'Внешний вид',
    ctx,
    onUpdate,
    body: () =>
      group(THEMES.map(([v, l, h]) => choiceRow('theme', v, l, h, prefs.theme === v)).join('')) +
      group(`<div class="row-label seg-title">Вид списка</div>${segmentRow(prefs, 'view', VIEWS)}`, 'pad-seg'),
  });
}
