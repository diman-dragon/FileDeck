// Тип файла по расширению: значок, подпись, можно ли править как текст.
import { extOf } from './path.js';

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
