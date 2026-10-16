// Сканирование: запуск, отмена, живое обновление прогресса.
import { CL } from '../plugin.js';
import { listen } from '../listen.js';
import { toast } from '../layers/toast.js';
import { formatSize } from '../format.js';
import { CATS } from './config.js';
import { defaultSelection } from './rows.js';

export async function startScan(c) {
  const { o, ctx, state, primary, secondary } = c;
  const roots = [];
  if (o.primary && primary) roots.push(primary.path);
  if (o.sd && secondary) roots.push(secondary.path);
  if (!roots.length) return toast('Выберите, где искать', { error: true });
  if (!CATS.some((x) => o[x.opt])) return toast('Выберите, что искать', { error: true });
  ctx.savePrefs();
  state.view = 'scanning';
  state.progress = { phase: 'walk', files: 0, bytes: 0, done: 0, total: 0, current: '' };
  c.draw();
  c.offProgress = await listen(CL, 'scanProgress', (e) => {
    state.progress = e;
    updateProgress(c);
  });
  try {
    const res = await CL.scan({
      roots, duplicates: o.duplicates, similar: o.similar, junk: o.junk, apks: o.apks, largest: o.largest,
      emptyFolders: o.emptyFolders, zeroFiles: o.zeroFiles, appFolders: o.appFolders,
    });
    if (c.closed) return;
    if (res.cancelled) {
      toast('Сканирование отменено');
      state.view = 'setup';
    } else {
      state.result = res;
      state.sel = defaultSelection(res);
      state.view = 'summary';
    }
  } catch (e) {
    if (c.closed) return;
    toast(e.message || 'Не удалось выполнить сканирование', { error: true });
    state.view = 'setup';
  } finally {
    c.offProgress && c.offProgress();
    c.offProgress = null;
  }
  c.draw();
}
export const cancelScan = () => CL.cancelScan().catch(() => {});

export function updateProgress(c) {
  const { state } = c;
  if (state.view !== 'scanning') return;
  const p = state.progress;
  const phaseText = { walk: 'Просматриваем файлы…', hash: 'Сравниваем содержимое…', images: 'Анализируем фотографии…' }[p.phase] || 'Работаем…';
  const frac = p.total > 0 ? Math.min(1, p.done / p.total) : 0;
  const q = (s) => c.node.querySelector(s);
  if (!q('.scan-phase')) return;
  q('.scan-phase').textContent = phaseText;
  q('.scan-files').textContent = `${(p.files || 0).toLocaleString('ru')}`;
  q('.scan-bytes').textContent = formatSize(p.bytes || 0) || '0 Б';
  q('.scan-cur').textContent = p.current ? shorten(p.current) : '';
  const bar = q('.pbar');
  bar.classList.toggle('indeterminate', p.phase === 'walk' || !(p.total > 0));
  q('.pbar i').style.width = p.phase === 'walk' || !(p.total > 0) ? '' : `${(frac * 100).toFixed(1)}%`;
}
const shorten = (s) => (s.length > 52 ? '…' + s.slice(-50) : s);
