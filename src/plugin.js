import { Capacitor, registerPlugin } from '@capacitor/core';
import { createMock, mockThumb } from './mock-fs.js';

export const isNative = Capacitor.isNativePlatform();

/** Нативный плагин (Java: FileManagerPlugin). В браузере подставляется мок. */
export const FM = registerPlugin('FileManager', {
  web: async () => createMock(),
});

/** URL для <img>/<video>: на устройстве файл отдаёт локальный сервер Capacitor. */
export function fileUrl(path) {
  if (!isNative) return mockThumb(path);
  return Capacitor.convertFileSrc('file://' + path.split('/').map(encodeURIComponent).join('/'));
}
