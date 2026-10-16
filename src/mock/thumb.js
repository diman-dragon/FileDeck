// Миниатюры-заглушки для превью в браузере.

/** Заглушка превью изображений в браузере: цветной градиент по хэшу имени. */
export function mockThumb(path) {
  let h = 0;
  for (const c of path) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  const a = h % 360;
  const b = (a + 50) % 360;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="hsl(${a} 60% 45%)"/><stop offset="1" stop-color="hsl(${b} 70% 30%)"/></linearGradient></defs><rect width="400" height="300" fill="url(#g)"/><circle cx="${100 + (h % 200)}" cy="110" r="46" fill="rgba(255,255,255,.25)"/></svg>`;
  return 'data:image/svg+xml;utf8,' + encodeURIComponent(svg);
}
