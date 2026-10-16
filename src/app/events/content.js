// Нажатия, долгое нажатие и прокрутка в области содержимого.
import { S } from '../state.js';
import { content } from '../dom-refs.js';
import { requestPermission } from '../permission.js';
import { go, up } from '../navigation.js';
import { openCleanerPage, openTrashPage } from '../pages.js';
import { toggleSelect } from '../selection.js';
import { openEntry } from '../open-entry.js';

// жесты: обычный тап и долгое нажатие
let pressTimer = null;

let longFired = false;

let pressXY = [0, 0];

const entryFromEvent = (ev) => {
  const it = ev.target.closest('.item');
  return it ? S.visible[Number(it.dataset.i)] : null;
};

content.addEventListener('pointerdown', (ev) => {
  const e = entryFromEvent(ev);
  longFired = false;
  if (!e) return;
  pressXY = [ev.clientX, ev.clientY];
  pressTimer = setTimeout(() => {
    longFired = true;
    navigator.vibrate?.(12);
    toggleSelect(e.path);
  }, 380);
});

const cancelPress = () => { clearTimeout(pressTimer); pressTimer = null; };

content.addEventListener('pointermove', (ev) => {
  if (pressTimer && Math.hypot(ev.clientX - pressXY[0], ev.clientY - pressXY[1]) > 10) cancelPress();
});
['pointerup', 'pointercancel', 'pointerleave', 'scroll'].forEach((t) => content.addEventListener(t, cancelPress, { passive: true }));

content.addEventListener('contextmenu', (ev) => ev.preventDefault());

content.addEventListener('click', (ev) => {
  const actEl = ev.target.closest('[data-act]');
  const act = actEl?.dataset.act;
  if (act === 'grant') return requestPermission();
  if (act === 'up') return up();
  if (act === 'open-cleaner') return openCleanerPage();
  if (act === 'open-trash') return openTrashPage();
  if (longFired) { longFired = false; return; }

  const pathBtn = ev.target.closest('[data-path]');
  if (pathBtn && !pathBtn.classList.contains('item')) return go(pathBtn.dataset.path);

  const e = entryFromEvent(ev);
  if (!e) return;
  if (act === 'tick' || S.selection.size > 0) return toggleSelect(e.path);
  openEntry(e);
});
