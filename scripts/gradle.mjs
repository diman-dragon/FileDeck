// Кроссплатформенный запуск gradlew и копирование результата в release/ с версией в имени.
//   node scripts/gradle.mjs assembleDebug
//   node scripts/gradle.mjs assembleRelease bundleRelease
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { projectKeys, prettyFp } from './lib/signing.mjs';

const root = process.cwd();
const androidDir = join(root, 'android');
if (!existsSync(androidDir)) {
  console.error('Папка android/ не найдена. Выполните: npx cap add android');
  process.exit(1);
}
const { version } = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const isWin = process.platform === 'win32';
const args = process.argv.slice(2);
if (args.length === 0) args.push('assembleDebug');

// Перед сборкой показываем, каким ключом она будет подписана: отпечаток должен быть одинаковым
// на всех ваших машинах и в CI — именно тогда новая версия ставится поверх старой.
const isRelease = args.some((a) => /release/i.test(a));
const keys = projectKeys(root);
if (!keys.sideload || keys.sideload.error) {
  console.error(`\n✘ Ключ подписи android/signing/ недоступен: ${keys.sideload?.error || 'нет signing.properties'}\n  Без него APK не обновится поверх установленной версии. Верните папку android/signing/ из архива или репозитория.`);
  process.exit(1);
}
const used = isRelease && keys.personal && !keys.personal.error ? keys.personal : keys.sideload;
console.log(`\nПодпись: ${used === keys.personal ? 'личный ключ' : 'общий ключ (sideload)'} · SHA-256 ${prettyFp(used.fp)}\n`);

// На Linux/macOS запускаем через sh — не зависит от бита исполняемости у gradlew
const res = isWin
  ? spawnSync('gradlew.bat', args, { cwd: androidDir, stdio: 'inherit', shell: true })
  : spawnSync('sh', ['./gradlew', ...args], { cwd: androidDir, stdio: 'inherit' });

if (res.error) {
  console.error('Не удалось запустить Gradle:', res.error.message);
  process.exit(1);
}
if (res.status !== 0) process.exit(res.status ?? 1);

const out = join(root, 'release');
const artifacts = [
  ['assembleDebug', 'app/build/outputs/apk/debug/app-debug.apk', `filedeck-${version}-debug.apk`],
  ['assembleRelease', 'app/build/outputs/apk/release/app-release.apk', `filedeck-${version}.apk`],
  ['bundleRelease', 'app/build/outputs/bundle/release/app-release.aab', `filedeck-${version}.aab`],
];
for (const [task, from, name] of artifacts) {
  if (!args.includes(task)) continue;
  const src = join(androidDir, from);
  if (!existsSync(src)) continue;
  mkdirSync(out, { recursive: true });
  copyFileSync(src, join(out, name));
  console.log(`\nГотово: release/${name}`);
}
