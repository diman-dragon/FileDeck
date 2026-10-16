package dev.filedeck.app;

import java.io.File;
import java.io.IOException;

/** Запись в новый архив: папка, файл по частям, закрытие. */
interface ArchiveSink {
    void dir(String name, long mtime) throws IOException;

    void begin(String name, File f) throws IOException;

    void write(byte[] b, int off, int len) throws IOException;

    void end() throws IOException;

    void close() throws IOException;

    static ArchiveSink open(ArchiveFormat fmt, File target, String password, int level) throws IOException {
        switch (fmt) {
            case ZIP:
                return new ZipSink(target, password, level);
            case SEVENZ:
                return new SevenZSink(target, level);
            default:
                return new TarSink(fmt, target, level);
        }
    }
}
