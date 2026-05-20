package ui;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

/**
 * Redimensionnement par glisser sur les bords d’un {@link Stage} sans décoration. La bande supérieure centrale est
 * exclue pour laisser la barre de titre personnalisée gérer le déplacement ; coins et autres bords redimensionnent.
 */
public final class StageResizeBehavior {

    private static final double M = 6;
    /** Exclude this strip from resize so custom title-bar controls (min/max/close) receive clicks. */
    private static final double TITLE_BAR_HEIGHT = 44;
    private static final double WINDOW_CONTROLS_WIDTH = 200;

    private enum Edge {
        NONE,
        NW,
        NE,
        SW,
        SE,
        S,
        E,
        W
    }

    private Edge edge = Edge.NONE;
    private double startW;
    private double startH;
    private double startStageX;
    private double startStageY;
    private double pressScreenX;
    private double pressScreenY;

    private StageResizeBehavior() {}

    /** Installe les gestionnaires souris pour le redimensionnement sur les bords. */
    public static void install(Stage stage, Scene scene) {
        StageResizeBehavior r = new StageResizeBehavior();
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> r.onMoved(stage, scene, e));
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> r.onPressed(stage, scene, e));
        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> r.onDragged(stage, scene, e));
        scene.addEventFilter(
                MouseEvent.MOUSE_RELEASED,
                e -> {
                    r.edge = Edge.NONE;
                    if (!e.isPrimaryButtonDown()) {
                        scene.setCursor(Cursor.DEFAULT);
                    }
                });
    }

    private void onMoved(Stage stage, Scene scene, MouseEvent e) {
        if (stage.isMaximized() || stage.isFullScreen()) {
            scene.setCursor(Cursor.DEFAULT);
            return;
        }
        if (e.isPrimaryButtonDown()) {
            return;
        }
        if (isTitleBarInteractiveTarget(e)) {
            scene.setCursor(Cursor.DEFAULT);
            return;
        }
        Edge d = detect(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
        scene.setCursor(cursorFor(d));
    }

    private static Cursor cursorFor(Edge ed) {
        return switch (ed) {
            case NW -> Cursor.NW_RESIZE;
            case NE -> Cursor.NE_RESIZE;
            case SW -> Cursor.SW_RESIZE;
            case SE -> Cursor.SE_RESIZE;
            case S -> Cursor.S_RESIZE;
            case E -> Cursor.E_RESIZE;
            case W -> Cursor.W_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    /**
     * Top-center (not corners) is NONE so the title bar can own drag in that strip.
     * <p>Horizontal resize (E/W) must not apply in the title-bar band: the rightmost few pixels
     * would otherwise steal clicks from minimize / maximize / close (undecorated window).
     */
    private static Edge detect(double x, double y, double w, double h) {
        if (x >= w - WINDOW_CONTROLS_WIDTH && y <= TITLE_BAR_HEIGHT) {
            return Edge.NONE;
        }
        boolean left = x <= M;
        boolean right = x >= w - M;
        boolean top = y <= M;
        boolean bottom = y >= h - M;
        if (top && left) {
            return Edge.NW;
        }
        if (top && right) {
            return Edge.NE;
        }
        if (bottom && left) {
            return Edge.SW;
        }
        if (bottom && right) {
            return Edge.SE;
        }
        if (top) {
            return Edge.NONE;
        }
        if (bottom) {
            return Edge.S;
        }
        // Pas de redimensionnement horizontal sur la bande de la barre de titre (boutons chrome).
        if (y <= TITLE_BAR_HEIGHT) {
            return Edge.NONE;
        }
        if (left) {
            return Edge.W;
        }
        if (right) {
            return Edge.E;
        }
        return Edge.NONE;
    }

    private void onPressed(Stage stage, Scene scene, MouseEvent e) {
        if (stage.isMaximized() || stage.isFullScreen() || !e.isPrimaryButtonDown()) {
            return;
        }
        if (isTitleBarInteractiveTarget(e)) {
            return;
        }
        edge = detect(e.getSceneX(), e.getSceneY(), scene.getWidth(), scene.getHeight());
        if (edge == Edge.NONE) {
            return;
        }
        startW = stage.getWidth();
        startH = stage.getHeight();
        startStageX = stage.getX();
        startStageY = stage.getY();
        pressScreenX = e.getScreenX();
        pressScreenY = e.getScreenY();
        e.consume();
    }

    private void onDragged(Stage stage, Scene scene, MouseEvent e) {
        if (edge == Edge.NONE || stage.isMaximized() || stage.isFullScreen()) {
            return;
        }
        double dx = e.getScreenX() - pressScreenX;
        double dy = e.getScreenY() - pressScreenY;
        double minW = stage.getMinWidth() > 0 ? stage.getMinWidth() : 320;
        double minH = stage.getMinHeight() > 0 ? stage.getMinHeight() : 240;

        double nw = startW;
        double nh = startH;
        double nx = startStageX;
        double ny = startStageY;

        switch (edge) {
            case E -> nw = Math.max(minW, startW + dx);
            case W -> {
                nw = Math.max(minW, startW - dx);
                nx = startStageX + (startW - nw);
            }
            case S -> nh = Math.max(minH, startH + dy);
            case SE -> {
                nw = Math.max(minW, startW + dx);
                nh = Math.max(minH, startH + dy);
            }
            case SW -> {
                nw = Math.max(minW, startW - dx);
                nx = startStageX + (startW - nw);
                nh = Math.max(minH, startH + dy);
            }
            case NE -> {
                nw = Math.max(minW, startW + dx);
                nh = Math.max(minH, startH - dy);
                ny = startStageY + (startH - nh);
            }
            case NW -> {
                nw = Math.max(minW, startW - dx);
                nx = startStageX + (startW - nw);
                nh = Math.max(minH, startH - dy);
                ny = startStageY + (startH - nh);
            }
            default -> {
                return;
            }
        }

        stage.setX(nx);
        stage.setY(ny);
        stage.setWidth(nw);
        stage.setHeight(nh);
        e.consume();
    }

    /** Cibles qui ne doivent pas déclencher le redimensionnement (barre de titre personnalisée). */
    private static boolean isTitleBarInteractiveTarget(MouseEvent e) {
        if (e.getSceneY() > TITLE_BAR_HEIGHT) {
            return false;
        }
        Object t = e.getTarget();
        return t instanceof Button
                || t instanceof Label
                || t instanceof ImageView
                || t instanceof TextFlow
                || t instanceof Text;
    }
}
