// Окно «Свойства».
import { FM } from '../../plugin.js';
import { infoDialog } from '../../layers/info-dialog.js';
import { parentOf, extOf } from '../../path.js';
import { formatSize, formatDate, plural } from '../../format.js';
import { kindOf, KIND_LABEL } from '../../file-kinds.js';

export async function showProps(e) {
  const kind = kindOf(e);
  const dlg = infoDialog({
    title: e.name,
    rows: [
      ['Тип', KIND_LABEL[kind] + (e.dir ? '' : extOf(e.name) ? ` (.${extOf(e.name)})` : '')],
      ['Путь', parentOf(e.path)],
      ['Размер', e.dir ? 'считаем…' : `${formatSize(e.size)} (${e.size.toLocaleString('ru')} Б)`],
      ...(e.dir ? [['Содержимое', 'считаем…']] : []),
      ['Изменён', formatDate(e.mtime, true)],
    ],
  });
  if (e.dir) {
    try {
      const r = await FM.size({ path: e.path });
      dlg.set('Размер', formatSize(r.size));
      dlg.set('Содержимое', `${r.files} ${plural(r.files, ['файл', 'файла', 'файлов'])}, ${r.folders} ${plural(r.folders, ['папка', 'папки', 'папок'])}`);
    } catch {
      dlg.set('Размер', 'не удалось посчитать');
      dlg.set('Содержимое', '—');
    }
  }
}
