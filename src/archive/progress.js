// Выполнение операции архиватора с окном прогресса и отменой.
import { AR } from '../plugin.js';
import { listen } from '../listen.js';
import { progressDialog } from '../layers/progress-dialog.js';

/** Показывает окно прогресса на время операции с подпиской на события плагина. */
export async function withProgress(title, fn) {
  const ui = progressDialog({ title, onCancel: () => AR.cancel().catch(() => {}) });
  let off = () => {};
  try {
    off = await listen(AR, 'archiveProgress', (e) => ui.set(e.fraction, e.current));
    return await fn();
  } finally {
    off();
    ui.close();
  }
}
