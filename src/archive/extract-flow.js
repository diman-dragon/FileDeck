// Распаковка архива.
import { AR } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { promptDialog } from '../layers/prompt-dialog.js';
import { plural } from '../format.js';
import { basename } from '../path.js';
import { explain } from './errors.js';
import { withProgress } from './progress.js';

/** mode: 'folder' — в новую папку с именем архива, 'here' — прямо в dest. entries — только выбранное. */
export async function extractFlow({ path, dest, mode = 'folder', entries = [], password = '' }) {
  let pw = password;
  for (;;) {
    try {
      const res = await withProgress('Распаковываем…', () => AR.extract({ path, dest, mode, entries, password: pw }));
      if (res.cancelled) {
        toast('Отменено');
        return null;
      }
      toast(`Распаковано: ${res.count} ${plural(res.count, ['файл', 'файла', 'файлов'])}`);
      return { ...res, password: pw };
    } catch (e) {
      if (e.code === 'PASSWORD_REQUIRED' || e.code === 'WRONG_PASSWORD') {
        const typed = await promptDialog({
          title: 'Пароль архива',
          text: basename(path),
          type: 'password',
          okText: 'Продолжить',
          error: e.code === 'WRONG_PASSWORD' ? 'Неверный пароль. Попробуйте ещё раз.' : '',
        });
        if (typed == null || typed === '') return null;
        pw = typed;
        continue;
      }
      toast(explain(e), { error: true });
      return null;
    }
  }
}
