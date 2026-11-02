package br.com.cdip.ecartaftp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class AppStateStore {
    private final Path stateFile;

    AppStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    synchronized AppState load() throws IOException {
        Properties props = ConfigCrypto.load(stateFile);
        AppState state = new AppState();
        state.ftpHost = props.getProperty("ftp.host", "");
        state.ftpPort = parseInt(props.getProperty("ftp.port"), 21);
        state.ftpUser = props.getProperty("ftp.user", "");
        state.ftpPassword = props.getProperty("ftp.password", "");
        state.ftpRemoteSendDir = props.getProperty("ftp.remote.send", "");
        state.ftpRemoteReceiveDir = props.getProperty("ftp.remote.receive", "");

        for (MatrixType type : MatrixType.values()) {
            state.matrixNumbers.put(type, props.getProperty("matrix." + type.name(), ""));
        }

        int lastCount = parseInt(props.getProperty("last.count"), 0);
        for (int i = 0; i < lastCount; i++) {
            String matrix = props.getProperty("last." + i + ".matrix", "");
            String lote = props.getProperty("last." + i + ".lote", "");
            if (!matrix.isBlank()) {
                state.lastLoteByMatrix.put(matrix, lote);
            }
        }

        int pendingCount = parseInt(props.getProperty("pending.count"), 0);
        for (int i = 0; i < pendingCount; i++) {
            PendingTransfer pending = new PendingTransfer();
            pending.id = props.getProperty("pending." + i + ".id", "");
            pending.matrixType = props.getProperty("pending." + i + ".matrixType", "");
            pending.matrixNumber = props.getProperty("pending." + i + ".matrixNumber", "");
            pending.loteNumber = props.getProperty("pending." + i + ".loteNumber", "");
            pending.baseName = props.getProperty("pending." + i + ".baseName", "");
            pending.serviceZipPath = props.getProperty("pending." + i + ".serviceZipPath", "");
            pending.responseZipPath = props.getProperty("pending." + i + ".responseZipPath", "");
            pending.createdAt = parseLong(props.getProperty("pending." + i + ".createdAt"), 0L);
            pending.serviceSentAt = parseLong(props.getProperty("pending." + i + ".serviceSentAt"), 0L);
            pending.nextDownloadAt = parseLong(props.getProperty("pending." + i + ".nextDownloadAt"), 0L);
            pending.downloadAttempted = Boolean.parseBoolean(props.getProperty("pending." + i + ".downloadAttempted", "false"));
            pending.serviceSent = Boolean.parseBoolean(props.getProperty("pending." + i + ".serviceSent", "false"));
            pending.responseSent = Boolean.parseBoolean(props.getProperty("pending." + i + ".responseSent", "false"));
            pending.receiptDownloaded = Boolean.parseBoolean(props.getProperty("pending." + i + ".receiptDownloaded", "false"));
            pending.inconsistencyDownloaded = Boolean.parseBoolean(props.getProperty("pending." + i + ".inconsistencyDownloaded", "false"));
            pending.reminder2hShown = Boolean.parseBoolean(props.getProperty("pending." + i + ".reminder2hShown", "false"));
            pending.reminder24hShown = Boolean.parseBoolean(props.getProperty("pending." + i + ".reminder24hShown", "false"));
            pending.reminder44hShown = Boolean.parseBoolean(props.getProperty("pending." + i + ".reminder44hShown", "false"));
            if (!pending.id.isBlank()) {
                state.pendingTransfers.add(pending);
            }
        }
        return state;
    }

    synchronized void save(AppState state) throws IOException {
        Properties props = new Properties();
        props.setProperty("ftp.host", safe(state.ftpHost));
        props.setProperty("ftp.port", String.valueOf(state.ftpPort <= 0 ? 21 : state.ftpPort));
        props.setProperty("ftp.user", safe(state.ftpUser));
        props.setProperty("ftp.password", safe(state.ftpPassword));
        props.setProperty("ftp.remote.send", safe(state.ftpRemoteSendDir));
        props.setProperty("ftp.remote.receive", safe(state.ftpRemoteReceiveDir));

        for (MatrixType type : MatrixType.values()) {
            props.setProperty("matrix." + type.name(), safe(state.matrixNumbers.get(type)));
        }

        props.setProperty("last.count", String.valueOf(state.lastLoteByMatrix.size()));
        int idx = 0;
        for (Map.Entry<String, String> entry : state.lastLoteByMatrix.entrySet()) {
            props.setProperty("last." + idx + ".matrix", safe(entry.getKey()));
            props.setProperty("last." + idx + ".lote", safe(entry.getValue()));
            idx++;
        }

        props.setProperty("pending.count", String.valueOf(state.pendingTransfers.size()));
        for (int i = 0; i < state.pendingTransfers.size(); i++) {
            PendingTransfer pending = state.pendingTransfers.get(i);
            String prefix = "pending." + i + ".";
            props.setProperty(prefix + "id", safe(pending.id));
            props.setProperty(prefix + "matrixType", safe(pending.matrixType));
            props.setProperty(prefix + "matrixNumber", safe(pending.matrixNumber));
            props.setProperty(prefix + "loteNumber", safe(pending.loteNumber));
            props.setProperty(prefix + "baseName", safe(pending.baseName));
            props.setProperty(prefix + "serviceZipPath", safe(pending.serviceZipPath));
            props.setProperty(prefix + "responseZipPath", safe(pending.responseZipPath));
            props.setProperty(prefix + "createdAt", String.valueOf(pending.createdAt));
            props.setProperty(prefix + "serviceSentAt", String.valueOf(pending.serviceSentAt));
            props.setProperty(prefix + "nextDownloadAt", String.valueOf(pending.nextDownloadAt));
            props.setProperty(prefix + "downloadAttempted", String.valueOf(pending.downloadAttempted));
            props.setProperty(prefix + "serviceSent", String.valueOf(pending.serviceSent));
            props.setProperty(prefix + "responseSent", String.valueOf(pending.responseSent));
            props.setProperty(prefix + "receiptDownloaded", String.valueOf(pending.receiptDownloaded));
            props.setProperty(prefix + "inconsistencyDownloaded", String.valueOf(pending.inconsistencyDownloaded));
            props.setProperty(prefix + "reminder2hShown", String.valueOf(pending.reminder2hShown));
            props.setProperty(prefix + "reminder24hShown", String.valueOf(pending.reminder24hShown));
            props.setProperty(prefix + "reminder44hShown", String.valueOf(pending.reminder44hShown));
        }
        ConfigCrypto.save(stateFile, props);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    static final class AppState {
        String ftpHost = "";
        int ftpPort = 21;
        String ftpUser = "";
        String ftpPassword = "";
        String ftpRemoteSendDir = "";
        String ftpRemoteReceiveDir = "";
        final EnumMap<MatrixType, String> matrixNumbers = new EnumMap<>(MatrixType.class);
        final Map<String, String> lastLoteByMatrix = new LinkedHashMap<>();
        final List<PendingTransfer> pendingTransfers = new ArrayList<>();
    }

    static final class PendingTransfer {
        String id = "";
        String matrixType = "";
        String matrixNumber = "";
        String loteNumber = "";
        String baseName = "";
        String serviceZipPath = "";
        String responseZipPath = "";
        long createdAt;
        long serviceSentAt;
        long nextDownloadAt;
        boolean downloadAttempted;
        boolean serviceSent;
        boolean responseSent;
        boolean receiptDownloaded;
        boolean inconsistencyDownloaded;
        boolean reminder2hShown;
        boolean reminder24hShown;
        boolean reminder44hShown;

        boolean isFinished() {
            return serviceSent && responseSent;
        }
    }
}
