// Короткие подписи под пунктами главного меню настроек.
import { formatSize } from '../format.js';
import { THEMES, SORTS, STARTS, labelOf } from './options.js';

export const trashSummary = (ctx) => {
  const t = ctx.trashInfo ? ctx.trashInfo() : null;
  return t && t.count ? `${t.count} · ${formatSize(t.bytes) || '0 Б'}` : 'Пусто';
};

export const appearanceSummary = ({ prefs }) => labelOf(THEMES, prefs.theme);

export const filesSummary = ({ prefs }) => `${labelOf(SORTS, prefs.sortBy)}${prefs.hidden ? ' · скрытые видны' : ''}`;

export const startupSummary = ({ prefs }) => labelOf(STARTS, prefs.start);

export const accessSummary = (ctx) => (ctx.hasAccess() ? 'Доступ выдан' : 'Доступ не выдан');

export const aboutSummary = (ctx) => `Версия ${ctx.version}`;
