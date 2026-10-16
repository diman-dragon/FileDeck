// Копировать, вырезать, вставить.
import { FM } from '../../plugin.js';
import { toast } from '../../layers/toast.js';
import { S } from '../state.js';
import { refresh } from '../storage.js';
import { render } from '../render.js';
import { report } from './report.js';

export function toClipboard(mode) {
  S.clip = { mode, paths: [...S.selection] };
  const n = S.clip.paths.length;
  S.selection.clear();
  render({ keepScroll: true });
  toast(`${mode === 'move' ? 'Вырезано' : 'Скопировано'}: ${n}. Откройте нужную папку и нажмите «Вставить сюда».`, { ms: 3600 });
}

export async function paste() {
  if (!S.clip || !S.path) return;
  const { mode, paths } = S.clip;
  toast(mode === 'move' ? 'Переносим…' : 'Копируем…', { ms: 600000 });
  try {
    const res = await (mode === 'move' ? FM.move : FM.copy)({ paths, dest: S.path });
    S.clip = null;
    report(res, mode === 'move' ? 'Перенесено' : 'Скопировано');
  } catch (e) {
    toast(e.message || 'Операция не удалась', { error: true });
  }
  refresh();
}
