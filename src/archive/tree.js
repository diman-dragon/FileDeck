// Дерево папок и файлов внутри архива.

export function buildTree(entries) {
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
