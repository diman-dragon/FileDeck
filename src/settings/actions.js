// Действия кнопок настроек (всё, что не просто переключение значения).
import { toast } from '../layers/toast.js';
import { confirmDialog } from '../layers/confirm-dialog.js';

export function createActions(ctx) {
  return {
    'open-trash': async () => ctx.openTrash(),
    'open-cleaner': async () => ctx.openCleaner(),
    grant: async () => ctx.requestAccess(),
    reset: async () => {
      const ok = await confirmDialog({ title: 'Сбросить настройки?', text: 'Файлы не затрагиваются.', okText: 'Сбросить' });
      if (!ok) return;
      ctx.reset();
      ctx.save();
      ctx.onChange();
      toast('Настройки сброшены');
    },
  };
}
