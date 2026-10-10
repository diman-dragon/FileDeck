package android.graphics;
import java.awt.image.BufferedImage;
import java.io.File;
public class BitmapFactory {
    public static class Options {
        public boolean inJustDecodeBounds; public int outWidth; public int outHeight; public int inSampleSize = 1; public Bitmap.Config inPreferredConfig;
    }
    public static Bitmap decodeFile(String path, Options o) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(new File(path));
            if (img == null) return null;
            if (o != null && o.inJustDecodeBounds) { o.outWidth = img.getWidth(); o.outHeight = img.getHeight(); return null; }
            int s = o == null ? 1 : Math.max(1, o.inSampleSize);
            if (s > 1) {
                BufferedImage out = new BufferedImage(img.getWidth() / s, img.getHeight() / s, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.drawImage(img.getScaledInstance(out.getWidth(), out.getHeight(), java.awt.Image.SCALE_AREA_AVERAGING), 0, 0, null); g.dispose();
                img = out;
            }
            return new Bitmap(img);
        } catch (Exception e) { return null; }
    }
}
