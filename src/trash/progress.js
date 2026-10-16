// Окно прогресса для операций корзины (восстановление, удаление, очистка).
// Java присылает событие progress плагина Trash { fraction, current }; кнопка «Отмена» вызывает cancel.
import { TR } from '../plugin.js';
import { listen } from '../listen.js';
import { progressDialog } from '../layers/progress-dialog.js';

/** Показывает прогресс на время fn() и закрывает окно, что бы ни случилось. Возвращает результат fn(). */
export async function withTrashProgress(title, fn) {
  const ui = progressDialog({ title, onCancel: () => TR.cancel().catch(() => {}) });
  const off = await listen(TR, 'progress', (e) => ui.set(e.fraction, e.current));
  try {
    return await fn();
  } finally {
    off();
    ui.close();
  }
}
