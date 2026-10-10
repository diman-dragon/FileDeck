import { CL, FM, listen, fileUrl } from './plugin.js';
import { icon, folderGlyph } from './icons.js';
import { pushLayer, el, toast, sheet, confirmDialog } from './layers.js';
import { escapeHtml, formatSize, formatDate, basename, parentOf, kindOf, KIND_ICON, KIND_LABEL } from './utils.js';

export const DEFAULT_CLEANER = {
  primary: true, sd: true, duplicates: true, similar: false, junk: true, apks: true,
  largest: true, emptyFolders: true, zeroFiles: true, appFolders: false,
};

const CATS = [
  { id: 'duplicates', opt: 'duplicates', title: 'Дубликаты', icon: 'copy', hint: 'Одинаковые файлы по содержимому, даже с разными именами', group: true, safe: true },
  { id: 'similar', opt: 'similar', title: 'Похожие фото', icon: 'image', hint: 'Серии кадров и копии разного размера · сканирование дольше', group: true, safe: false },
  { id: 'junk', opt: 'junk', title: 'Мусор и кэш', icon: 'trash', hint: 'Временные файлы, логи, миниатюры, недокачанное', safe: true },
  { id: 'apks', opt: 'apks', title: 'Установщики APK', icon: 'apk', hint: 'Файлы приложений, которые уже не нужны', safe: false },
  { id: 'largest', opt: 'largest', title: 'Самые большие файлы', icon: 'drive', hint: 'Что занимает больше всего места', safe: false },
  { id: 'emptyFolders', opt: 'emptyFolders', title: 'Пустые папки', icon: 'folder', hint: 'Папки без единого файла', safe: true },
  { id: 'zeroFiles', opt: 'zeroFiles', title: 'Пустые файлы', icon: 'file', hint: 'Файлы размером 0 байт', safe: true },
];

const JUNK_LABEL = {
  cache: 'Кэш', temp: 'Временные', thumbnails: 'Миниатюры', logs: 'Логи', trashed: 'Из системной корзины',
  system: 'Служебные', trash: 'Корзина приложения', messenger: 'Статусы мессенджера', partial: 'Недокачанное',
};
const USAGE = [
  ['images', 'Фото', 'var(--k-image)'], ['video', 'Видео', 'var(--k-video)'], ['audio', 'Музыка', 'var(--k-audio)'],
  ['docs', 'Документы', 'var(--k-doc)'], ['archives', 'Архивы', 'var(--k-archive)'], ['apk', 'APK', 'var(--k-apk)'], ['other', 'Другое', 'var(--k-file)'],
];

const plural = (n, [one, few, many]) => {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
};

/**
 * ctx: { roots, prefs, savePrefs, locate(path), onChange(), viewImage(entry) }
 */
export function openCleaner(ctx) {
  const { prefs } = ctx;
  prefs.cleaner = Object.assign({}, DEFAULT_CLEANER, prefs.cleaner || {});
  const o = prefs.cleaner;
  const node = el('', 'layer-root layer-cleaner');
  const state = { view: 'setup', cat: null, result: null, sel: {}, progress: null, busy: false };
  let offProgress = null;
  let closed = false;

  const closeLayer = pushLayer(node, { onBack: () => back() });
  const close = () => {
    closed = true;
    offProgress && offProgress();
    closeLayer();
  };

  // ------------------------------------------------------------ данные
  const secondary = ctx.roots.find((r) => !r.primary);
  const primary = ctx.roots.find((r) => r.primary) || ctx.roots[0];

  const rowsOf = (cat) => {
    const r = state.result;
    if (!r) return [];
    switch (cat) {
      case 'duplicates':
        return r.duplicates.flatMap((g, gi) => g.files.map((f) => ({ path: f.path, size: g.size, mtime: f.mtime, group: gi })));
      case 'similar':
        return r.similar.flatMap((g, gi) => g.files.map((f) => ({ path: f.path, size: f.size, mtime: f.mtime, w: f.w, h: f.h, group: gi })));
      case 'junk':
        return r.junk.map((j) => ({ path: j.path, size: j.size, dir: j.dir, kind: j.kind }));
      case 'apks':
        return r.apks.map((a) => ({ ...a }));
      case 'largest':
        return r.largest.map((a) => ({ ...a }));
      case 'emptyFolders':
        return r.emptyFolders.map((p) => ({ path: p, size: 0, dir: true }));
      case 'zeroFiles':
        return r.zeroFiles.map((p) => ({ path: p, size: 0 }));
      default:
        return [];
    }
  };
  const countOf = (cat) => {
    const r = state.result;
    if (!r) return 0;
    if (cat === 'duplicates') return r.duplicates.length;
    if (cat === 'similar') return r.similar.length;
    return rowsOf(cat).length;
  };
  const wasteOf = (cat) => {
    const rows = rowsOf(cat);
    if (cat === 'duplicates' || cat === 'similar') {
      const byGroup = new Map();
      rows.forEach((r) => (byGroup.get(r.group) || byGroup.set(r.group, []).get(r.group)).push(r));
      let sum = 0;
      byGroup.forEach((list) => {
        const keep = keeper(list);
        sum += list.filter((x) => x !== keep).reduce((a, x) => a + x.size, 0);
      });
      return sum;
    }
    return rows.reduce((a, r) => a + r.size, 0);
  };

  // «лучший» экземпляр в группе: не из Загрузок, без « (1)» и «копия», в основной папке, старше
  function keeper(list) {
    const score = (f) => {
      const p = f.path;
      let s = p.length / 1000;
      if (/\/Download\//i.test(p)) s += 3;
      if (/ \(\d+\)(\.[^./]+)?$/.test(p)) s += 4;
      if (/(копия|copy|~)/i.test(basename(p))) s += 3;
      if (/\/(Telegram|WhatsApp)|\/Android\/media/i.test(p)) s += 2;
      if (f.w && f.h) s -= (f.w * f.h) / 1e8; // у фото лучше версия с большим разрешением
      s -= (f.size || 0) / 1e12;
      return s;
    };
    return list.slice().sort((a, b) => score(a) - score(b) || (a.mtime || 0) - (b.mtime || 0))[0];
  }

  function defaultSelection() {
    const sel = {};
    for (const c of CATS) {
      const rows = rowsOf(c.id);
      const set = new Set();
      if (c.group) {
        const byGroup = new Map();
        rows.forEach((r) => (byGroup.get(r.group) || byGroup.set(r.group, []).get(r.group)).push(r));
        byGroup.forEach((list) => {
          const keep = keeper(list);
          list.forEach((r) => r !== keep && set.add(r.path));
        });
      } else if (c.id === 'junk' || c.id === 'emptyFolders' || c.id === 'zeroFiles') rows.forEach((r) => set.add(r.path));
      sel[c.id] = set;
    }
    return sel;
  }
  const selBytes = (cat) => rowsOf(cat).filter((r) => state.sel[cat]?.has(r.path)).reduce((a, r) => a + r.size, 0);
  const safeCats = () => CATS.filter((c) => c.safe && c.id !== 'similar');
  const safePaths = () => safeCats().flatMap((c) => rowsOf(c.id).filter((r) => state.sel[c.id]?.has(r.path)).map((r) => r.path));
  const safeBytes = () => safeCats().reduce((a, c) => a + selBytes(c.id), 0);

  // удалённое убираем из всех списков (файл может числиться сразу в нескольких категориях)
  function purge(paths) {
    const gone = new Set(paths);
    const hit = (p) => gone.has(p) || [...gone].some((g) => p.startsWith(g + '/'));
    const r = state.result;
    r.duplicates = r.duplicates.map((g) => ({ ...g, files: g.files.filter((f) => !hit(f.path)) })).filter((g) => g.files.length > 1);
    r.similar = r.similar.map((g) => ({ ...g, files: g.files.filter((f) => !hit(f.path)) })).filter((g) => g.files.length > 1);
    r.junk = r.junk.filter((j) => !hit(j.path));
    r.apks = r.apks.filter((a) => !hit(a.path));
    r.largest = r.largest.filter((a) => !hit(a.path));
    r.emptyFolders = r.emptyFolders.filter((p) => !hit(p));
    r.zeroFiles = r.zeroFiles.filter((p) => !hit(p));
    for (const c of CATS) {
      const valid = new Set(rowsOf(c.id).map((x) => x.path));
      state.sel[c.id] = new Set([...(state.sel[c.id] || [])].filter((p) => valid.has(p)));
    }
  }

  // ------------------------------------------------------------ навигация
  function back() {
    if (state.view === 'detail') {
      state.view = 'summary';
      return draw(true);
    }
    if (state.view === 'scanning') return cancelScan();
    close();
  }

  // ------------------------------------------------------------ сканирование
  async function startScan() {
    const roots = [];
    if (o.primary && primary) roots.push(primary.path);
    if (o.sd && secondary) roots.push(secondary.path);
    if (!roots.length) return toast('Выберите, где искать', { error: true });
    if (!CATS.some((c) => o[c.opt])) return toast('Выберите, что искать', { error: true });
    ctx.savePrefs();
    state.view = 'scanning';
    state.progress = { phase: 'walk', files: 0, bytes: 0, done: 0, total: 0, current: '' };
    draw();
    offProgress = await listen(CL, 'scanProgress', (e) => {
      state.progress = e;
      updateProgress();
    });
    try {
      const res = await CL.scan({
        roots, duplicates: o.duplicates, similar: o.similar, junk: o.junk, apks: o.apks, largest: o.largest,
        emptyFolders: o.emptyFolders, zeroFiles: o.zeroFiles, appFolders: o.appFolders,
      });
      if (closed) return;
      if (res.cancelled) {
        toast('Сканирование отменено');
        state.view = 'setup';
      } else {
        state.result = res;
        state.sel = defaultSelection();
        state.view = 'summary';
      }
    } catch (e) {
      if (closed) return;
      toast(e.message || 'Не удалось выполнить сканирование', { error: true });
      state.view = 'setup';
    } finally {
      offProgress && offProgress();
      offProgress = null;
    }
    draw();
  }
  const cancelScan = () => CL.cancelScan().catch(() => {});

  function updateProgress() {
    if (state.view !== 'scanning') return;
    const p = state.progress;
    const phaseText = { walk: 'Просматриваем файлы…', hash: 'Сравниваем содержимое…', images: 'Анализируем фотографии…' }[p.phase] || 'Работаем…';
    const frac = p.total > 0 ? Math.min(1, p.done / p.total) : 0;
    const q = (s) => node.querySelector(s);
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

  // ------------------------------------------------------------ удаление
  async function removePaths(paths, bytes, label) {
    if (!paths.length) return;
    const useTrash = prefs.useTrash;
    const days = prefs.trashDays || 30;
    const describe = `${paths.length} ${plural(paths.length, ['объект', 'объекта', 'объектов'])} · ${formatSize(bytes) || '0 Б'}`;
    const choose = () =>
      new Promise((resolve) => {
        if (!useTrash) {
          confirmDialog({ title: `Удалить навсегда?`, text: `${label}: ${describe}. Восстановить будет нельзя.`, okText: 'Удалить' }).then((ok) => resolve(ok ? 'permanent' : null));
          return;
        }
        sheet({
          title: label,
          subtitle: describe,
          onDismiss: () => resolve(null),
          items: [
            { icon: 'trash', label: 'Переместить в корзину', hint: `Хранится ${days} ${plural(days, ['день', 'дня', 'дней'])}, можно восстановить`, onClick: () => resolve('trash') },
            { icon: 'close', label: 'Удалить навсегда', hint: 'Сразу освободит место, без восстановления', danger: true, onClick: () => resolve('permanent') },
          ],
        });
      });
    const mode = await choose();
    if (!mode) return;
    state.busy = true;
    toast(mode === 'trash' ? 'Переносим в корзину…' : 'Удаляем…', { ms: 600000 });
    try {
      const res = await (mode === 'trash' ? CL.trashMove({ paths }) : FM.remove({ paths }));
      const failed = res.failed?.length || 0;
      const okPaths = paths.filter((p) => !(res.failed || []).includes(p));
      purge(okPaths);
      toast(
        mode === 'trash'
          ? `В корзине: ${res.done}${failed ? `, не удалось: ${failed}` : ''}. Место освободится после очистки корзины.`
          : `Удалено: ${res.done}${failed ? `, не удалось: ${failed}` : ''} · освобождено ${formatSize(bytes)}`,
        { error: failed > 0, ms: 4200 }
      );
    } catch (e) {
      toast(e.message || 'Не удалось удалить', { error: true });
    }
    state.busy = false;
    ctx.onChange && ctx.onChange();
    draw();
  }

  async function deleteCategory(catId) {
    const cat = CATS.find((c) => c.id === catId);
    const rows = rowsOf(catId).filter((r) => state.sel[catId]?.has(r.path));
    if (!rows.length) return;
    if (cat.group) {
      const byGroup = new Map();
      rowsOf(catId).forEach((r) => (byGroup.get(r.group) || byGroup.set(r.group, []).get(r.group)).push(r));
      let full = 0;
      byGroup.forEach((list) => { if (list.every((r) => state.sel[catId].has(r.path))) full++; });
      if (full > 0) {
        const ok = await confirmDialog({
          title: 'Выбраны все копии',
          text: `В ${full} ${plural(full, ['группе', 'группах', 'группах'])} отмечены все экземпляры — файл исчезнет совсем. Продолжить?`,
          okText: 'Продолжить',
        });
        if (!ok) return;
      }
    }
    await removePaths(rows.map((r) => r.path), rows.reduce((a, r) => a + r.size, 0), cat.title);
  }

  async function quickClean() {
    const paths = safePaths();
    if (!paths.length) return toast('Безопасной очистки не нашлось');
    await removePaths(paths, safeBytes(), 'Быстрая очистка');
  }

  // ------------------------------------------------------------ отрисовка
  const topbar = (title, right = '') => `
    <div class="cl-bar">
      <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
      <h1>${escapeHtml(title)}</h1>${right}
    </div>`;

  const switchRow = (key, label, hint, disabled) => `
    <button class="row" role="switch" aria-checked="${!!o[key]}" data-opt="${key}" ${disabled ? 'disabled' : ''}>
      <span class="row-text"><span class="row-label">${label}</span>${hint ? `<small>${hint}</small>` : ''}</span>
      <span class="sw ${o[key] ? 'on' : ''}"><i></i></span>
    </button>`;

  function setupHtml() {
    return `${topbar('Очистка')}
      <div class="cl-body">
        <div class="cl-hero"><div class="cl-hero-ic">${icon('sparkle', 30)}</div>
          <p>Найдём дубликаты, мусор, пустые папки и самые тяжёлые файлы. Ничего не удаляется без вашего подтверждения.</p></div>
        <h2 class="st-h">Где искать</h2>
        <div class="group">
          ${switchRow('primary', 'Память телефона', primary ? `${formatSize(primary.free)} свободно из ${formatSize(primary.total)}` : '', !primary)}
          ${secondary ? switchRow('sd', 'SD-карта', `${formatSize(secondary.free)} свободно из ${formatSize(secondary.total)}`) : ''}
        </div>
        <h2 class="st-h">Что искать</h2>
        <div class="group">${CATS.map((c) => switchRow(c.opt, c.title, c.hint)).join('')}</div>
        <h2 class="st-h">Дополнительно</h2>
        <div class="group">${switchRow('appFolders', 'Папки приложений', 'Android/media: медиа мессенджеров. Удаление может оставить пустые места в чатах')}</div>
      </div>
      <div class="cl-actions"><button class="btn primary big" data-act="scan">Сканировать</button></div>`;
  }

  function scanningHtml() {
    return `${topbar('Сканирование')}
      <div class="cl-body scanning">
        <div class="scan-ring">${icon('sparkle', 34)}</div>
        <div class="scan-phase">Просматриваем файлы…</div>
        <div class="pbar indeterminate"><i></i></div>
        <div class="scan-stats"><div><b class="scan-files">0</b><span>файлов</span></div><div><b class="scan-bytes">0 Б</b><span>просмотрено</span></div></div>
        <div class="scan-cur"></div>
      </div>
      <div class="cl-actions"><button class="btn ghost big" data-act="cancel-scan">Отмена</button></div>`;
  }

  function summaryHtml() {
    const r = state.result;
    const bar = USAGE.filter(([k]) => r.usage[k]?.bytes).map(([k, , c]) => `<i style="flex:${r.usage[k].bytes};background:${c}"></i>`).join('');
    const legend = USAGE.filter(([k]) => r.usage[k]?.bytes).sort((a, b) => r.usage[b[0]].bytes - r.usage[a[0]].bytes).slice(0, 5)
      .map(([k, l, c]) => `<span><i style="background:${c}"></i>${l} <b>${formatSize(r.usage[k].bytes)}</b></span>`).join('');
    const safe = safeBytes();
    const cards = CATS.filter((c) => o[c.opt]).map((c) => {
      const n = countOf(c.id);
      const bytes = c.id === 'largest' ? rowsOf(c.id).reduce((a, x) => a + x.size, 0) : wasteOf(c.id);
      return `<button class="cat-card${n ? '' : ' clean'}" data-cat="${c.id}" ${n ? '' : 'disabled'}>
        <span class="cat-ic">${icon(n ? c.icon : 'check', 22)}</span>
        <span class="cat-text"><b>${c.title}</b><small>${n ? `${n} ${c.group ? plural(n, ['группа', 'группы', 'групп']) : plural(n, ['объект', 'объекта', 'объектов'])}${bytes ? ' · ' + formatSize(bytes) : ''}` : 'Ничего не найдено'}</small></span>
        ${n ? icon('chevron', 20) : ''}
      </button>`;
    }).join('');
    return `${topbar('Результаты', `<button class="icon-btn" data-act="rescan" aria-label="Сканировать заново">${icon('refresh')}</button>`)}
      <div class="cl-body">
        <div class="cl-total"><small>Можно безопасно освободить</small><b>${formatSize(safe) || '0 Б'}</b>
          <span>Просмотрено ${r.stats.files.toLocaleString('ru')} ${plural(r.stats.files, ['файл', 'файла', 'файлов'])} · ${formatSize(r.stats.bytes)} · ${(r.stats.ms / 1000).toFixed(1)} с</span></div>
        <div class="usage"><div class="usage-bar">${bar}</div><div class="usage-legend">${legend}</div></div>
        <h2 class="st-h">Найдено</h2>
        <div class="cats">${cards || '<div class="muted pad">Включите хотя бы одну категорию</div>'}</div>
      </div>
      <div class="cl-actions">${safe > 0 ? `<button class="btn primary big" data-act="quick">Быстрая очистка · ${formatSize(safe)}</button>` : `<button class="btn ghost big" data-act="rescan">Сканировать заново</button>`}</div>`;
  }

  function thumbHtml(r) {
    const kind = kindOf({ name: basename(r.path), dir: r.dir });
    if (kind === 'folder') return folderGlyph(40);
    if (kind === 'image') return `<img src="${fileUrl(r.path)}" alt="" loading="lazy" decoding="async" draggable="false" />`;
    return `<span class="kind-ic">${icon(KIND_ICON[kind] || 'file', 22)}</span>`;
  }

  function rowHtml(c, r, i, keepPath) {
    const on = state.sel[c.id]?.has(r.path);
    const kind = kindOf({ name: basename(r.path), dir: r.dir });
    const where = parentOf(r.path).replace(/^\/storage\/(emulated\/0|[^/]+)/, (m, g) => (g === 'emulated/0' ? 'Память' : 'SD')) || '/';
    let sub = '';
    if (c.id === 'junk') sub = `${JUNK_LABEL[r.kind] || 'Мусор'} · ${formatSize(r.size)}`;
    else if (c.id === 'apks') sub = `${r.version ? 'v' + r.version + ' · ' : ''}${formatSize(r.size)}${r.mtime ? ' · ' + formatDate(r.mtime) : ''}`;
    else if (c.id === 'emptyFolders') sub = 'пустая папка';
    else if (c.id === 'zeroFiles') sub = '0 Б';
    else sub = `${r.w ? r.w + '×' + r.h + ' · ' : ''}${formatSize(r.size)}${r.mtime ? ' · ' + formatDate(r.mtime) : ''}`;
    const title = c.id === 'apks' && r.label ? `${r.label}` : basename(r.path);
    return `<div class="item cl-row${on ? ' selected' : ''}" data-i="${i}" data-path="${escapeHtml(r.path)}">
      <button class="chk${on ? ' on' : ''}" data-act="chk" aria-label="Выбрать">${icon('check', 16)}</button>
      <div class="lead k-${kind}">${thumbHtml(r)}</div>
      <div class="body"><div class="name">${escapeHtml(title)}${keepPath === r.path ? ' <em class="keep">оставить</em>' : ''}</div>
        <div class="sub">${escapeHtml(sub)}</div><div class="sub where">${escapeHtml(where)}</div></div>
      <div class="cl-tools">
        ${r.dir ? '' : `<button class="icon-btn small" data-act="view" aria-label="Открыть">${icon('eye', 19)}</button>`}
        <button class="icon-btn small" data-act="locate" aria-label="Показать в папке">${icon('folder', 19)}</button>
      </div>
    </div>`;
  }

  function detailHtml() {
    const c = CATS.find((x) => x.id === state.cat);
    const rows = rowsOf(c.id);
    const n = state.sel[c.id]?.size || 0;
    const bytes = selBytes(c.id);
    const all = rows.length && n === rows.length;
    let body = '';
    if (!rows.length) body = '<div class="state"><div class="empty-glyph">' + icon('check', 40) + '</div><h1>Всё чисто</h1><p>В этой категории больше ничего нет.</p></div>';
    else if (c.group) {
      const byGroup = new Map();
      rows.forEach((r) => (byGroup.get(r.group) || byGroup.set(r.group, []).get(r.group)).push(r));
      let idx = 0;
      body = [...byGroup.values()].map((list, gi) => {
        const keep = keeper(list);
        const waste = list.filter((x) => x !== keep).reduce((a, x) => a + x.size, 0);
        const head = `<div class="grp-head"><b>${c.id === 'similar' ? 'Похожие' : 'Одинаковые'} · ${list.length}</b><span>можно освободить ${formatSize(waste)}</span></div>`;
        return head + list.map((r) => rowHtml(c, r, idx++, keep.path)).join('');
      }).join('');
    } else {
      body = rows.map((r, i) => rowHtml(c, r, i, null)).join('');
    }
    return `${topbar(c.title, rows.length ? `<button class="btn ghost small" data-act="toggle-all">${all ? 'Снять всё' : 'Выбрать всё'}</button>` : '')}
      <div class="cl-body list-body"><p class="cl-hint">${c.hint}</p><div class="items list">${body}</div></div>
      <div class="cl-actions">${n > 0 ? `<button class="btn danger big" data-act="delete">Удалить · ${n} · ${formatSize(bytes) || '0 Б'}</button>` : `<button class="btn ghost big" disabled>Ничего не выбрано</button>`}</div>`;
  }

  function draw(resetScroll = false) {
    if (closed) return;
    const y = resetScroll ? 0 : node.querySelector('.cl-body')?.scrollTop || 0;
    node.innerHTML = { setup: setupHtml, scanning: scanningHtml, summary: summaryHtml, detail: detailHtml }[state.view]();
    const body = node.querySelector('.cl-body');
    if (body && state.view !== 'scanning') body.scrollTop = y;
    if (state.view === 'scanning') updateProgress();
  }

  // ------------------------------------------------------------ события
  node.addEventListener('click', (ev) => {
    if (state.busy) return;
    const opt = ev.target.closest('[data-opt]');
    if (opt) {
      o[opt.dataset.opt] = !o[opt.dataset.opt];
      return draw();
    }
    const catBtn = ev.target.closest('[data-cat]');
    if (catBtn) {
      state.cat = catBtn.dataset.cat;
      state.view = 'detail';
      return draw(true);
    }
    const act = ev.target.closest('[data-act]')?.dataset.act;
    const row = ev.target.closest('.cl-row');
    if (act === 'back') return back();
    if (act === 'scan' || act === 'rescan') {
      state.view = 'setup';
      return act === 'scan' ? startScan() : draw();
    }
    if (act === 'cancel-scan') return cancelScan();
    if (act === 'quick') return quickClean();
    if (act === 'delete') return deleteCategory(state.cat);
    if (act === 'toggle-all') {
      const rows = rowsOf(state.cat);
      const set = state.sel[state.cat];
      if (set.size === rows.length) set.clear();
      else rows.forEach((r) => set.add(r.path));
      return draw();
    }
    if (!row) return;
    const path = row.dataset.path;
    if (act === 'locate') {
      close();
      return ctx.locate(parentOf(path));
    }
    if (act === 'view') {
      const e = { path, name: basename(path), size: 0, mtime: 0 };
      if (kindOf(e) === 'image') return ctx.viewImage(e);
      return FM.open({ path }).catch((err) => toast(err.message, { error: true }));
    }
    const set = state.sel[state.cat];
    set.has(path) ? set.delete(path) : set.add(path);
    draw();
  });

  void KIND_LABEL;
  draw();
}
