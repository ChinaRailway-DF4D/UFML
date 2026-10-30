package io.github.ufml.launcher.ui;

import io.github.ufml.launcher.VersionSettings;
import io.github.ufml.launcher.minecraft.VersionSummary;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Files;
import java.nio.file.Path;

final class VersionSettingsDialog extends JDialog {
    enum Action { NONE, SAVE, SET_DEFAULT, REPAIR, OPEN_FOLDER, DELETE }

    private final DarkTextField javaField;
    private final DarkTextField memoryField;
    private final DarkTextField widthField;
    private final DarkTextField heightField;
    private final DarkTextField jvmField;
    private final DarkTextField gameField;
    private final ToggleChip fullscreenToggle;
    private final JLabel status;
    private Action action = Action.NONE;
    private VersionSettings settings;
    private Point dragOffset;

    VersionSettingsDialog(JFrame owner, VersionSummary version, VersionSettings initial,
                          Runnable openFolder, Runnable deleteVersion) {
        this(owner, version, initial, 0, openFolder, deleteVersion);
    }

    VersionSettingsDialog(JFrame owner, VersionSummary version, VersionSettings initial,
                          int globalMemoryMb, Runnable openFolder, Runnable deleteVersion) {
        super(owner, "UFML · 版本设置", true);
        setUndecorated(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setBackground(Theme.BG_TOP);
        setSize(760, 610);
        setMinimumSize(new Dimension(720, 580));
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
        JLabel title = new JLabel("版本设置 · " + version.id());
        title.setFont(Theme.bold(19f));
        title.setForeground(Theme.TEXT);
        JLabel subtitle = new JLabel(version.typeLabel() + "  ·  " + dateOf(version.releaseTime())
                + "  ·  仅作用于这个本地版本");
        subtitle.setFont(Theme.regular(10.8f));
        subtitle.setForeground(Theme.MUTED);
        titles.add(title);
        titles.add(Box.createVerticalStrut(3));
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);
        UiButton close = new UiButton("关闭", UiButton.Kind.GHOST, 10);
        close.compactText(11f);
        close.setPreferredSize(new Dimension(64, 32));
        close.addActionListener(event -> dispose());
        header.add(close, BorderLayout.EAST);
        installWindowDrag(header);
        root.add(header, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 9);

        form.add(label("Java 运行时"), c);
        c.gridx = 1;
        c.weightx = 1;
        javaField = new DarkTextField(initial.javaPath());
        javaField.setPlaceholder("自动选择（跟随全局设置与版本要求）");
        javaField.setPreferredSize(new Dimension(0, 40));
        javaField.setToolTipText("留空时自动选择；也可以指定 java.exe 或 Java 主目录");
        form.add(javaField, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        JPanel javaActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        javaActions.setOpaque(false);
        UiButton automatic = new UiButton("自动", UiButton.Kind.GHOST, 9);
        automatic.compactText(10.5f);
        automatic.setPreferredSize(new Dimension(48, 40));
        automatic.addActionListener(event -> javaField.setText(""));
        UiButton browse = new UiButton("选择", UiButton.Kind.GHOST, 9);
        browse.compactText(10.5f);
        browse.setPreferredSize(new Dimension(52, 40));
        browse.addActionListener(event -> chooseJava());
        javaActions.add(automatic);
        javaActions.add(browse);
        form.add(javaActions, c);

        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(label("内存上限"), c);
        c.gridx = 1;
        c.weightx = 1;
        JPanel memoryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        memoryRow.setOpaque(false);
        memoryField = new DarkTextField(initial.memoryMb() <= 0 ? ""
                : String.valueOf(initial.memoryMb()));
        memoryField.setPlaceholder(globalMemoryMb > 0
                ? "跟随启动器（" + globalMemoryMb + " MB）" : "跟随启动器设置");
        memoryField.setHorizontalAlignment(DarkTextField.CENTER);
        memoryField.setPreferredSize(new Dimension(170, 40));
        memoryRow.add(memoryField);
        memoryRow.add(label("MB", Theme.regular(10.5f), Theme.MUTED_DARK));
        form.add(memoryRow, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(label("留空或 0 跟随全局", Theme.regular(9.8f), Theme.MUTED_DARK), c);

        c.gridy = 2;
        c.gridx = 0;
        c.weightx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(label("显示模式"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        JPanel displayRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
        displayRow.setOpaque(false);
        fullscreenToggle = new ToggleChip("全屏启动");
        fullscreenToggle.setSelected(initial.fullscreen());
        fullscreenToggle.setPreferredSize(new Dimension(112, 30));
        displayRow.add(fullscreenToggle);
        displayRow.add(label("全屏时仍会保存窗口尺寸，退出全屏后继续使用", Theme.regular(10.2f), Theme.MUTED));
        form.add(displayRow, c);
        c.gridwidth = 1;

        c.gridy = 3;
        c.gridx = 0;
        c.weightx = 0;
        form.add(label("游戏窗口"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        sizeRow.setOpaque(false);
        widthField = new DarkTextField(String.valueOf(initial.windowWidth()));
        widthField.setHorizontalAlignment(DarkTextField.CENTER);
        widthField.setPreferredSize(new Dimension(105, 40));
        heightField = new DarkTextField(String.valueOf(initial.windowHeight()));
        heightField.setHorizontalAlignment(DarkTextField.CENTER);
        heightField.setPreferredSize(new Dimension(105, 40));
        sizeRow.add(widthField);
        sizeRow.add(label("×", Theme.regular(13f), Theme.MUTED));
        sizeRow.add(heightField);
        sizeRow.add(label("像素", Theme.regular(10.5f), Theme.MUTED_DARK));
        form.add(sizeRow, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        form.add(label(VersionSettings.MIN_SIZE + "–" + VersionSettings.MAX_SIZE,
                Theme.regular(9.8f), Theme.MUTED_DARK), c);

        c.gridy = 4;
        c.gridx = 1;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 12, 0);
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        presets.setOpaque(false);
        presets.add(label("常用大小", Theme.regular(10.5f), Theme.MUTED_DARK));
        preset(presets, "854 × 480", 854, 480);
        preset(presets, "1280 × 720", 1280, 720);
        preset(presets, "1920 × 1080", 1920, 1080);
        form.add(presets, c);
        c.gridwidth = 1;

        c.gridy = 5;
        c.gridx = 0;
        c.insets = new Insets(0, 0, 10, 9);
        form.add(label("JVM 参数"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        jvmField = new DarkTextField(initial.jvmArguments());
        jvmField.setPlaceholder("可选，例如 -XX:+UseG1GC -Dfile.encoding=UTF-8");
        jvmField.setPreferredSize(new Dimension(0, 40));
        jvmField.setToolTipText("按空格分词，支持引号和 ${game_directory} 等占位符");
        form.add(jvmField, c);
        c.gridwidth = 1;

        c.gridy = 6;
        c.gridx = 0;
        c.insets = new Insets(0, 0, 0, 9);
        form.add(label("游戏参数"), c);
        c.gridx = 1;
        c.gridwidth = 2;
        c.weightx = 1;
        gameField = new DarkTextField(initial.gameArguments());
        gameField.setPlaceholder("可选，例如 --fullscreen 或额外启动参数");
        gameField.setPreferredSize(new Dimension(0, 40));
        gameField.setToolTipText("按空格分词，支持引号和 ${game_directory} 占位符");
        form.add(gameField, c);

        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        status = label("设置保存在 Config\\version-settings.json", Theme.regular(10.2f), Theme.MUTED);
        footer.add(status, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        UiButton delete = new UiButton("删除版本", UiButton.Kind.GHOST, 9);
        delete.compactText(10.5f);
        delete.setPreferredSize(new Dimension(78, 40));
        delete.addActionListener(event -> confirmDelete(version, deleteVersion));
        actions.add(delete);
        UiButton repair = new UiButton("补全文件", UiButton.Kind.GHOST, 9);
        repair.compactText(10.8f);
        repair.setPreferredSize(new Dimension(84, 40));
        repair.setToolTipText("只下载缺失或损坏的游戏文件并重新校验本地实例");
        repair.addActionListener(event -> finish(Action.REPAIR));
        actions.add(repair);
        UiButton open = new UiButton("打开目录", UiButton.Kind.GHOST, 9);
        open.compactText(10.5f);
        open.setPreferredSize(new Dimension(76, 40));
        open.addActionListener(event -> {
            action = Action.OPEN_FOLDER;
            dispose();
        });
        actions.add(open);
        UiButton makeDefault = new UiButton("设为启动版本", UiButton.Kind.GHOST, 9);
        makeDefault.compactText(10.5f);
        makeDefault.setPreferredSize(new Dimension(102, 40));
        makeDefault.addActionListener(event -> finish(Action.SET_DEFAULT));
        actions.add(makeDefault);
        UiButton save = new UiButton("保存设置", UiButton.Kind.ACCENT, 9);
        save.compactText(11.2f);
        save.setPreferredSize(new Dimension(90, 40));
        save.addActionListener(event -> finish(Action.SAVE));
        actions.add(save);
        footer.add(actions, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(save);
        setContentPane(root);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                javaField.requestFocusInWindow();
            }
        });
    }

    Action action() { return action; }
    VersionSettings settings() { return settings; }

    private void preset(JPanel row, String text, int width, int height) {
        UiButton button = new UiButton(text, UiButton.Kind.QUIET, 8);
        button.compactText(10.2f);
        button.setPreferredSize(new Dimension(88, 28));
        button.addActionListener(event -> {
            widthField.setText(String.valueOf(width));
            heightField.setText(String.valueOf(height));
        });
        row.add(button);
    }

    private void finish(Action selectedAction) {
        int width;
        int height;
        try {
            width = Integer.parseInt(widthField.getText().trim());
            height = Integer.parseInt(heightField.getText().trim());
        } catch (NumberFormatException ex) {
            showError("窗口宽度和高度必须是整数");
            return;
        }
        if (width < VersionSettings.MIN_SIZE || width > VersionSettings.MAX_SIZE
                || height < VersionSettings.MIN_SIZE || height > VersionSettings.MAX_SIZE) {
            showError("窗口大小必须在 " + VersionSettings.MIN_SIZE + "–"
                    + VersionSettings.MAX_SIZE + " 像素之间");
            return;
        }
        int memory;
        String memoryText = memoryField.getText().trim();
        if (memoryText.isEmpty()) {
            memory = 0;
        } else {
            try {
                memory = Integer.parseInt(memoryText);
            } catch (NumberFormatException ex) {
                showError("内存上限必须是整数");
                return;
            }
            if (memory != 0 && (memory < VersionSettings.MIN_MEMORY
                    || memory > VersionSettings.MAX_MEMORY)) {
                showError("内存上限必须在 " + VersionSettings.MIN_MEMORY + "–"
                        + VersionSettings.MAX_MEMORY + " MB 之间，或留空跟随全局");
                return;
            }
        }
        String javaPath = javaField.getText().trim().replace("\"", "");
        if (!javaPath.isEmpty()) {
            try {
                if (!Files.exists(Path.of(javaPath))) {
                    showError("指定的 Java 路径不存在");
                    return;
                }
            } catch (Exception ex) {
                showError("Java 路径格式无效");
                return;
            }
        }
        settings = new VersionSettings(javaPath, memory, fullscreenToggle.isSelected(),
                width, height, jvmField.getText(), gameField.getText());
        action = selectedAction;
        dispose();
    }

    private void chooseJava() {
        Path start = Path.of(System.getProperty("user.home"));
        if (!javaField.getText().isBlank()) {
            try {
                Path current = Path.of(javaField.getText().trim().replace("\"", ""));
                Path parent = current.getParent();
                if (parent != null && Files.isDirectory(parent)) start = parent;
                else if (Files.isDirectory(current)) start = current;
            } catch (Exception ignored) { }
        }
        JFileChooser chooser = new JFileChooser(start.toFile());
        chooser.setDialogTitle("选择这个版本使用的 Java");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            chooser.setFileFilter(new FileNameExtensionFilter("Java 可执行文件 (*.exe)", "exe"));
        }
        if (chooser.showDialog(this, "选择") == JFileChooser.APPROVE_OPTION) {
            javaField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void confirmDelete(VersionSummary version, Runnable deleteVersion) {
        int answer = JOptionPane.showConfirmDialog(this,
                "确定删除本地版本 Minecraft " + version.id() + " 吗？\n\n"
                        + "会删除该版本的目录和独立设置，公共运行库与资源会保留。\n"
                        + "删除后如需使用，请到“下载游戏”菜单重新下载。",
                "UFML · 删除本地版本", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        action = Action.DELETE;
        deleteVersion.run();
        dispose();
    }

    private void showError(String message) {
        status.setText(message);
        status.setForeground(Theme.ERROR);
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
        return label;
    }

    private static JLabel label(String text) {
        return label(text, Theme.regular(11.5f), Theme.MUTED);
    }

    private static String dateOf(String releaseTime) {
        return releaseTime == null || releaseTime.length() < 10 ? "未知日期" : releaseTime.substring(0, 10);
    }
}
