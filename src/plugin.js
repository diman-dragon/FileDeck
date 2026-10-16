// Реестр нативных плагинов. На телефоне это Java-классы, в браузере — моки из src/mock/.
import { registerPlugin } from '@capacitor/core';
import { createMock } from './mock/file-manager.js';
import { createMockCleaner } from './mock/cleaner.js';
import { createMockTrash } from './mock/trash.js';
import { createMockArchive } from './mock/archive.js';

/** Файлы: список, копирование, поиск, чтение/запись текста (Java: FileManagerPlugin). */
export const FM = registerPlugin('FileManager', { web: async () => createMock() });

/** Глубокая очистка: поиск дубликатов, мусора и крупных файлов (Java: CleanerPlugin). */
export const CL = registerPlugin('Cleaner', { web: async () => createMockCleaner() });

/** Корзина: перенос, список, восстановление, удаление с прогрессом (Java: TrashPlugin). */
export const TR = registerPlugin('Trash', { web: async () => createMockTrash() });

/** Архиватор: ZIP / 7z / TAR / RAR… (Java: ArchivePlugin). */
export const AR = registerPlugin('Archive', { web: async () => createMockArchive() });
