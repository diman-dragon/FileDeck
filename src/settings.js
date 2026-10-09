import { icon } from './icons.js';
import { pushLayer, el, toast, confirmDialog } from './layers.js';
import { escapeHtml } from './utils.js';

const THEMES = [
  ['auto', 'Как в системе', 'Тёмная ночью, светлая днём'],
  ['amoled', 'AMOLED', 'Чистый чёрный — экономит заряд на OLED-экранах'],
  ['light', 'Светло-коричневая', 'Тёплая светлая тема цвета какао с молоком'],
];
const SORTS = [['name', 'По имени'], ['date', 'По дате'], ['size', 'По размеру'], ['type', 'По типу']];
const STARTS = [['home', 'Главный экран'], ['primary', 'Память телефона'], ['sd', 'SD-карта']];

/**
 * ctx: { prefs, save(), onChange(), version, build, native, hasAccess(), requestAccess(), reset() }
 */
export function openSettings(ctx) {
  const { prefs } = ctx;
  const node = el('', 'layer-root layer-settings');
  const close = pushLayer(node);

  const toggle = (key, label, hint) => `
    <button class="row" role="switch" aria-checked="${!!prefs[key]}" data-toggle="${key}">
      <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
      <span class="sw ${prefs[key] ? 'on' : ''}"><i></i></span>
    </button>`;
  const choice = (key, label, hint, active) => `
    <button class="row" role="radio" aria-checked="${active}" data-set="${key}" data-val="${label[0]}">
      <span class="row-text"><span class="row-label">${label[1]}</span>${hint ? `<small>${hint}</small>` : ''}</span>
      <span class="radio ${active ? 'on' : ''}">${active ? icon('check', 16) : ''}</span>
    </button>`;
  const segment = (key, items) => `
    <div class="segment" role="radiogroup">${items
      .map(([v, l, ic]) => `<button role="radio" aria-checked="${prefs[key] === v}" class="${prefs[key] === v ? 'on' : ''}" data-set="${key}" data-val="${v}">${icon(ic, 18)}<span>${l}</span></button>`)
      .join('')}</div>`;

  const draw = () => {
    const granted = ctx.hasAccess();
    node.innerHTML = `
      <div class="st-bar">
        <button class="icon-btn" data-act="close" aria-label="Назад">${icon('back')}</button>
        <h1>Настройки</h1>
      </div>
      <div class="st-body">
        <h2 class="st-h">Внешний вид</h2>
        <div class="group">
          ${THEMES.map(([v, l, h]) => choice('theme', [v, l], h, prefs.theme === v)).join('')}
        </div>
        <div class="group pad-seg">
          <div class="row-label seg-title">Вид списка</div>
          ${segment('view', [['list', 'Список', 'list'], ['grid', 'Сетка', 'grid']])}
        </div>

        <h2 class="st-h">Файлы</h2>
        <div class="group">
          ${SORTS.map(([v, l]) => choice('sortBy', [v, l], '', prefs.sortBy === v)).join('')}
          ${toggle('desc', 'В обратном порядке', 'Новые и большие файлы сверху, Я → А')}
        </div>
        <div class="group">
          ${toggle('hidden', 'Скрытые файлы', 'Показывать файлы и папки, имена которых начинаются с точки')}
          ${toggle('confirmDelete', 'Подтверждать удаление', 'Спрашивать перед тем, как удалить файлы навсегда')}
        </div>

        <h2 class="st-h">При запуске открывать</h2>
        <div class="group">
          ${STARTS.map(([v, l]) => choice('start', [v, l], '', prefs.start === v)).join('')}
        </div>

        <h2 class="st-h">Доступ к файлам</h2>
        <div class="group">
          <div class="row static">
            <span class="row-text"><span class="row-label">${granted ? 'Доступ выдан' : 'Доступ не выдан'}</span>
            <small>${granted ? 'FileDeck может читать и менять файлы на устройстве' : 'Без доступа приложение не увидит файлы'}</small></span>
            <span class="badge ${granted ? 'ok' : 'warn'}">${granted ? 'Вкл' : 'Выкл'}</span>
          </div>
          ${granted ? '' : `<button class="row" data-act="grant"><span class="row-text"><span class="row-label">Выдать доступ</span></span>${icon('chevron', 20)}</button>`}
        </div>

        <h2 class="st-h">О приложении</h2>
        <div class="group about">
          <div class="about-logo"><span class="brand-d big" aria-hidden="true"></span></div>
          <div class="about-name">FileDeck</div>
          <div class="about-ver">Версия ${escapeHtml(ctx.version)} · сборка ${escapeHtml(String(ctx.build))}</div>
          <div class="about-by">Сделано в DataLaw</div>
          <div class="about-tech">Capacitor 8 · ${ctx.native ? 'Android' : 'веб-превью'}</div>
        </div>
        <div class="group">
          <button class="row danger" data-act="reset"><span class="row-text"><span class="row-label">Сбросить настройки</span><small>Вернуть тему, сортировку и вид по умолчанию</small></span></button>
        </div>
      </div>`;
  };
  draw();

  node.addEventListener('click', async (ev) => {
    const t = ev.target.closest('[data-toggle],[data-set],[data-act]');
    if (!t) return;
    if (t.dataset.toggle) {
      prefs[t.dataset.toggle] = !prefs[t.dataset.toggle];
    } else if (t.dataset.set) {
      prefs[t.dataset.set] = t.dataset.val;
    } else if (t.dataset.act === 'close') {
      return close();
    } else if (t.dataset.act === 'grant') {
      await ctx.requestAccess();
      return draw();
    } else if (t.dataset.act === 'reset') {
      const ok = await confirmDialog({ title: 'Сбросить настройки?', text: 'Файлы не затрагиваются.', okText: 'Сбросить' });
      if (!ok) return;
      ctx.reset();
      toast('Настройки сброшены');
    }
    ctx.save();
    ctx.onChange();
    const body = node.querySelector('.st-body');
    const y = body.scrollTop;
    draw();
    node.querySelector('.st-body').scrollTop = y;
  });
  return close;
}
