package android.content;
import android.content.pm.PackageManager;
import java.io.File;
public class Context {
    public File[] extra = new File[0];
    public File[] getExternalFilesDirs(String t) { return extra; }
    public PackageManager getPackageManager() { return new PackageManager(); }
    public String getPackageName() { return "dev.filedeck.app"; }
}
