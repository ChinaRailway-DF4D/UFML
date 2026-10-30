package io.github.ufml.launcher.ui;

import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

public final class DarkTextField extends JTextField {
    private String placeholder = "";
    private boolean error;
    private boolean focused;

    public DarkTextField(String text) {
        super(text);
        setOpaque(false);
        setFont(Theme.regular(13.5f));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.ACCENT);
        setSelectionColor(Theme.ACCENT_DARK);
        setSelectedTextColor(Theme.TEXT);
        setBorder(new EmptyBorder(0, 13, 0, 13));
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
            @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
        });
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        repaint();
    }

    public void setError(boolean error) {
        this.error = error;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics raw) {
        Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
        try {
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f,
                    getWidth() - 1f, getHeight() - 1f, 12, 12);
            graphics.setColor(Theme.GLASS_SURFACE_ALT);
            graphics.fill(shape);
            graphics.setStroke(new BasicStroke(focused ? 1.4f : 1f));
            graphics.setColor(error ? Theme.ERROR : focused ? Theme.alpha(Theme.ACCENT, 130) : Theme.BORDER);
            graphics.draw(shape);
        } finally {
            graphics.dispose();
        }
        super.paintComponent(raw);
        if (getText().isEmpty() && !placeholder.isEmpty()) {
            Graphics2D placeholderGraphics = Theme.quality((Graphics2D) raw.create());
            try {
                placeholderGraphics.setFont(getFont());
                placeholderGraphics.setColor(Theme.MUTED_DARK);
                int y = (getHeight() - placeholderGraphics.getFontMetrics().getHeight()) / 2
                        + placeholderGraphics.getFontMetrics().getAscent();
                placeholderGraphics.drawString(placeholder, getInsets().left, y);
            } finally {
                placeholderGraphics.dispose();
            }
        }
    }
}
