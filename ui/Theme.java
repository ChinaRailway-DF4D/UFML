package io.github.ufml.launcher.ui;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Theme {
    public static final Color BG_TOP = new Color(8, 13, 20);
    public static final Color BG_BOTTOM = new Color(15, 24, 32);
    public static final Color GLASS_TOP = new Color(8, 13, 20, 154);
    public static final Color GLASS_BOTTOM = new Color(15, 24, 32, 138);
    public static final Color GLASS_EDGE = new Color(177, 213, 235, 58);
    public static final Color SURFACE = new Color(19, 29, 41);
    public static final Color GLASS_SURFACE = new Color(19, 29, 41, 205);
    public static final Color GLASS_SURFACE_ALT = new Color(13, 21, 31, 176);
    public static final Color SURFACE_HOVER = new Color(24, 37, 51);
    public static final Color SURFACE_ALT = new Color(13, 21, 31);
    public static final Color BORDER = new Color(42, 59, 77);
    public static final Color BORDER_SOFT = new Color(31, 45, 60);
    public static final Color TEXT = new Color(236, 242, 248);
    public static final Color MUTED = new Color(140, 156, 175);
    public static final Color MUTED_DARK = new Color(101, 119, 139);
    public static final Color ACCENT = new Color(136, 224, 84);
    public static final Color ACCENT_HOVER = new Color(156, 238, 108);
    public static final Color ACCENT_DARK = new Color(53, 105, 37);
    public static final Color ACCENT_GLOW = new Color(136, 224, 84, 40);
    public static final Color WARNING = new Color(255, 190, 94);
    public static final Color ERROR = new Color(255, 113, 113);
    public static final Color INFO = new Color(109, 190, 255);

    private static final String FONT_FAMILY = chooseFont();
    private static final String MONO_FAMILY = chooseMonoFont();

    private Theme() { }

    private static String chooseFont() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : new String[]{"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "Segoe UI", "SansSerif"}) {
            if (available.contains(candidate)) return candidate;
        }
        return Font.SANS_SERIF;
    }

    private static String chooseMonoFont() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String candidate : new String[]{"Cascadia Mono", "Consolas", "JetBrains Mono", "Monospaced"}) {
            if (available.contains(candidate)) return candidate;
        }
        return Font.MONOSPACED;
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, Math.round(size)).deriveFont(size);
    }

    public static Font bold(float size) { return font(Font.BOLD, size); }
    public static Font regular(float size) { return font(Font.PLAIN, size); }
    public static Font mono(float size) {
        return new Font(MONO_FAMILY, Font.PLAIN, Math.round(size)).deriveFont(size);
    }

    public static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public static void installDefaults() {
        UIManager.put("Panel.background", SURFACE);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", regular(13));
        UIManager.put("Button.font", regular(13));
        UIManager.put("TextField.background", SURFACE_ALT);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.selectionBackground", ACCENT_DARK);
        UIManager.put("TextArea.background", SURFACE_ALT);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", ACCENT);
        UIManager.put("TextArea.selectionBackground", ACCENT_DARK);
        UIManager.put("ComboBox.background", SURFACE_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_DARK);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("List.background", SURFACE_ALT);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", ACCENT_DARK);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("ScrollPane.background", SURFACE);
        UIManager.put("Viewport.background", SURFACE);
        UIManager.put("ScrollBar.background", SURFACE);
        UIManager.put("ScrollBar.track", SURFACE_ALT);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ToolTip.background", new Color(28, 40, 54));
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.font", regular(12));
        UIManager.put("FileChooser.background", SURFACE);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("FileChooser.listFont", regular(13));
        UIManager.put("CheckBox.background", SURFACE);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.font", regular(13));
        UIManager.put("RadioButton.background", SURFACE);
        UIManager.put("RadioButton.foreground", TEXT);
        UIManager.put("Spinner.background", SURFACE_ALT);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Separator.foreground", BORDER_SOFT);
    }

    public static Graphics2D quality(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return graphics;
    }

    public static RadialGradientPaint glow(float centerX, float centerY, float radius, Color color) {
        return new RadialGradientPaint(new Point2D.Float(centerX, centerY), radius,
                new float[]{0f, 0.55f, 1f},
                new Color[]{color, alpha(color, color.getAlpha() / 3), alpha(color, 0)});
    }
}
