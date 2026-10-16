// Открытие файла или папки по нажатию.
import { FM } from '../plugin.js';
import { toast } from '../layers/toast.js';
import { sheet } from '../layers/sheet.js';
import { openImageViewer } from '../viewer/image-viewer.js';
import { openEditor } from '../viewer/text-editor.js';
import { openArchiveSheet } from '../archive/sheet.js';
import { formatSize } from '../format.js';
import { kindOf, isEditable } from '../file-kinds.js';
import { isArchive } from '../archive-types.js';
import { S } from './state.js';
import { refresh } from './storage.js';
import { go } from './navigation.js';

export function openEntry(e) {
  const kind = kindOf(e);
  if (e.dir) return go(e.path);
  if (kind === 'image') {
    const images = S.visible.filter((x) => kindOf(x) === 'image');
    return openImageViewer(images, Math.max(0, images.findIndex((x) => x.path === e.path)));
  }
  if (isArchive(e.name)) return openArchiveSheet(e, { onDone: refresh, onLocate: go });
  if (isEditable(e)) {
    return sheet({
      title: e.name,
      subtitle: formatSize(e.size),
      items: [
        { icon: 'edit', label: 'Открыть в редакторе', onClick: () => openEditor(e, { onSaved: refresh }) },
        { icon: 'external', label: 'Открыть в приложении', onClick: () => openExternal(e) },
      ],
    });
  }
  openExternal(e);
}

export const openExternal = (e) => FM.open({ path: e.path }).catch((err) => toast(err.message, { error: true }));
