// Установка собранного debug-APK на подключённое устройство: npm run install:device
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';

const apk = 'android/app/build/outputs/apk/debug/app-debug.apk';
if (!existsSync(apk)) {
  console.error('APK не найден. Сначала соберите его: npm run apk');
  process.exit(1);
}
const res = spawnSync('adb', ['install', '-r', apk], { stdio: 'inherit', shell: process.platform === 'win32' });
process.exit(res.status ?? 1);
