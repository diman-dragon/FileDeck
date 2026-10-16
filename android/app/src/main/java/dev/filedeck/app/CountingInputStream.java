package dev.filedeck.app;

import java.io.IOException;
import java.io.InputStream;

/** Считает прочитанные байты — по ним строится прогресс распаковки. */
final class CountingInputStream extends InputStream {
    private final InputStream in;
    long count;

    CountingInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        int b = in.read();
        if (b >= 0) count++;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) count += n;
        return n;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
