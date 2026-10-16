// Сколько отмечено в категориях и что входит в «быструю очистку».
import { CATS } from './config.js';
import { rowsOf } from './rows.js';

export const selBytes = (c, cat) =>
  rowsOf(c.state.result, cat).filter((r) => c.state.sel[cat]?.has(r.path)).reduce((a, r) => a + r.size, 0);

/** Категории, которые можно чистить без раздумий (похожие фото — только вручную). */
export const safeCats = () => CATS.filter((x) => x.safe && x.id !== 'similar');

export const safePaths = (c) =>
  safeCats().flatMap((x) => rowsOf(c.state.result, x.id).filter((r) => c.state.sel[x.id]?.has(r.path)).map((r) => r.path));

export const safeBytes = (c) => safeCats().reduce((a, x) => a + selBytes(c, x.id), 0);
