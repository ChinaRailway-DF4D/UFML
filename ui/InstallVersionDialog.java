package io.github.ufml.launcher.ui;

import io.github.ufml.launcher.minecraft.ModLoaderType;
import io.github.ufml.launcher.minecraft.VersionSummary;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;

final class InstallVersionDialog extends JDialog {
    enum Action { CANCEL, INSTALL }

    private ModernComboBox<ModLoaderType> loaderCombo;
    private JTextArea loaderDescription;
    private ToggleChip defaultToggle;
    private ToggleChip launchToggle;
    private ModLoaderType loader;
    private boolean setDefaultVersion;
    private boolean openLaunchPage;
    private Action action = Action.CANCEL;
    private Point dragOffset;

    InstallVersionDialog(JFrame owner, VersionSummary version, Path gameDirectory,
                         ModLoaderType initialLoader) {
        super(owner, "UFML · 安装游戏", true);
        loader = initialLoader == null ? ModLoaderType.VANILLA : initialLoader;
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(Theme.BG_TOP);
        setResizable(false);
        setSize(760, 700);
        setLocationRelativeTo(owner);
        try {
            setOpacity(0.94f);
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 28, 28));
        } catch (UnsupportedOperationException ignored) { }

        Surface.Background root = new Surface.Background(true);
        root.setLayout(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(22, 24, 20, 24));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("安装游戏 · " + version.id());
        title.setFont(Theme.bold(19f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel(version.typeLabel() + "  ·  " + dateOf(version.releaseTime())
                + "  ·  创建独立的本地实例");
        subtitle.setFont(Theme.regular(10.8f));
        subtitle.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);
        UiButton close = new UiButton("取消", UiButton.Kind.GHOST, 10);
        close.compactText(11f);
        close.setPreferredSize(new Dimension(64, 32));
        close.addActionListener(event -> dispose());
        header.add(close, BorderLayout.EAST);
        installWindowDrag(header);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.insets = new Insets(0, 0, 12, 0);

        body.add(buildVersionCard(version), constraints);
        constraints.gridy = 1;
        body.add(buildLoaderCard(), constraints);
        constraints.gridy = 2;
        body.add(buildOptionsCard(), constraints);
        constraints.gridy = 3;
        constraints.insets = new Insets(0, 0, 0, 0);
        body.add(buildLocationCard(gameDirectory), constraints);
        constraints.gridy = 4;
        constraints.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        body.add(filler, constraints);
        root.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        JLabel status = label("需要联网下载游戏、加载器与 Java；安装完成后文件保存在本地。",
                Theme.regular(10.2f), Theme.MUTED);
        footer.add(status, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        UiButton cancel = new UiButton("取消", UiButton.Kind.GHOST, 9);
        cancel.compactText(11f);
        cancel.setPreferredSize(new Dimension(72, 42));
        cancel.addActionListener(event -> dispose());
        UiButton install = new UiButton("开始安装", UiButton.Kind.ACCENT, 9);
        install.compactText(12.5f);
        install.setPreferredSize(new Dimension(112, 42));
        install.addActionListener(event -> confirm());
        actions.add(cancel);
        actions.add(install);
        footer.add(actions, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    boolean approved() { return action == Action.INSTALL; }
    ModLoaderType loader() { return loader; }
    boolean setDefaultVersion() { return setDefaultVersion; }
    boolean openLaunchPage() { return openLaunchPage; }

    private Surface.Card buildVersionCard(VersionSummary version) {
        Surface.Card card = card();
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel heading = transparent(new BorderLayout(12, 0));
        JPanel name = transparent();
        name.setLayout(new BoxLayout(name, BoxLayout.Y_AXIS));
        JLabel title = label("Minecraft " + version.id(), Theme.bold(17f), Theme.TEXT);
        JLabel detail = label("选择安装加载器和其他选项后，再开始下载", Theme.regular(10.5f), Theme.MUTED);
        name.add(title);
        name.add(Box.createVerticalStrut(3));
        name.add(detail);
        heading.add(name, BorderLayout.CENTER);
        Surface.Pill pending = new Surface.Pill("待安装", Theme.ACCENT,
                Theme.alpha(Theme.ACCENT, 14), Theme.ACCENT);
        heading.add(pending, BorderLayout.EAST);
        content.add(heading);
        content.add(Box.createVerticalStrut(12));

        JPanel facts = transparent(new FlowLayout(FlowLayout.LEFT, 28, 0));
        facts.add(fact("版本类型", version.typeLabel()));
        facts.add(fact("发布时间", dateOf(version.releaseTime())));
        facts.add(fact("下载来源", "Mojang 官方"));
        content.add(facts);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private Surface.Card buildLoaderCard() {
        Surface.Card card = card();
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(left(label("模组加载器", Theme.bold(14f), Theme.TEXT)));
        content.add(Box.createVerticalStrut(3));
        content.add(left(label("为这个实例选择原版或模组运行环境", Theme.regular(10.5f), Theme.MUTED)));
        content.add(Box.createVerticalStrut(10));

        JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        loaderCombo = new ModernComboBox<>();
        for (ModLoaderType type : ModLoaderType.values()) loaderCombo.addItem(type);
        loaderCombo.setSelectedItem(loader);
        loaderCombo.setPreferredSize(new Dimension(250, 42));
        loaderCombo.setMaximumSize(new Dimension(250, 42));
        loaderCombo.setToolTipText("选择原版、Fabric、Fabric + Fabric API 或 Forge");
        row.add(loaderCombo);
        content.add(row);
        content.add(Box.createVerticalStrut(8));

        loaderDescription = note(loader.description());
        loaderDescription.setPreferredSize(new Dimension(0, 36));
        loaderDescription.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        loaderDescription.setAlignmentX(LEFT_ALIGNMENT);
        content.add(left(loaderDescription));
        content.add(Box.createVerticalStrut(4));
        JLabel hint = label("Fabric API 会放入实例的 mods 文件夹；Forge 使用官方安装器。",
                Theme.regular(9.8f), Theme.MUTED_DARK);
        hint.setAlignmentX(LEFT_ALIGNMENT);
        content.add(left(hint));
        card.add(content, BorderLayout.CENTER);

        loaderCombo.addActionListener(event -> {
            Object selected = loaderCombo.getSelectedItem();
            if (selected instanceof ModLoaderType type) {
                loader = type;
                loaderDescription.setText(type.description());
            }
        });
        return pinHeight(card);
    }

    private Surface.Card buildOptionsCard() {
        Surface.Card card = card();
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(left(label("安装后设置", Theme.bold(14f), Theme.TEXT)));
        content.add(Box.createVerticalStrut(8));

        JPanel defaultRow = transparent(new FlowLayout(FlowLayout.LEFT, 9, 0));
        defaultToggle = new ToggleChip("设为启动版本");
        defaultToggle.setSelected(true);
        defaultToggle.setPreferredSize(new Dimension(132, 30));
        defaultRow.add(defaultToggle);
        defaultRow.add(label("安装成功后，把这个实例设为当前启动版本", Theme.regular(10.5f), Theme.MUTED));
        content.add(defaultRow);
        content.add(Box.createVerticalStrut(5));

        JPanel launchRow = transparent(new FlowLayout(FlowLayout.LEFT, 9, 0));
        launchToggle = new ToggleChip("完成后进启动页");
        launchToggle.setSelected(true);
        launchToggle.setPreferredSize(new Dimension(148, 30));
        launchRow.add(launchToggle);
        launchRow.add(label("安装完成后自动切换到启动游戏菜单", Theme.regular(10.5f), Theme.MUTED));
        content.add(launchRow);
        card.add(content, BorderLayout.CENTER);
        return pinHeight(card);
    }

    private Surface.Card buildLocationCard(Path gameDirectory) {
        Surface.Card card = card();
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(left(label("安装位置", Theme.bold(14f), Theme.TEXT)));
        content.add(Box.createVerticalStrut(6));
        JLabel path = label(gameDirectory.toString(), Theme.mono(10.5f), Theme.ACCENT);
        path.setToolTipText(gameDirectory.toString());
        content.add(left(path));
        content.add(Box.createVerticalStrut(5));
        JTextArea hint = note("客户端、运行库、资源和 mods 都保存在这个 Minecraft 目录；启动器配置仍在 Config 目录。");
        hint.setPreferredSize(new Dimension(0, 30));
        hint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        content.add(left(hint));
        card.add(content, BorderLayout.CENTER);
        return pinHeight(card);
    }

    private void confirm() {
        setDefaultVersion = defaultToggle.isSelected();
        openLaunchPage = launchToggle.isSelected();
        action = Action.INSTALL;
        dispose();
    }

    private static Surface.Card card() {
        Surface.Card card = new Surface.Card(new BorderLayout(), 15);
        card.setFill(Theme.GLASS_SURFACE_ALT);
        card.setOutline(Theme.BORDER_SOFT);
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        return card;
    }

    private static JPanel fact(String title, String value) {
        JPanel panel = transparent();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(label(title, Theme.regular(9.5f), Theme.MUTED_DARK));
        panel.add(Box.createVerticalStrut(2));
        panel.add(label(value, Theme.bold(11.5f), Theme.TEXT));
        return panel;
    }

    private static JTextArea note(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.regular(10.5f));
        area.setForeground(Theme.MUTED);
        area.setBorder(javax.swing.BorderFactory.createEmptyBorder());
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    private static JPanel transparent() {
        return transparent(null);
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel left(JComponent component) {
        JPanel panel = transparent(new BorderLayout());
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static Surface.Card pinHeight(Surface.Card card) {
        Dimension preferred = card.getPreferredSize();
        card.setMinimumSize(new Dimension(0, preferred.height));
        return card;
    }

    private void installWindowDrag(Component component) {
        if (component instanceof javax.swing.AbstractButton) return;
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return;
                Point screen = event.getLocationOnScreen();
                dragOffset = new Point(screen.x - getX(), screen.y - getY());
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (dragOffset == null || !javax.swing.SwingUtilities.isLeftMouseButton(event)) return;
                Point screen = event.getLocationOnScreen();
                setLocation(screen.x - dragOffset.x, screen.y - dragOffset.y);
            }

            @Override public void mouseReleased(MouseEvent event) {
                dragOffset = null;
            }
        };
        component.addMouseListener(dragHandler);
        component.addMouseMotionListener(dragHandler);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) installWindowDrag(child);
        }
    }

    private static JLabel label(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(LEFT_ALIGNMENT);
        return label;
    }

    private static String dateOf(String releaseTime) {
        return releaseTime == null || releaseTime.length() < 10 ? "未知日期" : releaseTime.substring(0, 10);
    }
}
