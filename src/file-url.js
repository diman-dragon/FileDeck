// Адрес файла для <img> и <video>: на телефоне его отдаёт локальный сервер Capacitor.
import { Capacitor } from '@capacitor/core';
import { isNative } from './platform.js';
import { mockThumb } from './mock/thumb.js';

export function fileUrl(path) {
  if (!isNative) return mockThumb(path);
  return Capacitor.convertFileSrc('file://' + path.split('/').map(encodeURIComponent).join('/'));
}
