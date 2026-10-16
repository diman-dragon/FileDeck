import { openSubscreen } from '../subscreen.js';
import { THEMES } from '../options.js';
import { choiceRow, group } from '../rows.js';

/** Внешний вид: тема. Вид списка переключается кнопкой на каждом экране. */
export function openAppearance(ctx, onUpdate) {
  const { prefs } = ctx;
  return openSubscreen({
    title: 'Внешний вид',
    ctx,
    onUpdate,
    body: () => group(THEMES.map(([v, l, h]) => choiceRow('theme', v, l, h, prefs.theme === v)).join('')),
  });
}
