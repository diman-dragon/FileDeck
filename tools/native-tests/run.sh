#!/usr/bin/env bash
# Проверка нативной логики (очистка, корзина, архиватор) на обычной JVM, без телефона и Gradle.
# Нужно: JDK 21 и системные jar-файлы (Ubuntu/Debian):
#   sudo apt install default-jdk-headless libcommons-compress-java libxz-java libzip4j-java \
#                    libcommons-io-java libcommons-lang3-java libandroid-json-java
# Запуск:  npm run test:native
set -euo pipefail
cd "$(dirname "$0")"
J=/usr/share/java
LIBS="$J/commons-compress.jar:$J/xz.jar:$J/zip4j.jar:$J/commons-io.jar:$J/commons-lang3.jar:$J/com.android.json.jar"
SRC=../../android/app/src/main/java/dev/filedeck/app
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" -cp "$LIBS" \
  $(find stubs fakes test -name '*.java' | grep -v -e FileProvider -e BridgeActivity) \
  "$SRC/FsUtil.java" "$SRC/CleanerPlugin.java" "$SRC/ArchivePlugin.java"
LC_ALL=C.UTF-8 java -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
  -Djava.awt.headless=true -cp "$OUT:$LIBS" dev.filedeck.app.FdTest
