// Итоговое уведомление о результате файловой операции.
import { toast } from '../../layers/toast.js';

export function report(res, verb) {
  const failed = res.failed?.length || 0;
  if (failed) toast(`${verb}: ${res.done}, не удалось: ${failed}. Нельзя вставлять папку саму в себя.`, { error: true, ms: 4200 });
  else toast(`${verb}: ${res.done}`);
}
