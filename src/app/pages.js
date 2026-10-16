// Открытие полноэкранных страниц: очистка, корзина, настройки.
import { isNative } from '../platform.js';
import { toast } from '../layers/toast.js';
import { openImageViewer } from '../viewer/image-viewer.js';
import { openSettings } from '../settings/index.js';
import { openCleaner } from '../cleaner/index.js';
import { openTrash } from '../trash/index.js';
import { APP_BUILD, APP_VERSION } from './version.js';
import { DEFAULTS, prefs, savePrefs } from './prefs.js';
import { S } from './state.js';
import { applyTheme } from './theme.js';
import { requestPermission } from './permission.js';
import { loadRoots, refresh, refreshTrash } from './storage.js';
import { go } from './navigation.js';
import { render } from './render.js';

export function openCleanerPage() {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  openCleaner({
    roots: S.roots,
    prefs,
    savePrefs,
    locate: (path) => go(path),
    viewImage: (e) => openImageViewer([e], 0),
    onChange: () => { refreshTrash(); S.path ? refresh() : loadRoots().then(() => render({ keepScroll: true })); },
  });
}

export function openTrashPage() {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  openTrash({ prefs, onChange: () => { refreshTrash(); S.path ? refresh() : loadRoots().then(() => render({ keepScroll: true })); } });
}

export function openSettingsPage() {
  openSettings({
    prefs,
    save: savePrefs,
    onChange: () => { applyTheme(); render({ keepScroll: true }); },
    version: APP_VERSION,
    build: APP_BUILD,
    native: isNative,
    hasAccess: () => !!S.perm,
    requestAccess: requestPermission,
    trashInfo: () => S.trash,
    openTrash: openTrashPage,
    openCleaner: openCleanerPage,
    reset: () => { Object.assign(prefs, JSON.parse(DEFAULTS)); savePrefs(); },
  });
}
