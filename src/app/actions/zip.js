// Упаковка выбранного в ZIP.
import { createArchiveFlow } from '../../archive/create-flow.js';
import { parentOf } from '../../path.js';
import { S } from '../state.js';
import { refresh } from '../storage.js';
import { selectedEntries } from '../selection.js';

export async function zipSelected() {
  const sel = selectedEntries();
  if (!sel.length) return;
  const res = await createArchiveFlow({ entries: sel, destDir: S.path || parentOf(sel[0].path) });
  if (res) {
    S.selection.clear();
    refresh();
  }
}
