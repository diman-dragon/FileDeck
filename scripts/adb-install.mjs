// Установка APK на подключённое устройство ПОВЕРХ предыдущей версии (adb install -r).
//   npm run install:device
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';

const { version } = JSON.parse(readFileSync('package.json', 'utf8'));
const candidates = [`release/filedeck-${version}-debug.apk`, `release/filedeck-${version}.apk`];
const apk = candidates.find((p) => existsSync(p));
if (!apk) {
  console.error(`APK версии ${version} не найден в release/. Сначала соберите: npm run apk`);
  process.exit(1);
}
console.log(`Устанавливаю ${apk} (обновление поверх текущей версии)…`);
const res = spawnSync('adb', ['install', '-r', apk], { stdio: 'inherit', shell: process.platform === 'win32' });
if (res.status !== 0) {
  console.error('\nЕсли ошибка INSTALL_FAILED_UPDATE_INCOMPATIBLE — на телефоне стоит версия, подписанная другим ключом.\nУдалите её один раз (adb uninstall dev.filedeck.app), дальше все обновления будут ставиться поверх.');
}
process.exit(res.status ?? 1);
