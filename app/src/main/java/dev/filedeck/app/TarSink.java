package dev.filedeck.app;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class TarSink implements ArchiveSink {
    private final TarArchiveOutputStream tar;

    TarSink(ArchiveFormat fmt, File target, int level) throws IOException {
        OutputStream raw = new BufferedOutputStream(new FileOutputStream(target), ArchiveIO.BUF);
        OutputStream comp;
        try {
            switch (fmt) {
                case TGZ: {
                    GzipParameters gp = new GzipParameters();
                    gp.setCompressionLevel(level <= 2 ? 1 : level >= 8 ? 9 : 6);
                    comp = new GzipCompressorOutputStream(raw, gp);
                    break;
                }
                case TBZ2:
                    comp = new BZip2CompressorOutputStream(raw, level <= 2 ? 1 : 9);
                    break;
                case TXZ:
                    comp = new XZCompressorOutputStream(raw, level <= 2 ? 1 : level >= 8 ? 9 : 6);
                    break;
                default:
                    comp = raw;
                    break;
            }
        } catch (IOException e) {
            raw.close();
            throw e;
        }
        tar = new TarArchiveOutputStream(comp, "UTF-8");
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
    }

    @Override
    public void dir(String name, long mtime) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name + "/");
        e.setModTime(mtime);
        tar.putArchiveEntry(e);
        tar.closeArchiveEntry();
    }

    @Override
    public void begin(String name, File f) throws IOException {
        TarArchiveEntry e = new TarArchiveEntry(name);
        e.setSize(f.length());
        e.setModTime(f.lastModified());
        tar.putArchiveEntry(e);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        tar.write(b, off, len);
    }

    @Override
    public void end() throws IOException {
        tar.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        tar.close();
    }
}
