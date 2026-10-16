// Мок глубокой очистки (сканирование).
import { basename, extOf } from '../path.js';
import { ROOT, fs } from './seed.js';
import { emitter, sleep } from './events.js';

export function createMockCleaner() {
  const ev = emitter();
  let cancelled = false;
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
  };
}
