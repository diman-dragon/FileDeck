// Общие помощники моков.
import { basename, parentOf, join, extOf } from '../path.js';
import { fs } from './seed.js';

export const wait = (v) => new Promise((r) => setTimeout(() => r(v), 60));

export const err = (m) => Promise.reject(new Error(m));

export const childrenOf = (p) => [...fs.entries()].filter(([k]) => k !== p && parentOf(k) === p);

export const describe = (p, n) => ({
  name: basename(p),
  path: p,
  dir: n.dir,
  size: n.dir ? 0 : n.size,
  mtime: n.mtime,
  ...(n.dir ? { count: childrenOf(p).length } : { mime: guessMime(p) }),
});

const MIME = { jpg: 'image/jpeg', png: 'image/png', mp4: 'video/mp4', mp3: 'audio/mpeg', txt: 'text/plain', json: 'application/json', md: 'text/plain', pdf: 'application/pdf' };

const guessMime = (p) => MIME[extOf(p)] || 'application/octet-stream';

export const underPrefix = (k, p) => k === p || k.startsWith(p + '/');

export const unique = (dest, name) => {
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

export function transfer(paths, dest, move) {
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
