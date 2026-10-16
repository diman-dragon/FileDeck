package dev.filedeck.app;

import android.content.Context;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.imageio.ImageIO;

public class FdTest {
    static int failed = 0, passed = 0;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("  ok   " + what); }
        else { failed++; System.out.println("  FAIL " + what); }
    }

    static org.json.JSONArray arr(String... v) { org.json.JSONArray a = new JSArray(); for (String s : v) a.put(s); return a; }

    static PluginCall call(Plugin p, String method, JSObject data) throws Exception {
        PluginCall c = new PluginCall(data);
        Method m = p.getClass().getMethod(method, PluginCall.class);
        m.invoke(p, c);
        c.future.get(180, TimeUnit.SECONDS);
        return c;
    }

    static void write(Path p, byte[] b) throws IOException { Files.createDirectories(p.getParent()); Files.write(p, b); }
    static void text(Path p, String s) throws IOException { write(p, s.getBytes(StandardCharsets.UTF_8)); }
    static byte[] rnd(long seed, int n) { byte[] b = new byte[n]; new Random(seed).nextBytes(b); return b; }

    static BufferedImage pattern(int w, int h, int kind) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, kind == 0 ? new Color(20, 40, 200) : new Color(230, 200, 30), w, h, kind == 0 ? new Color(240, 240, 250) : new Color(10, 90, 40)));
        g.fillRect(0, 0, w, h);
        if (kind == 0) { g.setColor(Color.RED); g.fillOval(w / 5, h / 4, w / 3, h / 2); g.setColor(Color.BLACK); g.fillRect(w * 3 / 5, h / 8, w / 4, h / 3); }
        else { g.setColor(Color.WHITE); for (int i = 0; i < w; i += w / 6) g.fillRect(i, 0, w / 14, h); g.setColor(Color.BLACK); g.fillOval(w / 2, h / 2, w / 3, h / 3); }
        g.dispose();
        Random rn = new Random(kind + 100);
        for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) {
            int p = im.getRGB(xx, yy);
            int n = rn.nextInt(9) - 4;
            int r = Math.max(0, Math.min(255, ((p >> 16) & 255) + n)), gg = Math.max(0, Math.min(255, ((p >> 8) & 255) + n)), b = Math.max(0, Math.min(255, (p & 255) + n));
            im.setRGB(xx, yy, (r << 16) | (gg << 8) | b);
        }
        return im;
    }
    static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src.getScaledInstance(w, h, java.awt.Image.SCALE_SMOOTH), 0, 0, null); g.dispose(); return out;
    }
    static BufferedImage bright(BufferedImage src, int add) {
        BufferedImage o = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < o.getHeight(); y++) for (int x = 0; x < o.getWidth(); x++) {
            int p = src.getRGB(x, y);
            int r = Math.min(255, ((p >> 16) & 255) + add), gg = Math.min(255, ((p >> 8) & 255) + add), b = Math.min(255, (p & 255) + add);
            o.setRGB(x, y, (r << 16) | (gg << 8) | b);
        }
        return o;
    }

    static boolean has(org.json.JSONArray a, String suffix) throws Exception {
        for (int i = 0; i < a.length(); i++) {
            Object o = a.get(i);
            String p = o instanceof org.json.JSONObject ? ((org.json.JSONObject) o).optString("path") : String.valueOf(o);
            if (p.endsWith(suffix)) return true;
        }
        return false;
    }

    static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (var s = Files.walk(p)) { s.sorted(Comparator.reverseOrder()).forEach(x -> x.toFile().delete()); }
    }

    static Map<String, String> snapshot(Path root) throws Exception {
        Map<String, String> m = new TreeMap<>();
        try (var s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (p.equals(root)) continue;
                String rel = root.relativize(p).toString().replace('\\', '/');
                m.put(rel, Files.isDirectory(p) ? "<dir>" : java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(p))));
            }
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("fdroot");
        System.setProperty("fd.root", root.toString());
        Context ctx = new Context();
        ctx.extra = new File[0];

        // ---------------------------------------------------------------- данные для очистки
        BufferedImage A = pattern(800, 600, 0), B = pattern(800, 600, 1);
        ImageIO.write(A, "jpg", mk(root.resolve("DCIM/Camera/img1.jpg")));
        Files.createDirectories(root.resolve("Pictures/Copies"));
        Files.copy(root.resolve("DCIM/Camera/img1.jpg"), root.resolve("Pictures/Copies/img1_copy.jpg"));
        ImageIO.write(scale(A, 790, 592), "jpg", mk(root.resolve("DCIM/Camera/img1_small.jpg")));
        ImageIO.write(bright(A, 12), "jpg", mk(root.resolve("DCIM/Camera/img1_bright.jpg")));
        ImageIO.write(B, "jpg", mk(root.resolve("DCIM/Camera/other.jpg")));

        byte[] big = rnd(1, 200_000);
        write(root.resolve("Download/big.bin"), big);
        write(root.resolve("Documents/big_copy.bin"), big);
        byte[] x = rnd(2, 300_000), y = x.clone();
        y[150_000] ^= 0x55;                              // отличается только в середине
        write(root.resolve("Download/x.bin"), x);
        write(root.resolve("Download/y.bin"), y);
        write(root.resolve("Download/tiny1.txt"), "hello".getBytes());   // меньше minDup — не дубликаты
        write(root.resolve("Download/tiny2.txt"), "hello".getBytes());
        write(root.resolve("Download/movie.mkv"), rnd(3, 3_500_000));
        write(root.resolve("Download/app.apk"), rnd(4, 5000));
        Files.createDirectories(root.resolve("emptyA"));
        Files.createDirectories(root.resolve("nest/inner/deep"));
        Files.createDirectories(root.resolve("Pictures/EmptyKeep")); // внутри защищённой Pictures, но не прямой потомок корня
        Files.createDirectories(root.resolve("Music"));             // защищённая пустая
        text(root.resolve("notempty/file.txt"), "data");
        Files.createDirectories(root.resolve("mixed/empty1"));
        text(root.resolve("mixed/keep.txt"), "x");
        write(root.resolve("zero.txt"), new byte[0]);
        write(root.resolve("DCIM/.nomedia"), new byte[0]);
        write(root.resolve("junk/foo.tmp"), rnd(5, 100));
        write(root.resolve("junk/app.log"), rnd(6, 100));
        write(root.resolve("junk/.trashed-1700000000-old.jpg"), rnd(7, 100));
        write(root.resolve("pics/.thumbnails/t1.dat"), rnd(8, 2000));
        write(root.resolve("pics/cache/c1.bin"), rnd(9, 3000));
        write(root.resolve("Android/data/com.x/secret.bin"), rnd(10, 5000));
        write(root.resolve("Android/media/com.a/Media/big_media.bin"), big);

        // ---------------------------------------------------------------- СКАН
        System.out.println("== Cleaner.scan");
        CleanerPlugin cl = new CleanerPlugin();
        cl.testContext = ctx;
        JSObject opts = new JSObject();
        opts.put("roots", arr(root.toString()));
        opts.put("similar", true);
        PluginCall sc = call(cl, "scan", opts);
        check(sc.errorMessage == null, "scan завершился без ошибки (" + sc.errorMessage + ")");
        JSObject r = sc.future.get();
        org.json.JSONArray dups = r.getJSONArray("duplicates");
        System.out.println("     дублей групп: " + dups.length());
        check(has(dups.getJSONObject(0).getJSONArray("files"), "big.bin") || findGroup(dups, "big.bin"), "найден дубликат big.bin / big_copy.bin");
        check(findGroupWith(dups, "big.bin", "big_copy.bin"), "big.bin и big_copy.bin в одной группе");
        check(!findGroupWith(dups, "big.bin", "big_media.bin"), "Android/media не сканируется по умолчанию");
        check(!anyGroupWith(dups, "/x.bin") && !anyGroupWith(dups, "/y.bin"), "x.bin и y.bin (одинаковые края, разная середина) — не дубликаты");
        check(!anyGroupWith(dups, "tiny1.txt"), "мелкие файлы меньше порога не считаются дубликатами");
        check(findGroupWith(dups, "img1.jpg", "img1_copy.jpg"), "точные копии фото найдены как дубликаты");

        org.json.JSONArray sim = r.getJSONArray("similar");
        System.out.println("     похожих групп: " + sim.length());
        check(sim.length() == 1, "ровно одна группа похожих фото");
        if (sim.length() == 1) {
            org.json.JSONArray f = sim.getJSONObject(0).getJSONArray("files");
            for (int q = 0; q < f.length(); q++) System.out.println("       в группе: " + f.getJSONObject(q).getString("path").replace(root.toString(), "") + " " + f.getJSONObject(q).getLong("size") + " Б");
            check(f.length() == 3, "в группе 3 фото (оригинал, уменьшенная, светлее): " + f.length());
            check(!has(f, "other.jpg"), "другая картинка в группу не попала");
            check(!has(f, "img1_copy.jpg"), "точная копия исключена из похожих");
        }

        org.json.JSONArray empties = r.getJSONArray("emptyFolders");
        check(has(empties, "/emptyA"), "emptyA — пустая папка");
        check(has(empties, "/nest") && !has(empties, "/nest/inner") && !has(empties, "/nest/inner/deep"), "nest/inner/deep сведено к верхней пустой папке nest");
        check(has(empties, "/mixed/empty1"), "пустая папка внутри непустой найдена");
        check(!has(empties, "/Music"), "защищённая Music не предлагается");
        check(!has(empties, "/notempty"), "непустая папка не предлагается");

        org.json.JSONArray zero = r.getJSONArray("zeroFiles");
        check(has(zero, "/zero.txt") && !has(zero, ".nomedia"), "пустые файлы: zero.txt есть, .nomedia нет");

        org.json.JSONArray junk = r.getJSONArray("junk");
        check(has(junk, "foo.tmp") && has(junk, "app.log") && has(junk, ".trashed-1700000000-old.jpg"), "мусорные файлы найдены (tmp, log, .trashed)");
        check(has(junk, "/pics/.thumbnails") && has(junk, "/pics/cache"), "мусорные папки найдены как одна запись");
        check(!has(junk, "t1.dat"), "внутри мусорной папки файлы не перечисляются отдельно");
        check(!has(r.getJSONArray("largest"), "foo.tmp"), "мусор не попадает в крупные");
        check(r.getJSONArray("largest").getJSONObject(0).getString("path").endsWith("movie.mkv"), "самый большой файл — movie.mkv");
        check(has(r.getJSONArray("apks"), "app.apk"), "apk найден");
        check(!has(dups, "secret.bin") && !r.toString().contains("secret.bin"), "Android/data не читается");
        org.json.JSONObject usage = r.getJSONObject("usage");
        check(usage.getJSONObject("video").getLong("bytes") == 3_500_000L, "статистика типов: видео = 3 500 000 Б");
        check(cl.testEvents.stream().anyMatch(e -> e.startsWith("scanProgress")), "приходят события прогресса");

        // с папками приложений
        JSObject o2 = new JSObject();
        o2.put("roots", arr(root.toString()));
        o2.put("appFolders", true);
        JSObject r2 = call(cl, "scan", o2).future.get();
        check(findGroupWith(r2.getJSONArray("duplicates"), "big.bin", "big_media.bin"), "с appFolders=true Android/media сканируется");
        check(!r2.toString().contains("secret.bin"), "…но Android/data всё равно закрыта");

        // исключения
        JSObject o3 = new JSObject();
        o3.put("roots", arr(root.toString()));
        o3.put("excludes", arr(root.resolve("Documents").toString()));
        JSObject r3 = call(cl, "scan", o3).future.get();
        check(!anyGroupWith(r3.getJSONArray("duplicates"), "big_copy.bin"), "исключённая папка пропущена");

        // ---------------------------------------------------------------- КОРЗИНА
        System.out.println("== Trash");
        TrashPlugin tr = new TrashPlugin();
        tr.testContext = ctx;
        JSObject tm = new JSObject();
        tm.put("paths", arr(root.resolve("Download/big.bin").toString(), root.resolve("mixed").toString(), root.resolve("нет такого").toString()));
        JSObject trm = call(tr, "move", tm).future.get();
        check(trm.getInt("done") == 2 && trm.getJSONArray("failed").length() == 1, "в корзину перенесено 2, ошибка у несуществующего");
        check(!Files.exists(root.resolve("Download/big.bin")) && !Files.exists(root.resolve("mixed")), "оригиналы исчезли с места");
        JSObject tl = call(tr, "list", new JSObject()).future.get();
        check(tl.getInt("count") == 2, "в корзине 2 элемента");
        String idFile = null, idDir = null;
        for (int i = 0; i < tl.getJSONArray("items").length(); i++) {
            var it = tl.getJSONArray("items").getJSONObject(i);
            if (it.getString("name").equals("big.bin")) idFile = it.getString("id"); else idDir = it.getString("id");
        }
        check(tl.getLong("bytes") == 200_000L + 1L, "размер корзины = 200 001 Б (файл + keep.txt)");
        // конфликт имён при восстановлении
        write(root.resolve("Download/big.bin"), "new".getBytes());
        JSObject rs = new JSObject(); rs.put("ids", arr(idFile, idDir));
        JSObject rr = call(tr, "restore", rs).future.get();
        check(rr.getInt("done") == 2, "восстановлено 2");
        check(Files.exists(root.resolve("Download/big (1).bin")) && Files.size(root.resolve("Download/big (1).bin")) == 200_000L, "при конфликте имя получило суффикс (1), новый файл не затёрт");
        check(Files.readString(root.resolve("Download/big.bin")).equals("new"), "файл с тем же именем не перезаписан");
        check(Files.exists(root.resolve("mixed/keep.txt")), "папка восстановлена вместе с содержимым");
        check(call(tr, "list", new JSObject()).future.get().getInt("count") == 0, "корзина пуста после восстановления");
        // защита от удаления корня и корзины в корзину
        JSObject bad = new JSObject(); bad.put("paths", arr(root.toString()));
        check(call(tr, "move", bad).future.get().getInt("done") == 0, "корень накопителя нельзя отправить в корзину");
        // purge / empty
        JSObject t2 = new JSObject(); t2.put("paths", arr(root.resolve("Download/big (1).bin").toString(), root.resolve("zero.txt").toString()));
        call(tr, "move", t2);
        JSObject pg = new JSObject(); pg.put("days", 30);
        check(call(tr, "purge", pg).future.get().getInt("done") == 0, "purge(30 дн.) свежие не трогает");
        JSObject pg0 = new JSObject(); pg0.put("days", 0);
        Thread.sleep(20);
        check(call(tr, "purge", pg0).future.get().getInt("done") == 2, "purge(0 дн.) удаляет всё просроченное");
        call(tr, "move", t2 = single(root.resolve("Download/x.bin")));
        JSObject em = call(tr, "empty", new JSObject()).future.get();
        check(em.getInt("done") == 1 && call(tr, "list", new JSObject()).future.get().getInt("count") == 0, "trashEmpty очищает корзину");
        check(tr.testEvents.stream().anyMatch(e -> e.startsWith("progress")), "очистка корзины присылает прогресс");
        check(!Files.exists(root.resolve(".FileDeckTrash/.nomedia")) == false, ".nomedia в корзине на месте");

        // ---------------------------------------------------------------- АРХИВЫ
        System.out.println("== Archive");
        ArchivePlugin ar = new ArchivePlugin();
        ar.testContext = ctx;
        Path src = root.resolve("src");
        text(src.resolve("a.txt"), "привет мир\n".repeat(500));
        write(src.resolve("sub/b.bin"), rnd(11, 70_000));
        write(src.resolve("sub/deep/c.bin"), rnd(12, 1_000));
        Files.createDirectories(src.resolve("emptydir"));
        write(src.resolve("zero.dat"), new byte[0]);
        text(src.resolve("имя с пробелами и юникодом ёжик.txt"), "юникод");
        Map<String, String> want = snapshot(src);

        String[] formats = { "zip", "7z", "tar", "tar.gz", "tar.bz2", "tar.xz" };
        for (String fmt : formats) {
            JSObject co = new JSObject();
            co.put("paths", arr(src.toString()));
            co.put("dest", root.resolve("out").toString().replace("out", "")); // корень
            co.put("name", "pack-" + fmt.replace('.', '_'));
            co.put("format", fmt);
            PluginCall cc = call(ar, "create", co);
            check(cc.errorMessage == null, fmt + ": создание без ошибки (" + cc.errorMessage + ")");
            if (cc.errorMessage != null) continue;
            String apath = cc.future.get().getString("path");
            // список
            JSObject lo = new JSObject(); lo.put("path", apath);
            PluginCall lc = call(ar, "list", lo);
            check(lc.errorMessage == null && lc.future.get().getLong("count") >= 6, fmt + ": просмотр содержимого (" + (lc.errorMessage == null ? lc.future.get().getLong("count") : lc.errorMessage) + " записей)");
            // распаковка в папку
            Path dest = Files.createDirectories(root.resolve("x-" + fmt));
            JSObject eo = new JSObject(); eo.put("path", apath); eo.put("dest", dest.toString());
            PluginCall ec = call(ar, "extract", eo);
            check(ec.errorMessage == null, fmt + ": распаковка без ошибки (" + ec.errorMessage + ")");
            if (ec.errorMessage != null) continue;
            Path out = Paths.get(ec.future.get().getString("path"));
            check(snapshot(out.resolve("src")).equals(want), fmt + ": распакованное идентично исходному (дерево, пустые папки, Unicode, хеши)");
        }

        // пароль ZIP
        JSObject pw = new JSObject();
        pw.put("paths", arr(src.toString())); pw.put("dest", root.toString()); pw.put("name", "secret"); pw.put("format", "zip"); pw.put("password", "p@ss-Парoль");
        String zpath = call(ar, "create", pw).future.get().getString("path");
        JSObject e1 = new JSObject(); e1.put("path", zpath); e1.put("dest", Files.createDirectories(root.resolve("pw1")).toString());
        PluginCall c1 = call(ar, "extract", e1);
        check("PASSWORD_REQUIRED".equals(c1.errorCode), "zip с паролем без пароля → PASSWORD_REQUIRED (" + c1.errorCode + ")");
        JSObject e2 = new JSObject(); e2.put("path", zpath); e2.put("dest", Files.createDirectories(root.resolve("pw2")).toString()); e2.put("password", "wrong");
        PluginCall c2 = call(ar, "extract", e2);
        check("WRONG_PASSWORD".equals(c2.errorCode), "zip с неверным паролем → WRONG_PASSWORD (" + c2.errorCode + " " + c2.errorMessage + ")");
        check(c1.errorMessage != null && c2.errorMessage != null && (!Files.exists(root.resolve("pw1/secret")) && !Files.exists(root.resolve("pw2/secret"))), "после ошибки пароля пустых папок не остаётся");
        JSObject e3 = new JSObject(); e3.put("path", zpath); e3.put("dest", Files.createDirectories(root.resolve("pw3")).toString()); e3.put("password", "p@ss-Парoль");
        PluginCall c3 = call(ar, "extract", e3);
        check(c3.errorMessage == null && snapshot(Paths.get(c3.future.get().getString("path")).resolve("src")).equals(want), "zip с верным паролем распаковывается корректно");
        JSObject l4 = new JSObject(); l4.put("path", zpath);
        check(call(ar, "list", l4).future.get().getBoolean("encrypted"), "список ZIP с паролем показывает encrypted=true без ввода пароля");

        // режим «здесь» + конфликт имён
        Path here = Files.createDirectories(root.resolve("here"));
        text(here.resolve("src/old.txt"), "old");
        JSObject eh = new JSObject();
        eh.put("path", root.resolve("pack-zip.zip").toString()); eh.put("dest", here.toString()); eh.put("mode", "here");
        PluginCall ch = call(ar, "extract", eh);
        check(ch.errorMessage == null && Files.exists(here.resolve("src (1)/a.txt")) && Files.exists(here.resolve("src/old.txt")) && !Files.exists(here.resolve("src/a.txt")), "режим «здесь»: конфликт имён → src (1), существующее не затронуто");

        // выборочная распаковка
        JSObject es = new JSObject();
        es.put("path", root.resolve("pack-7z.7z").toString()); es.put("dest", Files.createDirectories(root.resolve("sel")).toString());
        es.put("entries", arr("src/sub/deep/c.bin", "src/a.txt"));
        PluginCall cs = call(ar, "extract", es);
        Map<String, String> sn = snapshot(Paths.get(cs.future.get().getString("path")));
        check(sn.containsKey("src/a.txt") && sn.containsKey("src/sub/deep/c.bin") && !sn.containsKey("src/sub/b.bin"), "выборочная распаковка 7z: только выбранное");

        // Zip Slip
        Path evil = root.resolve("evil.zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(evil.toFile()))) {
            z.putNextEntry(new ZipEntry("../escaped.txt")); z.write("x".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("/abs.txt")); z.write("y".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("ok/fine.txt")); z.write("z".getBytes()); z.closeEntry();
            z.putNextEntry(new ZipEntry("a/../../escaped2.txt")); z.write("w".getBytes()); z.closeEntry();
        }
        Path slip = Files.createDirectories(root.resolve("slip"));
        JSObject ez = new JSObject(); ez.put("path", evil.toString()); ez.put("dest", slip.toString());
        PluginCall cz = call(ar, "extract", ez);
        check(cz.errorMessage == null, "Zip Slip: распаковка завершилась (" + cz.errorMessage + ")");
        check(!Files.exists(root.resolve("escaped.txt")) && !Files.exists(slip.resolve("escaped.txt")) && !Files.exists(root.resolve("escaped2.txt")), "Zip Slip: файлы вне папки не созданы");
        check(Files.exists(slip.resolve("evil/ok/fine.txt")) && Files.exists(slip.resolve("evil/abs.txt")), "Zip Slip: нормальные файлы распакованы, абсолютный путь стал относительным");

        // одиночный .gz
        Path gz = root.resolve("single.txt.gz");
        try (OutputStream o = new GZIPOutputStream(new FileOutputStream(gz.toFile()))) { o.write("single file content".getBytes()); }
        JSObject eg = new JSObject(); eg.put("path", gz.toString()); eg.put("dest", Files.createDirectories(root.resolve("gzout")).toString());
        PluginCall cg = call(ar, "extract", eg);
        check(cg.errorMessage == null && Files.readString(Paths.get(cg.future.get().getString("path")).resolve("single.txt")).equals("single file content"), "одиночный .gz распаковывается");

        // повреждённый архив не оставляет мусор
        Path broken = root.resolve("broken.7z");
        write(broken, rnd(13, 400));
        JSObject eb = new JSObject(); eb.put("path", broken.toString()); eb.put("dest", Files.createDirectories(root.resolve("brk")).toString());
        PluginCall cb = call(ar, "extract", eb);
        check(cb.errorMessage != null && !Files.exists(root.resolve("brk/broken")), "повреждённый архив: понятная ошибка, мусорной папки нет");

        // архив внутри архивируемой папки не включает сам себя
        JSObject self = new JSObject();
        self.put("paths", arr(src.toString())); self.put("dest", src.toString()); self.put("name", "inside"); self.put("format", "zip");
        PluginCall cself = call(ar, "create", self);
        JSObject lself = new JSObject(); lself.put("path", cself.future.get().getString("path"));
        org.json.JSONArray names = call(ar, "list", lself).future.get().getJSONArray("entries");
        check(!has2(names, "inside.zip"), "архив, создаваемый внутри архивируемой папки, не включает сам себя");

        // имя + расширение, уникальность
        JSObject dup = new JSObject();
        dup.put("paths", arr(src.resolve("a.txt").toString())); dup.put("dest", root.toString()); dup.put("name", "pack-zip.zip"); dup.put("format", "zip");
        String second = call(ar, "create", dup).future.get().getString("path");
        check(second.endsWith("pack-zip (1).zip"), "имя архива уникализируется: " + Paths.get(second).getFileName());

        deleteTree(root);
        System.out.println();
        FileOpsTest.run();
        System.out.println("ИТОГО: пройдено " + passed + ", провалено " + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    static JSObject single(Path p) { JSObject o = new JSObject(); o.put("paths", arr(p.toString())); return o; }
    static File mk(Path p) throws IOException { Files.createDirectories(p.getParent()); return p.toFile(); }
    static boolean has2(org.json.JSONArray a, String name) throws Exception { for (int i = 0; i < a.length(); i++) if (a.getJSONObject(i).getString("name").endsWith(name)) return true; return false; }
    static boolean findGroup(org.json.JSONArray groups, String name) throws Exception { return anyGroupWith(groups, name); }
    static boolean anyGroupWith(org.json.JSONArray groups, String name) throws Exception {
        for (int i = 0; i < groups.length(); i++) if (has(groups.getJSONObject(i).getJSONArray("files"), name)) return true;
        return false;
    }
    static boolean findGroupWith(org.json.JSONArray groups, String a, String b) throws Exception {
        for (int i = 0; i < groups.length(); i++) {
            org.json.JSONArray f = groups.getJSONObject(i).getJSONArray("files");
            if (has(f, a) && has(f, b)) return true;
        }
        return false;
    }
}
