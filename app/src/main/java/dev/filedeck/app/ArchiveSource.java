package dev.filedeck.app;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Чтение из архива: записи по очереди и поток текущей записи. */
abstract class ArchiveSource implements java.io.Closeable {
    abstract ArchiveEntry next() throws IOException;

    abstract InputStream stream();

    abstract double fraction();

    static ArchiveSource open(File f, ArchiveFormat fmt, String password) throws IOException {
        switch (fmt) {
            case ZIP:
                return new ZipSource(f, password);
            case SEVENZ:
                return new SevenZSource(f, password);
            case TAR:
            case TGZ:
            case TBZ2:
            case TXZ:
                return new TarSource(f, fmt);
            case GZ:
            case BZ2:
            case XZ:
                return new SingleSource(f, fmt);
            default:
                throw new ArchiveError("FORMAT", "Этот формат не поддерживается");
        }
    }
}
