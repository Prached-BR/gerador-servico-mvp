package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetProcessorIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldProcessCsvAndGenerateArtifacts() throws Exception {
        Path spreadsheet = tempDir.resolve("entrada.csv");
        Path enviarDir = tempDir.resolve("enviar");
        Files.createDirectories(enviarDir);
        Files.writeString(spreadsheet, buildValidCsv(), StandardCharsets.UTF_8);

        SpreadsheetProcessor processor = new SpreadsheetProcessor();
        SpreadsheetProcessor.ProcessResult result =
            processor.process(spreadsheet, MatrixType.SIMPLEX, "123456", "987654321", null, enviarDir);

        assertEquals("e-Carta_123456_987654321", result.baseName);
        assertEquals("987654321", result.lote);
        assertEquals("123456", result.matrixNumber);
        assertEquals(MatrixType.SIMPLEX, result.matrixType);
        assertEquals(0, result.copiedPdfCount);
        assertTrue(Files.exists(result.workDir));
        assertTrue(Files.exists(result.processedCsv));
        assertTrue(Files.exists(result.serviceZip));
        assertTrue(Files.exists(result.responseZip));

        String processedCsv = Files.readString(result.processedCsv, StandardCharsets.UTF_8);
        assertTrue(processedCsv.contains("987654321"));
        assertTrue(processedCsv.contains("OBJ001"));

        try (ZipFile serviceZip = new ZipFile(result.serviceZip.toFile())) {
            assertEquals(1, serviceZip.size());
            assertTrue(serviceZip.getEntry(result.baseName + "_servico.txt") != null);
        }

        try (ZipFile responseZip = new ZipFile(result.responseZip.toFile())) {
            assertEquals(1, responseZip.size());
            assertTrue(responseZip.getEntry(result.baseName + "_Resposta.txt") != null);
        }
    }

    private static String buildValidCsv() {
        List<String> header = new ArrayList<>();
        List<String> row = new ArrayList<>();
        for (int i = 0; i < 19; i++) {
            header.add("col" + i);
            row.add("");
        }

        row.set(1, "987654321");
        row.set(2, "12345");
        row.set(3, "67890");
        row.set(7, "N");
        row.set(9, "OBJ001");
        row.set(11, "Destinatario Teste");
        row.set(12, "Rua das Flores, 100");
        row.set(13, "Centro");
        row.set(14, "10");
        row.set(15, "Sala 1");
        row.set(16, "Curitiba");
        row.set(17, "PR");
        row.set(18, "80000000");

        return String.join(";", header) + System.lineSeparator()
            + String.join(";", row) + System.lineSeparator();
    }
}
