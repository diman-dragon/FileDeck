// Системная кнопка «Назад», возврат в приложение, смена вкладки.
import { App as CapApp } from '@capacitor/app';
import { handleBack } from '../layers/stack.js';
import { S, inSearch } from './state.js';
import { checkPermission } from './permission.js';
import { loadRoots, refreshTrash } from './storage.js';
import { go } from './navigation.js';
import { render } from './render.js';
import { exitSearch } from './search.js';

function onResume() {
  if (!S.perm) checkPermission();
  else { loadRoots().then(() => render({ keepScroll: true })); refreshTrash(); }
}

function onBackButton() {
  if (handleBack()) return;
  if (S.selection.size) { S.selection.clear(); return render({ keepScroll: true }); }
  if (inSearch()) return exitSearch();
  // шаг назад по истории; на стартовом экране (история пуста) остаёмся, приложение не закрываем
  if (S.history.length) go(S.history.pop(), { replace: true });
}

// Подписки идут по очереди: параллельные вызовы в веб-превью создают два экземпляра плагина App.
(async () => {
  await CapApp.addListener('backButton', onBackButton);
  // Возврат с экрана системных настроек — перепроверяем разрешение
  await CapApp.addListener('resume', onResume);
})();

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && S.perm === false) checkPermission();
});
