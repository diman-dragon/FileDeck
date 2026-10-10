// Браузерная имитация файловой системы — чтобы разрабатывать интерфейс в VS Code
// через `npm run dev`, не собирая APK. На устройстве НЕ используется.
import { basename, parentOf, join, extOf } from './utils.js';

const ROOT = '/storage/emulated/0';
const SD = '/storage/1A2B-3C4D';
const DAY = 86400000;
const now = Date.now();

const fs = new Map();
const put = (path, props = {}) => fs.set(path, { dir: false, size: 0, mtime: now - Math.random() * 40 * DAY, ...props });
const dir = (path) => put(path, { dir: true });
const file = (path, size, props = {}) => put(path, { size, ...props });

[ROOT, SD].forEach(dir);
['Download', 'DCIM', 'DCIM/Camera', 'Pictures', 'Pictures/Screenshots', 'Music', 'Movies', 'Documents', 'Documents/Работа', 'Android', 'Telegram'].forEach((d) => dir(`${ROOT}/${d}`));
for (let i = 1; i <= 14; i++) file(`${ROOT}/DCIM/Camera/IMG_2026${String(i).padStart(2, '0')}15_1${i}2233.jpg`, 2_400_000 + i * 91_000);
for (let i = 1; i <= 6; i++) file(`${ROOT}/Pictures/Screenshots/Screenshot_${i}.png`, 640_000 + i * 12_000);
file(`${ROOT}/DCIM/Camera/VID_20260301_184502.mp4`, 184_000_000);
file(`${ROOT}/Download/report-q3.pdf`, 1_820_000);
file(`${ROOT}/Download/setup-1.4.2.apk`, 48_300_000);
file(`${ROOT}/Download/backup-2026-09.zip`, 312_000_000);
file(`${ROOT}/Download/Фильм 2024 (1080p).mkv`, 2_400_000_000);
file(`${ROOT}/Music/Дорога домой.mp3`, 7_900_000);
file(`${ROOT}/Music/ambient-02.flac`, 38_500_000);
file(`${ROOT}/Documents/Работа/бюджет.xlsx`, 84_000);
file(`${ROOT}/Documents/Работа/договор.docx`, 129_000);
file(`${ROOT}/Documents/заметки.txt`, 214, { text: 'Купить хлеб\nПозвонить в сервис\nОбновить FileDeck\n' });
file(`${ROOT}/Documents/config.json`, 58, { text: '{\n  "theme": "dark",\n  "sync": false\n}\n' });
file(`${ROOT}/Documents/README.md`, 120, { text: '# Проект\n\nОписание проекта.\n' });
file(`${ROOT}/Download/report-q3 (1).pdf`, 1_820_000);
file(`${ROOT}/Documents/Работа/report-q3.pdf`, 1_820_000);
file(`${ROOT}/Download/setup-1.4.2 (1).apk`, 48_300_000);
file(`${ROOT}/DCIM/Camera/IMG_20260115_112233 (1).jpg`, 2_490_000);
file(`${ROOT}/Telegram/IMG_20260115_112233.jpg`, 2_490_000);
for (let i = 2; i <= 4; i++) file(`${ROOT}/Pictures/Screenshots/Screenshot_${i}_edit.png`, 640_000 + i * 12_000);
file(`${ROOT}/Download/installer-old.apk`, 22_000_000);
file(`${ROOT}/Download/video-part.crdownload`, 54_000_000);
file(`${ROOT}/Download/export.tmp`, 3_100_000);
file(`${ROOT}/Documents/debug.log`, 780_000);
file(`${ROOT}/Pictures/.thumbnails/t1.dat`, 18_000_000);
file(`${ROOT}/Telegram/cache/c1.bin`, 96_000_000);
file(`${ROOT}/Documents/пусто.txt`, 0, { text: '' });
file(`${ROOT}/Download/пустой файл.dat`, 0);
dir(`${ROOT}/Documents/Старое`); dir(`${ROOT}/Documents/Старое/2019`);
dir(`${ROOT}/Download/Новая папка`);
file(`${ROOT}/Documents/secret.zip`, 120_000);
file(`${ROOT}/Download/archive-2025.7z`, 88_000_000);
file(`${ROOT}/Download/photos.rar`, 41_000_000);
dir(`${SD}/Backup`);
file(`${SD}/Backup/photos-2025.zip`, 1_200_000_000);
file(`${SD}/video.mp4`, 920_000_000);

const wait = (v) => new Promise((r) => setTimeout(() => r(v), 60));
const err = (m) => Promise.reject(new Error(m));
const childrenOf = (p) => [...fs.entries()].filter(([k]) => k !== p && parentOf(k) === p);
const describe = (p, n) => ({
  name: basename(p),
  path: p,
  dir: n.dir,
  size: n.dir ? 0 : n.size,
  mtime: n.mtime,
  ...(n.dir ? { count: childrenOf(p).length } : { mime: guessMime(p) }),
});
const MIME = { jpg: 'image/jpeg', png: 'image/png', mp4: 'video/mp4', mp3: 'audio/mpeg', txt: 'text/plain', json: 'application/json', md: 'text/plain', pdf: 'application/pdf' };
const guessMime = (p) => MIME[extOf(p)] || 'application/octet-stream';
const underPrefix = (k, p) => k === p || k.startsWith(p + '/');
const unique = (dest, name) => {
  let t = join(dest, name);
  if (!fs.has(t)) return t;
  const dot = name.lastIndexOf('.');
  const base = dot > 0 ? name.slice(0, dot) : name;
  const ext = dot > 0 ? name.slice(dot) : '';
  for (let i = 1; i < 999; i++) {
    t = join(dest, `${base} (${i})${ext}`);
    if (!fs.has(t)) return t;
  }
  return t;
};

function transfer(paths, dest, move) {
  const failed = [];
  let done = 0;
  for (const p of paths) {
    if (fs.get(p)?.dir && underPrefix(dest, p)) { failed.push(p); continue; }
    if (move && parentOf(p) === dest) { done++; continue; }
    const target = unique(dest, basename(p));
    for (const [k, v] of [...fs.entries()].filter(([k]) => underPrefix(k, p))) {
      fs.set(target + k.slice(p.length), { ...v });
      if (move) fs.delete(k);
    }
    done++;
  }
  return wait({ done, failed });
}

export function createMock() {
  return {
    checkPermission: () => wait({ granted: true }),
    requestPermission: () => wait({ granted: true }),
    getRoots: () => wait({
      roots: [
        { name: 'Внутренняя память', path: ROOT, primary: true, total: 128e9, free: 41.6e9 },
        { name: 'SD-карта', path: SD, primary: false, total: 64e9, free: 38.2e9 },
      ],
    }),
    list: ({ path }) => {
      const n = fs.get(path);
      if (!n) return err('Папка не существует');
      if (!n.dir) return err('Это не папка');
      return wait({ entries: childrenOf(path).map(([k, v]) => describe(k, v)) });
    },
    search: ({ path, query, limit = 400 }) => {
      const q = (query || '').toLowerCase().trim();
      const entries = q ? [...fs.entries()].filter(([k]) => k !== path && underPrefix(k, path) && basename(k).toLowerCase().includes(q)).slice(0, limit).map(([k, v]) => describe(k, v)) : [];
      return wait({ entries, truncated: false });
    },
    size: ({ path }) => {
      let size = 0, files = 0, folders = 0;
      for (const [k, v] of fs) if (k !== path && underPrefix(k, path)) v.dir ? folders++ : (files++, (size += v.size));
      if (!fs.get(path)?.dir) size = fs.get(path)?.size || 0;
      return wait({ size, files, folders });
    },
    mkdir: ({ path }) => (fs.has(path) ? err('Папка с таким именем уже есть') : (dir(path), wait({ path }))),
    createFile: ({ path }) => (fs.has(path) ? err('Файл с таким именем уже есть') : (file(path, 0, { text: '' }), wait({ path }))),
    rename: ({ path, name }) => {
      if (!name || name.includes('/')) return err('Недопустимое имя');
      const target = join(parentOf(path), name);
      if (fs.has(target)) return err('Элемент с таким именем уже существует');
      for (const [k, v] of [...fs.entries()].filter(([k]) => underPrefix(k, path))) {
        fs.set(target + k.slice(path.length), v);
        fs.delete(k);
      }
      return wait({ path: target });
    },
    remove: ({ paths }) => {
      paths.forEach((p) => [...fs.keys()].filter((k) => underPrefix(k, p)).forEach((k) => fs.delete(k)));
      return wait({ done: paths.length, failed: [] });
    },
    copy: ({ paths, dest }) => transfer(paths, dest, false),
    move: ({ paths, dest }) => transfer(paths, dest, true),
    readText: ({ path }) => {
      const n = fs.get(path);
      return n ? wait({ text: n.text ?? '' }) : err('Файл не найден');
    },
    writeText: ({ path, text }) => {
      const n = fs.get(path);
      if (n) { n.text = text; n.size = new Blob([text]).size; n.mtime = Date.now(); }
      return wait();
    },
    appInfo: () => wait({ package: 'dev.filedeck.app', versionName: typeof __APP_VERSION__ === 'string' ? __APP_VERSION__ : 'dev', versionCode: typeof __APP_BUILD__ === 'number' ? __APP_BUILD__ : 0, signature: 'DB:51:0F:3E:24:09:94:FF:3D:A3:D6:95:2B:08:AB:9C:E7:EF:7B:C9:07:81:6E:E6:99:40:DD:10:98:17:75:1F', firstInstall: Date.now() - 20 * DAY, lastUpdate: Date.now() - DAY, sdk: 34 }),
    open: ({ path }) => { console.info('[mock] open', path); return wait(); },
    share: ({ paths }) => { console.info('[mock] share', paths); return wait(); },
  };
}

// ============================================================ мок очистки и корзины
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const emitter = () => {
  const map = {};
  return {
    emit: (ev, data) => (map[ev] || []).forEach((cb) => cb(data)),
    addListener: (ev, cb) => { (map[ev] ||= []).push(cb); return Promise.resolve({ remove: async () => { map[ev] = map[ev].filter((x) => x !== cb); } }); },
  };
};

export function createMockCleaner() {
  const ev = emitter();
  let cancelled = false;
  const trash = [];
  const stripCopy = (n) => n.replace(/ \(\d+\)(?=\.[^.]+$|$)/, '').replace(/_edit(?=\.[^.]+$)/, '');
  const underRoots = (k, roots) => roots.some((r) => k === r || k.startsWith(r + '/'));

  return {
    addListener: ev.addListener,
    cancelScan: async () => { cancelled = true; },
    async scan(o) {
      cancelled = false;
      const roots = o.roots;
      const all = [...fs.entries()].filter(([k]) => underRoots(k, roots) && !k.includes('/.FileDeckTrash'));
      const files = all.filter(([, v]) => !v.dir);
      const steps = 12;
      for (let i = 1; i <= steps; i++) {
        if (cancelled) return { cancelled: true };
        ev.emit('scanProgress', { phase: 'walk', files: Math.round((files.length * i) / steps) * 37, folders: i * 11, bytes: i * 4e8, current: roots[0] });
        await sleep(70);
      }
      const bySize = new Map();
      for (const [k, v] of files) if (v.size >= (o.minDuplicateSize ?? 1024) && o.duplicates !== false) (bySize.get(v.size + '|' + extOf(k)) || bySize.set(v.size + '|' + extOf(k), []).get(v.size + '|' + extOf(k))).push([k, v]);
      const duplicates = [];
      for (const list of bySize.values()) {
        const byName = new Map();
        for (const [k, v] of list) { const key = stripCopy(basename(k)); (byName.get(key) || byName.set(key, []).get(key)).push([k, v]); }
        for (const g of byName.values()) if (g.length > 1) duplicates.push({ size: g[0][1].size, count: g.length, files: g.map(([k, v]) => ({ path: k, mtime: v.mtime })) });
      }
      duplicates.sort((a, b) => b.size * (b.count - 1) - a.size * (a.count - 1));
      if (o.duplicates !== false) for (let i = 0; i <= 10; i++) { if (cancelled) return { cancelled: true }; ev.emit('scanProgress', { phase: 'hash', files: files.length * 37, folders: 120, bytes: 9e9, done: i, total: 10 }); await sleep(60); }
      let similar = [];
      if (o.similar) {
        for (let i = 0; i <= 10; i++) { if (cancelled) return { cancelled: true }; ev.emit('scanProgress', { phase: 'images', files: files.length * 37, folders: 120, bytes: 9e9, done: i * 20, total: 200 }); await sleep(60); }
        const shots = files.filter(([k]) => k.includes('/Pictures/Screenshots/') && !k.includes('_edit')).slice(0, 3);
        const edits = files.filter(([k]) => k.includes('_edit'));
        similar = shots.length >= 2 ? [{ files: [...shots, ...edits.slice(0, 1)].map(([k, v], i) => ({ path: k, size: v.size, mtime: v.mtime, w: i === 0 ? 1080 : 720, h: i === 0 ? 2400 : 1600 })) }] : [];
      }
      const emptyFolders = [];
      for (const [k, v] of all) if (v.dir && k !== roots.find((r) => k === r) && !all.some(([k2]) => k2 !== k && k2.startsWith(k + '/')) && !/\/(DCIM|Download|Pictures|Music|Movies|Documents|Android)$/.test(k) && o.emptyFolders !== false) emptyFolders.push(k);
      const zeroFiles = o.zeroFiles === false ? [] : files.filter(([, v]) => v.size === 0).map(([k]) => k);
      const junkRe = /\.(tmp|crdownload|part|log)$/i;
      const junk = o.junk === false ? [] : [
        ...files.filter(([k]) => junkRe.test(k)).map(([k, v]) => ({ path: k, size: v.size, dir: false, kind: /\.log$/i.test(k) ? 'logs' : /\.(crdownload|part)$/i.test(k) ? 'partial' : 'temp' })),
        { path: `${ROOT}/Pictures/.thumbnails`, size: 18_000_000, dir: true, kind: 'thumbnails' },
        { path: `${ROOT}/Telegram/cache`, size: 96_000_000, dir: true, kind: 'cache' },
      ];
      const largest = o.largest === false ? [] : files.filter(([, v]) => v.size >= 1e6).sort((a, b) => b[1].size - a[1].size).slice(0, 50).map(([k, v]) => ({ path: k, size: v.size, mtime: v.mtime }));
      const apks = o.apks === false ? [] : files.filter(([k]) => /\.apk$/i.test(k)).map(([k, v]) => ({ path: k, size: v.size, mtime: v.mtime, pkg: 'com.example.app', version: '1.4.2', label: 'Example App' }));
      const kinds = { images: /\.(jpe?g|png|webp)$/i, video: /\.(mp4|mkv|avi)$/i, audio: /\.(mp3|flac)$/i, docs: /\.(pdf|docx?|xlsx?|txt|md)$/i, archives: /\.(zip|7z|rar)$/i, apk: /\.apk$/i };
      const usage = Object.fromEntries(['images', 'video', 'audio', 'docs', 'archives', 'apk', 'other'].map((k) => [k, { count: 0, bytes: 0 }]));
      for (const [k, v] of files) { const kind = Object.keys(kinds).find((n) => kinds[n].test(k)) || 'other'; usage[kind].count++; usage[kind].bytes += v.size; }
      return { duplicates, similar, emptyFolders, zeroFiles, junk, largest, apks, usage, stats: { files: files.length, folders: all.length - files.length, bytes: files.reduce((a, [, v]) => a + v.size, 0), ms: 840 } };
    },
    async trashMove({ paths }) {
      let done = 0, bytes = 0; const failed = [];
      for (const p of paths) {
        const nodes = [...fs.entries()].filter(([k]) => underPrefix(k, p));
        if (!nodes.length) { failed.push(p); continue; }
        const size = nodes.reduce((a, [, v]) => a + (v.dir ? 0 : v.size), 0);
        trash.push({ id: 'm' + Math.random().toString(36).slice(2), name: basename(p), orig: p, time: Date.now(), size, dir: !!fs.get(p).dir, nodes });
        nodes.forEach(([k]) => fs.delete(k)); done++; bytes += size;
      }
      await sleep(80); return { done, bytes, failed };
    },
    async trashList() { await sleep(30); const items = trash.map(({ nodes, ...rest }) => rest).sort((a, b) => b.time - a.time); return { items, count: items.length, bytes: items.reduce((a, i) => a + i.size, 0) }; },
    async trashRestore({ ids }) {
      let done = 0; const restored = [];
      for (const id of ids) {
        const i = trash.findIndex((t) => t.id === id); if (i < 0) continue;
        const t = trash[i]; let target = t.orig; if (fs.has(target)) target = unique(parentOf(t.orig), basename(t.orig));
        for (const [k, v] of t.nodes) fs.set(target + k.slice(t.orig.length), v);
        trash.splice(i, 1); restored.push(target); done++;
      }
      await sleep(80); return { done, failed: [], restored };
    },
    async trashDelete({ ids }) { let done = 0, bytes = 0; for (const id of ids) { const i = trash.findIndex((t) => t.id === id); if (i >= 0) { bytes += trash[i].size; trash.splice(i, 1); done++; } } await sleep(60); return { done, bytes }; },
    async trashEmpty() { const done = trash.length, bytes = trash.reduce((a, t) => a + t.size, 0); trash.length = 0; await sleep(60); return { done, bytes }; },
    async trashPurge({ days = 30 }) { const lim = Date.now() - days * DAY; let done = 0; for (let i = trash.length - 1; i >= 0; i--) if (trash[i].time < lim) { trash.splice(i, 1); done++; } return { done, bytes: 0 }; },
  };
}

// ============================================================ мок архиватора
const archiveError = (code, message) => Object.assign(new Error(message), { code });
export function createMockArchive() {
  const ev = emitter();
  let cancelled = false;
  const contents = new Map(); // путь архива -> [{name,dir,size}]
  const fake = (path) => contents.get(path) || (/\.rar$/i.test(path) ? null : [
    { name: 'docs', dir: true, size: 0 }, { name: 'docs/readme.txt', dir: false, size: 1200 }, { name: 'docs/manual.pdf', dir: false, size: 2_400_000 },
    { name: 'photos', dir: true, size: 0 }, { name: 'photos/2026', dir: true, size: 0 }, { name: 'photos/2026/IMG_001.jpg', dir: false, size: 3_100_000 },
    { name: 'photos/2026/IMG_002.jpg', dir: false, size: 2_900_000 }, { name: 'notes.txt', dir: false, size: 340 },
  ]);
  const progress = async (op, names) => { for (let i = 1; i <= 8; i++) { if (cancelled) throw 'cancel'; ev.emit('archiveProgress', { op, fraction: i / 8, current: names[i % names.length] }); await sleep(90); } };
  const needPw = (path, pw) => { if (/secret/i.test(path)) { if (!pw) throw archiveError('PASSWORD_REQUIRED', 'Архив защищён паролем'); if (pw !== '1234') throw archiveError('WRONG_PASSWORD', 'Неверный пароль'); } };
  return {
    addListener: ev.addListener,
    cancel: async () => { cancelled = true; },
    async create({ paths, dest, name, format = 'zip', password }) {
      cancelled = false;
      if (password && format !== 'zip') throw archiveError('PASSWORD_UNSUPPORTED', 'Пароль поддерживается только для ZIP');
      const ext = { zip: '.zip', '7z': '.7z', tar: '.tar', 'tar.gz': '.tar.gz', 'tar.bz2': '.tar.bz2', 'tar.xz': '.tar.xz' }[format];
      const base = name.replace(/\.(zip|7z|tar(\.(gz|bz2|xz))?)$/i, '');
      const target = unique(dest, base + ext);
      const entries = []; let size = 0;
      for (const p of paths) for (const [k, v] of fs) if (underPrefix(k, p)) { entries.push({ name: basename(p) + k.slice(p.length), dir: v.dir, size: v.dir ? 0 : v.size }); size += v.dir ? 0 : v.size; }
      try { await progress('create', entries.map((e) => e.name)); } catch { return { cancelled: true }; }
      file(target, Math.round(size * 0.6) + 200);
      contents.set(target, entries);
      return { path: target, size: Math.round(size * 0.6), items: entries.length };
    },
    async list({ path, password }) {
      cancelled = false; await sleep(150);
      const entries = fake(path);
      const secret = /secret/i.test(path);
      if (entries === null) return { format: 'RAR', entries: [], count: 0, bytes: 0, encrypted: false, listing: false, truncated: false };
      void password;
      return { format: /7z$/i.test(path) ? 'SEVENZ' : 'ZIP', entries: entries.map((e) => ({ ...e, mtime: Date.now() - 5 * DAY })), count: entries.length, bytes: entries.reduce((a, e) => a + e.size, 0), encrypted: secret, listing: true, truncated: false };
    },
    async extract({ path, dest, password, mode = 'folder', entries: only = [] }) {
      cancelled = false; needPw(path, password);
      const list = fake(path) || [{ name: 'from-rar.txt', dir: false, size: 100 }];
      const picked = list.filter((e) => !only.length || only.some((o) => e.name === o || e.name.startsWith(o + '/')));
      try { await progress('extract', picked.map((e) => e.name)); } catch { return { cancelled: true }; }
      const base = basename(path).replace(/\.[^.]+$/, '');
      const root = mode === 'here' ? dest : unique(dest, base);
      if (mode !== 'here') dir(root);
      let count = 0;
      for (const e of picked) { const p = join(root, e.name); if (e.dir) dir(p); else { const parent = parentOf(p); if (!fs.has(parent)) dir(parent); file(p, e.size); count++; } }
      return { path: root, count };
    },
  };
}

/** Заглушка превью изображений в браузере: цветной градиент по хэшу имени. */
export function mockThumb(path) {
  let h = 0;
  for (const c of path) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const a = h % 360;
  const b = (a + 50) % 360;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${a} 60% 45%)"/><stop offset="1" stop-color="hsl(${b} 70% 30%)"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/><circle cx="${100 + (h % 200)}" cy="110" r="46" fill="rgba(255,255,255,.25)"/></svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}
