// Работа с путями вида /storage/emulated/0/Папка/файл.txt.

export const extOf = (name) => {
  const i = name.lastIndexOf('.');
  return i > 0 && i < name.length - 1 ? name.slice(i + 1).toLowerCase() : '';
};

export const basename = (p) => p.replace(/\/+$/, '').split('/').pop();
export const parentOf = (p) => {
  const t = p.replace(/\/+$/, '');
  const i = t.lastIndexOf('/');
  return i <= 0 ? '/' : t.slice(0, i);
};
export const join = (dir, name) => (dir.endsWith('/') ? dir + name : `${dir}/${name}`);

/** Папка для людей: /storage/emulated/0/DCIM → «Память/DCIM», карта → «SD/…». */
export const shortLocation = (dir) =>
  dir.replace(/^\/storage\/(emulated\/0|[^/]+)/, (m, g) => (g === 'emulated/0' ? 'Память' : 'SD')) || '/';
