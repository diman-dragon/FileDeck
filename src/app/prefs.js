// Настройки пользователя: значения по умолчанию, чтение и сохранение в localStorage.

const PREFS_KEY = 'filedeck.prefs.v1';

const BASE_PREFS = { view: 'list', sortBy: 'name', desc: false, hidden: false, theme: 'auto', confirmDelete: true, start: 'home', useTrash: true, trashDays: 30, cleaner: {} };

export const prefs = Object.assign(
  {},
  BASE_PREFS,
  (() => { try { return JSON.parse(localStorage.getItem(PREFS_KEY)) || {}; } catch { return {}; } })()
);
if (prefs.theme === 'dark') prefs.theme = 'amoled'; // миграция со старой версии

export const DEFAULTS = JSON.stringify(BASE_PREFS);

export const savePrefs = () => { try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch { /* ignore */ } };
