package io.github.ufml.launcher.ui;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

final class WindowResizeGlassPane extends JPanel {
    private static final int EDGE = 8;

    private final JFrame window;
    private Zone zone = Zone.NONE;
    private Rectangle startBounds;
    private Point startScreen;

    WindowResizeGlassPane(JFrame window) {
        this.window = window;
        setOpaque(false);
        MouseAdapter handler = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                setCursor(Cursor.getPredefinedCursor(zoneAt(event.getX(), event.getY()).cursor));
            }

            @Override public void mouseExited(MouseEvent event) {
                setCursor(Cursor.getDefaultCursor());
            }

            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) return;
                zone = zoneAt(event.getX(), event.getY());
                if (zone == Zone.NONE) return;
                startBounds = window.getBounds();
                startScreen = event.getLocationOnScreen();
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (zone == Zone.NONE || startBounds == null || startScreen == null) return;
                Point current = event.getLocationOnScreen();
                resizeWindow(current.x - startScreen.x, current.y - startScreen.y);
            }

            @Override public void mouseReleased(MouseEvent event) {
                zone = Zone.NONE;
                startBounds = null;
                startScreen = null;
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    @Override
    public boolean contains(int x, int y) {
        return zoneAt(x, y) != Zone.NONE;
    }

    private void resizeWindow(int dx, int dy) {
        Dimension minimum = window.getMinimumSize();
        Rectangle bounds = new Rectangle(startBounds);
        if (zone.left) {
            int width = startBounds.width - dx;
            if (width < minimum.width) {
                bounds.x = startBounds.x + startBounds.width - minimum.width;
                bounds.width = minimum.width;
            } else {
                bounds.x = startBounds.x + dx;
                bounds.width = width;
            }
        } else if (zone.right) {
            bounds.width = Math.max(minimum.width, startBounds.width + dx);
        }
        if (zone.top) {
            int height = startBounds.height - dy;
            if (height < minimum.height) {
                bounds.y = startBounds.y + startBounds.height - minimum.height;
                bounds.height = minimum.height;
            } else {
                bounds.y = startBounds.y + dy;
                bounds.height = height;
            }
        } else if (zone.bottom) {
            bounds.height = Math.max(minimum.height, startBounds.height + dy);
        }
        window.setBounds(bounds);
    }

    private Zone zoneAt(int x, int y) {
        boolean left = x <= EDGE;
        boolean right = x >= getWidth() - EDGE;
        boolean top = y <= EDGE;
        boolean bottom = y >= getHeight() - EDGE;
        if (top && left) return Zone.NORTH_WEST;
        if (top && right) return Zone.NORTH_EAST;
        if (bottom && left) return Zone.SOUTH_WEST;
        if (bottom && right) return Zone.SOUTH_EAST;
        if (top) return Zone.NORTH;
        if (bottom) return Zone.SOUTH;
        if (left) return Zone.WEST;
        if (right) return Zone.EAST;
        return Zone.NONE;
    }

    private enum Zone {
        NONE(Cursor.DEFAULT_CURSOR, false, false, false, false),
        NORTH(Cursor.N_RESIZE_CURSOR, false, false, true, false),
        SOUTH(Cursor.S_RESIZE_CURSOR, false, false, false, true),
        WEST(Cursor.W_RESIZE_CURSOR, true, false, false, false),
        EAST(Cursor.E_RESIZE_CURSOR, false, true, false, false),
        NORTH_WEST(Cursor.NW_RESIZE_CURSOR, true, false, true, false),
        NORTH_EAST(Cursor.NE_RESIZE_CURSOR, false, true, true, false),
        SOUTH_WEST(Cursor.SW_RESIZE_CURSOR, true, false, false, true),
        SOUTH_EAST(Cursor.SE_RESIZE_CURSOR, false, true, false, true);

        private final int cursor;
        private final boolean left;
        private final boolean right;
        private final boolean top;
        private final boolean bottom;

        Zone(int cursor, boolean left, boolean right, boolean top, boolean bottom) {
            this.cursor = cursor;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }
}
