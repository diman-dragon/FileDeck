package dev.filedeck.app;

import com.getcapacitor.JSObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Чтение и запись небольших текстовых файлов (UTF-8) для встроенного редактора. */
final class TextFiles {
    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private static final int BUFFER = 64 * 1024;

    private TextFiles() {}

    static JSObject read(String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        File f = new File(path);
        if (!f.isFile()) throw new IOException("Файл не найден");
        if (f.length() > MAX_TEXT_BYTES) throw new IOException("Файл слишком большой для редактора (больше 2 МБ)");
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.max(f.length(), 16));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        JSObject ret = new JSObject();
        ret.put("text", new String(bos.toByteArray(), StandardCharsets.UTF_8));
        return ret;
    }

    static void write(String path, String text) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        try (OutputStream out = new FileOutputStream(new File(path), false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
