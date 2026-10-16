// Отправка выбранных файлов в другие приложения.
import { FM } from '../../plugin.js';
import { toast } from '../../layers/toast.js';
import { selectedEntries } from '../selection.js';

export async function shareSelected() {
  const paths = selectedEntries().filter((e) => !e.dir).map((e) => e.path);
  if (!paths.length) return toast('Поделиться можно только файлами, не папками', { error: true });
  try { await FM.share({ paths }); } catch (e) { toast(e.message, { error: true }); }
}
