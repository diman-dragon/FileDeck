package dev.filedeck.app;

import java.io.File;

/** Что попадёт в новый архив: файл или папка и её имя внутри архива. */
final class ArchiveItem {
    final File file;
    final String name;
    final boolean dir;

    ArchiveItem(File file, String name, boolean dir) {
        this.file = file;
        this.name = name;
        this.dir = dir;
    }
}
