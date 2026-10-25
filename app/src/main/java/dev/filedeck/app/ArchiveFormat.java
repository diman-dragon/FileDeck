package dev.filedeck.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Формат архива: определяется по имени или по первым байтам файла. */
enum ArchiveFormat {
    ZIP, SEVENZ, TAR, TGZ, TBZ2, TXZ, GZ, BZ2, XZ, RAR, UNKNOWN;

    static ArchiveFormat detect(File f) {
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".zip") || n.endsWith(".jar") || n.endsWith(".cbz") || n.endsWith(".apk")) return ArchiveFormat.ZIP;
        if (n.endsWith(".7z")) return ArchiveFormat.SEVENZ;
        if (n.endsWith(".rar")) return ArchiveFormat.RAR;
        if (n.endsWith(".tar.gz") || n.endsWith(".tgz")) return ArchiveFormat.TGZ;
        if (n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz")) return ArchiveFormat.TBZ2;
        if (n.endsWith(".tar.xz") || n.endsWith(".txz")) return ArchiveFormat.TXZ;
        if (n.endsWith(".tar")) return ArchiveFormat.TAR;
        if (n.endsWith(".gz")) return ArchiveFormat.GZ;
        if (n.endsWith(".bz2")) return ArchiveFormat.BZ2;
        if (n.endsWith(".xz")) return ArchiveFormat.XZ;
        return sniff(f);
    }

    private static ArchiveFormat sniff(File f) {
        byte[] b = new byte[8];
        int n;
        try (InputStream in = new FileInputStream(f)) {
            n = in.read(b);
        } catch (IOException e) {
            return ArchiveFormat.UNKNOWN;
        }
        if (n >= 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) return ArchiveFormat.ZIP;
        if (n >= 6 && (b[0] & 0xFF) == 0x37 && (b[1] & 0xFF) == 0x7A && (b[2] & 0xFF) == 0xBC && (b[3] & 0xFF) == 0xAF) {
            return ArchiveFormat.SEVENZ;
        }
        if (n >= 4 && b[0] == 'R' && b[1] == 'a' && b[2] == 'r' && b[3] == '!') return ArchiveFormat.RAR;
        if (n >= 2 && (b[0] & 0xFF) == 0x1F && (b[1] & 0xFF) == 0x8B) return ArchiveFormat.GZ;
        if (n >= 3 && b[0] == 'B' && b[1] == 'Z' && b[2] == 'h') return ArchiveFormat.BZ2;
        if (n >= 6 && (b[0] & 0xFF) == 0xFD && b[1] == '7' && b[2] == 'z' && b[3] == 'X' && b[4] == 'Z') return ArchiveFormat.XZ;
        return ArchiveFormat.UNKNOWN;
    }

    static ArchiveFormat byName(String s) {
        switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
            case "zip":
                return ArchiveFormat.ZIP;
            case "7z":
                return ArchiveFormat.SEVENZ;
            case "tar":
                return ArchiveFormat.TAR;
            case "tar.gz":
            case "tgz":
                return ArchiveFormat.TGZ;
            case "tar.bz2":
            case "tbz2":
                return ArchiveFormat.TBZ2;
            case "tar.xz":
            case "txz":
                return ArchiveFormat.TXZ;
            default:
                return ArchiveFormat.UNKNOWN;
        }
    }

    String extension() {
        switch (this) {
            case ZIP:
                return ".zip";
            case SEVENZ:
                return ".7z";
            case TAR:
                return ".tar";
            case TGZ:
                return ".tar.gz";
            case TBZ2:
                return ".tar.bz2";
            case TXZ:
                return ".tar.xz";
            default:
                return "";
        }
    }

    static String stripExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String[] exts = { ".tar.gz", ".tar.bz2", ".tar.xz", ".tgz", ".tbz2", ".tbz", ".txz", ".zip", ".7z", ".rar", ".tar", ".gz", ".bz2",
            ".xz", ".jar", ".cbz" };
        for (String e : exts) {
            if (lower.endsWith(e) && name.length() > e.length()) return name.substring(0, name.length() - e.length());
        }
        return name;
    }
}
