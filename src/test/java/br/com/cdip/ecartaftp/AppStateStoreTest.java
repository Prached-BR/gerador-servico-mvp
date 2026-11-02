package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadPreserveEncryptedState() throws Exception {
        Path stateFile = tempDir.resolve("config").resolve("app-state.enc");
        AppStateStore store = new AppStateStore(stateFile);

        AppStateStore.AppState state = new AppStateStore.AppState();
        state.ftpHost = "ftp.exemplo.local";
        state.ftpPort = 2121;
        state.ftpUser = "usuario";
        state.ftpPassword = "segredo";
        state.ftpRemoteSendDir = "/entrada";
        state.ftpRemoteReceiveDir = "/saida";
        state.matrixNumbers.put(MatrixType.SIMPLEX, "10001");
        state.matrixNumbers.put(MatrixType.DUPLEX, "20002");
        state.lastLoteByMatrix.put("10001", "321");

        AppStateStore.PendingTransfer pending = new AppStateStore.PendingTransfer();
        pending.id = "pend-001";
        pending.matrixType = MatrixType.SIMPLEX.name();
        pending.matrixNumber = "10001";
        pending.loteNumber = "321";
        pending.baseName = "e-Carta_10001_321";
        pending.serviceZipPath = "ENVIAR/service.zip";
        pending.responseZipPath = "ENVIAR/response.zip";
        pending.createdAt = 10L;
        pending.serviceSentAt = 20L;
        pending.nextDownloadAt = 30L;
        pending.serviceSent = true;
        pending.responseSent = false;
        pending.downloadAttempted = true;
        pending.receiptDownloaded = true;
        pending.inconsistencyDownloaded = false;
        pending.reminder2hShown = true;
        state.pendingTransfers.add(pending);

        store.save(state);

        assertTrue(Files.exists(stateFile));

        AppStateStore.AppState loaded = store.load();
        assertEquals("ftp.exemplo.local", loaded.ftpHost);
        assertEquals(2121, loaded.ftpPort);
        assertEquals("usuario", loaded.ftpUser);
        assertEquals("segredo", loaded.ftpPassword);
        assertEquals("/entrada", loaded.ftpRemoteSendDir);
        assertEquals("/saida", loaded.ftpRemoteReceiveDir);
        assertEquals("10001", loaded.matrixNumbers.get(MatrixType.SIMPLEX));
        assertEquals("20002", loaded.matrixNumbers.get(MatrixType.DUPLEX));
        assertEquals("321", loaded.lastLoteByMatrix.get("10001"));
        assertEquals(1, loaded.pendingTransfers.size());

        AppStateStore.PendingTransfer loadedPending = loaded.pendingTransfers.getFirst();
        assertEquals("pend-001", loadedPending.id);
        assertEquals("e-Carta_10001_321", loadedPending.baseName);
        assertTrue(loadedPending.serviceSent);
        assertFalse(loadedPending.responseSent);
        assertTrue(loadedPending.downloadAttempted);
        assertTrue(loadedPending.receiptDownloaded);
        assertFalse(loadedPending.isFinished());
    }
}
