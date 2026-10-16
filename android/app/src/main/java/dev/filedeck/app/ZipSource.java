package dev.filedeck.app;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.model.LocalFileHeader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

final class ZipSource extends ArchiveSource {
    private final CountingInputStream cin;
    private final ZipInputStream zis;
    private final long length;
    private final boolean hasPassword;

    ZipSource(File f, String password) throws IOException {
        length = Math.max(1, f.length());
        cin = new CountingInputStream(new BufferedInputStream(new FileInputStream(f), ArchiveIO.BUF));
        hasPassword = password != null && !password.isEmpty();
        zis = hasPassword ? new ZipInputStream(cin, password.toCharArray()) : new ZipInputStream(cin);
    }

    @Override
    ArchiveEntry next() throws IOException {
        LocalFileHeader h;
        try {
            h = zis.getNextEntry();
        } catch (ZipException e) {
            throw mapZip(e, hasPassword);
        }
        if (h == null) return null;
        if (h.isEncrypted() && !hasPassword) throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
        ArchiveEntry e = new ArchiveEntry();
        e.name = h.getFileName();
        e.dir = h.isDirectory();
        e.size = h.getUncompressedSize();
        e.mtime = h.getLastModifiedTimeEpoch();
        return e;
    }

    @Override
    InputStream stream() {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    return zis.read();
                } catch (ZipException e) {
                    throw mapZip(e, hasPassword);
                }
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                try {
                    return zis.read(b, off, len);
                } catch (ZipException e) {
                    throw mapZip(e, hasPassword);
                }
            }
        };
    }

    @Override
    double fraction() {
        return (double) cin.count / length;
    }

    @Override
    public void close() throws IOException {
        zis.close();
    }

    static IOException mapZip(ZipException e, boolean hadPassword) {
        if (e.getType() == ZipException.Type.WRONG_PASSWORD) {
            return hadPassword
                ? new ArchiveError("WRONG_PASSWORD", "Неверный пароль")
                : new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
        }
        return e;
    }
}
