// Выделение файлов.
import { S } from './state.js';
import { render } from './render.js';

export function toggleSelect(path) {
  S.selection.has(path) ? S.selection.delete(path) : S.selection.add(path);
  render({ keepScroll: true });
}

export function selectAll() {
  const all = S.visible;
  if (S.selection.size === all.length) S.selection.clear();
  else all.forEach((e) => S.selection.add(e.path));
  render({ keepScroll: true });
}

export const selectedEntries = () => S.visible.filter((e) => S.selection.has(e.path));
