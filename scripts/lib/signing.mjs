// Общие помощники для проверки подписи APK и ключей. Работают без зависимостей, нужны только keytool (JDK) и, по желанию, adb/apksigner.
import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

export const APP_ID = 'dev.filedeck.app';
const isWin = process.platform === 'win32';

export function run(cmd, args, opts = {}) {
  const r = spawnSync(cmd, args, { encoding: 'utf8', shell: isWin, ...opts });
  return { ok: r.status === 0, out: `${r.stdout || ''}${r.stderr || ''}`, status: r.status, error: r.error };
}

export function readProps(file) {
  const props = {};
  if (!existsSync(file)) return props;
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const m = line.match(/^\s*([^#=\s][^=]*?)\s*=\s*(.*?)\s*$/);
    if (m) props[m[1]] = m[2];
  }
  return props;
}

export const normalizeFp = (s) => (s || '').replace(/[^0-9a-f]/gi, '').toUpperCase();
export const prettyFp = (s) => normalizeFp(s).replace(/(..)(?=.)/g, '$1:');

/** SHA-256 отпечаток сертификата в хранилище ключей. */
export function keystoreFingerprint(file, storePass, alias) {
  if (!existsSync(file)) return { error: `файл не найден: ${file}` };
  const args = ['-list', '-v', '-keystore', file, '-storepass', storePass];
  if (alias) args.push('-alias', alias);
  const r = run('keytool', args);
  if (!r.ok && !/SHA256:/i.test(r.out)) {
    return { error: r.error ? 'keytool не найден (установите JDK 21)' : r.out.split('\n').find((l) => l.trim()) || 'не удалось прочитать ключ' };
  }
  const m = r.out.match(/SHA256:\s*([0-9A-F:]+)/i);
  return m ? { fp: normalizeFp(m[1]) } : { error: 'отпечаток не найден' };
}

/** Какой ключ использует проект: личный (keystore.properties) или общий sideload. */
export function projectKeys(root = process.cwd()) {
  const android = join(root, 'android');
  const side = readProps(join(android, 'signing', 'signing.properties'));
  const personal = readProps(join(android, 'keystore.properties'));
  const out = {};
  if (side.storeFile) out.sideload = { path: join(android, side.storeFile), pass: side.storePassword, alias: side.keyAlias, ...keystoreFingerprint(join(android, side.storeFile), side.storePassword, side.keyAlias) };
  if (personal.storeFile) out.personal = { path: join(android, personal.storeFile), pass: personal.storePassword, alias: personal.keyAlias, ...keystoreFingerprint(join(android, personal.storeFile), personal.storePassword, personal.keyAlias) };
  return out;
}

export function findSdk(root = process.cwd()) {
  const env = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT;
  if (env && existsSync(env)) return env;
  const lp = readProps(join(root, 'android', 'local.properties'));
  if (lp['sdk.dir'] && existsSync(lp['sdk.dir'])) return lp['sdk.dir'];
  return null;
}

export function findApksigner(root = process.cwd()) {
  const sdk = findSdk(root);
  if (!sdk) return null;
  const bt = join(sdk, 'build-tools');
  if (!existsSync(bt)) return null;
  const versions = readdirSync(bt).sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
  for (const v of versions) {
    const p = join(bt, v, isWin ? 'apksigner.bat' : 'apksigner');
    if (existsSync(p)) return p;
  }
  return null;
}

/** SHA-256 сертификата, которым подписан APK (через apksigner). */
export function apkFingerprint(apk, root = process.cwd()) {
  const tool = findApksigner(root);
  if (!tool) return { error: 'apksigner не найден (нужен Android SDK build-tools)' };
  const r = run(tool, ['verify', '--print-certs', apk]);
  const m = r.out.match(/certificate SHA-256 digest:\s*([0-9a-f]+)/i);
  return m ? { fp: normalizeFp(m[1]) } : { error: 'не удалось прочитать подпись APK' };
}
