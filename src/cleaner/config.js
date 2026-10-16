// Категории очистки, подписи и палитра диаграммы занятого места.
export const DEFAULT_CLEANER = {
  primary: true, sd: true, duplicates: true, similar: false, junk: true, apks: true,
  largest: true, emptyFolders: true, zeroFiles: true, appFolders: false,
};

export const CATS = [
  { id: 'duplicates', opt: 'duplicates', title: 'Дубликаты', icon: 'copy', hint: 'Одинаковые файлы по содержимому, даже с разными именами', group: true, safe: true },
  { id: 'similar', opt: 'similar', title: 'Похожие фото', icon: 'image', hint: 'Серии кадров и копии разного размера · сканирование дольше', group: true, safe: false },
  { id: 'junk', opt: 'junk', title: 'Мусор и кэш', icon: 'trash', hint: 'Временные файлы, логи, миниатюры, недокачанное', safe: true },
  { id: 'apks', opt: 'apks', title: 'Установщики APK', icon: 'apk', hint: 'Файлы приложений, которые уже не нужны', safe: false },
  { id: 'largest', opt: 'largest', title: 'Самые большие файлы', icon: 'drive', hint: 'Что занимает больше всего места', safe: false },
  { id: 'emptyFolders', opt: 'emptyFolders', title: 'Пустые папки', icon: 'folder', hint: 'Папки без единого файла', safe: true },
  { id: 'zeroFiles', opt: 'zeroFiles', title: 'Пустые файлы', icon: 'file', hint: 'Файлы размером 0 байт', safe: true },
];

export const JUNK_LABEL = {
  cache: 'Кэш', temp: 'Временные', thumbnails: 'Миниатюры', logs: 'Логи', trashed: 'Из системной корзины',
  system: 'Служебные', trash: 'Корзина приложения', messenger: 'Статусы мессенджера', partial: 'Недокачанное',
};
export const USAGE = [
  ['images', 'Фото', 'var(--k-image)'], ['video', 'Видео', 'var(--k-video)'], ['audio', 'Музыка', 'var(--k-audio)'],
  ['docs', 'Документы', 'var(--k-doc)'], ['archives', 'Архивы', 'var(--k-archive)'], ['apk', 'APK', 'var(--k-apk)'], ['other', 'Другое', 'var(--k-file)'],
];
