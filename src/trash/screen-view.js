// Экран корзины целиком: шапка, список или пустое состояние, нижние кнопки.
import { icon } from '../icons/icon.js';
import { formatSize, plural } from '../format.js';
import { rowHtml } from './row-view.js';

export function trashHtml(c) {
  const { items, selected, days, bytes, loading } = c;
  const n = selected.size;
  const dayWord = plural(days, ['день', 'дня', 'дней']);
  let body;
  if (loading) body = '<div class="state"><div class="spinner"></div></div>';
  else if (!items.length) body = `<div class="state"><div class="empty-glyph">${icon('trash', 44)}</div><h1>Корзина пуста</h1><p>Удалённые файлы хранятся здесь ${days} ${dayWord} и могут быть восстановлены.</p></div>`;
  else body = `<div class="list-note">${items.length} ${plural(items.length, ['объект', 'объекта', 'объектов'])} · ${formatSize(bytes) || '0 Б'} · хранение ${days} ${dayWord}</div><div class="items list">${items.map((t, i) => rowHtml(t, i, selected.has(t.id), days)).join('')}</div>`;
  return `
    <div class="cl-bar"><button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button><h1>Корзина</h1>
      ${items.length ? `<button class="btn ghost small" data-act="toggle-all">${n === items.length ? 'Снять всё' : 'Выбрать всё'}</button>` : ''}</div>
    <div class="cl-body list-body">${body}</div>
    <div class="cl-actions">${
      n > 0
        ? `<div class="two"><button class="btn primary big" data-act="restore">Восстановить · ${n}</button><button class="btn danger big" data-act="delete">Удалить</button></div>`
        : items.length
          ? `<button class="btn ghost big" data-act="empty">Очистить корзину</button>`
          : ''
    }</div>`;
}
