package dev.filedeck.app;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Хранилище корзины: на каждом накопителе скрытая папка .FileDeckTrash; у каждого элемента есть .json с метаданными. */
final class TrashStore {
    static final String TRASH_DIR = ".FileDeckTrash";

    static final class TrashEntry {
        final File dir;
        final File content;
        final File metaFile;
        final JSONObject meta;

        TrashEntry(File dir, File content, File metaFile, JSONObject meta) {
            this.dir = dir;
            this.content = content;
            this.metaFile = metaFile;
            this.meta = meta;
        }

        String id() {
            return meta.optString("id");
        }
    }

    private final Context ctx;

    TrashStore(Context ctx) {
        this.ctx = ctx;
    }

    File rootFor(File f) {
        File best = null;
        String path = f.getAbsolutePath();
        for (File r : FsUtil.storageRoots(ctx)) {
            String rp = r.getAbsolutePath();
            if ((path.equals(rp) || path.startsWith(rp + "/")) && (best == null || rp.length() > best.getAbsolutePath().length())) {
                best = r;
            }
        }
        return best;
    }

    File trashDir(File root, boolean create) throws IOException {
        File td = new File(root, TRASH_DIR);
        if (create) {
            if (!td.isDirectory() && !td.mkdirs()) throw new IOException("Не удалось создать корзину");
            File nomedia = new File(td, ".nomedia");
            if (!nomedia.exists()) {
                //noinspection ResultOfMethodCallIgnored
                nomedia.createNewFile();
            }
        }
        return td;
    }

    List<TrashEntry> readTrash() {
        List<TrashEntry> out = new ArrayList<>();
        for (File root : FsUtil.storageRoots(ctx)) {
            File td = new File(root, TRASH_DIR);
            File[] metas = td.listFiles();
            if (metas == null) continue;
            for (File m : metas) {
                String n = m.getName();
                if (!n.endsWith(".json")) continue;
                String id = n.substring(0, n.length() - 5);
                File content = new File(td, id);
                try {
                    JSONObject meta = new JSONObject(readUtf8(m));
                    if (!content.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        m.delete();
                        continue;
                    }
                    meta.put("id", id);
                    out.add(new TrashEntry(td, content, m, meta));
                } catch (Exception e) {
                    // повреждённые метаданные — пропускаем
                }
            }
        }
        return out;
    }

    static String readUtf8(File f) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }

    static void writeUtf8(File f, String text) throws IOException {
        try (OutputStream out = new FileOutputStream(f, false)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
