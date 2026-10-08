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
    zip: ({ paths, dest, name = 'archive.zip' }) => {
      const target = unique(dest, name.endsWith('.zip') ? name : name + '.zip');
      file(target, 1_000_000 * paths.length);
      return wait({ path: target });
    },
    unzip: ({ path, dest }) => {
      const base = basename(path).replace(/\.[^.]+$/, '');
      const target = unique(dest, base);
      dir(target);
      file(join(target, 'readme.txt'), 120, { text: 'Распаковано (мок)\n' });
      return wait({ path: target });
    },
    readText: ({ path }) => {
      const n = fs.get(path);
      return n ? wait({ text: n.text ?? '' }) : err('Файл не найден');
    },
    writeText: ({ path, text }) => {
      const n = fs.get(path);
      if (n) { n.text = text; n.size = new Blob([text]).size; n.mtime = Date.now(); }
      return wait();
    },
    open: ({ path }) => { console.info('[mock] open', path); return wait(); },
    share: ({ paths }) => { console.info('[mock] share', paths); return wait(); },
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
