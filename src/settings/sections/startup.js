import { openSubscreen } from '../subscreen.js';
import { STARTS } from '../options.js';
import { choiceRow, group } from '../rows.js';

/** При запуске: какой экран открывать. */
export function openStartup(ctx, onUpdate) {
  const { prefs } = ctx;
  return openSubscreen({
    title: 'При запуске открывать',
    ctx,
    onUpdate,
    body: () => group(STARTS.map(([v, l]) => choiceRow('start', v, l, '', prefs.start === v)).join('')),
  });
}
