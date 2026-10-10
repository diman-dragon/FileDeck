// Диагностика окружения и подписи:  npm run doctor            (проверка debug/sideload-ключа)
//                                   npm run doctor -- --release (с учётом личного ключа)
// Если к компьютеру подключён телефон, скажет, обновится ли новая сборка поверх установленной.
import { existsSync, readFileSync, mkdtempSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
import { APP_ID, run, projectKeys, findSdk, prettyFp, apkFingerprint } from './lib/signing.mjs';

const root = process.cwd();
const wantRelease = process.argv.includes('--release');
const pkg = JSON.parse(readFileSync(join(root, 'package.json'), 'utf8'));
const [maj, min, pat] = pkg.version.split('.').map(Number);
const versionCode = maj * 10000 + min * 100 + pat;
let problems = 0;
const ok = (m) => console.log(`  ✔ ${m}`);
const warn = (m) => console.log(`  ! ${m}`);
const bad = (m) => { problems++; console.log(`  ✘ ${m}`); };

console.log(`\nFileDeck ${pkg.version} (versionCode ${versionCode}), applicationId ${APP_ID}\n`);

console.log('Окружение');
const nodeMajor = Number(process.versions.node.split('.')[0]);
nodeMajor >= 22 ? ok(`Node ${process.versions.node}`) : bad(`Node ${process.versions.node}: нужен 22+`);
const java = run('java', ['-version']);
const jv = java.out.match(/version "(\d+)/);
jv && Number(jv[1]) >= 21 ? ok(`Java ${jv[1]}`) : bad(java.ok ? `Java ${jv ? jv[1] : '?'}: нужен JDK 21` : 'Java не найдена (нужен JDK 21)');
const sdk = findSdk(root);
sdk ? ok(`Android SDK: ${sdk}`) : bad('Android SDK не найден: задайте ANDROID_HOME или android/local.properties (sdk.dir=...)');

console.log('\nПодпись');
const keys = projectKeys(root);
if (!keys.sideload) bad('нет android/signing/signing.properties — APK подпишется случайным ключом и не обновится поверх');
else if (keys.sideload.error) bad(`ключ sideload: ${keys.sideload.error}`);
else ok(`общий ключ (debug / CI / установка «для себя»): ${prettyFp(keys.sideload.fp)}`);
if (keys.personal) {
  keys.personal.error ? bad(`личный ключ: ${keys.personal.error}`) : ok(`личный ключ (release / Google Play): ${prettyFp(keys.personal.fp)}`);
}
const buildKey = wantRelease && keys.personal && !keys.personal.error ? keys.personal : keys.sideload;
console.log(`  → следующая сборка${wantRelease ? ' (release)' : ''} будет подписана: ${buildKey?.fp ? prettyFp(buildKey.fp) : 'неизвестно'}`);

console.log('\nУстройство');
const adb = run('adb', ['devices']);
const devices = adb.ok ? adb.out.split('\n').slice(1).filter((l) => /\tdevice$/.test(l.trim() ? l : '')) : [];
if (!adb.ok) warn('adb не найден — проверка телефона пропущена (он входит в Android SDK platform-tools)');
else if (devices.length === 0) warn('телефон не подключён (включите «Отладку по USB»), проверка пропущена');
else {
  ok(`подключено устройств: ${devices.length}`);
  const dump = run('adb', ['shell', 'dumpsys', 'package', APP_ID]);
  const vc = dump.out.match(/versionCode=(\d+)/);
  const vn = dump.out.match(/versionName=([^\s]+)/);
  if (!vc) ok('FileDeck на телефоне не установлен — установится чисто');
  else {
    const installedCode = Number(vc[1]);
    ok(`на телефоне: ${vn ? vn[1] : '?'} (versionCode ${installedCode})`);
    if (installedCode > versionCode) bad(`на телефоне версия НОВЕЕ (${installedCode} > ${versionCode}): Android не даст понизить версию. Поднимите версию (npm run bump) или удалите приложение`);
    // подпись установленного приложения
    const path = run('adb', ['shell', 'pm', 'path', APP_ID]).out.match(/package:(\S+base\.apk)/);
    if (path && buildKey?.fp) {
      const tmp = join(mkdtempSync(join(tmpdir(), 'fd-')), 'installed.apk');
      const pull = run('adb', ['pull', path[1], tmp]);
      if (pull.ok && existsSync(tmp)) {
        const inst = apkFingerprint(tmp, root);
        if (inst.fp) {
          console.log(`  → подпись установленного приложения: ${prettyFp(inst.fp)}`);
          if (inst.fp === buildKey.fp) ok(installedCode < versionCode ? 'подписи совпадают — новая сборка ОБНОВИТ приложение поверх, данные сохранятся' : 'подписи совпадают (версия та же — используйте adb install -r)');
          else bad(`ПОДПИСИ РАЗНЫЕ — Android откажется обновлять («Приложение не установлено»). Удалите старую версию один раз: adb uninstall ${APP_ID}  — дальше все обновления пойдут поверх`);
        } else warn(inst.error);
      } else warn('не удалось скопировать установленный APK с телефона');
    } else warn('подпись установленного приложения не проверена');
  }
}
console.log(problems ? `\nНайдено проблем: ${problems}\n` : '\nВсё в порядке.\n');
process.exit(problems ? 1 : 0);
