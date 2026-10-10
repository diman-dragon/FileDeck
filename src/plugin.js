import { Capacitor, registerPlugin } from '@capacitor/core';
import { createMock, createMockCleaner, createMockArchive, mockThumb } from './mock-fs.js';

export const isNative = Capacitor.isNativePlatform();

/** Файлы: список, копирование, поиск, чтение/запись текста (Java: FileManagerPlugin). В браузере — мок. */
export const FM = registerPlugin('FileManager', { web: async () => createMock() });

/** Глубокая очистка и корзина (Java: CleanerPlugin). */
export const CL = registerPlugin('Cleaner', { web: async () => createMockCleaner() });

/** Архиватор: ZIP / 7z / TAR / RAR… (Java: ArchivePlugin). */
export const AR = registerPlugin('Archive', { web: async () => createMockArchive() });

/** URL для <img>/<video>: на устройстве файл отдаёт локальный сервер Capacitor. */
export function fileUrl(path) {
  if (!isNative) return mockThumb(path);
  return Capacitor.convertFileSrc('file://' + path.split('/').map(encodeURIComponent).join('/'));
}

/** Подписка на событие плагина; возвращает функцию отписки. */
export async function listen(plugin, event, cb) {
  const handle = await plugin.addListener(event, cb);
  return () => handle.remove();
}
