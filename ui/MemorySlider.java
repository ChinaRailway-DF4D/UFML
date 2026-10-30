package io.github.ufml.launcher.ui;

import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class MemorySlider extends JSlider {
    public MemorySlider() {
        super(1024, 32768, 4096);
        setOpaque(false);
        setFocusable(false);
        setPaintTicks(false);
        setPaintLabels(false);
        setUI(new BasicSliderUI(this) {
            @Override
            public void paintTrack(Graphics raw) {
                Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
                try {
                    int y = slider.getHeight() / 2 - 3;
                    int start = 6;
                    int end = slider.getWidth() - 6;
                    graphics.setColor(Theme.SURFACE_ALT);
                    graphics.fillRoundRect(start, y, end - start, 6, 6, 6);
                    int valueX = xPositionForValue(slider.getValue());
                    graphics.setColor(Theme.ACCENT);
                    graphics.fillRoundRect(start, y, Math.max(6, valueX - start), 6, 6, 6);
                } finally {
                    graphics.dispose();
                }
            }

            @Override
            public void paintThumb(Graphics raw) {
                Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
                try {
                    int x = xPositionForValue(slider.getValue());
                    int y = slider.getHeight() / 2;
                    graphics.setColor(Theme.alpha(Theme.ACCENT, 38));
                    graphics.fillOval(x - 12, y - 12, 24, 24);
                    graphics.setColor(Theme.ACCENT);
                    graphics.fillOval(x - 7, y - 7, 14, 14);
                    graphics.setColor(new java.awt.Color(16, 31, 15));
                    graphics.fillOval(x - 3, y - 3, 6, 6);
                } finally {
                    graphics.dispose();
                }
            }

            @Override
            public void paintFocus(Graphics g) { }

            @Override
            protected void calculateThumbSize() {
                super.calculateThumbSize();
                thumbRect.setSize(22, 22);
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(220, 30);
    }
}
