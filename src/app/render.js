// Общая перерисовка: панель, содержимое, нижняя панель действий.
import { icon } from '../icons/icon.js';
import { S, inSearch } from './state.js';
import { $, content, fab } from './dom-refs.js';
import { renderTopbar } from './views/topbar.js';
import { renderContent } from './views/content.js';
import { renderDock } from './views/dock.js';

export function render({ keepScroll = false } = {}) {
  const scroll = content.scrollTop;
  renderTopbar();
  renderContent();
  renderDock();
  $('app').classList.toggle('selecting', S.selection.size > 0);
  const showFab = S.perm && S.path && !inSearch() && S.selection.size === 0 && !S.error;
  fab.innerHTML = icon('plus', 26);
  fab.classList.toggle('visible', !!showFab);
  fab.classList.toggle('lifted', !!S.clip);
  if (keepScroll) content.scrollTop = scroll;
  else if (S.loading) content.scrollTop = 0;
}
