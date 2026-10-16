// Выбор и применение темы оформления.
import { SystemBars, SystemBarsStyle } from '@capacitor/core';
import { isNative } from '../platform.js';
import { prefs } from './prefs.js';

const resolveTheme = () => (prefs.theme === 'auto' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'amoled') : prefs.theme);

export function applyTheme() {
  const t = resolveTheme();
  document.documentElement.dataset.theme = t;
  if (isNative) SystemBars.setStyle({ style: t === 'light' ? SystemBarsStyle.Light : SystemBarsStyle.Dark }).catch(() => {});
}
