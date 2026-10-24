package dev.filedeck.app;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Открытие файла в другом приложении и «Поделиться». Ошибки несут готовый текст для пользователя. */
final class FileOpener {
    private FileOpener() {}

    static void open(Context ctx, String path) throws IOException {
        if (path == null) throw new IOException("Не указан путь");
        File f = new File(path);
        try {
            Uri uri = uriFor(ctx, f);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, MimeTypes.of(f.getName()));
            intent.setClipData(ClipData.newRawUri("", uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            throw new IOException("Нет приложения для открытия этого файла");
        } catch (Exception e) {
            throw new IOException("Не удалось открыть файл: " + e.getMessage());
        }
    }

    static void share(Context ctx, List<String> paths) throws IOException {
        if (paths.isEmpty()) throw new IOException("Нечем делиться");
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            for (String p : paths) {
                File f = new File(p);
                if (f.isFile()) uris.add(uriFor(ctx, f));
            }
            if (uris.isEmpty()) throw new IOException("Поделиться можно только файлами, не папками");
            Intent intent;
            if (uris.size() == 1) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                intent.setType(MimeTypes.of(new File(paths.get(0)).getName()));
            } else {
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                intent.setType("*/*");
            }
            ClipData clip = ClipData.newRawUri("", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
            intent.setClipData(clip);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(intent, "Поделиться");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooser);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Не удалось поделиться: " + e.getMessage());
        }
    }

    private static Uri uriFor(Context ctx, File f) {
        return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
    }
}
