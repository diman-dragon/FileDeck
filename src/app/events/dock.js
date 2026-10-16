// Нажатия в нижней панели действий.
import { S } from '../state.js';
import { dock } from '../dom-refs.js';
import { render } from '../render.js';
import { paste, toClipboard } from '../actions/clipboard.js';
import { deletePaths } from '../actions/delete.js';
import { shareSelected } from '../actions/share.js';
import { moreForSelection } from '../actions/selection-menu.js';

dock.addEventListener('click', (ev) => {
  const act = ev.target.closest('[data-act]')?.dataset.act;
  if (!act) return;
  ({
    copy: () => toClipboard('copy'),
    cut: () => toClipboard('move'),
    share: () => shareSelected(),
    delete: () => deletePaths([...S.selection]),
    'more-sel': moreForSelection,
    paste,
    'paste-cancel': () => { S.clip = null; render({ keepScroll: true }); },
  })[act]?.();
});
