// Откладывает вызов fn, пока не пройдёт ms миллисекунд без новых вызовов.

export function debounce(fn, ms) {
  let t;
  return (...a) => {
    clearTimeout(t);
    t = setTimeout(() => fn(...a), ms);
  };
}
