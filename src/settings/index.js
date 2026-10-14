// Главный экран настроек: список разделов. Каждый раздел открывается отдельным подэкраном.
import { toast, confirmDialog } from '../layers.js';
import { openSubscreen } from './subscreen.js';
import { menuRow, group } from './rows.js';
import { openAppearance } from './sections/appearance.js';
import { openFiles } from './sections/files.js';
import { openTrashSettings } from './sections/trash.js';
import { openStartup } from './sections/startup.js';
import { openAccess } from './sections/access.js';
import { openAbout } from './sections/about.js';
import {
  appearanceSummary, filesSummary, trashSummary, startupSummary, accessSummary, aboutSummary,
} from './summaries.js';

/**
 * ctx: { prefs, save(), onChange(), version, build, native, hasAccess(), requestAccess(),
 *        trashInfo(), openTrash(), openCleaner(), reset() }
 */
export function openSettings(ctx) {
  const refresh = () => main.redraw();
  const main = openSubscreen({
    title: 'Настройки',
    ctx,
    actions: {
      appearance: async () => openAppearance(ctx, refresh),
      files: async () => openFiles(ctx, refresh),
      trash: async () => openTrashSettings(ctx, refresh),
      startup: async () => openStartup(ctx, refresh),
      access: async () => openAccess(ctx, refresh),
      about: async () => openAbout(ctx),
      reset: async () => {
        const ok = await confirmDialog({ title: 'Сбросить настройки?', text: 'Файлы не затрагиваются.', okText: 'Сбросить' });
        if (!ok) return;
        ctx.reset();
        ctx.save();
        ctx.onChange();
        toast('Настройки сброшены');
      },
    },
    body: () =>
      group(
        menuRow('appearance', 'theme', 'Внешний вид', appearanceSummary(ctx)) +
          menuRow('files', 'folder', 'Файлы', filesSummary(ctx)) +
          menuRow('trash', 'trash', 'Корзина и очистка', trashSummary(ctx)) +
          menuRow('startup', 'home', 'При запуске открывать', startupSummary(ctx))
      ) +
      group(menuRow('access', 'lock', 'Доступ к файлам', accessSummary(ctx)) + menuRow('about', 'info', 'О приложении', aboutSummary(ctx))) +
      group(
        `<button class="row danger" data-act="reset"><span class="row-text"><span class="row-label">Сбросить настройки</span><small>Вернуть тему, сортировку и вид по умолчанию</small></span></button>`
      ),
  });
  return main.close;
}
