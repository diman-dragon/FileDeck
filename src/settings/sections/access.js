import { openSubscreen } from '../subscreen.js';
import { infoRow, linkRow, group } from '../rows.js';

/** Доступ к файлам: статус разрешения и кнопка запроса. */
export function openAccess(ctx, onUpdate) {
  return openSubscreen({
    title: 'Доступ к файлам',
    ctx,
    onUpdate,
    actions: { grant: () => ctx.requestAccess() },
    body: () => {
      const granted = ctx.hasAccess();
      return group(
        infoRow(
          granted ? 'Доступ выдан' : 'Доступ не выдан',
          granted ? 'FileDeck может читать и менять файлы на устройстве' : 'Без доступа приложение не увидит файлы',
          `<span class="badge ${granted ? 'ok' : 'warn'}">${granted ? 'Вкл' : 'Выкл'}</span>`
        ) + (granted ? '' : linkRow('grant', 'Выдать доступ'))
      );
    },
  });
}
