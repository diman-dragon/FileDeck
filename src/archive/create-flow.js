// Создание архива из выбранных файлов.
import { AR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { formatSize } from '../format.js';
import { basename } from '../path.js';
import { explain } from './errors.js';
import { withProgress } from './progress.js';
import { createOptionsDialog } from './create-dialog.js';

/** entries: [{ path, name, dir }], destDir — куда положить архив. Возвращает результат или null. */
export async function createArchiveFlow({ entries, destDir }) {
  const first = entries[0];
  const defaultName = entries.length === 1 ? (first.dir ? first.name : first.name.replace(/\.[^.]+$/, '')) : 'Архив';
  const opts = await createOptionsDialog({ defaultName, count: entries.length });
  if (!opts) return null;
  try {
    const res = await withProgress('Создаём архив…', () =>
      AR.create({ paths: entries.map((e) => e.path), dest: destDir, name: opts.name, format: opts.format, level: opts.level, password: opts.password })
    );
    if (res.cancelled) {
      toast('Отменено');
      return null;
    }
    toast(`Архив создан: ${basename(res.path)} · ${formatSize(res.size)}`);
    return res;
  } catch (e) {
    toast(explain(e), { error: true });
    return null;
  }
}
