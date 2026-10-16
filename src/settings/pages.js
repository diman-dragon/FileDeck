// Страницы настроек: заголовок вкладки и сборка блоков в порядке показа.
import { generalPage } from './sections/general.js';
import { filesBlock } from './sections/files.js';
import { trashBlock } from './sections/trash.js';
import { aboutCard } from './sections/about.js';
import { accessBlock, resetBlock } from './sections/system.js';

export const PAGES = [
  { id: 'general', title: 'Общие', html: generalPage },
  { id: 'files', title: 'Файлы', html: (ctx) => filesBlock(ctx) + trashBlock(ctx) },
  { id: 'about', title: 'О нас', html: (ctx) => aboutCard(ctx) + accessBlock(ctx) + resetBlock() },
];
