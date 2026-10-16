// Сортировка списка файлов: папки всегда сверху.
import { extOf } from './path.js';

const collator = new Intl.Collator('ru', { numeric: true, sensitivity: 'base' });
export const compareNames = (a, b) => collator.compare(a, b);

export function sortEntries(list, { by, desc }) {
  const dir = desc ? -1 : 1;
  const cmp = {
    name: (a, b) => compareNames(a.name, b.name),
    size: (a, b) => (a.dir && b.dir ? (a.count || 0) - (b.count || 0) : a.size - b.size) || compareNames(a.name, b.name),
    date: (a, b) => a.mtime - b.mtime || compareNames(a.name, b.name),
    type: (a, b) => compareNames(extOf(a.name), extOf(b.name)) || compareNames(a.name, b.name),
  }[by];
  return [...list].sort((a, b) => {
    if (a.dir !== b.dir) return a.dir ? -1 : 1; // папки всегда сверху
    return cmp(a, b) * dir;
  });
}
