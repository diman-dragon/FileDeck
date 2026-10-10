const collator = new Intl.Collator('ru', { numeric: true, sensitivity: 'base' });
export const compareNames = (a, b) => collator.compare(a, b);

export function formatSize(bytes) {
  if (bytes == null || isNaN(bytes)) return '';
  if (bytes < 1024) return `${bytes} Б`;
  const units = ['КБ', 'МБ', 'ГБ', 'ТБ'];
  let v = bytes / 1024;
  let i = 0;
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
  }
  return `${v >= 100 ? v.toFixed(0) : v >= 10 ? v.toFixed(1) : v.toFixed(2)} ${units[i]}`.replace('.', ',');
}

const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
export function formatDate(ms, withTime = false) {
  if (!ms) return '';
  const d = new Date(ms);
  const now = new Date();
  const sameYear = d.getFullYear() === now.getFullYear();
  let s = `${d.getDate()} ${MONTHS[d.getMonth()]}${sameYear ? '' : ' ' + d.getFullYear()}`;
  if (withTime) s += `, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return s;
}

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
export const escapeHtml = (s) =>
  String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const KINDS = {
  image: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'avif', 'svg'],
  video: ['mp4', 'mkv', 'webm', 'avi', 'mov', '3gp', 'm4v', 'flv'],
  audio: ['mp3', 'flac', 'wav', 'ogg', 'm4a', 'aac', 'opus', 'amr', 'wma'],
  archive: ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'],
  pdf: ['pdf'],
  apk: ['apk', 'xapk', 'aab'],
  doc: ['doc', 'docx', 'odt', 'rtf', 'xls', 'xlsx', 'ods', 'ppt', 'pptx', 'odp', 'epub', 'fb2', 'csv'],
  code: ['js', 'mjs', 'ts', 'jsx', 'tsx', 'json', 'html', 'htm', 'css', 'scss', 'xml', 'java', 'kt', 'py', 'c', 'h', 'cpp', 'cs', 'go', 'rs', 'rb', 'php', 'sh', 'bat', 'sql', 'yml', 'yaml', 'toml', 'gradle', 'lua', 'swift'],
  text: ['txt', 'md', 'log', 'ini', 'conf', 'cfg', 'properties', 'env', 'gitignore', 'srt', 'nfo'],
};
const KIND_BY_EXT = Object.fromEntries(Object.entries(KINDS).flatMap(([k, list]) => list.map((e) => [e, k])));

export function kindOf(entry) {
  if (entry.dir) return 'folder';
  return KIND_BY_EXT[extOf(entry.name)] || 'file';
}

export const KIND_ICON = {
  image: 'image', video: 'video', audio: 'music', archive: 'archive', pdf: 'pdf', apk: 'apk',
  doc: 'doc', code: 'code', text: 'doc', file: 'file',
};
export const KIND_LABEL = {
  folder: 'Папка', image: 'Изображение', video: 'Видео', audio: 'Аудио', archive: 'Архив', pdf: 'PDF-документ',
  apk: 'Приложение Android', doc: 'Документ', code: 'Исходный код', text: 'Текстовый файл', file: 'Файл',
};
export const isEditable = (entry) => {
  const k = kindOf(entry);
  return (k === 'text' || k === 'code' || (entry.mime || '').startsWith('text/')) && entry.size <= 2 * 1024 * 1024;
};

export function debounce(fn, ms) {
  let t;
  return (...a) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...a), ms);
  };
}

export function sortEntries(list, { by, desc }) {
  const dir = desc ? -1 : 1;
  const cmp = {
    name: (a, b) => compareNames(a.name, b.name),
    size: (a, b) => (a.dir && b.dir ? (a.count || 0) - (b.count || 0) : a.size - b.size) || compareNames(a.name, b.name),
    date: (a, b) => a.mtime - b.mtime || compareNames(a.name, b.name),
    type: (a, b) => compareNames(extOf(a.name), extOf(b.name)) || compareNames(a.name, b.name),
  }[by];
  return [...list].sort((a, b) => {
    if (a.dir !== b.dir) return a.dir ? -1 : 1; // папки всегда сверху
    return cmp(a, b) * dir;
  });
}

const ARCHIVE_RE = /\.(zip|7z|rar|tar|tgz|tbz2?|txz|gz|bz2|xz|jar|cbz)$/i;
const MULTI_ARCHIVE_RE = /\.tar\.(gz|bz2|xz)$/i;
export const isArchive = (name) => ARCHIVE_RE.test(name) || MULTI_ARCHIVE_RE.test(name);
export const archiveBase = (name) => name.replace(/\.tar\.(gz|bz2|xz)$/i, '').replace(/\.[^.]+$/, '');
