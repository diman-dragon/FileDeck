// Создание папки или файла.
import { FM } from '../../plugin.js';
import { toast } from '../../layers/toast.js';
import { promptDialog } from '../../layers/prompt-dialog.js';
import { join } from '../../path.js';
import { S } from '../state.js';
import { refresh } from '../storage.js';

export async function createItem(isDir) {
  const name = await promptDialog({
    title: isDir ? 'Новая папка' : 'Новый файл',
    placeholder: isDir ? 'Название папки' : 'заметка.txt',
    value: isDir ? '' : 'Новый файл.txt',
    okText: 'Создать',
    selectBase: !isDir,
  });
  if (!name || !name.trim()) return;
  if (name.includes('/')) return toast('В имени нельзя использовать «/»', { error: true });
  try {
    const path = join(S.path, name.trim());
    await (isDir ? FM.mkdir : FM.createFile)({ path });
    toast(isDir ? 'Папка создана' : 'Файл создан');
  } catch (e) {
    toast(e.message || 'Не удалось создать', { error: true });
  }
  refresh();
}
