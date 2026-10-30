package io.github.ufml.launcher.ui;

import io.github.ufml.launcher.ConfigStore;
import io.github.ufml.launcher.LauncherConfig;
import io.github.ufml.launcher.VersionSettings;
import io.github.ufml.launcher.VersionSettingsStore;
import io.github.ufml.launcher.minecraft.Downloader;
import io.github.ufml.launcher.minecraft.GameLauncher;
import io.github.ufml.launcher.minecraft.GameVersion;
import io.github.ufml.launcher.minecraft.InstalledVersion;
import io.github.ufml.launcher.minecraft.JavaRuntimeManager;
import io.github.ufml.launcher.minecraft.MinecraftInstaller;
import io.github.ufml.launcher.minecraft.ModLoaderService;
import io.github.ufml.launcher.minecraft.ModLoaderType;
import io.github.ufml.launcher.minecraft.MojangService;
import io.github.ufml.launcher.minecraft.ProgressListener;
import io.github.ufml.launcher.minecraft.VersionManifest;
import io.github.ufml.launcher.minecraft.VersionCategory;
import io.github.ufml.launcher.minecraft.VersionSummary;
import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;
import io.github.ufml.launcher.util.PathSafety;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class LauncherFrame extends JFrame {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LauncherConfig config;
    private final ConfigStore configStore = new ConfigStore();
    private final VersionSettingsStore versionSettingsStore = new VersionSettingsStore();
    private final Downloader downloader = new Downloader();
    private final MojangService mojang = new MojangService(downloader);
    private final MinecraftInstaller installer = new MinecraftInstaller(downloader);
    private final ModLoaderService modLoaders = new ModLoaderService(downloader, installer);
    private final JavaRuntimeManager javaManager = new JavaRuntimeManager(mojang, downloader);
    private final GameLauncher gameLauncher = new GameLauncher();
    private final ExecutorService worker = Executors.newFixedThreadPool(3, runnable -> {
        Thread thread = new Thread(runnable, "ufml-worker");
        thread.setDaemon(true);
        return thread;
    });

    private Artwork.AvatarPanel avatar;
    private DarkTextField usernameField;
    private JLabel uuidLabel;
    private ModernComboBox<VersionSummary> versionCombo;
    private JLabel versionDetail;
    private DarkTextField gameDirectoryField;
    private DarkTextField javaField;
    private MemorySlider memorySlider;
    private JLabel memoryValue;
    private ToggleChip snapshotToggle;
    private UiButton playButton;
    private UiButton refreshButton;
    private UiButton browseJavaButton;
    private UiButton autoJavaButton;
    private UiButton openFolderButton;
    private UiButton logButton;
    private UiButton navLaunchButton;
    private UiButton navDownloadButton;
    private UiButton navManageButton;
    private JLabel statusTitle;
    private JLabel statusDetail;
    private SlimProgressBar progressBar;
    private JDialog logDialog;
    private JTextArea logArea;

    private CardLayout pageLayout;
    private JPanel pageHost;
    private VersionMenuPanel downloadMenu;
    private JLabel downloadStatusTitle;
    private JLabel downloadStatusDetail;
    private SlimProgressBar downloadProgressBar;
    private JLabel downloadPathLabel;
    private UiButton downloadButton;
    private UiButton downloadRefreshButton;
    private ModLoaderType lastLoaderChoice = ModLoaderType.VANILLA;

    private VersionMenuPanel manageMenu;
    private JLabel manageStatusTitle;
    private JLabel manageStatusDetail;
    private JLabel managePathLabel;
    private UiButton manageRefreshButton;

    private VersionManifest manifest;
    private List<VersionSummary> localVersions = List.of();
    private boolean populatingVersions;
    private boolean downloadsLoading;
    private boolean installing;
    private boolean repairing;
    private boolean showingDownloadPage;
    private boolean launching;
    private boolean gameRunning;
    private int detailGeneration;
    private Point windowDragOffset;

    public LauncherFrame() {
        super("UFML · Minecraft Java 启动器");
        this.config = configStore.load();
        setUndecorated(true);
        setResizable(true);
        setBackground(Theme.BG_TOP);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1040, 720));
        setSize(1160, 760);
        try {
            setOpacity(0.94f);
            updateWindowShape();
        } catch (UnsupportedOperationException ex) {
            LauncherLog.warn("当前系统不支持透明窗口，使用普通窗口");
        }
        setLocationRelativeTo(null);
        setIconImages(List.of(Artwork.appIcon(32), Artwork.appIcon(64), Artwork.appIcon(128)));
        setContentPane(buildRoot());
        setGlassPane(new WindowResizeGlassPane(this));
        getGlassPane().setVisible(true);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                updateWindowShape();
            }
        });
        showLaunchPage();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { closeLauncher(); }
        });
        LauncherLog.addListener(line -> SwingUtilities.invokeLater(() -> {
            if (logArea == null) return;
            logArea.append(line);
            if (logArea.getText().length() > 300_000) {
                logArea.setText(LauncherLog.text());
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));
        refreshAvatar();
        scanLocalVersions();
        populateLaunchVersions();
    }

    private void updateWindowShape() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        try {
            setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 32, 32));
        } catch (UnsupportedOperationException ignored) { }
    }

    private JPanel buildRoot() {
        Surface.Background root = new Surface.Background(true);
        root.setLayout(new BorderLayout(0, 16));
        root.setBorder(new EmptyBorder(21, 26, 22, 26));
        root.add(buildHeader(), BorderLayout.NORTH);

        pageLayout = new CardLayout();
        pageHost = transparent(pageLayout);
        pageHost.add(buildLaunchPage(), "launch");
        pageHost.add(buildDownloadPage(), "download");
        pageHost.add(buildManagementPage(), "manage");
        root.add(pageHost, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildLaunchPage() {
        JPanel main = transparent(new BorderLayout(18, 0));
        main.add(buildAccountCard(), BorderLayout.WEST);

        JPanel center = transparent(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 15, 0);
        JPanel hero = buildHeroCard();
        hero.setPreferredSize(new Dimension(0, 224));
        center.add(hero, constraints);

        constraints.gridy = 1;
        constraints.weighty = 1;
        constraints.insets = new Insets(0, 0, 15, 0);
        center.add(buildSettingsCard(), constraints);

        constraints.gridy = 2;
        constraints.weighty = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        JPanel action = buildLaunchCard();
        action.setPreferredSize(new Dimension(0, 102));
        center.add(action, constraints);
        main.add(center, BorderLayout.CENTER);
        return main;
    }

    private JPanel buildDownloadPage() {
        JPanel page = transparent(new BorderLayout(18, 0));
        page.add(buildDownloadIntroCard(), BorderLayout.WEST);

        JPanel center = transparent(new BorderLayout(0, 14));
        center.add(buildDownloadMenuCard(), BorderLayout.CENTER);
        JPanel action = buildDownloadActionCard();
        action.setPreferredSize(new Dimension(0, 154));
        center.add(action, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildDownloadMenuCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(), 22);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        downloadMenu = new VersionMenuPanel("可下载版本",
                "只显示尚未安装的官方版本；按类型查看完整版本菜单",
                version -> onDownloadVersionSelected(version));
        card.add(downloadMenu, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildManagementPage() {
        JPanel page = transparent(new BorderLayout(18, 0));
        page.add(buildManagementIntroCard(), BorderLayout.WEST);

        JPanel center = transparent(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.gridy = 0;
        constraints.insets = new Insets(0, 0, 14, 0);
        center.add(buildManagementMenuCard(), constraints);

        constraints.gridy = 1;
        constraints.weighty = 0;
        constraints.insets = new Insets(0, 0, 0, 0);
        JPanel action = buildManagementActionCard();
        action.setPreferredSize(new Dimension(0, 112));
        center.add(action, constraints);
        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private JPanel buildManagementMenuCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(), 22);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        manageMenu = new VersionMenuPanel("本地版本菜单",
                "只显示已经下载到本地的实例；点击版本右侧“设置”单独管理",
                version -> onManageVersionSelected(version),
                version -> openVersionSettings(version));
        card.add(manageMenu, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildManagementIntroCard() {
        Surface.Card card = new Surface.Card(null, 22);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 22, 20, 22));
        card.setPreferredSize(new Dimension(300, 0));

        JPanel top = transparent(new BorderLayout());
        top.add(label("游戏管理", Theme.bold(16f), Theme.TEXT), BorderLayout.WEST);
        Surface.Pill local = new Surface.Pill("LOCAL", null,
                Theme.alpha(Theme.INFO, 16), Theme.INFO);
        local.setFont(Theme.regular(10f));
        top.add(local, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        card.add(top);
        card.add(Box.createVerticalStrut(19));

        card.add(downloadStep("01", "查看本地版本", "只列出已经下载的实例"));
        card.add(Box.createVerticalStrut(12));
        card.add(downloadStep("02", "点击版本设置", "每个版本行右侧都有独立入口"));
        card.add(Box.createVerticalStrut(12));
        card.add(downloadStep("03", "单独管理", "Java、内存、窗口、参数与补全"));
        card.add(Box.createVerticalStrut(20));

        JLabel location = label("本地版本目录", Theme.regular(10.5f), Theme.MUTED_DARK);
        location.setAlignmentX(LEFT_ALIGNMENT);
        card.add(location);
        card.add(Box.createVerticalStrut(5));
        managePathLabel = label(shortPath(config.gameDirectoryPath().resolve("versions").toString()),
                Theme.mono(9.5f), Theme.MUTED);
        managePathLabel.setToolTipText(config.gameDirectoryPath().resolve("versions").toString());
        managePathLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(managePathLabel);
        card.add(Box.createVerticalGlue());

        Surface.Card note = new Surface.Card(new BorderLayout(0, 6), 13);
        note.setFill(Theme.GLASS_SURFACE_ALT);
        note.setOutline(Theme.BORDER_SOFT);
        note.setBorder(new EmptyBorder(12, 13, 12, 13));
        note.add(label("列表完全分开", Theme.bold(11.5f), Theme.ACCENT), BorderLayout.NORTH);
        JTextArea noteText = new JTextArea("设置只影响所选版本。\n补全文件会校验并补齐缺失内容。");
        noteText.setEditable(false);
        noteText.setFocusable(false);
        noteText.setOpaque(false);
        noteText.setLineWrap(true);
        noteText.setWrapStyleWord(true);
        noteText.setFont(Theme.regular(11.2f));
        noteText.setForeground(Theme.MUTED);
        noteText.setBorder(BorderFactory.createEmptyBorder());
        note.add(noteText, BorderLayout.CENTER);
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        note.setAlignmentX(LEFT_ALIGNMENT);
        card.add(note);
        return card;
    }

    private JPanel buildManagementActionCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(18, 0), 20);
        card.setBorder(new EmptyBorder(17, 21, 17, 21));
        JPanel state = transparent(null);
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        manageStatusTitle = label("选择本地版本", Theme.bold(14f), Theme.TEXT);
        manageStatusTitle.setAlignmentX(LEFT_ALIGNMENT);
        manageStatusDetail = label("点击版本右侧的“设置”管理该版本，不联网", Theme.regular(10.8f), Theme.MUTED);
        manageStatusDetail.setAlignmentX(LEFT_ALIGNMENT);
        state.add(manageStatusTitle);
        state.add(Box.createVerticalStrut(4));
        state.add(manageStatusDetail);
        card.add(state, BorderLayout.CENTER);

        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        manageRefreshButton = new UiButton("刷新本地", UiButton.Kind.GHOST, 12);
        manageRefreshButton.setPreferredSize(new Dimension(88, 46));
        manageRefreshButton.addActionListener(event -> refreshManagement());
        actions.add(manageRefreshButton);
        card.add(actions, BorderLayout.EAST);
        return card;
    }

    private JPanel buildDownloadIntroCard() {
        Surface.Card card = new Surface.Card(null, 22);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 22, 20, 22));
        card.setPreferredSize(new Dimension(300, 0));

        JPanel top = transparent(new BorderLayout());
        top.add(label("下载游戏", Theme.bold(16f), Theme.TEXT), BorderLayout.WEST);
        Surface.Pill local = new Surface.Pill("INSTALL", null,
                Theme.alpha(Theme.ACCENT, 16), Theme.ACCENT);
        local.setFont(Theme.regular(10f));
        top.add(local, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        card.add(top);
        card.add(Box.createVerticalStrut(19));

        card.add(downloadStep("01", "选择版本", "读取 Mojang 版本目录"));
        card.add(Box.createVerticalStrut(12));
        card.add(downloadStep("02", "下载到本地", "客户端、加载器、运行库与资源"));
        card.add(Box.createVerticalStrut(12));
        card.add(downloadStep("03", "返回启动", "安装完成后从启动页进入游戏"));
        card.add(Box.createVerticalStrut(20));

        JLabel location = label("安装位置", Theme.regular(10.5f), Theme.MUTED_DARK);
        location.setAlignmentX(LEFT_ALIGNMENT);
        card.add(location);
        card.add(Box.createVerticalStrut(5));
        downloadPathLabel = label(shortPath(config.gameDirectoryPath().toString()), Theme.mono(9.5f), Theme.MUTED);
        downloadPathLabel.setToolTipText(config.gameDirectoryPath().toString());
        downloadPathLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(downloadPathLabel);
        card.add(Box.createVerticalStrut(10));
        JLabel configLocation = label("配置与运行时", Theme.regular(10.5f), Theme.MUTED_DARK);
        configLocation.setAlignmentX(LEFT_ALIGNMENT);
        card.add(configLocation);
        card.add(Box.createVerticalStrut(4));
        JLabel configPath = label(shortPath(AppPaths.configDirectory().toString()),
                Theme.mono(9.5f), Theme.MUTED);
        configPath.setToolTipText(AppPaths.configDirectory().toString());
        configPath.setAlignmentX(LEFT_ALIGNMENT);
        card.add(configPath);
        card.add(Box.createVerticalGlue());

        Surface.Card note = new Surface.Card(new BorderLayout(0, 6), 13);
        note.setFill(Theme.GLASS_SURFACE_ALT);
        note.setOutline(Theme.BORDER_SOFT);
        note.setBorder(new EmptyBorder(12, 13, 12, 13));
        note.add(label("下载与启动分离", Theme.bold(11.5f), Theme.ACCENT), BorderLayout.NORTH);
        JTextArea noteText = new JTextArea("只有本菜单会下载游戏文件。\n启动页只读取本地完整实例。");
        noteText.setEditable(false);
        noteText.setFocusable(false);
        noteText.setOpaque(false);
        noteText.setLineWrap(true);
        noteText.setWrapStyleWord(true);
        noteText.setFont(Theme.regular(11.2f));
        noteText.setForeground(Theme.MUTED);
        noteText.setBorder(BorderFactory.createEmptyBorder());
        note.add(noteText, BorderLayout.CENTER);
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        note.setAlignmentX(LEFT_ALIGNMENT);
        card.add(note);
        return card;
    }

    private JPanel downloadStep(String number, String title, String detail) {
        JPanel row = transparent(new BorderLayout(10, 0));
        Surface.Pill badge = new Surface.Pill(number, Theme.ACCENT,
                Theme.alpha(Theme.ACCENT, 14), Theme.ACCENT);
        badge.setFont(Theme.mono(9.5f));
        badge.setPreferredSize(new Dimension(36, 24));
        row.add(badge, BorderLayout.WEST);
        JPanel text = transparent(null);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(label(title, Theme.bold(11.5f), Theme.TEXT));
        text.add(Box.createVerticalStrut(2));
        text.add(label(detail, Theme.regular(10.2f), Theme.MUTED_DARK));
        row.add(text, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        row.setAlignmentX(LEFT_ALIGNMENT);
        return row;
    }

    private JPanel buildDownloadActionCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(18, 0), 20);
        card.setBorder(new EmptyBorder(17, 21, 17, 21));
        JPanel state = transparent(null);
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        downloadStatusTitle = label("等待下载", Theme.bold(14f), Theme.TEXT);
        downloadStatusTitle.setAlignmentX(LEFT_ALIGNMENT);
        downloadStatusDetail = label("选择版本后点击安装，再确认加载器与其他安装选项",
                Theme.regular(10.8f), Theme.MUTED);
        downloadStatusDetail.setAlignmentX(LEFT_ALIGNMENT);
        downloadProgressBar = new SlimProgressBar();
        downloadProgressBar.setProgress(0);
        downloadProgressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        downloadProgressBar.setAlignmentX(LEFT_ALIGNMENT);
        state.add(downloadStatusTitle);
        state.add(Box.createVerticalStrut(4));
        state.add(downloadStatusDetail);
        state.add(Box.createVerticalStrut(7));

        state.add(downloadProgressBar);
        card.add(state, BorderLayout.CENTER);

        downloadButton = new UiButton("安装此版本", UiButton.Kind.ACCENT, 15);
        downloadButton.setPreferredSize(new Dimension(190, 54));
        downloadButton.setFont(Theme.bold(15f));
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(event -> installSelectedVersion());
        downloadRefreshButton = new UiButton("刷新列表", UiButton.Kind.GHOST, 13);
        downloadRefreshButton.compactText(11.5f);
        downloadRefreshButton.setPreferredSize(new Dimension(92, 54));
        downloadRefreshButton.addActionListener(event -> loadRemoteVersions(true));
        JPanel holder = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        holder.add(downloadRefreshButton);
        holder.add(downloadButton);
        card.add(holder, BorderLayout.EAST);
        return card;
    }

    private JPanel buildHeader() {
        JPanel header = transparent(new BorderLayout());
        JPanel brand = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        Artwork.LogoMark logo = new Artwork.LogoMark();
        logo.setPreferredSize(new Dimension(42, 42));
        brand.add(logo);
        brand.add(Box.createHorizontalStrut(12));

        JPanel titles = transparent(null);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel name = label("UFML", Theme.bold(21f), Theme.TEXT);
        JLabel subtitle = label("MINECRAFT  JAVA  LAUNCHER", Theme.regular(9f), Theme.MUTED_DARK);
        titles.add(name);
        titles.add(Box.createVerticalStrut(1));
        titles.add(subtitle);
        brand.add(titles);
        header.add(brand, BorderLayout.WEST);

        JPanel navigation = transparent(new FlowLayout(FlowLayout.CENTER, 6, 4));
        navLaunchButton = new UiButton("启动游戏", UiButton.Kind.GHOST, 11);
        navLaunchButton.compactText(11.5f);
        navLaunchButton.setPreferredSize(new Dimension(104, 34));
        navLaunchButton.addActionListener(event -> showLaunchPage());
        navDownloadButton = new UiButton("下载游戏", UiButton.Kind.GHOST, 11);
        navDownloadButton.compactText(11.5f);
        navDownloadButton.setPreferredSize(new Dimension(104, 34));
        navDownloadButton.addActionListener(event -> showDownloadPage());
        navManageButton = new UiButton("游戏管理", UiButton.Kind.GHOST, 11);
        navManageButton.compactText(11.5f);
        navManageButton.setPreferredSize(new Dimension(104, 34));
        navManageButton.addActionListener(event -> showManagementPage());
        navigation.add(navLaunchButton);
        navigation.add(navDownloadButton);
        navigation.add(navManageButton);
        header.add(navigation, BorderLayout.CENTER);

        JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 9, 6));
        Surface.Pill status = new Surface.Pill("离线模式", Theme.ACCENT,
                Theme.alpha(Theme.ACCENT, 15), Theme.ACCENT);
        right.add(status);
        logButton = new UiButton("日志", UiButton.Kind.GHOST, 11);
        logButton.compactText(11.5f);
        logButton.setPreferredSize(new Dimension(68, 30));
        logButton.addActionListener(event -> showLogDialog());
        right.add(logButton);
        right.add(label("v1.0.0", Theme.mono(10f), Theme.MUTED_DARK));
        UiButton minimizeButton = new UiButton("—", UiButton.Kind.QUIET, 9);
        minimizeButton.setFont(Theme.bold(14f));
        minimizeButton.setPreferredSize(new Dimension(28, 28));
        minimizeButton.setToolTipText("最小化");
        minimizeButton.addActionListener(event -> setState(JFrame.ICONIFIED));
        right.add(minimizeButton);
        UiButton closeButton = new UiButton("×", UiButton.Kind.QUIET, 9);
        closeButton.setFont(Theme.bold(17f));
        closeButton.setPreferredSize(new Dimension(28, 28));
        closeButton.setToolTipText("关闭启动器");
        closeButton.addActionListener(event -> closeLauncher());
        right.add(closeButton);
        header.add(right, BorderLayout.EAST);
        installWindowDrag(header);
        return header;
    }

    private void installWindowDrag(Component component) {
        if (component instanceof AbstractButton) return;
        MouseAdapter dragHandler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                Point screen = event.getLocationOnScreen();
                windowDragOffset = new Point(screen.x - getX(), screen.y - getY());
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (windowDragOffset == null || !SwingUtilities.isLeftMouseButton(event)) return;
                Point screen = event.getLocationOnScreen();
                setLocation(screen.x - windowDragOffset.x, screen.y - windowDragOffset.y);
            }

            @Override public void mouseReleased(MouseEvent event) {
                windowDragOffset = null;
            }
        };
        component.addMouseListener(dragHandler);
        component.addMouseMotionListener(dragHandler);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) installWindowDrag(child);
        }
    }

    private JPanel buildAccountCard() {
        Surface.Card card = new Surface.Card(null, 22);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 22, 20, 22));
        card.setPreferredSize(new Dimension(300, 0));

        JPanel top = transparent(new BorderLayout());
        top.add(label("离线档案", Theme.bold(16f), Theme.TEXT), BorderLayout.WEST);
        Surface.Pill local = new Surface.Pill("LOCAL", null,
                Theme.alpha(Theme.INFO, 16), Theme.INFO);
        local.setFont(Theme.regular(10f));
        top.add(local, BorderLayout.EAST);
        top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        card.add(top);
        card.add(Box.createVerticalStrut(16));

        avatar = new Artwork.AvatarPanel();
        avatar.setPreferredSize(new Dimension(104, 104));
        avatar.setMaximumSize(new Dimension(104, 104));
        avatar.setAlignmentX(CENTER_ALIGNMENT);
        card.add(avatar);
        card.add(Box.createVerticalStrut(14));

        JLabel playerLabel = label("玩家名", Theme.regular(11f), Theme.MUTED);
        playerLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(playerLabel);
        card.add(Box.createVerticalStrut(6));
        usernameField = new DarkTextField(config.username());
        usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        usernameField.setAlignmentX(LEFT_ALIGNMENT);
        usernameField.getDocument().addDocumentListener(new SimpleDocumentListener(this::refreshAvatar));
        card.add(usernameField);
        card.add(Box.createVerticalStrut(7));
        uuidLabel = label("", Theme.mono(9.5f), Theme.MUTED_DARK);
        uuidLabel.setAlignmentX(LEFT_ALIGNMENT);
        card.add(uuidLabel);

        card.add(Box.createVerticalStrut(18));
        Surface.Card note = new Surface.Card(new BorderLayout(0, 6), 13);
        note.setFill(Theme.GLASS_SURFACE_ALT);
        note.setOutline(Theme.BORDER_SOFT);
        note.setBorder(new EmptyBorder(12, 13, 12, 13));
        note.add(label("离线模式", Theme.bold(11.5f), Theme.ACCENT), BorderLayout.NORTH);
        JTextArea noteText = new JTextArea("请先在“下载游戏”菜单安装版本。\n安装完成后可从本地离线启动。");
        noteText.setEditable(false);
        noteText.setFocusable(false);
        noteText.setOpaque(false);
        noteText.setLineWrap(true);
        noteText.setWrapStyleWord(true);
        noteText.setFont(Theme.regular(11.3f));
        noteText.setForeground(Theme.MUTED);
        noteText.setBorder(BorderFactory.createEmptyBorder());
        note.add(noteText, BorderLayout.CENTER);
        note.setMaximumSize(new Dimension(Integer.MAX_VALUE, 82));
        note.setAlignmentX(LEFT_ALIGNMENT);
        card.add(note);
        card.add(Box.createVerticalStrut(15));
        JLabel dataTitle = label("启动器数据", Theme.regular(10f), Theme.MUTED_DARK);
        dataTitle.setAlignmentX(LEFT_ALIGNMENT);
        card.add(dataTitle);
        card.add(Box.createVerticalStrut(3));
        JLabel dataPath = label(AppPaths.home().toString(), Theme.mono(9.2f), Theme.MUTED_DARK);
        dataPath.setAlignmentX(LEFT_ALIGNMENT);
        card.add(dataPath);
        card.add(Box.createVerticalGlue());

        Surface.Pill identity = new Surface.Pill("无需登录 · 本地档案", Theme.ACCENT,
                Theme.alpha(Theme.ACCENT, 12), Theme.MUTED);
        identity.setAlignmentX(LEFT_ALIGNMENT);
        card.add(identity);
        return card;
    }

    private JPanel buildHeroCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(16, 0), 22);
        card.setBorder(new EmptyBorder(20, 24, 18, 20));

        JPanel content = transparent(null);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        Surface.Pill edition = new Surface.Pill("MINECRAFT · JAVA EDITION", null,
                Theme.alpha(Theme.ACCENT, 16), Theme.ACCENT);
        edition.setAlignmentX(LEFT_ALIGNMENT);
        content.add(edition);
        content.add(Box.createVerticalStrut(11));

        JLabel title = label("启动你的方块世界", Theme.bold(28f), Theme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(5));
        JLabel subtitle = label("这里只显示本地已安装版本；新版本请先到下载游戏菜单安装。", Theme.regular(12.2f), Theme.MUTED);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(subtitle);
        content.add(Box.createVerticalStrut(15));

        JPanel versionRow = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        versionCombo = new ModernComboBox<>();
        versionCombo.setPreferredSize(new Dimension(276, 44));
        versionCombo.setToolTipText("选择要启动的本地 Minecraft 版本");
        versionCombo.setEnabled(false);
        versionCombo.addActionListener(event -> onVersionSelected());
        versionRow.add(versionCombo);
        versionRow.add(Box.createHorizontalStrut(8));
        refreshButton = new UiButton("重新扫描", UiButton.Kind.GHOST, 11);
        refreshButton.compactText(11.5f);
        refreshButton.setPreferredSize(new Dimension(82, 44));
        refreshButton.setToolTipText("重新扫描游戏目录中的本地版本，不联网");
        refreshButton.addActionListener(event -> scanLocalAndPopulate());
        versionRow.add(refreshButton);
        versionRow.setAlignmentX(LEFT_ALIGNMENT);
        versionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        content.add(versionRow);
        content.add(Box.createVerticalStrut(7));

        versionDetail = label("正在读取版本列表…", Theme.regular(10.8f), Theme.MUTED_DARK);
        versionDetail.setAlignmentX(LEFT_ALIGNMENT);
        content.add(versionDetail);

        Artwork.BlockScene scene = new Artwork.BlockScene();
        scene.setPreferredSize(new Dimension(224, 164));
        scene.setMinimumSize(new Dimension(180, 140));
        card.add(content, BorderLayout.CENTER);
        card.add(scene, BorderLayout.EAST);
        return card;
    }

    private JPanel buildSettingsCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(0, 11), 22);
        card.setBorder(new EmptyBorder(18, 22, 17, 22));

        JPanel header = transparent(new BorderLayout());
        JPanel heading = transparent(null);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = label("运行设置", Theme.bold(15f), Theme.TEXT);
        JLabel hint = label("Minecraft 固定目录、Config 配置与 Java 设置", Theme.regular(10.3f), Theme.MUTED_DARK);
        heading.add(title);
        heading.add(Box.createVerticalStrut(2));
        heading.add(hint);
        header.add(heading, BorderLayout.WEST);

        JPanel headerActions = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        snapshotToggle = new ToggleChip("显示快照");
        snapshotToggle.setSelected(config.showSnapshots());
        snapshotToggle.addActionListener(event -> {
            boolean selected = snapshotToggle.isSelected();
            config.showSnapshots(selected);
            populateLaunchVersions();
        });
        headerActions.add(snapshotToggle);
        openFolderButton = new UiButton("打开文件夹", UiButton.Kind.GHOST, 10);
        openFolderButton.compactText(10.8f);
        openFolderButton.setPreferredSize(new Dimension(92, 30));
        openFolderButton.addActionListener(event -> openPath(config.gameDirectoryPath()));
        headerActions.add(openFolderButton);
        header.add(headerActions, BorderLayout.EAST);
        card.add(header, BorderLayout.NORTH);

        JPanel form = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 9, 9);

        c.gridx = 0; c.weightx = 0;
        form.add(label("Minecraft 目录", Theme.regular(11.5f), Theme.MUTED), c);
        c.gridx = 1; c.weightx = 1;
        gameDirectoryField = new DarkTextField(config.gameDirectory());
        gameDirectoryField.setEditable(false);
        gameDirectoryField.setFocusable(false);
        gameDirectoryField.setFont(Theme.regular(11.5f));
        gameDirectoryField.setPreferredSize(new Dimension(0, 40));
        gameDirectoryField.setToolTipText("游戏固定存放于 " + config.gameDirectory()
                + "；启动器配置、缓存、Java 运行时和日志存放于 " + AppPaths.configDirectory());
        gameDirectoryField.setCaretPosition(0);
        form.add(gameDirectoryField, c);

        c.gridy = 1; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 9, 9);
        form.add(label("Java 运行时", Theme.regular(11.5f), Theme.MUTED), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        javaField = new DarkTextField(config.javaPath());
        javaField.setEditable(false);
        javaField.setFocusable(false);
        javaField.setFont(Theme.regular(11.5f));
        javaField.setPreferredSize(new Dimension(0, 40));
        javaField.setPlaceholder("自动选择（推荐）");
        javaField.setToolTipText("留空时自动选择与游戏版本匹配的 Java");
        javaField.setCaretPosition(0);
        form.add(javaField, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST; c.insets = new Insets(0, 8, 9, 0);
        JPanel javaActions = transparent(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        autoJavaButton = new UiButton("自动", UiButton.Kind.GHOST, 10);
        autoJavaButton.compactText(10.5f);
        autoJavaButton.setPreferredSize(new Dimension(47, 40));
        autoJavaButton.addActionListener(event -> setAutomaticJava());
        browseJavaButton = new UiButton("选择", UiButton.Kind.GHOST, 10);
        browseJavaButton.compactText(10.5f);
        browseJavaButton.setPreferredSize(new Dimension(52, 40));
        browseJavaButton.addActionListener(event -> chooseJava());
        javaActions.add(autoJavaButton);
        javaActions.add(browseJavaButton);
        form.add(javaActions, c);

        c.gridy = 2; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 0, 9);
        form.add(label("内存分配", Theme.regular(11.5f), Theme.MUTED), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 9);
        JPanel memoryRow = transparent(new BorderLayout(10, 0));
        memorySlider = new MemorySlider();
        memorySlider.setValue(config.memoryMb());
        memorySlider.addChangeListener(event -> {
            int value = memorySlider.getValue();
            config.memoryMb(value);
            memoryValue.setText(String.format("%.1f GB", value / 1024.0));
        });
        memoryRow.add(memorySlider, BorderLayout.CENTER);
        memoryValue = label(String.format("%.1f GB", config.memoryMb() / 1024.0),
                Theme.mono(11.5f), Theme.TEXT);
        memoryValue.setPreferredSize(new Dimension(58, 20));
        memoryValue.setHorizontalAlignment(JLabel.RIGHT);
        memoryRow.add(memoryValue, BorderLayout.EAST);
        form.add(memoryRow, c);
        c.gridx = 2; c.insets = new Insets(0, 0, 0, 0);
        form.add(Box.createHorizontalStrut(0), c);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildLaunchCard() {
        Surface.Card card = new Surface.Card(new BorderLayout(18, 0), 20);
        card.setBorder(new EmptyBorder(17, 21, 17, 21));
        JPanel state = transparent(null);
        state.setLayout(new BoxLayout(state, BoxLayout.Y_AXIS));
        statusTitle = label("准备就绪", Theme.bold(14f), Theme.TEXT);
        statusTitle.setAlignmentX(LEFT_ALIGNMENT);
        statusDetail = label("正在加载版本列表…", Theme.regular(10.8f), Theme.MUTED);
        statusDetail.setAlignmentX(LEFT_ALIGNMENT);
        progressBar = new SlimProgressBar();
        progressBar.setProgress(-1);
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
        progressBar.setAlignmentX(LEFT_ALIGNMENT);
        state.add(statusTitle);
        state.add(Box.createVerticalStrut(4));
        state.add(statusDetail);
        state.add(Box.createVerticalStrut(9));
        state.add(progressBar);
        card.add(state, BorderLayout.CENTER);

        playButton = new UiButton("启动游戏", UiButton.Kind.ACCENT, 15).playIcon();
        playButton.setPreferredSize(new Dimension(190, 54));
        playButton.setFont(Theme.bold(15f));
        playButton.setEnabled(false);
        playButton.addActionListener(event -> startGame());
        JPanel buttonHolder = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonHolder.add(playButton);
        card.add(buttonHolder, BorderLayout.EAST);
        return card;
    }

    private static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JLabel label(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static final class SimpleDocumentListener implements DocumentListener {
        private final Runnable action;
        private SimpleDocumentListener(Runnable action) { this.action = action; }
        @Override public void insertUpdate(DocumentEvent event) { action.run(); }
        @Override public void removeUpdate(DocumentEvent event) { action.run(); }
        @Override public void changedUpdate(DocumentEvent event) { action.run(); }
    }

    private void scanLocalVersions() {
        try {
            localVersions = mojang.loadLocalVersions(config.gameDirectoryPath());
            if (!localVersions.isEmpty()) {
                LauncherLog.info("发现 " + localVersions.size() + " 个本地已安装版本");
            }
        } catch (Exception ex) {
            localVersions = List.of();
            LauncherLog.warn("扫描本地版本失败: " + ex.getMessage());
        }
    }

    private void showLaunchPage() {
        if (installing || launching || repairing) return;
        showingDownloadPage = false;
        pageLayout.show(pageHost, "launch");
        navLaunchButton.active(true);
        navDownloadButton.active(false);
        navManageButton.active(false);
        scanLocalVersions();
        populateLaunchVersions();
        setStatus(localVersions.isEmpty() ? "尚未安装游戏" : "本地启动",
                localVersions.isEmpty() ? "请先打开“下载游戏”菜单安装一个版本"
                        : "已读取 " + localVersions.size() + " 个本地版本 · 启动不联网",
                Theme.TEXT, 0);
        applyInteractionState();
    }

    private void showDownloadPage() {
        if (installing || launching || repairing || gameRunning) return;
        showingDownloadPage = true;
        pageLayout.show(pageHost, "download");
        navLaunchButton.active(false);
        navDownloadButton.active(true);
        navManageButton.active(false);
        scanLocalVersions();
        updateDownloadPath();
        setStatus("下载游戏", "选择版本并安装到本地；启动页不会下载游戏文件", Theme.TEXT, 0);
        if (manifest == null) loadRemoteVersions(false);
        else populateDownloadVersions();
        applyInteractionState();
    }

    private void showManagementPage() {
        if (installing || launching || repairing || gameRunning) return;
        showingDownloadPage = false;
        pageLayout.show(pageHost, "manage");
        navLaunchButton.active(false);
        navDownloadButton.active(false);
        navManageButton.active(true);
        scanLocalVersions();
        populateManageVersions();
        setStatus("游戏管理", localVersions.isEmpty()
                ? "尚无本地版本 · 请先到“下载游戏”菜单安装"
                : "本地共 " + localVersions.size() + " 个版本 · 管理操作不联网", Theme.TEXT, 0);
        applyInteractionState();
    }

    private void refreshManagement() {
        scanLocalVersions();
        populateManageVersions();
        setManageStatus("本地版本已刷新", localVersions.isEmpty()
                ? "没有发现已下载版本" : "发现 " + localVersions.size() + " 个已下载版本",
                localVersions.isEmpty() ? Theme.MUTED : Theme.ACCENT);
        applyInteractionState();
    }

    private void populateManageVersions() {
        if (manageMenu == null) return;
        manageMenu.setVersions(localVersions);
        if (!config.versionId().isBlank()) manageMenu.selectById(config.versionId());
    }

    private void onManageVersionSelected(VersionSummary summary) {
        if (summary == null) {
            setManageStatus("当前分类没有本地版本",
                    "切换上方的正式版、快照版、远古版或愚人节版查看", Theme.MUTED_DARK);
        } else {
            setManageStatus("已选择 Minecraft " + summary.id(),
                    summary.typeLabel() + " · " + dateOf(summary.releaseTime()) + " · 已下载到本地",
                    Theme.ACCENT);
        }
        applyInteractionState();
    }

    private VersionSummary selectedManageVersion() {
        return manageMenu == null ? null : manageMenu.selected();
    }

    private void openVersionSettings(VersionSummary summary) {
        if (summary == null) return;
        VersionSettingsDialog dialog = new VersionSettingsDialog(this, summary,
                versionSettingsStore.get(summary.id()),
                config.memoryMb(),
                () -> openManagedVersion(summary),
                () -> deleteManagedVersion(summary));
        dialog.setVisible(true);
        switch (dialog.action()) {
            case SAVE -> {
                versionSettingsStore.put(summary.id(), dialog.settings());
                setManageStatus("版本设置已保存", "Minecraft " + summary.id()
                                + " 的 Java、内存、显示与启动参数已更新",
                        Theme.ACCENT);
                LauncherLog.info("已保存版本设置: " + summary.id());
            }
            case SET_DEFAULT -> {
                versionSettingsStore.put(summary.id(), dialog.settings());
                useManagedVersion(summary);
            }
            case OPEN_FOLDER -> openManagedVersion(summary);
            case DELETE -> deleteManagedVersion(summary);
            case REPAIR -> {
                versionSettingsStore.put(summary.id(), dialog.settings());
                repairManagedVersion(summary);
            }
            case NONE -> { }
        }
    }

    private void repairManagedVersion(VersionSummary summary) {
        if (summary == null || repairing || installing || launching || gameRunning) return;
        Path gameDirectory = config.gameDirectoryPath();
        VersionSettings settings = versionSettingsStore.get(summary.id());
        String javaSetting = settings.javaPath().isBlank() ? config.javaPath() : settings.javaPath();
        repairing = true;
        setManageStatus("正在补全 " + summary.id(), "只下载缺失或校验失败的文件…", Theme.TEXT);
        setStatus("正在补全游戏文件", "Minecraft " + summary.id() + " · 读取本地版本", Theme.TEXT, -1);
        applyInteractionState();
        LauncherLog.info("开始补全本地版本: " + summary.id());

        worker.submit(() -> {
            try {
                GameVersion version = mojang.loadLocalVersion(summary, gameDirectory);
                if (version == null) {
                    throw new java.io.IOException("本地版本元数据不存在");
                }
                installer.install(version, gameDirectory, this::onRepairProgress);
                SwingUtilities.invokeLater(() -> setManageStatus("检查 Java",
                        "确认本地 Java " + version.requiredJavaMajor() + " 可用", Theme.TEXT));
                javaManager.resolve(javaSetting.isBlank() ? null : javaSetting,
                        version.requiredJavaMajor(), version.requiredRuntimeComponent(),
                        this::onRepairProgress);
                List<VersionSummary> refreshed = mojang.loadLocalVersions(gameDirectory);
                SwingUtilities.invokeLater(() -> {
                    repairing = false;
                    localVersions = refreshed;
                    populateManageVersions();
                    populateLaunchVersions();
                    populateDownloadVersions();
                    setManageStatus("补全完成", "Minecraft " + summary.id()
                            + " 的客户端、运行库、资源和 Java 已就绪", Theme.ACCENT);
                    setStatus("游戏文件补全完成", "Minecraft " + summary.id()
                            + " 已通过本地与在线文件校验", Theme.ACCENT, 1);
                    applyInteractionState();
                    LauncherLog.info("本地版本补全完成: " + summary.id());
                });
            } catch (Exception ex) {
                LauncherLog.error("补全游戏文件失败: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    repairing = false;
                    setManageStatus("补全失败", shortMessage(ex), Theme.ERROR);
                    setStatus("补全失败", shortMessage(ex), Theme.ERROR, 0);
                    applyInteractionState();
                    JOptionPane.showMessageDialog(this,
                            "补全 Minecraft 文件失败。\n\n" + shortMessage(ex)
                                    + "\n\n详细信息请查看运行日志。",
                            "UFML · 补全失败", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void onRepairProgress(ProgressListener.ProgressUpdate update) {
        SwingUtilities.invokeLater(() -> {
            if (!repairing) return;
            setManageStatus(update.stage(), update.detail(), Theme.TEXT);
            setStatus("补全游戏文件 · " + update.stage(), update.detail(), Theme.TEXT,
                    update.progress());
        });
    }

    private void useManagedVersion(VersionSummary summary) {
        config.versionId(summary.id());
        configStore.save(config);
        scanLocalVersions();
        populateLaunchVersions();
        setManageStatus("已设为启动版本", "Minecraft " + summary.id() + " 会显示在启动页", Theme.ACCENT);
        setStatus("启动版本已更新", "启动页将默认选择 Minecraft " + summary.id(), Theme.ACCENT, 0);
    }

    private void openManagedVersion(VersionSummary summary) {
        if (summary == null) return;
        Path versionDirectory = summary.manifestPath() == null
                ? config.gameDirectoryPath().resolve("versions").resolve(PathSafety.safeName(summary.id()))
                : summary.manifestPath().getParent();
        openPath(versionDirectory);
        setManageStatus("已打开版本目录", versionDirectory.toString(), Theme.TEXT);
    }

    private void deleteManagedVersion(VersionSummary summary) {
        if (summary == null) return;
        Path versionsRoot = config.gameDirectoryPath().resolve("versions");
        Path versionDirectory = summary.manifestPath() == null
                ? versionsRoot.resolve(PathSafety.safeName(summary.id()))
                : summary.manifestPath().getParent();
        try {
            PathSafety.deleteDirectory(versionDirectory, versionsRoot);
            versionSettingsStore.remove(summary.id());
            if (summary.id().equals(config.versionId())) {
                config.versionId("");
                configStore.save(config);
            }
            scanLocalVersions();
            populateManageVersions();
            populateLaunchVersions();
            setManageStatus("已删除本地版本", "Minecraft " + summary.id() + " 已从本地移除", Theme.TEXT);
            setStatus("本地版本已删除", "Minecraft " + summary.id() + " 已移除", Theme.TEXT, 0);
            LauncherLog.info("已删除本地版本 " + summary.id());
        } catch (Exception ex) {
            LauncherLog.error("删除本地版本失败: " + ex.getMessage(), ex);
            setManageStatus("删除失败", shortMessage(ex), Theme.ERROR);
        }
        applyInteractionState();
    }

    private void setManageStatus(String title, String detail, Color color) {
        if (manageStatusTitle == null) return;
        manageStatusTitle.setText(title);
        manageStatusTitle.setForeground(color);
        manageStatusDetail.setText(detail == null ? "" : detail);
        manageStatusDetail.setToolTipText(detail);
    }

    private void scanLocalAndPopulate() {
        scanLocalVersions();
        populateLaunchVersions();
        setStatus("本地版本已更新", localVersions.isEmpty() ? "没有发现已安装版本"
                : "发现 " + localVersions.size() + " 个本地版本", Theme.TEXT, 0);
        applyInteractionState();
    }

    private void populateLaunchVersions() {
        String wanted = config.versionId();
        List<VersionSummary> options = new ArrayList<>();
        for (VersionSummary version : localVersions) {
            if (config.showSnapshots() || !version.snapshot()) options.add(version);
        }
        populatingVersions = true;
        versionCombo.removeAllItems();
        for (VersionSummary option : options) versionCombo.addItem(option);
        VersionSummary selection = null;
        for (VersionSummary option : options) {
            if (option.id().equals(wanted)) { selection = option; break; }
        }
        if (selection == null && !options.isEmpty()) selection = options.get(0);
        versionCombo.setSelectedItem(selection);
        populatingVersions = false;
        versionCombo.setEnabled(!options.isEmpty() && !launching && !gameRunning && !installing);
        if (options.isEmpty()) {
            versionDetail.setText("尚未安装任何版本 · 请到“下载游戏”菜单安装");
            versionDetail.setForeground(Theme.MUTED);
        }
        onVersionSelected();
    }

    private void loadRemoteVersions(boolean forceNetwork) {
        if (installing || launching || repairing || downloadsLoading) return;
        if (!forceNetwork && manifest != null) {
            populateDownloadVersions();
            return;
        }
        if (!forceNetwork) {
            try {
                VersionManifest cached = mojang.loadCachedVersionManifest();
                if (cached != null) {
                    manifest = cached;
                    populateDownloadVersions();
                    setDownloadStatus("版本目录已就绪", "使用本地缓存的 Mojang 版本清单", Theme.TEXT, 0);
                    applyInteractionState();
                    return;
                }
            } catch (Exception ex) {
                LauncherLog.warn("本地版本清单不可用: " + ex.getMessage());
            }
        }
        downloadsLoading = true;
        setDownloadStatus(forceNetwork ? "正在刷新版本目录" : "正在获取版本目录",
                "连接 Mojang 获取版本清单…", Theme.TEXT, -1);
        applyInteractionState();
        worker.submit(() -> {
            try {
                VersionManifest loaded = mojang.loadVersionManifest();
                SwingUtilities.invokeLater(() -> {
                    downloadsLoading = false;
                    manifest = loaded;
                    populateDownloadVersions();
                    setDownloadStatus("版本目录已更新", "版本清单已保存到本地", Theme.TEXT, 0);
                    applyInteractionState();
                });
            } catch (Exception ex) {
                LauncherLog.error("加载版本目录失败", ex);
                SwingUtilities.invokeLater(() -> {
                    downloadsLoading = false;
                    populateDownloadVersions();
                    setDownloadStatus("版本目录加载失败", shortMessage(ex), Theme.ERROR, 0);
                    applyInteractionState();
                });
            }
        });
    }

    private void populateDownloadVersions() {
        if (downloadMenu == null) return;
        List<VersionSummary> options = new ArrayList<>();
        if (manifest != null) {
            for (VersionSummary version : manifest.versions()) {
                if (!managedLocally(version.id())) options.add(version);
            }
        }
        downloadMenu.setVersions(options);
        if (manifest != null && downloadMenu.category() == VersionCategory.RELEASE) {
            downloadMenu.selectById(manifest.latestRelease());
        }
    }

    private void onDownloadVersionSelected(VersionSummary summary) {
        if (summary == null) {
            if (manifest == null) {
                setDownloadStatus("等待版本目录", "点击“刷新列表”读取官方版本菜单", Theme.MUTED, 0);
            } else if (downloadMenu != null && downloadMenu.count() == 0) {
                setDownloadStatus("当前分类没有可下载版本",
                        "切换正式版、快照版、远古版或愚人节版查看", Theme.MUTED_DARK, 0);
            } else {
                setDownloadStatus("请选择一个待下载版本", "从上方版本菜单中选择", Theme.MUTED, 0);
            }
            applyInteractionState();
            return;
        }
        setDownloadStatus("已选择 Minecraft " + summary.id(),
                summary.typeLabel() + " · " + dateOf(summary.releaseTime()) + " · "
                        + "尚未安装 · 点击“安装此版本”选择加载器",
                Theme.ACCENT, 0);
        applyInteractionState();
    }

    private VersionSummary selectedDownloadVersion() {
        return downloadMenu == null ? null : downloadMenu.selected();
    }

    private void onVersionSelected() {
        if (populatingVersions) return;
        VersionSummary summary = selectedVersion();
        if (summary == null) {
            versionDetail.setText("没有可用版本");
            versionDetail.setForeground(Theme.MUTED_DARK);
            applyInteractionState();
            return;
        }
        config.versionId(summary.id());
        boolean installed = installed(summary.id());
        versionDetail.setText(summary.typeLabel() + " · " + dateOf(summary.releaseTime()) + " · "
                + (installed ? "本地已安装 · 读取本地信息…" : "文件不完整 · 请到下载游戏菜单修复"));
        versionDetail.setForeground(installed ? Theme.ACCENT : Theme.MUTED);
        int generation = ++detailGeneration;
        Path gameDirectory = config.gameDirectoryPath();
        worker.submit(() -> {
            try {
                GameVersion meta = mojang.loadLocalVersion(summary, gameDirectory);
                if (meta == null) return;
                SwingUtilities.invokeLater(() -> {
                    VersionSummary current = selectedVersion();
                    if (generation != detailGeneration || current == null || !current.id().equals(summary.id())) return;
                    versionDetail.setText((meta.type().equals("release") ? "正式版" : summary.typeLabel())
                            + " · " + dateOf(summary.releaseTime()) + " · Java "
                            + meta.requiredJavaMajor() + " · "
                            + (installed(summary.id()) ? "本地已安装 · 离线可用" : "文件不完整"));
                    versionDetail.setForeground(installed(summary.id()) ? Theme.ACCENT : Theme.MUTED);
                });
            } catch (Exception ex) {
                LauncherLog.warn("读取版本信息失败: " + ex.getMessage());
                SwingUtilities.invokeLater(() -> {
                    if (generation == detailGeneration) {
                        versionDetail.setText(summary.typeLabel() + " · " + dateOf(summary.releaseTime())
                                + " · 本地元数据不可读，请重新下载");
                    }
                });
            }
        });
        applyInteractionState();
    }

    private VersionSummary selectedVersion() {
        Object selected = versionCombo.getSelectedItem();
        return selected instanceof VersionSummary summary ? summary : null;
    }

    private boolean installed(String versionId) {
        Path versionDir = config.gameDirectoryPath().resolve("versions").resolve(PathSafety.safeName(versionId));
        Path manifest = versionDir.resolve(versionId + ".json");
        if (!Files.isRegularFile(manifest)) return false;
        if (Files.isRegularFile(versionDir.resolve(versionId + ".jar"))) return true;
        try {
            Map<String, Object> json = Json.object(Json.parse(
                    Files.readString(manifest, StandardCharsets.UTF_8)));
            return Json.string(json, "inheritsFrom") != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean managedLocally(String versionId) {
        for (VersionSummary version : localVersions) {
            if (version.id().equals(versionId)) return true;
        }
        return false;
    }

    private void setDownloadStatus(String title, String detail, Color color, double progress) {
        if (downloadStatusTitle == null) return;
        downloadStatusTitle.setText(title);
        downloadStatusTitle.setForeground(color);
        downloadStatusDetail.setText(detail == null ? "" : detail);
        downloadStatusDetail.setToolTipText(detail);
        downloadProgressBar.setProgress(progress);
    }

    private void updateDownloadPath() {
        if (downloadPathLabel == null) return;
        String path = config.gameDirectoryPath().toString();
        downloadPathLabel.setText(shortPath(path));
        downloadPathLabel.setToolTipText(path);
        if (managePathLabel != null) {
            String versionsPath = config.gameDirectoryPath().resolve("versions").toString();
            managePathLabel.setText(shortPath(versionsPath));
            managePathLabel.setToolTipText(versionsPath);
        }
    }

    private static String shortPath(String path) {
        return path.length() <= 38 ? path : "..." + path.substring(path.length() - 35);
    }

    private static String dateOf(String releaseTime) {
        try {
            return OffsetDateTime.parse(releaseTime).format(DATE);
        } catch (Exception ex) {
            return releaseTime == null || releaseTime.length() < 10 ? "未知日期" : releaseTime.substring(0, 10);
        }
    }

    private void refreshAvatar() {
        if (usernameField == null) return;
        String username = usernameField.getText().trim();
        usernameField.setError(false);
        if (avatar != null) avatar.setUsername(username.isEmpty() ? "?" : username);
        if (uuidLabel != null) {
            UUID uuid = GameLauncher.offlineUuid(username.isEmpty() ? "Steve" : username);
            uuidLabel.setText("UUID  " + uuid);
        }
    }

    private void chooseJava() {
        Path start = Path.of(System.getProperty("user.home"));
        if (!config.javaPath().isBlank()) {
            Path configured = Path.of(config.javaPath());
            if (configured.getParent() != null && Files.isDirectory(configured.getParent())) {
                start = configured.getParent();
            }
        }
        JFileChooser chooser = new JFileChooser(start.toFile());
        chooser.setDialogTitle("选择 Java 可执行文件");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            chooser.setFileFilter(new FileNameExtensionFilter("Java 可执行文件 (*.exe)", "exe"));
        }
        if (chooser.showDialog(this, "选择") != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        config.javaPath(selected.toString());
        javaField.setText(selected.toString());
        javaField.setCaretPosition(0);
        javaManager.refresh();
        configStore.save(config);
        setStatus("Java 已指定", "启动时将检测 " + selected.getFileName(), Theme.TEXT, 0);
    }

    private void setAutomaticJava() {
        config.javaPath("");
        javaField.setText("");
        configStore.save(config);
        setStatus("Java 自动选择", "下载页会准备匹配 Java，启动页只使用本地 Java", Theme.TEXT, 0);
    }

    private void openPath(Path path) {
        try {
            Files.createDirectories(path);
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IllegalStateException("当前系统不支持打开文件夹");
            }
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception ex) {
            setStatus("无法打开文件夹", shortMessage(ex), Theme.ERROR, 0);
            LauncherLog.warn("打开文件夹失败: " + ex.getMessage());
        }
    }

    private void installSelectedVersion() {
        if (installing || launching || repairing || downloadsLoading) return;
        VersionSummary summary = selectedDownloadVersion();
        if (summary == null) {
            setDownloadStatus("没有可下载的版本", "请先刷新版本目录", Theme.ERROR, 0);
            return;
        }
        InstallVersionDialog dialog = new InstallVersionDialog(this, summary,
                config.gameDirectoryPath(), lastLoaderChoice);
        dialog.setVisible(true);
        if (!dialog.approved()) {
            setDownloadStatus("已取消安装", "没有下载或修改任何本地文件", Theme.MUTED, 0);
            return;
        }
        lastLoaderChoice = dialog.loader();
        startInstallation(summary, dialog.loader(), dialog.setDefaultVersion(), dialog.openLaunchPage());
    }

    private void startInstallation(VersionSummary summary, ModLoaderType loader,
                                   boolean makeDefaultVersion, boolean openLaunchPageAfterInstall) {
        Path gameDirectory = config.gameDirectoryPath();
        installing = true;
        downloadButton.setText("正在安装…");
        setDownloadStatus("正在准备 " + summary.id(), "读取版本元数据…", Theme.TEXT, -1);
        applyInteractionState();
        LauncherLog.info("开始下载游戏 " + summary.id() + "（" + loader.label() + "）到 " + gameDirectory);

        worker.submit(() -> {
            try {
                GameVersion version = mojang.loadVersion(summary, gameDirectory);
                installer.install(version, gameDirectory, this::onInstallProgress);
                SwingUtilities.invokeLater(() -> setDownloadStatus("准备 Java",
                        "下载并准备匹配的 Java " + version.requiredJavaMajor() + "…", Theme.TEXT, -1));
                JavaRuntimeManager.JavaInfo java = javaManager.resolve(null, version.requiredJavaMajor(),
                        version.requiredRuntimeComponent(), this::onInstallProgress);
                SwingUtilities.invokeLater(() -> setDownloadStatus("安装 " + loader.label(),
                        loader.description(), Theme.TEXT, -1));
                ModLoaderService.LoaderResult result = modLoaders.install(loader, summary.id(),
                        gameDirectory, java.executable(), this::onInstallProgress);
                List<VersionSummary> refreshed = mojang.loadLocalVersions(gameDirectory);
                SwingUtilities.invokeLater(() -> {
                    installing = false;
                    localVersions = refreshed;
                    if (makeDefaultVersion) {
                        config.versionId(result.versionId());
                        configStore.save(config);
                    }
                    populateDownloadVersions();
                    if (openLaunchPageAfterInstall) {
                        showLaunchPage();
                        setStatus("安装完成", result.detail()
                                + " · 现在可以从启动页进入游戏", Theme.ACCENT, 1);
                    } else {
                        setDownloadStatus("安装完成", result.detail() + " · 可以继续安装其他版本",
                                Theme.ACCENT, 1);
                        setStatus("安装完成", result.detail(), Theme.ACCENT, 1);
                    }
                    applyInteractionState();
                    LauncherLog.info("游戏下载完成: " + result.versionId()
                            + "（设为启动版本: " + makeDefaultVersion + "）");
                });
            } catch (Exception ex) {
                LauncherLog.error("下载游戏失败: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    installing = false;
                    setDownloadStatus("下载失败", shortMessage(ex), Theme.ERROR, 0);
                    applyInteractionState();
                    JOptionPane.showMessageDialog(this,
                            "下载 Minecraft 失败。\n\n" + shortMessage(ex)
                                    + "\n\n详细信息请查看运行日志。",
                            "UFML · 下载失败", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void onInstallProgress(ProgressListener.ProgressUpdate update) {
        SwingUtilities.invokeLater(() -> {
            if (!installing) return;
            setDownloadStatus(update.stage(), update.detail(), Theme.TEXT, update.progress());
        });
    }

    private void startGame() {
        if (launching || gameRunning || repairing) return;
        syncConfigFields();
        String username = usernameField.getText().trim();
        if (!USERNAME.matcher(username).matches()) {
            usernameField.setError(true);
            usernameField.requestFocusInWindow();
            setStatus("玩家名无效", "请输入 3-16 位字母、数字或下划线", Theme.ERROR, 0);
            return;
        }
        VersionSummary summary = selectedVersion();
        if (summary == null) {
            setStatus("尚未选择版本", "请先选择要启动的 Minecraft 版本", Theme.ERROR, 0);
            return;
        }

        Path gameDirectory = config.gameDirectoryPath();
        VersionSettings versionSettings = versionSettingsStore.get(summary.id());
        String javaSetting = versionSettings.javaPath().isBlank()
                ? config.javaPath() : versionSettings.javaPath();
        Path javaPath = javaSetting.isBlank() ? null : Path.of(javaSetting);
        int memoryMb = config.memoryMb();
        UUID playerId = GameLauncher.offlineUuid(username);
        launching = true;
        applyInteractionState();
        setStatus("正在准备 " + summary.id(), "获取版本信息…", Theme.TEXT, -1);
        LauncherLog.info("开始启动 " + summary.id() + "，玩家 " + username + "（离线模式）");

        worker.submit(() -> {
            try {
                GameVersion version = mojang.loadVersion(summary, gameDirectory);
                InstalledVersion installed = installer.prepareInstalled(version, gameDirectory, this::onProgress);
                SwingUtilities.invokeLater(() -> setStatus("准备 Java", "匹配 Java "
                        + version.requiredJavaMajor() + "…", Theme.TEXT, -1));
                JavaRuntimeManager.JavaInfo java = javaManager.resolveInstalled(
                        javaPath == null ? null : javaPath.toString(),
                        version.requiredJavaMajor(), version.requiredRuntimeComponent());
                SwingUtilities.invokeLater(() -> setStatus("正在启动 Minecraft", java.label(),
                        Theme.TEXT, -1));
                Process process = gameLauncher.launch(version, installed, java, gameDirectory,
                        username, playerId, memoryMb, versionSettings);
                SwingUtilities.invokeLater(() -> attached(process, summary.id()));
                streamProcess(process, summary.id());
            } catch (Exception ex) {
                LauncherLog.error("启动失败: " + ex.getMessage(), ex);
                SwingUtilities.invokeLater(() -> {
                    launching = false;
                    gameRunning = false;
                    applyInteractionState();
                    setStatus("启动失败", shortMessage(ex), Theme.ERROR, 0);
                    JOptionPane.showMessageDialog(this,
                            "启动 Minecraft 失败。\n\n" + shortMessage(ex) + "\n\n详细信息请查看运行日志。",
                            "UFML · 启动失败", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void attached(Process process, String versionId) {
        launching = false;
        gameRunning = true;
        applyInteractionState();
        setStatus("游戏正在运行", "Minecraft " + versionId + " 已启动 · 关闭启动器不会结束游戏",
                Theme.ACCENT, 1);
        LauncherLog.info("Minecraft 已进入运行状态");
    }

    private void streamProcess(Process process, String versionId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LauncherLog.info("[游戏] " + line);
            }
            int exitCode = process.waitFor();
            LauncherLog.info("Minecraft " + versionId + " 已退出，代码 " + exitCode);
            SwingUtilities.invokeLater(() -> {
                gameRunning = false;
                launching = false;
                applyInteractionState();
                setStatus(exitCode == 0 ? "游戏已退出" : "游戏异常退出",
                        "退出代码 " + exitCode + " · 查看日志了解详情",
                        exitCode == 0 ? Theme.TEXT : Theme.WARNING, 0);
            });
        } catch (Exception ex) {
            LauncherLog.warn("读取游戏输出失败: " + ex.getMessage());
            SwingUtilities.invokeLater(() -> {
                gameRunning = false;
                launching = false;
                applyInteractionState();
                setStatus("游戏进程已结束", shortMessage(ex), Theme.WARNING, 0);
            });
        }
    }

    private void onProgress(ProgressListener.ProgressUpdate update) {
        SwingUtilities.invokeLater(() -> {
            if (!launching) return;
            setStatus(update.stage(), update.detail(), Theme.TEXT, update.progress());
        });
    }

    private void applyInteractionState() {
        if (playButton == null) return;
        boolean busy = launching || gameRunning || installing || repairing;
        boolean idle = !busy;
        usernameField.setEnabled(idle);
        versionCombo.setEnabled(idle && versionCombo.getItemCount() > 0);
        snapshotToggle.setEnabled(idle);
        browseJavaButton.setEnabled(idle);
        autoJavaButton.setEnabled(idle);
        memorySlider.setEnabled(idle);
        refreshButton.setEnabled(idle && !downloadsLoading);
        openFolderButton.setEnabled(!installing && !launching && !repairing);
        navLaunchButton.setEnabled(idle);
        navDownloadButton.setEnabled(idle);
        navManageButton.setEnabled(idle);
        playButton.setEnabled(idle && selectedVersion() != null);
        playButton.setText(launching ? "正在启动…" : gameRunning ? "游戏运行中" : "启动游戏");
        if (downloadMenu != null) downloadMenu.setMenuEnabled(!busy && !downloadsLoading);
        if (downloadRefreshButton != null) downloadRefreshButton.setEnabled(!busy && !downloadsLoading);
        if (downloadButton != null) {
            downloadButton.setEnabled(idle && !downloadsLoading && selectedDownloadVersion() != null);
            downloadButton.setText(installing ? "正在安装…" : "安装此版本");
        }
        if (manageMenu != null) manageMenu.setMenuEnabled(idle);
        if (manageRefreshButton != null) manageRefreshButton.setEnabled(idle);
    }

    private void setStatus(String title, String detail, Color color, double progress) {
        if (statusTitle == null) return;
        statusTitle.setText(title);
        statusTitle.setForeground(color);
        statusDetail.setText(detail == null ? "" : detail);
        statusDetail.setToolTipText(detail);
        progressBar.setProgress(progress);
    }

    private void syncConfigFields() {
        config.username(usernameField.getText().trim());
        config.memoryMb(memorySlider.getValue());
        config.showSnapshots(snapshotToggle.isSelected());
        VersionSummary selection = selectedVersion();
        if (selection != null) config.versionId(selection.id());
        configStore.save(config);
    }

    private void showLogDialog() {
        if (logDialog == null) {
            logDialog = new JDialog(this, "运行日志", false);
            logDialog.setSize(880, 560);
            logDialog.setMinimumSize(new Dimension(680, 420));
            logDialog.setLocationRelativeTo(this);
            logDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

            Surface.Background root = new Surface.Background();
            root.setLayout(new BorderLayout(0, 13));
            root.setBorder(new EmptyBorder(20, 22, 20, 22));
            JPanel header = transparent(new BorderLayout());
            JPanel titles = transparent(null);
            titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
            titles.add(label("运行日志", Theme.bold(18f), Theme.TEXT));
            titles.add(Box.createVerticalStrut(2));
            titles.add(label("下载、Java 与游戏进程输出", Theme.regular(10.5f), Theme.MUTED_DARK));
            header.add(titles, BorderLayout.WEST);
            JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 2));
            UiButton logsFolder = new UiButton("日志文件夹", UiButton.Kind.GHOST, 10);
            logsFolder.compactText(11f);
            logsFolder.setPreferredSize(new Dimension(92, 32));
            logsFolder.addActionListener(event -> openPath(AppPaths.logs()));
            UiButton close = new UiButton("关闭", UiButton.Kind.GHOST, 10);
            close.compactText(11f);
            close.setPreferredSize(new Dimension(64, 32));
            close.addActionListener(event -> logDialog.setVisible(false));
            actions.add(logsFolder);
            actions.add(close);
            header.add(actions, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);

            logArea = new JTextArea();
            logArea.setEditable(false);
            logArea.setFont(Theme.mono(11f));
            logArea.setBackground(Theme.SURFACE_ALT);
            logArea.setForeground(new Color(201, 214, 227));
            logArea.setCaretColor(Theme.ACCENT);
            logArea.setSelectionColor(Theme.ACCENT_DARK);
            logArea.setBorder(new EmptyBorder(13, 14, 13, 14));
            JScrollPane scroll = new JScrollPane(logArea);
            scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
            scroll.getViewport().setBackground(Theme.SURFACE_ALT);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            root.add(scroll, BorderLayout.CENTER);
            logDialog.setContentPane(root);
        }
        logArea.setText(LauncherLog.text());
        logArea.setCaretPosition(logArea.getDocument().getLength());
        logDialog.setVisible(true);
        logDialog.toFront();
    }

    private void closeLauncher() {
        syncConfigFields();
        LauncherLog.info("启动器已关闭");
        worker.shutdownNow();
        installer.shutdown();
        if (logDialog != null) logDialog.dispose();
        dispose();
    }

    private static String shortMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) message = throwable.getClass().getSimpleName();
        return message.length() > 220 ? message.substring(0, 217) + "…" : message;
    }
}
