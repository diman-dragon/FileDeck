package dev.filedeck.app;

/** Файл, найденный при сканировании: путь, размер, дата. */
final class FileInfo {
    final String path;
    final long size;
    final long mtime;

    FileInfo(String path, long size, long mtime) {
        this.path = path;
        this.size = size;
        this.mtime = mtime;
    }
}
