package dev.filedeck.app;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZMethod;
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;

import org.tukaani.xz.LZMA2Options;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;

final class SevenZSink implements ArchiveSink {
    private final SevenZOutputFile out;

    SevenZSink(File target, int level) throws IOException {
        out = new SevenZOutputFile(target);
        int preset = level <= 2 ? 1 : level >= 8 ? 9 : 6;
        // Integer в SevenZMethodConfiguration означает размер словаря, а не уровень, поэтому передаём LZMA2Options
        out.setContentMethods(Collections.singletonList(new SevenZMethodConfiguration(SevenZMethod.LZMA2, new LZMA2Options(preset))));
    }

    private SevenZArchiveEntry entry(String name, boolean dir, boolean hasStream, long mtime) {
        SevenZArchiveEntry e = new SevenZArchiveEntry();
        e.setName(name);
        e.setDirectory(dir);
        e.setHasStream(hasStream);
        e.setLastModifiedDate(new Date(mtime));
        return e;
    }

    @Override
    public void dir(String name, long mtime) throws IOException {
        out.putArchiveEntry(entry(name, true, false, mtime));
        out.closeArchiveEntry();
    }

    @Override
    public void begin(String name, File f) throws IOException {
        out.putArchiveEntry(entry(name, false, f.length() > 0, f.lastModified()));
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void end() throws IOException {
        out.closeArchiveEntry();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
