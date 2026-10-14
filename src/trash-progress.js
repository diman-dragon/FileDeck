// Окно прогресса для операций корзины (восстановление, удаление, очистка).
// Java присылает событие trashProgress { fraction, current }; кнопка «Отмена» вызывает trashCancel.
import { CL, listen } from './plugin.js';
import { progressDialog } from './layers.js';

/** Показывает прогресс на время fn() и закрывает окно, что бы ни случилось. Возвращает результат fn(). */
export async function withTrashProgress(title, fn) {
  const ui = progressDialog({ title, onCancel: () => CL.trashCancel().catch(() => {}) });
  const off = await listen(CL, 'trashProgress', (e) => ui.set(e.fraction, e.current));
  try {
    return await fn();
  } finally {
    off();
    ui.close();
  }
}
