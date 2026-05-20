package ui;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Charge les images produits distantes (dont <strong>WebP</strong>) vers {@link Image} JavaFX.
 * ImageIO + SPI WebP (TwelveMonkeys), copie des pixels dans {@link WritableImage} — pas de {@code javafx.swing},
 * pour des builds Eclipse sans module Swing JavaFX.
 */
public final class ProductImageLoader {

    static {
        ImageIO.scanForPlugins();
    }

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    private ProductImageLoader() {}

    /**
     * Décode l’URL en arrière-plan (ImageIO + WebP), redimensionne, puis livre l’image sur le fil JavaFX.
     */
    public static void loadAsync(
            String urlString,
            int targetW,
            int targetH,
            Consumer<Image> onSuccess,
            Runnable onFailure) {
        if (urlString == null || urlString.isBlank()) {
            Platform.runLater(onFailure != null ? onFailure : () -> {});
            return;
        }
        new Thread(
                        () -> {
                            try {
                                HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
                                conn.setRequestProperty("User-Agent", USER_AGENT);
                                conn.setRequestProperty("Accept", "image/webp,image/apng,image/*;q=0.8,*/*;q=0.5");
                                conn.setConnectTimeout(20000);
                                conn.setReadTimeout(35000);
                                conn.setInstanceFollowRedirects(true);
                                int code = conn.getResponseCode();
                                if (code >= 400) {
                                    tryFallbackJavaFx(urlString, targetW, targetH, onSuccess, onFailure);
                                    return;
                                }
                                try (InputStream in = conn.getInputStream()) {
                                    BufferedImage bi = ImageIO.read(in);
                                    if (bi == null) {
                                        tryFallbackJavaFx(urlString, targetW, targetH, onSuccess, onFailure);
                                        return;
                                    }
                                    BufferedImage scaled = scaleCover(bi, targetW, targetH);
                                    Image fx = bufferedImageToFxImage(scaled);
                                    Platform.runLater(() -> onSuccess.accept(fx));
                                }
                            } catch (Throwable t) {
                                tryFallbackJavaFx(urlString, targetW, targetH, onSuccess, onFailure);
                            }
                        },
                        "product-img")
                .start();
    }

    /** ARGB BufferedImage -> JavaFX Image without SwingFXUtils. */
    private static Image bufferedImageToFxImage(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        BufferedImage argb = bi;
        if (bi.getType() != BufferedImage.TYPE_INT_ARGB) {
            argb = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(bi, 0, 0, null);
            g.dispose();
        }
        WritableImage out = new WritableImage(w, h);
        PixelWriter pw = out.getPixelWriter();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = argb.getRGB(x, y);
                double a = ((p >> 24) & 0xFF) / 255.0;
                double r = ((p >> 16) & 0xFF) / 255.0;
                double g = ((p >> 8) & 0xFF) / 255.0;
                double b = (p & 0xFF) / 255.0;
                pw.setColor(x, y, new Color(r, g, b, a));
            }
        }
        return out;
    }

    /** Last resort: JavaFX decoder (works for PNG/JPEG in some environments). */
    private static void tryFallbackJavaFx(
            String urlString,
            int targetW,
            int targetH,
            Consumer<Image> onSuccess,
            Runnable onFailure) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
            c.setRequestProperty("User-Agent", USER_AGENT);
            c.setConnectTimeout(15000);
            c.setReadTimeout(25000);
            try (InputStream in = c.getInputStream()) {
                // 5-arg constructor (JavaFX 8+): no backgroundLoading flag — avoids API mismatch in some IDEs.
                Image img = new Image(in, (double) targetW, (double) targetH, true, true);
                if (!img.isError()) {
                    Platform.runLater(() -> onSuccess.accept(img));
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        Platform.runLater(onFailure != null ? onFailure : () -> {});
    }

    /**
     * Scales to fill the box (may crop) so the tile always looks filled.
     */
    private static BufferedImage scaleCover(BufferedImage src, int boxW, int boxH) {
        if (boxW <= 0 || boxH <= 0) {
            return src;
        }
        double sw = src.getWidth();
        double sh = src.getHeight();
        double scale = Math.max(boxW / sw, boxH / sh);
        int nw = (int) Math.round(sw * scale);
        int nh = (int) Math.round(sh * scale);
        BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        int x = Math.max(0, (nw - boxW) / 2);
        int y = Math.max(0, (nh - boxH) / 2);
        int w = Math.min(boxW, nw - x);
        int h = Math.min(boxH, nh - y);
        if (w <= 0 || h <= 0) {
            return src;
        }
        return scaled.getSubimage(x, y, w, h);
    }
}
