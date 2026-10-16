// Чистые функции над результатом сканирования: строки категорий, группы, «что оставить», выбор по умолчанию.
import { basename } from '../path.js';
import { CATS } from './config.js';

/** Раскладывает строки по группам (дубликаты, похожие фото). */
export const groupRows = (rows) => {
  const byGroup = new Map();
  rows.forEach((r) => (byGroup.get(r.group) || byGroup.set(r.group, []).get(r.group)).push(r));
  return byGroup;
};

export function rowsOf(result, cat) {
  const r = result;
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
}

export const countOf = (result, cat) => {
  if (!result) return 0;
  if (cat === 'duplicates') return result.duplicates.length;
  if (cat === 'similar') return result.similar.length;
  return rowsOf(result, cat).length;
};

// «лучший» экземпляр в группе: не из Загрузок, без « (1)» и «копия», в основной папке, старше
export function keeper(list) {
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

/** Сколько байт освободится, если оставить в каждой группе только «лучший» файл. */
export const wasteOf = (result, cat) => {
  const rows = rowsOf(result, cat);
  if (cat === 'duplicates' || cat === 'similar') {
    let sum = 0;
    groupRows(rows).forEach((list) => {
      const keep = keeper(list);
      sum += list.filter((x) => x !== keep).reduce((a, x) => a + x.size, 0);
    });
    return sum;
  }
  return rows.reduce((a, r) => a + r.size, 0);
};

/** Что отмечено сразу после сканирования: лишние копии, мусор, пустые папки и файлы. */
export function defaultSelection(result) {
  const sel = {};
  for (const c of CATS) {
    const rows = rowsOf(result, c.id);
    const set = new Set();
    if (c.group) {
      groupRows(rows).forEach((list) => {
        const keep = keeper(list);
        list.forEach((r) => r !== keep && set.add(r.path));
      });
    } else if (c.id === 'junk' || c.id === 'emptyFolders' || c.id === 'zeroFiles') rows.forEach((r) => set.add(r.path));
    sel[c.id] = set;
  }
  return sel;
}

// удалённое убираем из всех списков (файл может числиться сразу в нескольких категориях)
export function purgeResult(result, sel, paths) {
  const gone = new Set(paths);
  const hit = (p) => gone.has(p) || [...gone].some((g) => p.startsWith(g + '/'));
  const r = result;
  r.duplicates = r.duplicates.map((g) => ({ ...g, files: g.files.filter((f) => !hit(f.path)) })).filter((g) => g.files.length > 1);
  r.similar = r.similar.map((g) => ({ ...g, files: g.files.filter((f) => !hit(f.path)) })).filter((g) => g.files.length > 1);
  r.junk = r.junk.filter((j) => !hit(j.path));
  r.apks = r.apks.filter((a) => !hit(a.path));
  r.largest = r.largest.filter((a) => !hit(a.path));
  r.emptyFolders = r.emptyFolders.filter((p) => !hit(p));
  r.zeroFiles = r.zeroFiles.filter((p) => !hit(p));
  for (const c of CATS) {
    const valid = new Set(rowsOf(result, c.id).map((x) => x.path));
    sel[c.id] = new Set([...(sel[c.id] || [])].filter((p) => valid.has(p)));
  }
}
