// Линейная иконка из набора ICON_PATHS. Неизвестное имя рисуется как «file».
import { ICON_PATHS } from './paths.js';

export function icon(name, size = 22, cls = '') {
  const body = ICON_PATHS[name] || ICON_PATHS.file;
  return `<svg class="ic ${cls}" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${body}</svg>`;
}
