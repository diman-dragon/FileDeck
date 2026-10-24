package dev.filedeck.app;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

final class TarSource extends ArchiveSource {
    private final CountingInputStream cin;
    private final TarArchiveInputStream tin;
    private final long length;

    TarSource(File f, ArchiveFormat fmt) throws IOException {
        length = Math.max(1, f.length());
        cin = new CountingInputStream(new BufferedInputStream(new FileInputStream(f), ArchiveIO.BUF));
        InputStream comp;
        switch (fmt) {
            case TGZ:
                comp = new GzipCompressorInputStream(cin);
                break;
            case TBZ2:
                comp = new BZip2CompressorInputStream(cin);
                break;
            case TXZ:
                comp = new XZCompressorInputStream(cin);
                break;
            default:
                comp = cin;
                break;
        }
        tin = new TarArchiveInputStream(comp);
    }

    @Override
    ArchiveEntry next() throws IOException {
        TarArchiveEntry e = (TarArchiveEntry) tin.getNextEntry();
        if (e == null) return null;
        ArchiveEntry ent = new ArchiveEntry();
        ent.name = e.getName();
        ent.dir = e.isDirectory();
        ent.size = e.getSize();
        ent.mtime = e.getModTime() == null ? 0 : e.getModTime().getTime();
        ent.link = e.isSymbolicLink() || e.isLink();
        return ent;
    }

    @Override
    InputStream stream() {
        return tin;
    }

    @Override
    double fraction() {
        return (double) cin.count / length;
    }

    @Override
    public void close() throws IOException {
        tin.close();
    }
}
