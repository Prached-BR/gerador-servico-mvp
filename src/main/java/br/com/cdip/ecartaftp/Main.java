package br.com.cdip.ecartaftp;

import br.com.cdip.ecartaftp.AppStateStore.AppState;
import java.io.File;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            try {
                Path appHome = resolveAppHome();
                AppStateStore store = new AppStateStore(appHome.resolve("config").resolve("app-state.enc"));
                AppState state = store.load();
                MainFrame frame = new MainFrame(appHome, store, state);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            } catch (Exception e) {
                throw new RuntimeException("Falha ao iniciar a aplicacao", e);
            }
        });
    }

    private static Path resolveAppHome() {
        try {
            File codeSource = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = codeSource.isDirectory() ? codeSource : codeSource.getParentFile();
            if (base != null) {
                return base.toPath().resolve("dados");
            }
        } catch (Exception ignored) {
        }
        return Path.of("dados");
    }
}
