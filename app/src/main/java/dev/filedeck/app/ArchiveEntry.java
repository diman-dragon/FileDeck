package dev.filedeck.app;

/** Запись внутри открытого архива. */
final class ArchiveEntry {
    String name;
    boolean dir;
    long size;
    long mtime;
    boolean link;
}
