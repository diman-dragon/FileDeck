package android.os;

/** Минимальная замена: размеры берутся из файловой системы JVM. */
public class StatFs {
    private final java.io.File f;
    public StatFs(String path) { f = new java.io.File(path); }
    public long getTotalBytes() { return f.getTotalSpace(); }
    public long getAvailableBytes() { return f.getUsableSpace(); }
}
