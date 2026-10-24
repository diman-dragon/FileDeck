package dev.filedeck.app;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

final class SingleSource extends ArchiveSource {
    private final CountingInputStream cin;
    private final InputStream comp;
    private final String name;
    private final long length;
    private boolean given;

    SingleSource(File f, ArchiveFormat fmt) throws IOException {
        length = Math.max(1, f.length());
        cin = new CountingInputStream(new BufferedInputStream(new FileInputStream(f), ArchiveIO.BUF));
        switch (fmt) {
            case GZ:
                comp = new GzipCompressorInputStream(cin);
                break;
            case BZ2:
                comp = new BZip2CompressorInputStream(cin);
                break;
            default:
                comp = new XZCompressorInputStream(cin);
                break;
        }
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        name = dot > 0 ? n.substring(0, dot) : n + ".out";
    }

    @Override
    ArchiveEntry next() {
        if (given) return null;
        given = true;
        ArchiveEntry e = new ArchiveEntry();
        e.name = name;
        e.size = -1;
        return e;
    }

    @Override
    InputStream stream() {
        return comp;
    }

    @Override
    double fraction() {
        return (double) cin.count / length;
    }

    @Override
    public void close() throws IOException {
        comp.close();
    }
}
