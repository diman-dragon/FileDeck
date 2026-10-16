// Мок архиватора.
import { basename, parentOf, join } from '../path.js';
import { DAY, dir, file, fs } from './seed.js';
import { underPrefix, unique } from './helpers.js';
import { emitter, sleep } from './events.js';

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
