// Создаёт <div> с классом и готовой HTML-разметкой внутри.
export function el(html, cls) {
  const d = document.createElement('div');
  d.className = cls;
  d.innerHTML = html;
  return d;
}
