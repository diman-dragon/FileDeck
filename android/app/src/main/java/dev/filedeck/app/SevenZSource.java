package dev.filedeck.app;

import org.apache.commons.compress.PasswordRequiredException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

final class SevenZSource extends ArchiveSource {
    private final SevenZFile sz;
    private final long total;
    private long read;

    SevenZSource(File f, String password) throws IOException {
        try {
            sz = password != null && !password.isEmpty() ? new SevenZFile(f, password.toCharArray()) : new SevenZFile(f);
        } catch (PasswordRequiredException e) {
            throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
        }
        long t = 0;
        for (SevenZArchiveEntry e : sz.getEntries()) {
            if (!e.isDirectory()) t += Math.max(0, e.getSize());
        }
        total = Math.max(1, t);
    }

    @Override
    ArchiveEntry next() throws IOException {
        SevenZArchiveEntry e;
        try {
            e = sz.getNextEntry();
        } catch (PasswordRequiredException ex) {
            throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
        }
        if (e == null) return null;
        ArchiveEntry ent = new ArchiveEntry();
        ent.name = e.getName();
        ent.dir = e.isDirectory();
        ent.size = e.getSize();
        ent.mtime = e.getHasLastModifiedDate() ? e.getLastModifiedDate().getTime() : 0;
        return ent;
    }

    @Override
    InputStream stream() {
        return new InputStream() {
            private final byte[] one = new byte[1];

            @Override
            public int read() throws IOException {
                int n = read(one, 0, 1);
                return n <= 0 ? -1 : one[0] & 0xFF;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                try {
                    int n = sz.read(b, off, len);
                    if (n > 0) read += n;
                    return n;
                } catch (PasswordRequiredException ex) {
                    throw new ArchiveError("PASSWORD_REQUIRED", "Архив защищён паролем");
                } catch (IOException ex) {
                    String m = String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT);
                    if (m.contains("password") || m.contains("padding") || m.contains("pad block") || m.contains("given final block")) {
                        throw new ArchiveError("WRONG_PASSWORD", "Неверный пароль");
                    }
                    throw ex;
                }
            }
        };
    }

    @Override
    double fraction() {
        return (double) read / total;
    }

    @Override
    public void close() throws IOException {
        sz.close();
    }
}
