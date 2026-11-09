package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigCryptoTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveAndLoadEncryptedProperties() throws Exception {
        Path stateFile = tempDir.resolve("config").resolve("app-state.enc");
        Properties source = new Properties();
        source.setProperty("ftp.host", "ftp.exemplo.local");
        source.setProperty("ftp.user", "operador");
        source.setProperty("ftp.password", "segredo-123");

        ConfigCrypto.save(stateFile, source);

        byte[] rawBytes = Files.readAllBytes(stateFile);
        String rawText = new String(rawBytes, StandardCharsets.UTF_8);
        assertTrue(Files.exists(stateFile));
        assertTrue(rawBytes.length > 0);
        assertFalseContains(rawText, "segredo-123");

        Properties loaded = ConfigCrypto.load(stateFile);
        assertEquals("ftp.exemplo.local", loaded.getProperty("ftp.host"));
        assertEquals("operador", loaded.getProperty("ftp.user"));
        assertEquals("segredo-123", loaded.getProperty("ftp.password"));
    }

    @Test
    void shouldRejectTamperedEncryptedFile() throws Exception {
        Path stateFile = tempDir.resolve("app-state.enc");
        Properties source = new Properties();
        source.setProperty("ftp.host", "ftp.exemplo.local");
        ConfigCrypto.save(stateFile, source);

        byte[] tampered = Files.readAllBytes(stateFile);
        tampered[0] = (byte) (tampered[0] + 1);
        Files.write(stateFile, tampered);

        IOException error = assertThrows(IOException.class, () -> ConfigCrypto.load(stateFile));
        assertTrue(error.getMessage().contains("descriptografar"));
    }

    private static void assertFalseContains(String content, String sensitiveValue) {
        assertTrue(!content.contains(sensitiveValue));
    }
}
