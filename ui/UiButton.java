package io.github.ufml.launcher.ui;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class UiButton extends JButton {
    public enum Kind { ACCENT, GHOST, QUIET }

    private final Kind kind;
    private final int radius;
    private boolean playIcon;
    private boolean hovering;
    private boolean active;

    public UiButton(String text, Kind kind) { this(text, kind, 13); }

    public UiButton(String text, Kind kind, int radius) {
        super(text);
        this.kind = kind;
        this.radius = radius;
        setFont(Theme.bold(13f));
        setBorderPainted(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setOpaque(false);
        setMargin(new Insets(0, 3, 0, 3));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setRolloverEnabled(true);
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovering = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hovering = false; repaint(); }
        });
    }

    public UiButton playIcon() {
        this.playIcon = true;
        setFont(Theme.bold(15f));
        return this;
    }

    public void compactText(float size) {
        setFont(kind == Kind.ACCENT ? Theme.bold(size) : Theme.regular(size));
    }

    public UiButton active(boolean active) {
        this.active = active;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics raw) {
        Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
        try {
            boolean pressed = getModel().isPressed();
            boolean enabled = isEnabled();
            Color fill;
            Color border;
            Color foreground;
            if (kind == Kind.ACCENT) {
                fill = enabled ? (pressed ? Theme.ACCENT : hovering ? Theme.ACCENT_HOVER : Theme.ACCENT)
                        : new Color(55, 71, 54);
                border = enabled ? Theme.alpha(Color.WHITE, 34) : Theme.BORDER;
                foreground = enabled ? new Color(12, 23, 12) : Theme.MUTED_DARK;
                if (enabled && hovering) {
                    graphics.setColor(Theme.ACCENT_GLOW);
                    graphics.fillRoundRect(-2, 2, getWidth() + 4, getHeight() + 2, radius + 4, radius + 4);
                }
            } else if (kind == Kind.GHOST) {
                if (active && enabled) {
                    fill = Theme.alpha(Theme.ACCENT, 20);
                    border = Theme.alpha(Theme.ACCENT, 110);
                    foreground = Theme.ACCENT;
                } else {
                    fill = hovering && enabled ? Theme.SURFACE_HOVER : Theme.SURFACE_ALT;
                    border = hovering && enabled ? Theme.alpha(Theme.ACCENT, 80) : Theme.BORDER;
                    foreground = enabled ? Theme.TEXT : Theme.MUTED_DARK;
                }
            } else {
                fill = hovering && enabled ? Theme.SURFACE_HOVER : new Color(0, 0, 0, 0);
                border = new Color(0, 0, 0, 0);
                foreground = enabled ? Theme.MUTED : Theme.MUTED_DARK;
            }
            graphics.setColor(fill);
            graphics.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            graphics.setColor(border);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (kind == Kind.ACCENT && enabled) {
                graphics.setColor(new Color(255, 255, 255, 35));
                graphics.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, radius - 1, radius - 1);
            }
            setForeground(foreground);
        } finally {
            graphics.dispose();
        }
        super.paintComponent(raw);
        if (playIcon) {
            Graphics2D iconGraphics = Theme.quality((Graphics2D) raw.create());
            try {
                FontMetrics metrics = iconGraphics.getFontMetrics(getFont());
                int textWidth = metrics.stringWidth(getText());
                int start = getWidth() / 2 - textWidth / 2;
                int x = start - 23;
                int y = getHeight() / 2 - 5;
                int[] xs = {x, x, x + 9};
                int[] ys = {y, y + 11, y + 5};
                iconGraphics.setColor(getForeground());
                iconGraphics.fillPolygon(xs, ys, 3);
            } finally {
                iconGraphics.dispose();
            }
        }
    }
}
