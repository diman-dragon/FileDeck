// Мини-эмиттер событий и задержка для моков.

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const emitter = () => {
  const map = {};
  return {
    emit: (ev, data) => (map[ev] || []).forEach((cb) => cb(data)),
    addListener: (ev, cb) => { (map[ev] ||= []).push(cb); return Promise.resolve({ remove: async () => { map[ev] = map[ev].filter((x) => x !== cb); } }); },
  };
};
