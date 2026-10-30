package io.github.ufml.launcher;

import io.github.ufml.launcher.ui.LauncherFrame;
import io.github.ufml.launcher.ui.Theme;
import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.LauncherLog;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            LauncherLog.error("未捕获异常: " + error.getMessage(), error);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "启动器遇到未预期的错误：\n" + error.getMessage() + "\n\n详细信息已写入日志。",
                    "UFML · 错误", JOptionPane.ERROR_MESSAGE));
        });

        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("当前环境不支持图形界面，无法启动 UFML。");
            return;
        }
        AppPaths.initializeLocalData();
        SwingUtilities.invokeLater(() -> {
            Theme.installDefaults();
            LauncherFrame frame = new LauncherFrame();
            frame.setVisible(true);
            LauncherLog.info("UFML 启动器已打开");
        });
    }
}
