package io.github.ufml.launcher.ui;

import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class ToggleChip extends JToggleButton {
    public ToggleChip(String text) {
        super(text);
        setFont(Theme.regular(11.5f));
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new java.awt.Dimension(104, 30));
    }

    @Override
    protected void paintComponent(Graphics raw) {
        Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
        try {
            boolean selected = isSelected();
            int dotX = 12;
            graphics.setColor(selected ? Theme.ACCENT : Theme.GLASS_SURFACE_ALT);
            graphics.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            graphics.setColor(selected ? Theme.alpha(Theme.ACCENT, 70) : Theme.BORDER);
            graphics.setStroke(new BasicStroke(1f));
            graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            graphics.setColor(selected ? new java.awt.Color(14, 29, 13) : Theme.MUTED_DARK);
            graphics.fillOval(dotX, getHeight() / 2 - 3, 6, 6);
            setForeground(selected ? new java.awt.Color(14, 29, 13) : Theme.MUTED);
        } finally {
            graphics.dispose();
        }
        super.paintComponent(raw);
    }
}
