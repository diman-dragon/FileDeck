import { AR, FM, listen } from './plugin.js';
import { icon, folderGlyph } from './icons.js';
import { pushLayer, el, toast, sheet, promptDialog, progressDialog } from './layers.js';
import { escapeHtml, formatSize, formatDate, parentOf, basename, archiveBase, compareNames, extOf, kindOf, KIND_ICON } from './utils.js';

const FORMATS = [
  ['zip', 'ZIP', 'Подходит всем, есть пароль AES-256'],
  ['7z', '7z', 'Самое сильное сжатие'],
  ['tar.gz', 'TAR.GZ', 'Для Linux и серверов'],
  ['tar.xz', 'TAR.XZ', 'Сильное сжатие, медленнее'],
  ['tar.bz2', 'TAR.BZ2', 'Среднее сжатие'],
  ['tar', 'TAR', 'Без сжатия'],
];
const LEVELS = [[2, 'Быстро'], [5, 'Обычно'], [9, 'Максимум']];

const FORMAT_LABEL = { ZIP: 'ZIP', SEVENZ: '7z', TAR: 'TAR', TGZ: 'TAR.GZ', TBZ2: 'TAR.BZ2', TXZ: 'TAR.XZ', GZ: 'GZ', BZ2: 'BZ2', XZ: 'XZ', RAR: 'RAR' };

const explain = (e) => {
  const map = {
    RAR5: 'Формат RAR5 пока не поддерживается. RAR 4 и старше открываются.',
    FORMAT: e.message || 'Этот формат не поддерживается',
    PASSWORD_UNSUPPORTED: 'Пароль можно задать только для ZIP',
  };
  return map[e.code] || e.message || 'Операция не удалась';
};

/** Показывает окно прогресса на время операции с подпиской на события плагина. */
export async function withProgress(title, fn) {
  const ui = progressDialog({ title, onCancel: () => AR.cancel().catch(() => {}) });
  let off = () => {};
  try {
    off = await listen(AR, 'archiveProgress', (e) => ui.set(e.fraction, e.current));
    return await fn();
  } finally {
    off();
    ui.close();
  }
}

// ================================================================== создание

function createOptionsDialog({ defaultName, count }) {
  return new Promise((resolve) => {
    let format = 'zip';
    let level = 5;
    const node = el(
      `<div class="scrim"></div>
       <form class="dialog arc-dialog" role="dialog" aria-modal="true">
         <h2 class="dialog-title">Создать архив</h2>
         <p class="dialog-body">${count === 1 ? 'Один объект' : `Объектов: ${count}`}</p>
         <label class="fld-label" for="arc-name">Имя</label>
         <input id="arc-name" class="dialog-input" type="text" autocomplete="off" autocapitalize="off" spellcheck="false" value="${escapeHtml(defaultName)}" />
         <div class="fld-label">Формат</div>
         <div class="chips" data-group="format">${FORMATS.map(([v, l]) => `<button type="button" class="chip${v === format ? ' on' : ''}" data-v="${v}">${l}</button>`).join('')}</div>
         <p class="fld-hint" id="arc-hint"></p>
         <div class="fld-label">Сжатие</div>
         <div class="chips" data-group="level">${LEVELS.map(([v, l]) => `<button type="button" class="chip${v === level ? ' on' : ''}" data-v="${v}">${l}</button>`).join('')}</div>
         <div class="fld-label" id="pw-label">Пароль (необязательно)</div>
         <div class="pw-row"><input id="arc-pw" class="dialog-input" type="password" autocomplete="new-password" autocapitalize="off" spellcheck="false" placeholder="Без пароля" />
           <button type="button" class="icon-btn" data-act="eye" aria-label="Показать пароль">${icon('eye', 20)}</button></div>
         <div class="dialog-actions">
           <button type="button" class="btn ghost" data-act="cancel">Отмена</button>
           <button type="submit" class="btn primary">Создать</button>
         </div>
       </form>`,
      'layer-root layer-dialog'
    );
    let settled = false;
    const finish = (v) => {
      if (settled) return;
      settled = true;
      close();
      resolve(v);
    };
    const close = pushLayer(node, { onBack: () => finish(null) });
    const pw = node.querySelector('#arc-pw');
    const pwLabel = node.querySelector('#pw-label');
    const hint = node.querySelector('#arc-hint');
    const refresh = () => {
      hint.textContent = FORMATS.find((f) => f[0] === format)[2];
      const zip = format === 'zip';
      pw.disabled = !zip;
      if (!zip) pw.value = '';
      pw.placeholder = zip ? 'Без пароля' : 'Пароль есть только у ZIP';
      pwLabel.classList.toggle('muted', !zip);
    };
    refresh();
    node.querySelector('.scrim').addEventListener('click', () => finish(null));
    node.querySelector('[data-act="cancel"]').addEventListener('click', () => finish(null));
    node.querySelector('[data-act="eye"]').addEventListener('click', () => {
      pw.type = pw.type === 'password' ? 'text' : 'password';
    });
    node.querySelectorAll('.chips').forEach((group) =>
      group.addEventListener('click', (ev) => {
        const b = ev.target.closest('.chip');
        if (!b) return;
        group.querySelectorAll('.chip').forEach((c) => c.classList.toggle('on', c === b));
        if (group.dataset.group === 'format') format = b.dataset.v;
        else level = Number(b.dataset.v);
        refresh();
      })
    );
    node.querySelector('form').addEventListener('submit', (e) => {
      e.preventDefault();
      const name = node.querySelector('#arc-name').value.trim();
      if (!name) return toast('Введите имя архива', { error: true });
      if (name.includes('/')) return toast('В имени нельзя использовать «/»', { error: true });
      finish({ name, format, level, password: format === 'zip' ? pw.value : '' });
    });
    setTimeout(() => {
      const input = node.querySelector('#arc-name');
      input.focus();
      input.select();
    }, 220);
  });
}

/** entries: [{ path, name, dir }], destDir — куда положить архив. Возвращает результат или null. */
export async function createArchiveFlow({ entries, destDir }) {
  const first = entries[0];
  const defaultName = entries.length === 1 ? (first.dir ? first.name : first.name.replace(/\.[^.]+$/, '')) : 'Архив';
  const opts = await createOptionsDialog({ defaultName, count: entries.length });
  if (!opts) return null;
  try {
    const res = await withProgress('Создаём архив…', () =>
      AR.create({ paths: entries.map((e) => e.path), dest: destDir, name: opts.name, format: opts.format, level: opts.level, password: opts.password })
    );
    if (res.cancelled) {
      toast('Отменено');
      return null;
    }
    toast(`Архив создан: ${basename(res.path)} · ${formatSize(res.size)}`);
    return res;
  } catch (e) {
    toast(explain(e), { error: true });
    return null;
  }
}

// ================================================================== распаковка

/** mode: 'folder' — в новую папку с именем архива, 'here' — прямо в dest. entries — только выбранное. */
export async function extractFlow({ path, dest, mode = 'folder', entries = [], password = '' }) {
  let pw = password;
  for (;;) {
    try {
      const res = await withProgress('Распаковываем…', () => AR.extract({ path, dest, mode, entries, password: pw }));
      if (res.cancelled) {
        toast('Отменено');
        return null;
      }
      toast(`Распаковано: ${res.count} ${plural(res.count, ['файл', 'файла', 'файлов'])}`);
      return { ...res, password: pw };
    } catch (e) {
      if (e.code === 'PASSWORD_REQUIRED' || e.code === 'WRONG_PASSWORD') {
        const typed = await promptDialog({
          title: 'Пароль архива',
          text: basename(path),
          type: 'password',
          okText: 'Продолжить',
          error: e.code === 'WRONG_PASSWORD' ? 'Неверный пароль. Попробуйте ещё раз.' : '',
        });
        if (typed == null || typed === '') return null;
        pw = typed;
        continue;
      }
      toast(explain(e), { error: true });
      return null;
    }
  }
}

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

// ================================================================== просмотр содержимого

function buildTree(entries) {
  const root = { name: '', path: '', dir: true, size: 0, count: 0, children: new Map() };
  const ensureDir = (parts) => {
    let node = root;
    let acc = '';
    for (const part of parts) {
      acc = acc ? `${acc}/${part}` : part;
      let next = node.children.get(part);
      if (!next) {
        next = { name: part, path: acc, dir: true, size: 0, count: 0, mtime: 0, children: new Map() };
        node.children.set(part, next);
      }
      node = next;
    }
    return node;
  };
  for (const e of entries) {
    const parts = e.name.split('/').filter(Boolean);
    if (!parts.length) continue;
    if (e.dir) {
      ensureDir(parts).mtime = e.mtime;
      continue;
    }
    const parent = ensureDir(parts.slice(0, -1));
    parent.children.set(parts[parts.length - 1], { name: parts[parts.length - 1], path: parts.join('/'), dir: false, size: e.size, mtime: e.mtime, children: null });
  }
  const sum = (n) => {
    if (!n.dir) return { size: n.size, count: 1 };
    let size = 0;
    let count = 0;
    for (const c of n.children.values()) {
      const r = sum(c);
      size += r.size;
      count += r.count;
    }
    n.size = size;
    n.count = count;
    return { size, count };
  };
  sum(root);
  return root;
}

export async function openArchiveBrowser(entry, { onDone, onLocate } = {}) {
  const node = el('', 'layer-root layer-arcview');
  let password = '';
  let data = null;
  let root = null;
  let cwd = '';
  let loading = true;
  const selected = new Set();
  let closed = false;
  const closeLayer = pushLayer(node, { onBack: () => (cwd ? goUp() : close()) });
  const close = () => {
    closed = true;
    closeLayer();
  };

  const nodeAt = (path) => {
    let n = root;
    for (const part of path.split('/').filter(Boolean)) n = n?.children?.get(part);
    return n;
  };
  const goUp = () => {
    cwd = cwd.includes('/') ? cwd.slice(0, cwd.lastIndexOf('/')) : '';
    draw();
  };

  const header = () => `
    <div class="av-bar">
      <button class="icon-btn" data-act="back" aria-label="Назад">${icon('back')}</button>
      <div class="av-title"><div class="av-name">${escapeHtml(entry.name)}</div>
        <div class="av-sub">${data ? `${FORMAT_LABEL[data.format] || data.format} · ${data.count} ${plural(data.count, ['объект', 'объекта', 'объектов'])} · ${formatSize(data.bytes)}` : 'Читаем архив…'}${data?.encrypted ? ` <span class="badge warn">пароль</span>` : ''}</div></div>
    </div>`;

  const actionBar = () => {
    if (!data) return '';
    const n = selected.size;
    return `<div class="av-actions">
      ${data.listing ? `<button class="btn ghost" data-act="extract-all">Распаковать всё</button>` : ''}
      ${n > 0 ? `<button class="btn primary" data-act="extract-sel">Выбранное: ${n}</button>` : data.listing ? '' : `<button class="btn primary" data-act="extract-all">Распаковать архив</button>`}
    </div>`;
  };

  const draw = () => {
    if (closed) return;
    let body;
    if (loading) body = `<div class="state"><div class="spinner"></div></div>`;
    else if (!data.listing) {
      body = `<div class="state"><div class="empty-glyph">${icon('archive', 40)}</div><h1>Список недоступен</h1><p>Содержимое RAR-архивов показать нельзя, но архив можно распаковать целиком.</p></div>`;
    } else {
      const dir = nodeAt(cwd);
      const kids = [...(dir?.children?.values() || [])].sort((a, b) => (a.dir !== b.dir ? (a.dir ? -1 : 1) : compareNames(a.name, b.name)));
      const crumbs = ['<button class="tab' + (cwd ? '' : ' current') + '" data-cd="">Архив</button>'];
      let acc = '';
      cwd.split('/').filter(Boolean).forEach((p, i, arr) => {
        acc = acc ? `${acc}/${p}` : p;
        crumbs.push(`<button class="tab${i === arr.length - 1 ? ' current' : ''}" data-cd="${escapeHtml(acc)}">${escapeHtml(p)}</button>`);
      });
      body = `<div class="av-crumbs">${crumbs.join('')}</div>
        <div class="items list">${
          kids.length
            ? kids.map((k, i) => `
          <div class="item${selected.has(k.path) ? ' selected' : ''}" data-i="${i}" data-path="${escapeHtml(k.path)}" data-dir="${k.dir ? 1 : 0}">
            <div class="lead k-${k.dir ? 'folder' : kindOf({ name: k.name })}">${k.dir ? folderGlyph(42) : `<span class="kind-ic">${icon(KIND_ICON[kindOf({ name: k.name })] || 'file', 22)}</span>`}</div>
            <div class="body"><div class="name">${escapeHtml(k.name)}</div>
              <div class="sub">${k.dir ? `${k.count} ${plural(k.count, ['файл', 'файла', 'файлов'])} · ${formatSize(k.size)}` : `${formatSize(k.size)}${k.mtime ? ' · ' + formatDate(k.mtime) : ''}`}</div></div>
            <button class="chk${selected.has(k.path) ? ' on' : ''}" data-act="chk" aria-label="Выбрать">${icon('check', 16)}</button>
          </div>`).join('')
            : '<div class="state"><p>Папка пуста</p></div>'
        }</div>${data.truncated ? '<div class="list-note">Показаны первые 20 000 записей</div>' : ''}`;
    }
    node.innerHTML = `${header()}<div class="av-body">${body}</div>${actionBar()}`;
  };

  async function load() {
    for (;;) {
      try {
        data = await AR.list({ path: entry.path, password });
        break;
      } catch (e) {
        if (e.code === 'PASSWORD_REQUIRED' || e.code === 'WRONG_PASSWORD') {
          const typed = await promptDialog({ title: 'Пароль архива', text: 'Имена файлов зашифрованы', type: 'password', okText: 'Открыть', error: e.code === 'WRONG_PASSWORD' ? 'Неверный пароль.' : '' });
          if (typed == null || typed === '') {
            close();
            return;
          }
          password = typed;
          continue;
        }
        toast(explain(e), { error: true });
        close();
        return;
      }
    }
    root = data.listing ? buildTree(data.entries) : null;
    loading = false;
    draw();
  }

  const doExtract = (only) => {
    sheet({
      title: only.length ? `Распаковать выбранное (${only.length})` : 'Распаковать всё',
      items: [
        { icon: 'folder', label: `В папку «${archiveBase(entry.name)}»`, hint: 'Рекомендуется', onClick: () => run('folder', only) },
        ...(data.format === 'RAR' ? [] : [{ icon: 'download', label: 'Сюда, рядом с архивом', onClick: () => run('here', only) }]),
      ],
    });
  };
  const run = async (mode, only) => {
    const res = await extractFlow({ path: entry.path, dest: parentOf(entry.path), mode, entries: only, password });
    if (res) {
      password = res.password || password;
      onDone && onDone();
      selected.clear();
      draw();
    }
  };

  node.addEventListener('click', (ev) => {
    const cd = ev.target.closest('[data-cd]');
    if (cd) {
      cwd = cd.dataset.cd;
      return draw();
    }
    const act = ev.target.closest('[data-act]')?.dataset.act;
    if (act === 'back') return cwd ? goUp() : close();
    if (act === 'extract-all') return doExtract([]);
    if (act === 'extract-sel') return doExtract([...selected]);
    const row = ev.target.closest('.item');
    if (!row) return;
    const path = row.dataset.path;
    const toggle = () => {
      selected.has(path) ? selected.delete(path) : selected.add(path);
      const y = node.querySelector('.av-body').scrollTop;
      draw();
      node.querySelector('.av-body').scrollTop = y;
    };
    if (act === 'chk') return toggle();
    if (row.dataset.dir === '1') {
      cwd = path;
      draw();
    } else toggle();
  });

  void onLocate;
  draw();
  load();
}

function plural(n, [one, few, many]) {
  const m10 = n % 10;
  const m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

export { extOf };
