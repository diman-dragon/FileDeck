// Вопрос «да / нет». Возвращает Promise<boolean>.
import { escapeHtml } from '../html.js';
import { dialog } from './dialog.js';

export const confirmDialog = ({ title, text, okText = 'Удалить', danger = true }) =>
  dialog({ title, body: text ? escapeHtml(text) : '', okText, danger });
