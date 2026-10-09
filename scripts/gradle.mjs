// Кроссплатформенный запуск gradlew: node scripts/gradle.mjs assembleDebug
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

const androidDir = join(process.cwd(), 'android');
if (!existsSync(androidDir)) {
  console.error('Папка android/ не найдена. Выполните: npx cap add android');
  process.exit(1);
}
const isWin = process.platform === 'win32';
// На Linux/macOS запускаем через sh: не зависит от бита исполняемости у gradlew
const cmd = isWin ? 'gradlew.bat' : 'sh';
const args = process.argv.slice(2);
if (args.length === 0) args.push('assembleDebug');
const full = isWin ? args : ['./gradlew', ...args];

const res = spawnSync(cmd, full, { cwd: androidDir, stdio: 'inherit', shell: isWin });
if (res.error) {
  console.error('Не удалось запустить Gradle:', res.error.message);
  process.exit(1);
}
if (res.status === 0 && args.includes('assembleDebug')) {
  console.log('\nГотово: android/app/build/outputs/apk/debug/app-debug.apk');
}
process.exit(res.status ?? 1);