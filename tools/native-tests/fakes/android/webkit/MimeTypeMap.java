package android.webkit;

/** Минимальная замена для проверок на обычной JVM. */
public class MimeTypeMap {
    private static final MimeTypeMap INSTANCE = new MimeTypeMap();
    public static MimeTypeMap getSingleton() { return INSTANCE; }
    public String getMimeTypeFromExtension(String ext) {
        switch (ext) {
            case "txt": return "text/plain";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "zip": return "application/zip";
            default: return null;
        }
    }
}
