package io.github.ufml.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.geom.RoundRectangle2D;

public final class ModernComboBox<E> extends JComboBox<E> {
    public ModernComboBox() {
        setOpaque(false);
        setFont(Theme.regular(13.5f));
        setForeground(Theme.TEXT);
        setBackground(Theme.SURFACE_ALT);
        setBorder(BorderFactory.createEmptyBorder());
        setFocusable(false);
        setMaximumRowCount(15);
        setRenderer(renderer());
        setUI(new BasicComboBoxUI() {
            @Override
            protected JButton createArrowButton() {
                Arrow arrow = new Arrow();
                arrow.setBorder(new EmptyBorder(0, 0, 0, 14));
                return arrow;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds, boolean hasFocus) {
                // The rounded container is painted by the combo itself.
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, 44);
    }

    @Override
    protected void paintComponent(Graphics raw) {
        Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
        try {
            RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f,
                    getWidth() - 1f, getHeight() - 1f, 12, 12);
            graphics.setColor(Theme.GLASS_SURFACE_ALT);
            graphics.fill(shape);
            graphics.setColor(isEnabled() ? Theme.BORDER : Theme.BORDER_SOFT);
            graphics.setStroke(new BasicStroke(1f));
            graphics.draw(shape);
        } finally {
            graphics.dispose();
        }
        super.paintComponent(raw);
    }

    private static ListCellRenderer<Object> renderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                setFont(Theme.regular(13f));
                setBorder(new EmptyBorder(0, 13, 0, 8));
                if (index < 0) {
                    setOpaque(false);
                    setBackground(new Color(0, 0, 0, 0));
                    setForeground(isEnabled() ? Theme.TEXT : Theme.MUTED_DARK);
                } else if (selected) {
                    setOpaque(true);
                    setBackground(Theme.ACCENT_DARK);
                    setForeground(Theme.TEXT);
                } else {
                    setOpaque(true);
                    setBackground(Theme.SURFACE_ALT);
                    setForeground(Theme.TEXT);
                }
                return this;
            }
        };
    }

    private static final class Arrow extends JButton {
        private Arrow() {
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() { return new Dimension(38, 38); }

        @Override
        public Insets getInsets() { return new Insets(0, 0, 0, 12); }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                int x = getWidth() - 23;
                int y = getHeight() / 2 - 2;
                graphics.setColor(isEnabled() ? Theme.MUTED : Theme.MUTED_DARK);
                graphics.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.drawLine(x, y, x + 5, y + 5);
                graphics.drawLine(x + 5, y + 5, x + 10, y);
            } finally {
                graphics.dispose();
            }
        }
    }
}
