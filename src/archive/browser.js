// Просмотр содержимого архива.
import { AR } from '../plugin.js';
import { icon } from '../icons/icon.js';
import { folderGlyph } from '../icons/folder-glyph.js';
import { pushLayer } from '../layers/stack.js';
import { el } from '../layers/dom.js';
import { toast } from '../layers/toast.js';
import { sheet } from '../layers/sheet.js';
import { promptDialog } from '../layers/prompt-dialog.js';
import { escapeHtml } from '../html.js';
import { formatSize, formatDate, plural } from '../format.js';
import { parentOf } from '../path.js';
import { archiveBase } from '../archive-types.js';
import { compareNames } from '../sort.js';
import { kindOf, KIND_ICON } from '../file-kinds.js';
import { FORMAT_LABEL } from './formats.js';
import { explain } from './errors.js';
import { extractFlow } from './extract-flow.js';
import { buildTree } from './tree.js';

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
