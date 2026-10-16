// Блоки страницы «О нас», кроме карточки: доступ к файлам и сброс настроек.
import { caption, infoRow, linkRow, group } from '../rows.js';

export const accessBlock = (ctx) => {
  const granted = ctx.hasAccess();
  return (
    caption('Доступ к файлам') +
    group(
      infoRow(
        granted ? 'Доступ выдан' : 'Доступ не выдан',
        granted ? 'FileDeck может читать и менять файлы на устройстве' : 'Без доступа приложение не увидит файлы',
        `<span class="badge ${granted ? 'ok' : 'warn'}">${granted ? 'Вкл' : 'Выкл'}</span>`
      ) + (granted ? '' : linkRow('grant', 'Выдать доступ'))
    )
  );
};

export const resetBlock = () =>
  group(
    `<button class="row danger" data-act="reset"><span class="row-text"><span class="row-label">Сбросить настройки</span><small>Вернуть все настройки по умолчанию</small></span></button>`
  );
