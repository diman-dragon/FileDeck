// Поднять версию: npm run bump            (patch: 1.0.1 → 1.0.2)
//                 npm run bump minor      (1.0.1 → 1.1.0)
//                 npm run bump major      (1.0.1 → 2.0.0)
//                 npm run bump 1.4.7      (явная версия)
// versionCode в Android считается из версии автоматически (major*10000 + minor*100 + patch).
import { readFileSync, writeFileSync } from 'node:fs';

const arg = process.argv[2] || 'patch';
const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
let [maj, min, pat] = pkg.version.split('.').map(Number);
let next;
if (/^\d+\.\d+\.\d+$/.test(arg)) next = arg;
else if (arg === 'major') next = `${maj + 1}.0.0`;
else if (arg === 'minor') next = `${maj}.${min + 1}.0`;
else if (arg === 'patch') next = `${maj}.${min}.${pat + 1}`;
else {
  console.error('Использование: npm run bump [patch|minor|major|x.y.z]');
  process.exit(1);
}
const [a, b, c] = next.split('.').map(Number);
if (b > 99 || c > 99) {
  console.error('minor и patch должны быть не больше 99 (из-за формулы versionCode).');
  process.exit(1);
}
const code = a * 10000 + b * 100 + c;
const oldCode = maj * 10000 + min * 100 + pat;
if (code <= oldCode) {
  console.error(`Новая версия ${next} не больше текущей ${pkg.version} — Android не примет её как обновление.`);
  process.exit(1);
}
const prev = pkg.version;
pkg.version = next;
writeFileSync('package.json', JSON.stringify(pkg, null, 2) + '\n');
try {
  const lock = JSON.parse(readFileSync('package-lock.json', 'utf8'));
  lock.version = next;
  if (lock.packages?.['']) lock.packages[''].version = next;
  writeFileSync('package-lock.json', JSON.stringify(lock, null, 2) + '\n');
} catch { /* без lock-файла тоже ок */ }
console.log(`Версия: ${prev} → ${next} (versionCode ${oldCode} → ${code})`);
console.log('Не забудьте добавить запись в CHANGELOG.md и закоммитить.');
