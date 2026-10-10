package android.graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
public class Bitmap {
    public enum Config { RGB_565, ARGB_8888 }
    public final BufferedImage img;
    public Bitmap(BufferedImage i) { img = i; }
    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        // area-averaging, как это делает хороший даунскейл
        java.awt.Image scaled = src.img.getScaledInstance(w, h, java.awt.Image.SCALE_AREA_AVERAGING);
        g.drawImage(scaled, 0, 0, null); g.dispose();
        return new Bitmap(out);
    }
    public int getPixel(int x, int y) { return img.getRGB(x, y) | 0xFF000000; }
    public void recycle() {}
}
