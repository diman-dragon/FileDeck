// Где работает приложение: на телефоне (Capacitor) или в браузере (превью).
import { Capacitor } from '@capacitor/core';

export const isNative = Capacitor.isNativePlatform();
