package io.github.ufml.launcher.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.geom.RoundRectangle2D;

public final class Surface {
    private Surface() { }

    public static final class Background extends JPanel {
        private final boolean glass;

        public Background() { this(false); }

        public Background(boolean glass) {
            this.glass = glass;
            setOpaque(!glass);
        }

        @Override
        protected void paintComponent(Graphics raw) {
            super.paintComponent(raw);
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                int width = getWidth();
                int height = getHeight();
                RoundRectangle2D shape = new RoundRectangle2D.Float(1f, 1f,
                        width - 2f, height - 2f, 30f, 30f);
                if (glass) graphics.setClip(shape);
                graphics.setPaint(new GradientPaint(0, 0,
                        glass ? Theme.GLASS_TOP : Theme.BG_TOP,
                        width, height, glass ? Theme.GLASS_BOTTOM : Theme.BG_BOTTOM));
                graphics.fillRect(0, 0, width, height);

                graphics.setPaint(Theme.glow(width * 0.84f, height * 0.10f,
                        Math.max(width, height) * 0.42f, new Color(57, 137, 50, 55)));
                graphics.fillRect(0, 0, width, height);
                graphics.setPaint(Theme.glow(width * 0.10f, height * 0.96f,
                        Math.max(width, height) * 0.35f, new Color(25, 91, 122, 38)));
                graphics.fillRect(0, 0, width, height);

                graphics.setColor(new Color(180, 210, 230, 7));
                for (int x = 18; x < width; x += 42) {
                    graphics.drawLine(x, 0, x, height);
                }
                for (int y = 18; y < height; y += 42) {
                    graphics.drawLine(0, y, width, y);
                }

                drawFloatingPixels(graphics, width, height);
                if (glass) {
                    graphics.setClip(null);
                    graphics.setColor(Theme.GLASS_EDGE);
                    graphics.setStroke(new BasicStroke(1f));
                    graphics.draw(shape);
                }
            } finally {
                graphics.dispose();
            }
        }

        private static void drawFloatingPixels(Graphics2D graphics, int width, int height) {
            graphics.setColor(new Color(136, 224, 84, 18));
            graphics.fillRect(width - 180, 116, 7, 7);
            graphics.fillRect(width - 156, 92, 4, 4);
            graphics.fillRect(44, height - 130, 5, 5);
            graphics.setColor(new Color(120, 180, 255, 14));
            graphics.fillRect(width - 76, height - 190, 9, 9);
            graphics.fillRect(width - 96, height - 210, 4, 4);
        }
    }

    public static class Card extends JPanel {
        private final int radius;
        private Color fill = Theme.GLASS_SURFACE;
        private Color outline = Theme.BORDER_SOFT;

        public Card(LayoutManager layout, int radius) {
            super(layout);
            this.radius = radius;
            setOpaque(false);
        }

        public void setFill(Color fill) { this.fill = fill; repaint(); }
        public void setOutline(Color outline) { this.outline = outline; repaint(); }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f,
                        getWidth() - 1f, getHeight() - 1f, radius, radius);
                graphics.setColor(fill);
                graphics.fill(shape);
                graphics.setColor(outline);
                graphics.setStroke(new BasicStroke(1f));
                graphics.draw(shape);
            } finally {
                graphics.dispose();
            }
            super.paintComponent(raw);
        }
    }

    public static final class Pill extends JComponent {
        private final String text;
        private final Color dot;
        private final Color fill;
        private final Color foreground;

        public Pill(String text, Color dot, Color fill, Color foreground) {
            this.text = text;
            this.dot = dot;
            this.fill = fill;
            this.foreground = foreground;
            setOpaque(false);
            setFont(Theme.regular(11.5f));
        }

        @Override
        public java.awt.Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            int width = metrics.stringWidth(text) + (dot == null ? 28 : 40);
            return new java.awt.Dimension(width, 28);
        }

        @Override
        public java.awt.Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                graphics.setColor(fill);
                graphics.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                int x = 12;
                if (dot != null) {
                    graphics.setColor(Theme.alpha(dot, 40));
                    graphics.fillOval(x, getHeight() / 2 - 6, 12, 12);
                    graphics.setColor(dot);
                    graphics.fillOval(x + 3, getHeight() / 2 - 3, 6, 6);
                    x += 18;
                }
                graphics.setFont(getFont());
                graphics.setColor(foreground);
                FontMetrics metrics = graphics.getFontMetrics();
                graphics.drawString(text, x, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            } finally {
                graphics.dispose();
            }
        }
    }
}
