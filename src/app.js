// Точка входа: запуск приложения.
import { applyTheme } from './app/theme.js';
import { checkPermission } from './app/permission.js';
import { render } from './app/render.js';
import './app/events/topbar.js';
import './app/events/fab.js';
import './app/events/dock.js';
import './app/events/content.js';
import './app/lifecycle.js';

async function init() {
  applyTheme();
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', applyTheme);
  render();
  await checkPermission();
}

export { init };
