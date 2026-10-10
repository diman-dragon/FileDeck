// Создаёт ЛИЧНЫЙ ключ подписи для Google Play / публичных релизов:  npm run keygen
// Пишет android/keystore.properties (в .gitignore) и android/signing/datalaw-upload.jks (в .gitignore).
import { existsSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { run, keystoreFingerprint, prettyFp } from './lib/signing.mjs';

const android = join(process.cwd(), 'android');
const propsFile = join(android, 'keystore.properties');
const jks = join(android, 'signing', 'datalaw-upload.jks');
if (existsSync(propsFile) || existsSync(jks)) {
  console.error('Личный ключ уже существует (android/keystore.properties или signing/datalaw-upload.jks). Ничего не перезаписываю.');
  process.exit(1);
}
mkdirSync(join(android, 'signing'), { recursive: true });
const pass = randomBytes(18).toString('base64url');
const gen = run('keytool', [
  '-genkeypair', '-keystore', jks, '-storetype', 'PKCS12', '-alias', 'datalaw',
  '-keyalg', 'RSA', '-keysize', '2048', '-validity', '36500',
  '-storepass', pass, '-keypass', pass, '-dname', 'CN=DataLaw, O=DataLaw, C=RU',
]);
if (!gen.ok) {
  console.error('keytool не сработал (нужен JDK 21):\n' + gen.out);
  process.exit(1);
}
writeFileSync(propsFile, `storeFile=signing/datalaw-upload.jks\nstorePassword=${pass}\nkeyAlias=datalaw\nkeyPassword=${pass}\n`);
const fp = keystoreFingerprint(jks, pass, 'datalaw').fp;
console.log(`
Личный ключ создан.
  файл:        android/signing/datalaw-upload.jks
  пароли:      android/keystore.properties
  SHA-256:     ${prettyFp(fp)}

ВАЖНО: сделайте резервную копию ОБОИХ файлов в надёжное место (менеджер паролей, зашифрованный диск).
Потеряете ключ — не сможете обновлять приложение в магазине. В git эти файлы не попадут (.gitignore).

Теперь «npm run release» и «npm run aab» подписывают сборки личным ключом.
Обычные «npm run apk» по-прежнему используют общий ключ — так обновления «для себя» не ломаются.
`);
