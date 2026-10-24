package dev.filedeck.app;

import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class ZipSink implements ArchiveSink {
    private final ZipOutputStream zos;
    private final boolean encrypt;
    private final CompressionLevel lvl;

    ZipSink(File target, String password, int level) throws IOException {
        OutputStream fos = new BufferedOutputStream(new FileOutputStream(target), ArchiveIO.BUF);
        encrypt = password != null;
        zos = encrypt ? new ZipOutputStream(fos, password.toCharArray()) : new ZipOutputStream(fos);
        lvl = level <= 2 ? CompressionLevel.FASTEST : level >= 8 ? CompressionLevel.ULTRA : CompressionLevel.NORMAL;
    }

    private ZipParameters params(String name, long mtime, boolean forDir) {
        ZipParameters p = new ZipParameters();
        p.setCompressionMethod(CompressionMethod.DEFLATE);
        p.setCompressionLevel(lvl);
        p.setFileNameInZip(name);
        p.setLastModifiedFileTime(mtime);
        if (encrypt && !forDir) {
            p.setEncryptFiles(true);
            p.setEncryptionMethod(EncryptionMethod.AES);
            p.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        }
        return p;
    }

    @Override
    public void dir(String name, long mtime) throws IOException {
        zos.putNextEntry(params(name + "/", mtime, true));
        zos.closeEntry();
    }

    @Override
    public void begin(String name, File f) throws IOException {
        ZipParameters p = params(name, f.lastModified(), false);
        p.setEntrySize(f.length());
        zos.putNextEntry(p);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        zos.write(b, off, len);
    }

    @Override
    public void end() throws IOException {
        zos.closeEntry();
    }

    @Override
    public void close() throws IOException {
        zos.close();
    }
}
