// Определение архивов по имени файла.

const ARCHIVE_RE = /\.(zip|7z|rar|tar|tgz|tbz2?|txz|gz|bz2|xz|jar|cbz)$/i;
const MULTI_ARCHIVE_RE = /\.tar\.(gz|bz2|xz)$/i;
export const isArchive = (name) => ARCHIVE_RE.test(name) || MULTI_ARCHIVE_RE.test(name);
export const archiveBase = (name) => name.replace(/\.tar\.(gz|bz2|xz)$/i, '').replace(/\.[^.]+$/, '');
