package io.github.ufml.launcher.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class SlimProgressBar extends JComponent {
    private double progress;
    private final Timer animation;
    private float phase;

    public SlimProgressBar() {
        setOpaque(false);
        animation = new Timer(32, event -> {
            phase += 0.018f;
            if (phase > 1f) phase -= 1f;
            repaint();
        });
    }

    public void setProgress(double progress) {
        this.progress = Math.max(-1, Math.min(1, progress));
        if (this.progress < 0 && isShowing()) {
            if (!animation.isRunning()) animation.start();
        } else {
            animation.stop();
        }
        repaint();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (progress < 0 && !animation.isRunning()) animation.start();
    }

    @Override
    public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }

    @Override
    public Dimension getPreferredSize() { return new Dimension(260, 6); }

    @Override
    protected void paintComponent(Graphics raw) {
        Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
        try {
            int width = getWidth();
            int height = getHeight();
            graphics.setColor(Theme.SURFACE_ALT);
            graphics.fillRoundRect(0, 0, width, height, height, height);
            if (progress < 0) {
                int segment = Math.max(52, width / 4);
                int span = width + segment;
                int x = (int) (-segment + span * phase);
                graphics.setColor(Theme.alpha(Theme.ACCENT, 180));
                graphics.fillRoundRect(x, 0, segment, height, height, height);
            } else if (progress > 0) {
                graphics.setColor(Theme.ACCENT);
                graphics.fillRoundRect(0, 0, Math.max(height, (int) (width * progress)), height,
                        height, height);
            }
        } finally {
            graphics.dispose();
        }
    }
}
