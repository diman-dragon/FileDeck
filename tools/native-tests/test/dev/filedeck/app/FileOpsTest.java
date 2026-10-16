package dev.filedeck.app;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;

import java.nio.file.*;
import java.util.*;

/** Проверки файловых операций: список, поиск, размер, создание, переименование, копирование, перенос, текст. */
final class FileOpsTest {
    private FileOpsTest() {}

    static void run() throws Exception {
        System.out.println("\n== Файловые операции ==");
        Path root = Files.createTempDirectory("fdfiles");
        Path a = root.resolve("a");
        FdTest.text(a.resolve("hello.txt"), "привет");
        FdTest.text(a.resolve("sub/deep/Report-2026.TXT"), "x");
        Files.createDirectories(root.resolve("dest"));

        JSObject list = FileBrowser.list(a.toString());
        org.json.JSONArray entries = list.getJSONArray("entries");
        FdTest.check(entries.length() == 2, "list: два элемента в папке");

        boolean thrown = false;
        try { FileBrowser.list(root.resolve("нет").toString()); } catch (java.io.IOException e) { thrown = e.getMessage().contains("не существует"); }
        FdTest.check(thrown, "list: понятная ошибка для несуществующей папки");

        JSObject found = FileBrowser.search(a.toString(), "report", 10);
        FdTest.check(found.getJSONArray("entries").length() == 1 && !found.getBoolean("truncated"), "search: нашёл файл без учёта регистра");
        JSObject limited = FileBrowser.search(root.toString(), "e", 1);
        FdTest.check(limited.getBoolean("truncated"), "search: лимит обрезает выдачу");

        JSObject size = FileBrowser.size(a.toString());
        FdTest.check(size.getLong("files") == 2 && size.getLong("folders") == 2, "size: 2 файла и 2 вложенные папки");

        JSObject made = FileOps.mkdir(root.resolve("new").toString());
        FdTest.check(Files.isDirectory(Paths.get(made.getString("path"))), "mkdir создаёт папку");
        boolean dup = false;
        try { FileOps.mkdir(root.resolve("new").toString()); } catch (java.io.IOException e) { dup = true; }
        FdTest.check(dup, "mkdir: повтор отклонён");

        FileOps.createFile(root.resolve("empty.txt").toString());
        FdTest.check(Files.size(root.resolve("empty.txt")) == 0, "createFile создаёт пустой файл");

        FileOps.rename(root.resolve("empty.txt").toString(), "renamed.txt");
        FdTest.check(Files.exists(root.resolve("renamed.txt")), "rename переименовывает");
        boolean bad = false;
        try { FileOps.rename(root.resolve("renamed.txt").toString(), "a/b"); } catch (java.io.IOException e) { bad = true; }
        FdTest.check(bad, "rename: имя со слешем отклонено");

        List<String> one = Arrays.asList(a.resolve("hello.txt").toString());
        FileOps.transfer(one, root.resolve("dest").toString(), false);
        JSObject second = FileOps.transfer(one, root.resolve("dest").toString(), false);
        FdTest.check(second.getInt("done") == 1 && Files.exists(root.resolve("dest/hello (1).txt")), "copy: при конфликте имён — « (1)»");
        FdTest.check(Files.exists(a.resolve("hello.txt")), "copy: исходный файл на месте");

        JSObject intoSelf = FileOps.transfer(Arrays.asList(a.toString()), a.resolve("sub").toString(), true);
        FdTest.check(intoSelf.getInt("done") == 0 && intoSelf.getJSONArray("failed").length() == 1, "move: папку нельзя перенести внутрь себя");

        JSObject moved = FileOps.transfer(Arrays.asList(a.resolve("sub").toString()), root.resolve("dest").toString(), true);
        FdTest.check(moved.getInt("done") == 1 && Files.exists(root.resolve("dest/sub/deep/Report-2026.TXT")) && !Files.exists(a.resolve("sub")), "move: папка перенесена целиком");

        TextFiles.write(root.resolve("t.txt").toString(), "строка\nвторая");
        FdTest.check("строка\nвторая".equals(TextFiles.read(root.resolve("t.txt").toString()).getString("text")), "TextFiles: запись и чтение UTF-8");
        Files.write(root.resolve("big.txt"), new byte[2 * 1024 * 1024 + 1]);
        boolean big = false;
        try { TextFiles.read(root.resolve("big.txt").toString()); } catch (java.io.IOException e) { big = e.getMessage().contains("слишком большой"); }
        FdTest.check(big, "TextFiles: файл больше 2 МБ отклонён");

        JSObject removed = FileOps.remove(Arrays.asList(root.resolve("dest").toString(), root.resolve("нет").toString()));
        FdTest.check(removed.getInt("done") == 1 && removed.getJSONArray("failed").length() == 1 && !Files.exists(root.resolve("dest")), "remove: папка удалена, отсутствующий путь — в failed");

        FdTest.check("text/plain".equals(MimeTypes.of("a.md")) && "application/json".equals(MimeTypes.of("x.json")) && "application/octet-stream".equals(MimeTypes.of("noext")), "MimeTypes: запасные типы");
    }
}
