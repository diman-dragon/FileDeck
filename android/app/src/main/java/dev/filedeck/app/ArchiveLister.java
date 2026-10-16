package dev.filedeck.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

import java.io.File;
import java.io.IOException;

/** Просмотр содержимого архива без распаковки. */
final class ArchiveLister {
    private static final int MAX_LIST = 20000;

    private ArchiveLister() {}

    static JSObject list(ArchiveHost host, String path, String password) throws IOException {
        if (path == null) throw new ArchiveError("PATH", "Не указан архив");
        File f = new File(path);
        if (!f.isFile()) throw new ArchiveError("PATH", "Архив не найден");
        ArchiveFormat fmt = ArchiveFormat.detect(f);
        if (fmt == ArchiveFormat.UNKNOWN) throw new ArchiveError("FORMAT", "Формат архива не распознан");

        JSArray arr = new JSArray();
        long totalSize = 0;
        long count = 0;
        boolean encrypted = false;
        boolean listing = true;

        if (fmt == ArchiveFormat.ZIP) {
            try (ZipFile zf = new ZipFile(f)) {
                encrypted = zf.isEncrypted();
                for (FileHeader h : zf.getFileHeaders()) {
                    host.checkCancel();
                    count++;
                    if (!h.isDirectory()) totalSize += Math.max(0, h.getUncompressedSize());
                    if (arr.length() < MAX_LIST) arr.put(entryJson(h.getFileName(), h.isDirectory(), h.getUncompressedSize(), h.getLastModifiedTimeEpoch()));
                }
            } catch (ZipException e) {
                throw new ArchiveError("BROKEN", "Архив повреждён или это не ZIP");
            }
        } else if (fmt == ArchiveFormat.RAR) {
            listing = false;
        } else {
            try (ArchiveSource src = ArchiveSource.open(f, fmt, password)) {
                ArchiveEntry e;
                while ((e = src.next()) != null) {
                    host.checkCancel();
                    count++;
                    if (!e.dir && e.size > 0) totalSize += e.size;
                    if (arr.length() < MAX_LIST) arr.put(entryJson(e.name, e.dir, e.size, e.mtime));
                    host.emit("list", src.fraction(), e.name, false);
                }
            }
        }
        JSObject ret = new JSObject();
        ret.put("format", fmt.name());
        ret.put("entries", arr);
        ret.put("count", count);
        ret.put("bytes", totalSize);
        ret.put("encrypted", encrypted);
        ret.put("listing", listing);
        ret.put("truncated", count > arr.length());
        return ret;
    }

    private static JSObject entryJson(String name, boolean dir, long size, long mtime) {
        JSObject o = new JSObject();
        String clean = name.replace('\\', '/');
        while (clean.endsWith("/") && clean.length() > 1) clean = clean.substring(0, clean.length() - 1);
        o.put("name", clean);
        o.put("dir", dir);
        o.put("size", Math.max(0, size));
        o.put("mtime", mtime);
        return o;
    }
}
