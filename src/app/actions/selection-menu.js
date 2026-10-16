// Меню «Ещё» для выделенных файлов.
import { sheet } from '../../layers/sheet.js';
import { openEditor } from '../../viewer/text-editor.js';
import { openArchiveSheet } from '../../archive/sheet.js';
import { isEditable } from '../../file-kinds.js';
import { isArchive } from '../../archive-types.js';
import { prefs } from '../prefs.js';
import { S } from '../state.js';
import { refresh } from '../storage.js';
import { go } from '../navigation.js';
import { render } from '../render.js';
import { selectedEntries } from '../selection.js';
import { openExternal } from '../open-entry.js';
import { deletePaths } from './delete.js';
import { renameEntry } from './rename.js';
import { zipSelected } from './zip.js';
import { showProps } from './properties.js';

export function moreForSelection() {
  const sel = selectedEntries();
  const one = sel.length === 1 ? sel[0] : null;
  const items = [];
  if (one) items.push({ icon: 'edit', label: 'Переименовать', onClick: () => renameEntry(one) });
  items.push({ icon: 'archive', label: 'Добавить в архив…', hint: 'ZIP, 7z, TAR · пароль, уровень сжатия', onClick: zipSelected });
  if (one && isArchive(one.name)) items.push({ icon: 'unzip', label: 'Распаковать…', hint: 'Просмотр содержимого и выбор файлов', onClick: () => { S.selection.clear(); render({ keepScroll: true }); openArchiveSheet(one, { onDone: refresh, onLocate: go }); } });
  if (one && !one.dir) items.push({ icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(one) });
  if (one && isEditable(one)) items.push({ icon: 'doc', label: 'Открыть в редакторе', onClick: () => openEditor(one, { onSaved: refresh }) });
  if (one) items.push({ icon: 'info', label: 'Свойства', onClick: () => showProps(one) });
  if (prefs.useTrash) items.push({ divider: true }, { icon: 'trash', label: 'Удалить навсегда', danger: true, onClick: () => deletePaths(sel.map((e) => e.path), { permanent: true }) });
  sheet({ title: one ? one.name : `Выбрано: ${sel.length}`, items });
}
