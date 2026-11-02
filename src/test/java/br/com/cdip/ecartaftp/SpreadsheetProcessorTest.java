package br.com.cdip.ecartaftp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpreadsheetProcessorTest {

    @TempDir
    Path tempDir;

    @Test
    void processGeneratesServiceAndResponseArtifactsFromCsv() throws Exception {
        Path spreadsheet = tempDir.resolve("entrada.csv");
        Path pdfDir = tempDir.resolve("pdfs");
        Path enviarDir = tempDir.resolve("ENVIAR");
        Files.createDirectories(pdfDir);
        Files.createDirectories(enviarDir);

        createSinglePagePdf(pdfDir.resolve("OBJ001.pdf"));
        Files.writeString(spreadsheet, buildCsv(), StandardCharsets.UTF_8);

        SpreadsheetProcessor processor = new SpreadsheetProcessor();
        SpreadsheetProcessor.ProcessResult result = processor.process(
            spreadsheet,
            MatrixType.SIMPLEX,
            "55555",
            "321",
            pdfDir,
            enviarDir
        );

        assertEquals("e-Carta_55555_321", result.baseName);
        assertEquals("321", result.lote);
        assertEquals(1, result.copiedPdfCount);
        assertTrue(Files.exists(result.serviceZip));
        assertTrue(Files.exists(result.responseZip));
        assertTrue(Files.exists(result.processedCsv));

        List<String> processedLines = Files.readAllLines(result.processedCsv, StandardCharsets.UTF_8);
        assertEquals(1, processedLines.size());
        assertTrue(processedLines.getFirst().contains("e-Carta_55555_321_OBJ001_1_complementar.pdf"));

        try (ZipFile zip = new ZipFile(result.serviceZip.toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("_servico.txt")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("_OBJ001_1_complementar.pdf")));
        }

        try (ZipFile zip = new ZipFile(result.responseZip.toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().endsWith("_Resposta.txt")));
        }
    }

    private static void createSinglePagePdf(Path file) throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(file.toFile());
        }
    }

    private static String buildCsv() {
        String header = String.join(";", List.of(
            "tipo", "lote", "cartao", "contrato", "adicional", "duplex", "reservado", "complemento",
            "arquivo", "objeto", "referencia", "nome", "endereco", "bairro", "complemento2", "numero",
            "cidade", "uf", "cep"
        ));

        String row = String.join(";", List.of(
            "",
            "321",
            "123456",
            "654321",
            "002",
            "",
            "",
            "S",
            "",
            "OBJ001",
            "",
            "Nome Teste",
            "Rua Principal",
            "",
            "",
            "",
            "Cidade Teste",
            "SP",
            "12345678"
        ));
        return header + System.lineSeparator() + row + System.lineSeparator();
    }
}
