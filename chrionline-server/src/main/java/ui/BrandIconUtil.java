package ui;

import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Logo ChriOnline en PNG vers {@link Image} JavaFX. Les icônes in-app utilisent un fond <strong>opaque</strong>
 * dont le RGB colle à la surface derrière (boîte vitrée, pastille d’onglet) pour éviter la « tuile » grise.
 * La scène / barre des tâches utilise {@link #PLATE_STAGE}.
 *
 * <p>Le motif est une silhouette de <strong>sac shopping</strong> (anse + corps) en orange / ambre / blanc.
 */
public final class BrandIconUtil {

    /** Windows title bar strip ({@code ChriOnlineClientApp} buildCustomTitleBar). */
    private static final int PLATE_STAGE = 0x0B0E14;

    /**
     * Solid equivalent of {@code createGlassBox}: {@code rgba(255,255,255,0.05)} over shell {@code #070B17}.
     */
    private static final int PLATE_UI_SIDEBAR = blendRgbOver(0x070B17, 0xFFFFFF, 0.05f);

    /**
     * Solid equivalent of tab pill: {@code rgba(28,36,54,0.95)} over {@link #PLATE_STAGE} title-bar strip.
     */
    private static final int PLATE_UI_TITLE_TAB = blendRgbOver(PLATE_STAGE, 0x1C2436, 0.95f);

    /** Harsh orange–red (Tailwind orange-600) — main bag fill. */
    private static final Color BAG_MAIN = new Color(234, 88, 12);
    /** Darker orange for depth (orange-700). */
    private static final Color BAG_SHADOW = new Color(194, 65, 12);
    /** Burnt outline (orange-900). */
    private static final Color BAG_OUTLINE = new Color(124, 45, 18);
    /** Amber accent stripe (amber-500). */
    private static final Color BAG_ACCENT = new Color(245, 158, 11);
    /** Handle / highlight — near white. */
    private static final Color BAG_INK = new Color(250, 250, 250);
    /** Lighter orange edge highlight (orange-500). */
    private static final Color BAG_EDGE_HI = new Color(249, 115, 22);

    private static final Map<String, Image> CACHE = new HashMap<>();

    private BrandIconUtil() {}

    /** Panneau latéral / vitré — fond aligné sur {@code createGlassBox} sur {@code #070B17}. */
    public static Image createFxImage(int size) {
        return getCached(size, UiPlate.SIDEBAR);
    }

    /** Pastille d’onglet de la barre de titre — fond aligné sur {@code rgba(28,36,54,0.95)} sur {@link #PLATE_STAGE}. */
    public static Image createFxImageTitleTab(int size) {
        return getCached(size, UiPlate.TITLE_TAB);
    }

    /** Icône de fenêtre / barre des tâches (fond {@link #PLATE_STAGE}). */
    public static Image createFxImageForStage(int size) {
        return getCached(size, UiPlate.STAGE);
    }

    private enum UiPlate {
        SIDEBAR,
        TITLE_TAB,
        STAGE
    }

    private static Image getCached(int size, UiPlate plate) {
        int px = Math.max(16, Math.min(512, size));
        String key = px + "-" + plate.name();
        synchronized (CACHE) {
            Image hit = CACHE.get(key);
            if (hit != null) {
                return hit;
            }
            try {
                Image img =
                        plate == UiPlate.STAGE
                                ? rasterizeStage(px)
                                : rasterizeUi(
                                        px,
                                        plate == UiPlate.TITLE_TAB ? PLATE_UI_TITLE_TAB : PLATE_UI_SIDEBAR);
                CACHE.put(key, img);
                return img;
            } catch (IOException e) {
                throw new IllegalStateException("Brand icon raster failed", e);
            }
        }
    }

    /** {@code dst * (1-a) + src * a} in sRGB (matches typical JavaFX over solid background). */
    private static int blendRgbOver(int dstRgb, int srcRgb, float srcAlpha) {
        int dr = (dstRgb >> 16) & 0xff;
        int dg = (dstRgb >> 8) & 0xff;
        int db = dstRgb & 0xff;
        int sr = (srcRgb >> 16) & 0xff;
        int sg = (srcRgb >> 8) & 0xff;
        int sb = srcRgb & 0xff;
        float a = Math.max(0f, Math.min(1f, srcAlpha));
        int r = Math.round(dr * (1 - a) + sr * a);
        int g = Math.round(dg * (1 - a) + sg * a);
        int b = Math.round(db * (1 - a) + sb * a);
        return (r << 16) | (g << 8) | b;
    }

    private static Image rasterizeUi(int px, int plateRgb) throws IOException {
        BufferedImage bi = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        try {
            setupGraphics(g);
            g.setColor(new Color(plateRgb));
            g.fill(new RoundRectangle2D.Double(0, 0, px, px, px * 0.22, px * 0.22));
            double s = px / 64.0;
            drawShopMark(g, s);
        } finally {
            g.dispose();
        }
        return encodePng(bi, px);
    }

    private static Image rasterizeStage(int px) throws IOException {
        BufferedImage bi = new BufferedImage(px, px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        try {
            setupGraphics(g);
            g.setColor(new Color(PLATE_STAGE));
            g.fill(new RoundRectangle2D.Double(0, 0, px, px, px * 0.22, px * 0.22));
            double s = px / 64.0;
            drawShopMark(g, s);
        } finally {
            g.dispose();
        }
        return encodePng(bi, px);
    }

    private static void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
    }

    /**
     * Shopping-bag mark: curved handle, tapered body, amber band, edge highlight, rivets, stitching. Grid
     * 64×64 ({@code s} scales to pixels).
     */
    private static void drawShopMark(Graphics2D g, double s) {
        float strokeOut = (float) Math.max(2.0, 2.75 * s);
        float strokeHandle = (float) Math.max(2.6, 3.4 * s);
        float stitch = (float) Math.max(0.9, 1.15 * s);

        Path2D bag = new Path2D.Double();
        bag.moveTo(14 * s, 27 * s);
        bag.lineTo(50 * s, 27 * s);
        bag.lineTo(47 * s, 53 * s);
        bag.lineTo(17 * s, 53 * s);
        bag.closePath();

        g.setColor(BAG_SHADOW);
        g.fill(bag);

        Path2D bagMain = new Path2D.Double();
        bagMain.moveTo(15 * s, 28 * s);
        bagMain.lineTo(49 * s, 28 * s);
        bagMain.lineTo(46.5 * s, 51.5 * s);
        bagMain.lineTo(17.5 * s, 51.5 * s);
        bagMain.closePath();
        g.setColor(BAG_MAIN);
        g.fill(bagMain);

        // Left-edge light strip (depth)
        Path2D edgeHi = new Path2D.Double();
        edgeHi.moveTo(15 * s, 28 * s);
        edgeHi.lineTo(21 * s, 28 * s);
        edgeHi.lineTo(20 * s, 51.5 * s);
        edgeHi.lineTo(17.5 * s, 51.5 * s);
        edgeHi.closePath();
        g.setColor(BAG_EDGE_HI);
        g.fill(edgeHi);

        Path2D accentBand = new Path2D.Double();
        accentBand.moveTo(17 * s, 42 * s);
        accentBand.lineTo(47 * s, 42 * s);
        accentBand.lineTo(46 * s, 46 * s);
        accentBand.lineTo(18 * s, 46 * s);
        accentBand.closePath();
        g.setColor(BAG_ACCENT);
        g.fill(accentBand);

        // Specular glint on band
        g.setColor(new Color(255, 243, 220));
        g.fill(new Ellipse2D.Double(36 * s, 42.5 * s, 7 * s, 3 * s));

        // Stitching above band
        g.setColor(BAG_OUTLINE);
        g.setStroke(new BasicStroke(stitch, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float dash = (float) Math.max(2, 2.5 * s);
        float gap = (float) Math.max(1.5, 2 * s);
        g.setStroke(
                new BasicStroke(
                        stitch,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10f,
                        new float[] {dash, gap},
                        0f));
        g.drawLine((int) (19 * s), (int) (40 * s), (int) (45 * s), (int) (40 * s));

        g.setColor(BAG_OUTLINE);
        g.setStroke(new BasicStroke(strokeOut, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(bag);

        // Rivets where handle meets bag
        double rivetR = Math.max(1.1, 1.45 * s);
        g.setColor(BAG_OUTLINE);
        g.fill(new Ellipse2D.Double(18 * s - rivetR, 26 * s - rivetR, 2 * rivetR, 2 * rivetR));
        g.fill(new Ellipse2D.Double(46 * s - rivetR, 26 * s - rivetR, 2 * rivetR, 2 * rivetR));
        g.setColor(BAG_ACCENT);
        g.fill(new Ellipse2D.Double(18 * s - rivetR * 0.55, 26 * s - rivetR * 0.55, rivetR * 1.1, rivetR * 1.1));
        g.fill(new Ellipse2D.Double(46 * s - rivetR * 0.55, 26 * s - rivetR * 0.55, rivetR * 1.1, rivetR * 1.1));

        Path2D handle = new Path2D.Double();
        handle.moveTo(19 * s, 27 * s);
        handle.quadTo(32 * s, 7 * s, 45 * s, 27 * s);
        g.setColor(BAG_INK);
        g.setStroke(new BasicStroke(strokeHandle, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(handle);

        // Inner fold lines (side creases)
        g.setColor(BAG_SHADOW);
        g.setStroke(new BasicStroke((float) Math.max(0.8, 1.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (22 * s), (int) (30 * s), (int) (20 * s), (int) (49 * s));
        g.drawLine((int) (42 * s), (int) (30 * s), (int) (44 * s), (int) (49 * s));

        g.setColor(BAG_OUTLINE);
        g.setStroke(new BasicStroke((float) Math.max(1.0, 1.2 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (18 * s), (int) (50 * s), (int) (46 * s), (int) (50 * s));
    }

    private static Image encodePng(BufferedImage bi, int px) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", baos);
        byte[] bytes = baos.toByteArray();
        return new Image(new ByteArrayInputStream(bytes), px, px, false, true);
    }
}
