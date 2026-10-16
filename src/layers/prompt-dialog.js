// Диалог с одним полем ввода. Возвращает Promise<string | null>.
import { escapeHtml } from '../html.js';
import { dialog } from './dialog.js';

export const promptDialog = ({ title, text = '', value = '', placeholder = '', okText = 'Готово', selectBase = false, type = 'text', error = '' }) =>
  dialog({ title, body: text ? escapeHtml(text) : '', okText, withInput: { value, placeholder, selectBase, type, error } });
