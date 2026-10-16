// Фирменная «манильная» папка для списков и сеток.
/** Фирменная манильная папка: корешок-вкладка + передняя стенка. */
export function folderGlyph(size = 40, open = false) {
  return `<svg class="folder-glyph" width="${size}" height="${size}" viewBox="0 0 40 40" aria-hidden="true">
    <path d="M4 10a3 3 0 0 1 3-3h8.2a3 3 0 0 1 2.2 1l1.6 1.8a1 1 0 0 0 .75.35H33a3 3 0 0 1 3 3V30a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3Z" fill="var(--manila-deep)"/>
    <path d="M4 17a3 3 0 0 1 3-3h26a3 3 0 0 1 3 3v13a3 3 0 0 1-3 3H7a3 3 0 0 1-3-3Z" fill="var(--manila)"/>
    ${open ? '' : '<path d="M4 17.5h32" stroke="rgba(255,255,255,.28)" stroke-width="1"/>'}
  </svg>`;
}
