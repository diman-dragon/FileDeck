// Подписка на событие нативного плагина; возвращает функцию отписки.
export async function listen(plugin, event, cb) {
  const handle = await plugin.addListener(event, cb);
  return () => handle.remove();
}
