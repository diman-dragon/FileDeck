// Мок корзины.
import { basename, parentOf } from '../path.js';
import { DAY, fs } from './seed.js';
import { underPrefix, unique } from './helpers.js';
import { emitter, sleep } from './events.js';

export function createMockTrash() {
  const ev = emitter();
  let cancelled = false;
  const trash = [];
  const mockTrashDelete = async (ids) => {
    cancelled = false;
    let done = 0, bytes = 0;
    for (const id of ids) {
      if (cancelled) return { done, bytes, cancelled: true };
      const i = trash.findIndex((t) => t.id === id); if (i < 0) continue;
      const t = trash[i];
      ev.emit('progress', { fraction: (done + 0.5) / ids.length, current: t.name }); await sleep(250);
      bytes += t.size; trash.splice(i, 1); done++;
      ev.emit('progress', { fraction: done / ids.length, current: t.name });
    }
    return { done, bytes, cancelled: false };
  };

  return {
    addListener: ev.addListener,
    async move({ paths }) {
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
    async list() { await sleep(30); const items = trash.map(({ nodes, ...rest }) => rest).sort((a, b) => b.time - a.time); return { items, count: items.length, bytes: items.reduce((a, i) => a + i.size, 0) }; },
    async restore({ ids }) {
      let done = 0; const restored = [];
      cancelled = false;
      for (const [n, id] of ids.entries()) {
        if (cancelled) return { done, failed: [], restored, cancelled: true };
        const i = trash.findIndex((t) => t.id === id); if (i < 0) continue;
        const t = trash[i]; let target = t.orig; if (fs.has(target)) target = unique(parentOf(t.orig), basename(t.orig));
        for (const [k, v] of t.nodes) fs.set(target + k.slice(t.orig.length), v);
        trash.splice(i, 1); restored.push(target); done++;
        ev.emit('progress', { fraction: (n + 1) / ids.length, current: basename(t.orig) }); await sleep(250);
      }
      return { done, failed: [], restored, cancelled: false };
    },
    async remove({ ids }) { return mockTrashDelete(ids); },
    async empty() { return mockTrashDelete(trash.map((t) => t.id)); },
    async cancel() { cancelled = true; },
    async purge({ days = 30 }) { const lim = Date.now() - days * DAY; let done = 0; for (let i = trash.length - 1; i >= 0; i--) if (trash[i].time < lim) { trash.splice(i, 1); done++; } return { done, bytes: 0 }; },
  };
}
