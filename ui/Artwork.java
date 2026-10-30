package io.github.ufml.launcher.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.Random;

public final class Artwork {
    private Artwork() { }

    public static final class LogoMark extends JComponent {
        public LogoMark() { setOpaque(false); }

        @Override public Dimension getPreferredSize() { return new Dimension(44, 44); }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                graphics.setColor(new Color(136, 224, 84, 24));
                graphics.fillOval(2, 4, getWidth() - 4, getHeight() - 4);
                drawCube(graphics, getWidth() / 2, 3, getWidth() - 10,
                        new Color(121, 205, 76), new Color(105, 74, 46),
                        new Color(80, 54, 34), new Color(143, 101, 63));
                graphics.setColor(new Color(255, 255, 255, 35));
                graphics.drawLine(9, 17, 21, 23);
            } finally {
                graphics.dispose();
            }
        }
    }

    public static final class BlockScene extends JComponent {
        private final Timer bobber;
        private float phase;
        private boolean animate = true;

        public BlockScene() {
            setOpaque(false);
            bobber = new Timer(55, event -> {
                phase += 0.04f;
                if (phase > (float) (Math.PI * 2)) phase -= (float) (Math.PI * 2);
                repaint();
            });
        }

        public void setAnimate(boolean animate) {
            this.animate = animate;
            if (!animate) bobber.stop();
            else if (isShowing()) bobber.start();
        }

        @Override public void addNotify() {
            super.addNotify();
            if (animate) bobber.start();
        }

        @Override public void removeNotify() {
            bobber.stop();
            super.removeNotify();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(265, 180); }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                int width = getWidth();
                int height = getHeight();
                graphics.setPaint(Theme.glow(width * 0.52f, height * 0.48f,
                        Math.min(width, height) * 0.7f, new Color(120, 230, 78, 44)));
                graphics.fillRect(0, 0, width, height);

                float bob = (float) Math.sin(phase) * 2.4f;
                graphics.setColor(new Color(0, 0, 0, 55));
                graphics.fillOval(width / 2 - 66, height - 42, 132, 18);
                drawCube(graphics, (int) (width * 0.51f), (int) (height * 0.22f + bob), 108,
                        new Color(124, 213, 76), new Color(104, 72, 44),
                        new Color(78, 52, 32), new Color(142, 98, 60));

                float smallBob = (float) Math.sin(phase + 1.7) * 3.5f;
                drawCube(graphics, (int) (width * 0.20f), (int) (height * 0.36f + smallBob), 48,
                        new Color(92, 176, 223), new Color(53, 104, 152),
                        new Color(37, 76, 120), new Color(70, 132, 187));
                drawCube(graphics, (int) (width * 0.80f), (int) (height * 0.58f - smallBob), 34,
                        new Color(236, 189, 79), new Color(180, 120, 47),
                        new Color(132, 82, 32), new Color(210, 153, 62));

                graphics.setColor(new Color(255, 255, 255, 32));
                graphics.setStroke(new BasicStroke(1f));
                graphics.drawLine(12, height - 62, width - 14, height - 62);
                graphics.setColor(new Color(136, 224, 84, 42));
                graphics.fillRect(width - 32, 28, 4, 4);
                graphics.fillRect(26, 32, 3, 3);
            } finally {
                graphics.dispose();
            }
        }
    }

    public static final class AvatarPanel extends JComponent {
        private String username;

        public AvatarPanel() { setOpaque(false); }

        public void setUsername(String username) {
            this.username = username;
            repaint();
        }

        @Override public Dimension getPreferredSize() { return new Dimension(104, 104); }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                graphics.setPaint(new GradientPaint(x, y, new Color(36, 54, 70),
                        x + size, y + size, new Color(19, 31, 42)));
                graphics.fillRoundRect(x, y, size, size, 22, 22);
                graphics.setColor(new Color(136, 224, 84, 30));
                graphics.setStroke(new BasicStroke(1f));
                graphics.drawRoundRect(x, y, size - 1, size - 1, 22, 22);

                int hash = username == null ? 0 : username.hashCode();
                Color[] skins = {new Color(244, 200, 143), new Color(222, 171, 113),
                        new Color(189, 133, 82), new Color(150, 99, 62), new Color(110, 72, 48)};
                Color[] hairs = {new Color(43, 27, 18), new Color(76, 47, 28),
                        new Color(29, 29, 31), new Color(160, 103, 55), new Color(220, 181, 97)};
                Color skin = skins[Math.floorMod(hash, skins.length)];
                Color hair = hairs[Math.floorMod(hash / 7, hairs.length)];
                Color shade = darken(skin, 0.85f);
                Color light = lighten(skin, 1.08f);
                int pixel = (size - 24) / 8;
                int startX = x + (size - pixel * 8) / 2;
                int startY = y + (size - pixel * 8) / 2;

                for (int row = 0; row < 8; row++) {
                    for (int col = 0; col < 8; col++) {
                        Color color = skin;
                        if (row < 2 || (row == 2 && (col < 2 || col > 5))) color = hair;
                        if (col == 0 || col == 7) color = darken(color, 0.86f);
                        if (row == 2 && col >= 2 && col <= 5) color = light;
                        if (row == 3 && (col == 2 || col == 5)) color = new Color(37, 48, 59);
                        if (row == 4 && (col == 3 || col == 4)) color = shade;
                        if (row == 5 && (col == 3 || col == 4)) color = darken(skin, 0.67f);
                        if (row == 6 && (col == 1 || col == 6)) color = shade;
                        if ((row == 1 && col == 2) || (row == 1 && col == 5)) color = lighten(hair, 1.2f);
                        graphics.setColor(color);
                        graphics.fillRect(startX + col * pixel, startY + row * pixel, pixel, pixel);
                    }
                }
                graphics.setColor(new Color(255, 255, 255, 30));
                graphics.drawRect(startX, startY, pixel * 8 - 1, pixel * 8 - 1);
                graphics.setColor(Theme.ACCENT);
                graphics.fillOval(x + size - 17, y + size - 17, 9, 9);
                graphics.setColor(new Color(12, 24, 15));
                graphics.fillOval(x + size - 15, y + size - 15, 5, 5);
            } finally {
                graphics.dispose();
            }
        }
    }

    public static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = Theme.quality(image.createGraphics());
        try {
            graphics.setColor(new Color(13, 22, 31));
            graphics.fillRoundRect(0, 0, size, size, size / 4, size / 4);
            drawCube(graphics, size / 2, size / 8, size * 3 / 4,
                    new Color(121, 205, 76), new Color(105, 74, 46),
                    new Color(80, 54, 34), new Color(143, 101, 63));
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void drawCube(Graphics2D graphics, int centerX, int topY, int size,
                                 Color top, Color left, Color right, Color leftLight) {
        int dx = size / 2;
        int dy = size / 4;
        int height = (int) (size * 0.72f);
        int midY = topY + dy * 2;
        Polygon topFace = new Polygon(
                new int[]{centerX, centerX + dx, centerX, centerX - dx},
                new int[]{topY, topY + dy, topY + dy * 2, topY + dy}, 4);
        Polygon leftFace = new Polygon(
                new int[]{centerX - dx, centerX, centerX, centerX - dx},
                new int[]{topY + dy, topY + dy * 2, midY + height, topY + dy + height}, 4);
        Polygon rightFace = new Polygon(
                new int[]{centerX, centerX + dx, centerX + dx, centerX},
                new int[]{topY + dy * 2, topY + dy, topY + dy + height, midY + height}, 4);
        graphics.setColor(left);
        graphics.fillPolygon(leftFace);
        graphics.setColor(right);
        graphics.fillPolygon(rightFace);

        int grass = Math.max(5, height / 4);
        Polygon leftGrass = new Polygon(
                new int[]{centerX - dx, centerX, centerX, centerX - dx},
                new int[]{topY + dy, topY + dy * 2, topY + dy * 2 + grass, topY + dy + grass}, 4);
        Polygon rightGrass = new Polygon(
                new int[]{centerX, centerX + dx, centerX + dx, centerX},
                new int[]{topY + dy * 2, topY + dy, topY + dy + grass, topY + dy * 2 + grass}, 4);
        graphics.setColor(new Color(Math.max(0, top.getRed() - 14), Math.max(0, top.getGreen() - 14),
                Math.max(0, top.getBlue() - 12)));
        graphics.fillPolygon(leftGrass);
        graphics.fillPolygon(rightGrass);
        graphics.setColor(top);
        graphics.fillPolygon(topFace);

        graphics.setColor(new Color(17, 29, 20, 70));
        graphics.setStroke(new BasicStroke(Math.max(1f, size / 90f)));
        graphics.drawPolygon(topFace);
        graphics.drawLine(centerX, topY + dy * 2, centerX, midY + height);
        graphics.drawLine(centerX - dx, topY + dy, centerX - dx, topY + dy + height);
        graphics.drawLine(centerX + dx, topY + dy, centerX + dx, topY + dy + height);

        Random random = new Random(size * 31L + centerX);
        graphics.setColor(new Color(255, 255, 255, 28));
        for (int i = 0; i < 7; i++) {
            int px = centerX - dx / 2 + random.nextInt(Math.max(2, dx));
            int py = topY + dy + random.nextInt(Math.max(2, height - 2));
            graphics.fillRect(px, py, Math.max(1, size / 42), Math.max(1, size / 42));
        }
        graphics.setColor(new Color(leftLight.getRed(), leftLight.getGreen(), leftLight.getBlue(), 80));
        graphics.setStroke(new BasicStroke(Math.max(1f, size / 100f)));
        graphics.drawLine(centerX - dx + 2, topY + dy + grass + 3,
                centerX - 2, topY + dy * 2 + grass + 3);
    }

    private static Color darken(Color color, float factor) {
        return new Color(Math.min(255, Math.round(color.getRed() * factor)),
                Math.min(255, Math.round(color.getGreen() * factor)),
                Math.min(255, Math.round(color.getBlue() * factor)), color.getAlpha());
    }

    private static Color lighten(Color color, float factor) {
        return new Color(Math.min(255, Math.round(color.getRed() * factor)),
                Math.min(255, Math.round(color.getGreen() * factor)),
                Math.min(255, Math.round(color.getBlue() * factor)), color.getAlpha());
    }
}
