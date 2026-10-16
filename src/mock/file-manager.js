// Мок файлового менеджера.
import { basename, parentOf, join } from '../path.js';
import { ROOT, SD, dir, file, fs } from './seed.js';
import { childrenOf, describe, err, transfer, underPrefix, wait } from './helpers.js';

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
    open: ({ path }) => { console.info('[mock] open', path); return wait(); },
    share: ({ paths }) => { console.info('[mock] share', paths); return wait(); },
  };
}
