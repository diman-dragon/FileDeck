// Стек слоёв: все окна живут в одном стеке, поэтому системная кнопка «Назад» закрывает верхнее.
const root = () => document.getElementById('layer');
const stack = [];

export function pushLayer(node, { onBack } = {}) {
  const entry = { node, onBack, closed: false };
  entry.close = () => {
    if (entry.closed) return;
    entry.closed = true;
    const i = stack.indexOf(entry);
    if (i >= 0) stack.splice(i, 1);
    node.classList.remove('open');
    node.classList.add('closing');
    setTimeout(() => node.remove(), 200);
  };
  root().appendChild(node);
  stack.push(entry);
  requestAnimationFrame(() => requestAnimationFrame(() => node.classList.add('open')));
  return entry.close;
}

/** true — «Назад» обработана слоем. */
export function handleBack() {
  const top = stack[stack.length - 1];
  if (!top) return false;
  if (top.onBack) top.onBack();
  else top.close();
  return true;
}
export const hasLayers = () => stack.length > 0;
