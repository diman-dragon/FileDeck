// Переименование.
import { FM } from '../../plugin.js';
import { toast } from '../../layers/toast.js';
import { promptDialog } from '../../layers/prompt-dialog.js';
import { S } from '../state.js';
import { refresh } from '../storage.js';

export async function renameEntry(e) {
  const name = await promptDialog({ title: 'Переименовать', value: e.name, okText: 'Переименовать', selectBase: !e.dir });
  if (name == null || name.trim() === '' || name === e.name) return;
  try {
    await FM.rename({ path: e.path, name });
    S.selection.clear();
    toast('Переименовано');
  } catch (err) {
    toast(err.message || 'Не удалось переименовать', { error: true });
  }
  refresh();
}
