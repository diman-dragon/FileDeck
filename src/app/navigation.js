// Переходы по папкам и накопителям, история для системной кнопки «Назад».
import { toast } from '../layers/toast.js';
import { parentOf } from '../path.js';
import { S, inSearch, primaryRoot, rootOf } from './state.js';
import { content } from './dom-refs.js';
import { load, loadRoots } from './storage.js';
import { render } from './render.js';

const HISTORY_LIMIT = 100;

/** Запоминает, откуда ушли. Переход на место, с которого пришли, считается шагом назад. */
function trackHistory(path) {
  const top = S.history[S.history.length - 1];
  if (S.history.length && top === path) S.history.pop();
  else if (S.path !== path) {
    S.history.push(S.path);
    if (S.history.length > HISTORY_LIMIT) S.history.shift();
  }
}

/** replace: true — не записывать в историю (стартовый экран и шаг назад). */
export function go(path, { replace = false } = {}) {
  if (!replace) trackHistory(path);
  if (S.path) S.scrollMemo.set(S.path, content.scrollTop);
  S.search = null;
  S.selection.clear();
  S.path = path;
  S.entries = [];
  if (path) {
    const r = rootOf(path);
    if (r) S.lastIn[r.path] = path;
  }
  if (path === null) {
    loadRoots().then(render);
    render();
    return;
  }
  load(path).then(() => {
    const y = S.scrollMemo.get(path);
    if (y != null) content.scrollTop = y;
  });
}

export function up() {
  if (!S.path) return false;
  const root = rootOf(S.path);
  if (!root || S.path === root.path) go(null);
  else go(parentOf(S.path));
  return true;
}

export function switchRoot(which) {
  if (!S.perm) return toast('Сначала выдайте доступ к файлам', { error: true });
  const root = which === 'sd' ? S.roots.find((r) => !r.primary) : primaryRoot();
  if (!root) return toast('SD-карта не найдена. Вставьте карту и вернитесь в приложение', { error: true });
  const cur = S.path ? rootOf(S.path) : null;
  if (cur && cur.path === root.path && S.path !== root.path && !inSearch()) return go(root.path); // повторный тап — в корень
  go(S.lastIn[root.path] || root.path);
}
