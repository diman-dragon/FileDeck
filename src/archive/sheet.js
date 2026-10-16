// Меню действий для файла-архива.
import { FM } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { sheet } from '../layers/sheet.js';
import { formatSize } from '../format.js';
import { parentOf } from '../path.js';
import { archiveBase } from '../archive-types.js';
import { extractFlow } from './extract-flow.js';
import { openArchiveBrowser } from './browser.js';

/** Лист действий для архива: просмотр / распаковка. */
export function openArchiveSheet(entry, { onDone, onLocate } = {}) {
  const dest = parentOf(entry.path);
  const rar = /\.rar$/i.test(entry.name);
  const items = [
    { icon: 'layers', label: 'Просмотреть содержимое', hint: rar ? 'Для RAR список недоступен — распаковка целиком' : 'Выбрать, что распаковать', onClick: () => openArchiveBrowser(entry, { onDone, onLocate }) },
    { icon: 'unzip', label: `Распаковать в папку «${archiveBase(entry.name)}»`, onClick: async () => { if (await extractFlow({ path: entry.path, dest, mode: 'folder' })) onDone && onDone(); } },
  ];
  if (!rar) items.push({ icon: 'download', label: 'Распаковать сюда', hint: 'Файлы окажутся рядом с архивом', onClick: async () => { if (await extractFlow({ path: entry.path, dest, mode: 'here' })) onDone && onDone(); } });
  items.push({ icon: 'external', label: 'Открыть в другом приложении', onClick: () => FM.open({ path: entry.path }).catch((e) => toast(e.message, { error: true })) });
  sheet({ title: entry.name, subtitle: formatSize(entry.size), items });
}
