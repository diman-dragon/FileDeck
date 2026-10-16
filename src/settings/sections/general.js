// Страница «Общие»: тема оформления и стартовый экран.
import { THEMES, STARTS } from '../options.js';
import { caption, choiceRow, group } from '../rows.js';

export const generalPage = ({ prefs }) =>
  caption('Тема') +
  group(THEMES.map(([v, l, h]) => choiceRow('theme', v, l, h, prefs.theme === v)).join('')) +
  caption('При запуске открывать') +
  group(STARTS.map(([v, l]) => choiceRow('start', v, l, '', prefs.start === v)).join(''));
