# Публикация FileDeck

## 1. Обновления поверх установленной версии

Подробности и диагностика — в `docs/SIGNING.md`.

Android ставит APK как **обновление**, если одновременно совпадают три вещи:

1. `applicationId` — `dev.filedeck.app` (не менять после первой публикации!);
2. подпись — один и тот же ключ;
3. `versionCode` новой версии больше прежней.

В проекте это сделано так:

- версия живёт только в `package.json`, `versionCode` считается автоматически (2.0.0 → 20000);
- `npm run bump` поднимает патч-версию (`bump minor`, `bump major`, `bump 1.4.0` — тоже можно);
- debug- и CI-сборки подписываются ключом из репозитория `android/signing/` — на любой машине
  подпись одинаковая, поэтому `npm run install:device` (`adb install -r`) и ручная установка APK
  обновляют приложение без потери настроек;
- если на телефоне стоит 1.0.0, собранная с другим (автоматическим debug) ключом, её нужно удалить
  **один раз**. Дальше всё обновляется поверх.

Порядок выпуска версии:

```bash
npm run bump            # 2.0.0 → 2.0.1
# допишите CHANGELOG.md
npm run release         # release/filedeck-1.0.2.apk и release/filedeck-1.0.2.aab
git add -A && git commit -m "Release 1.0.2" && git tag v1.0.2
```

## 2. Ключ для Google Play

Ключ из `android/signing/` публичный (он в git) — им нельзя подписывать релиз для магазина.

```bash
npm run keygen      # создаст android/signing/datalaw-upload.jks и android/keystore.properties
npm run release     # release/filedeck-X.Y.Z.apk и .aab, подписанные личным ключом
```

`keystore.properties` и `*.jks` игнорируются git — **храните копию ключа и паролей в надёжном месте**.
В Play Console включите Play App Signing и загрузите `.aab` как ключ загрузки (upload key).
Если приложение уже раздавалось APK с ключом из репозитория, пользователи с такой установкой
не смогут обновиться на сборку из Play (другая подпись) — им нужно будет переустановить один раз.

## 3. Чек-лист перед отправкой

- [ ] Решено окончательное `applicationId` (сейчас `dev.filedeck.app`). Менять нужно **до** первой
      публикации: `namespace`/`applicationId` в `android/app/build.gradle`, пакет и папка
      `android/app/src/main/java/...`, `appId` в `capacitor.config.json`, `strings.xml`.
- [ ] Личный ключ создан (`npm run keygen`), резервная копия .jks и паролей сохранена.
- [ ] `npm run doctor -- --release` не показывает проблем.
- [ ] `npm run bump`, запись в `CHANGELOG.md`.
- [ ] `npm run release` → `release/filedeck-X.Y.Z.aab`.
- [ ] Политика конфиденциальности размещена по публичному URL (текст: `docs/PRIVACY.md`).
- [ ] Заполнены «Безопасность данных» и декларация доступа ко всем файлам (`docs/STORE_LISTING.md`).
- [ ] Скриншоты (телефон, 16:9 или 9:16), значок 512×512 (`branding/play-store-icon-512.png`), графика 1024×500.
- [ ] Тест на реальных устройствах: Android 8, 11, 14+; с SD-картой и без.

## 4. Стек

Capacitor 8 (`@capacitor/core`, `@capacitor/android`, `@capacitor/app` 8.x), Vite 8, Node 22+,
JDK 21, Gradle 8.14, Android Gradle Plugin 8.13, compileSdk/targetSdk 36, minSdk 26 (Android 8.0).
Библиотеки архиватора: zip4j 2.11, commons-compress 1.26, xz 1.9, junrar 7.5.
Обновление Capacitor: `npm i @capacitor/core@latest @capacitor/android@latest @capacitor/app@latest`
и `npx cap migrate`.
