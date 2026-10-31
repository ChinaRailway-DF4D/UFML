package io.github.ufml.launcher.ui;

import io.github.ufml.launcher.minecraft.VersionCategory;
import io.github.ufml.launcher.minecraft.VersionSummary;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class VersionMenuPanel extends JPanel {
    public interface Listener {
        void versionSelected(VersionSummary version);
    }

    public interface SettingsListener {
        void settingsRequested(VersionSummary version);
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Listener listener;
    private final SettingsListener settingsListener;
    private final DefaultListModel<VersionSummary> model = new DefaultListModel<>();
    private final JList<VersionSummary> list = new JList<>(model);
    private final JLabel countLabel = new JLabel();
    private final Map<VersionCategory, ToggleChip> filters = new EnumMap<>(VersionCategory.class);
    private List<VersionSummary> allVersions = List.of();
    private VersionCategory category = VersionCategory.ALL;
    private boolean updating;

    public VersionMenuPanel(String title, String subtitle, Listener listener) {
        this(title, subtitle, listener, null);
    }

    public VersionMenuPanel(String title, String subtitle, Listener listener,
                            SettingsListener settingsListener) {
        this.listener = listener;
        this.settingsListener = settingsListener;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.bold(16f));
        heading.setForeground(Theme.TEXT);
        JLabel hint = new JLabel(subtitle);
        hint.setFont(Theme.regular(10.5f));
        hint.setForeground(Theme.MUTED_DARK);
        titles.add(heading);
        titles.add(Box.createVerticalStrut(2));
        titles.add(hint);
        header.add(titles, BorderLayout.WEST);
        countLabel.setFont(Theme.mono(10.5f));
        countLabel.setForeground(Theme.MUTED);
        header.add(countLabel, BorderLayout.EAST);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        header.setAlignmentX(LEFT_ALIGNMENT);
        add(header);
        add(Box.createVerticalStrut(12));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        filterRow.setOpaque(false);
        JLabel filterTitle = new JLabel("查看版本");
        filterTitle.setFont(Theme.regular(10.8f));
        filterTitle.setForeground(Theme.MUTED);
        filterRow.add(filterTitle);
        filterRow.add(Box.createHorizontalStrut(2));
        ButtonGroup group = new ButtonGroup();
        for (VersionCategory option : VersionCategory.values()) {
            ToggleChip chip = new ToggleChip(option.label());
            chip.setPreferredSize(new Dimension(option == VersionCategory.ALL ? 66 : 88, 30));
            chip.setFont(Theme.regular(10.8f));
            chip.setSelected(option == category);
            chip.addActionListener(event -> setCategory(option));
            group.add(chip);
            filters.put(option, chip);
            filterRow.add(chip);
        }
        filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        filterRow.setAlignmentX(LEFT_ALIGNMENT);
        add(filterRow);
        add(Box.createVerticalStrut(10));

        list.setOpaque(false);
        list.setBackground(new Color(0, 0, 0, 0));
        list.setForeground(Theme.TEXT);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(58);
        list.setCellRenderer(new VersionRenderer(settingsListener != null));
        list.setBorder(new EmptyBorder(3, 2, 3, 2));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updating && listener != null) {
                listener.versionSelected(selected());
            }
        });
        if (settingsListener != null) {
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    if (event.getButton() != MouseEvent.BUTTON1 || !list.isEnabled()) return;
                    int index = settingsIndexAt(event.getPoint());
                    if (index < 0) return;
                    list.setSelectedIndex(index);
                    settingsListener.settingsRequested(model.getElementAt(index));
                }
            });
            list.addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent event) {
                    list.setCursor(Cursor.getPredefinedCursor(settingsIndexAt(event.getPoint()) >= 0
                            ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        JScrollPane scroll = new JScrollPane(list,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        add(scroll);
        updateCount();
    }

    public void setVersions(List<VersionSummary> versions) {
        String wanted = selectedId();
        allVersions = versions == null ? List.of() : List.copyOf(versions);
        applyFilter(wanted);
    }

    public void setCategory(VersionCategory category) {
        if (category == null) return;
        this.category = category;
        ToggleChip chip = filters.get(category);
        if (chip != null && !chip.isSelected()) chip.setSelected(true);
        applyFilter(selectedId());
    }

    public VersionCategory category() { return category; }

    public VersionSummary selected() {
        Object selected = list.getSelectedValue();
        return selected instanceof VersionSummary summary ? summary : null;
    }

    public int count() { return model.size(); }

    public void setMenuEnabled(boolean enabled) {
        list.setEnabled(enabled);
        for (ToggleChip chip : filters.values()) chip.setEnabled(enabled);
    }

    public String selectedId() {
        VersionSummary summary = selected();
        return summary == null ? null : summary.id();
    }

    public void selectById(String id) {
        if (id == null) return;
        for (int index = 0; index < model.size(); index++) {
            if (id.equals(model.get(index).id())) {
                list.setSelectedIndex(index);
                list.ensureIndexIsVisible(index);
                return;
            }
        }
    }

    private void applyFilter(String wanted) {
        List<VersionSummary> visible = new ArrayList<>();
        for (VersionSummary version : allVersions) {
            if (category.matches(version)) visible.add(version);
        }
        updating = true;
        model.clear();
        for (VersionSummary version : visible) model.addElement(version);
        int selection = -1;
        if (wanted != null) {
            for (int index = 0; index < visible.size(); index++) {
                if (wanted.equals(visible.get(index).id())) { selection = index; break; }
            }
        }
        if (selection < 0 && !visible.isEmpty()) selection = 0;
        if (selection >= 0) list.setSelectedIndex(selection);
        else list.clearSelection();
        updating = false;
        updateCount();
        if (listener != null) listener.versionSelected(selected());
    }

    private void updateCount() {
        countLabel.setText(model.size() + " 个版本");
    }

    private int settingsIndexAt(java.awt.Point point) {
        int index = list.locationToIndex(point);
        if (index < 0 || index >= model.size()) return -1;
        Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(point)) return -1;
        return point.x >= bounds.x + bounds.width - 86 ? index : -1;
    }

    private static Color typeColor(VersionCategory category) {
        return switch (category) {
            case ALL -> Theme.MUTED;
            case RELEASE -> Theme.ACCENT;
            case SNAPSHOT -> Theme.INFO;
            case LEGACY -> Theme.WARNING;
            case APRIL_FOOLS -> new Color(255, 135, 205);
        };
    }

    private static String dateOf(String releaseTime) {
        try {
            return OffsetDateTime.parse(releaseTime).format(DATE);
        } catch (Exception ex) {
            return releaseTime == null || releaseTime.length() < 10 ? "未知日期" : releaseTime.substring(0, 10);
        }
    }

    private static final class VersionRenderer extends JPanel
            implements ListCellRenderer<VersionSummary> {
        private final boolean settingsAction;
        private final JLabel name = new JLabel();
        private final JLabel meta = new JLabel();
        private final JLabel status = new JLabel();
        private boolean selected;

        private VersionRenderer(boolean settingsAction) {
            this.settingsAction = settingsAction;
            setOpaque(false);
            setLayout(new BorderLayout(14, 0));
            setBorder(new EmptyBorder(8, 14, 8, 14));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            name.setFont(Theme.bold(13f));
            name.setForeground(Theme.TEXT);
            meta.setFont(Theme.regular(10.2f));
            meta.setForeground(Theme.MUTED);
            text.add(name);
            text.add(Box.createVerticalStrut(3));
            text.add(meta);
            add(text, BorderLayout.CENTER);
            status.setFont(Theme.regular(10.2f));
            status.setForeground(Theme.MUTED_DARK);
            add(status, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends VersionSummary> list,
                                                      VersionSummary value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            selected = isSelected;
            name.setText(value.id());
            meta.setText(value.typeLabel() + "   ·   " + dateOf(value.releaseTime()));
            meta.setForeground(typeColor(value.category()));
            boolean settings = settingsAction && value.local();
            status.setText(settings ? "设置  ›" : value.local() ? "已安装" : "可下载");
            status.setForeground(settings ? Theme.ACCENT : value.local() ? Theme.MUTED : Theme.MUTED_DARK);
            return this;
        }

        @Override
        protected void paintComponent(Graphics raw) {
            Graphics2D graphics = Theme.quality((Graphics2D) raw.create());
            try {
                RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f,
                        getWidth() - 1f, getHeight() - 5f, 12, 12);
                graphics.setColor(selected ? Theme.alpha(Theme.ACCENT, 25)
                        : new Color(9, 16, 24, 92));
                graphics.fill(shape);
                graphics.setColor(selected ? Theme.alpha(Theme.ACCENT, 115)
                        : new Color(139, 176, 202, 24));
                graphics.setStroke(new BasicStroke(1f));
                graphics.draw(shape);
            } finally {
                graphics.dispose();
            }
            super.paintComponent(raw);
        }
    }
}
