package br.com.cdip.ecartaftp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

final class SpreadsheetProcessor {
    private static final int MIN_COLUMNS = 19;
    private static final Pattern OBJECT_CODE_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,18}");
    private static final DateTimeFormatter STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    ProcessResult process(Path spreadsheet, MatrixType matrixType, String matrixNumber, String loteOverride, Path pdfDirectory, Path enviarDir) throws Exception {
        if (spreadsheet == null || !Files.isRegularFile(spreadsheet)) {
            throw new IOException("Arquivo de planilha invalido.");
        }
        if (matrixType == null) {
            throw new IllegalArgumentException("Tipo de matriz nao informado.");
        }
        if (matrixNumber == null || matrixNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Numero da matriz nao informado.");
        }

        TableData table = readTable(spreadsheet);
        if (table.rows.isEmpty()) {
            throw new IOException("A planilha nao possui linhas de dados.");
        }
        if (table.columnCount < MIN_COLUMNS) {
            throw new IOException("A planilha precisa ter pelo menos 19 colunas.");
        }

        String lote = resolveLote(table, loteOverride);
        String baseName = "e-Carta_" + matrixNumber.trim() + "_" + lote;
        Path workDir = spreadsheet.getParent().resolve(baseName + "_servico");
        Files.createDirectories(workDir);
        Files.createDirectories(enviarDir);

        int copiedPdfCount = 0;
        for (int rowIndex = 0; rowIndex < table.rows.size(); rowIndex++) {
            List<String> row = table.rows.get(rowIndex);
            normalizeAndValidateRow(row, rowIndex + 1, lote);

            String complementoFlag = upper(trimmed(row.get(7)));
            if ("S".equals(complementoFlag)) {
                String objectCode = trimmed(row.get(9));
                Path originalPdf = resolvePdf(spreadsheet.getParent(), pdfDirectory, objectCode + ".pdf");
                int pages = countPdfPages(originalPdf);
                if (!matrixType.isAllowed(pages)) {
                    throw new IOException("PDF " + originalPdf.getFileName() + " possui " + pages
                        + " pagina(s). Regra da matriz " + matrixType.getLabel() + ": " + matrixType.getRuleText() + ".");
                }
                String newFileName = baseName + "_" + objectCode + "_1_complementar.pdf";
                Files.copy(originalPdf, workDir.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
                row.set(8, newFileName);
                copiedPdfCount++;
            } else {
                row.set(8, "");
            }
        }

        Path serviceTxt = workDir.resolve(baseName + "_servico.txt");
        writePipeFile(serviceTxt, table.rows);

        Path processedCsv = workDir.resolve(baseName + "_processado.csv");
        writeSemicolonCsv(processedCsv, table.rows);

        Path serviceZip = enviarDir.resolve(baseName + "_servico.zip");
        zipServiceBundle(serviceZip, serviceTxt, workDir, table.rows);

        Path responseTxt = workDir.resolve(baseName + "_Resposta.txt");
        writeResponseTxt(responseTxt, lote);
        Path responseZip = enviarDir.resolve(baseName + "_Resposta.zip");
        zipSingleFile(responseZip, responseTxt);

        ProcessResult result = new ProcessResult();
        result.baseName = baseName;
        result.lote = lote;
        result.matrixNumber = matrixNumber.trim();
        result.matrixType = matrixType;
        result.serviceZip = serviceZip;
        result.responseZip = responseZip;
        result.workDir = workDir;
        result.processedCsv = processedCsv;
        result.copiedPdfCount = copiedPdfCount;
        return result;
    }

    private static void normalizeAndValidateRow(List<String> row, int lineNumber, String lote) throws IOException {
        ensureSize(row, MIN_COLUMNS);
        row.set(0, "1");

        String loteAtual = digitsOnly(trimmed(lote));
        if (loteAtual.isEmpty() || loteAtual.length() > 9 || loteAtual.startsWith("0")) {
            throw new IOException("Lote invalido para a linha " + lineNumber + ".");
        }
        row.set(1, loteAtual);

        String cartaoPostagem = digitsOnly(trimmed(row.get(2)));
        if (cartaoPostagem.isEmpty() || cartaoPostagem.length() > 10) {
            throw new IOException("Coluna 3 invalida na linha " + lineNumber + " (cartao de postagem).");
        }
        row.set(2, leftPad(cartaoPostagem, 10));

        String contrato = digitsOnly(trimmed(row.get(3)));
        if (contrato.isEmpty() || contrato.length() > 10) {
            throw new IOException("Coluna 4 invalida na linha " + lineNumber + " (numero do contrato).");
        }
        row.set(3, leftPad(contrato, 10));

        String servicoAdicional = trimmed(row.get(4));
        if (!servicoAdicional.isEmpty() && !"002".equals(servicoAdicional)) {
            throw new IOException("Coluna 5 invalida na linha " + lineNumber + " (em branco ou 002).");
        }

        row.set(5, "N");

        if (!trimmed(row.get(6)).isEmpty()) {
            throw new IOException("Coluna 7 deve permanecer em branco na linha " + lineNumber + ".");
        }

        String complemento = upper(trimmed(row.get(7)));
        if (complemento.isEmpty()) {
            complemento = "N";
        }
        if (!"S".equals(complemento) && !"N".equals(complemento)) {
            throw new IOException("Coluna 8 invalida na linha " + lineNumber + " (use S ou N).");
        }
        row.set(7, complemento);

        String objectCode = trimmed(row.get(9));
        if (!OBJECT_CODE_PATTERN.matcher(objectCode).matches()) {
            throw new IOException("Coluna 10 invalida na linha " + lineNumber + " (1 a 18 caracteres, letras/numeros/_/-).");
        }

        String nome = trimmed(row.get(11));
        String endereco = trimmed(row.get(12));
        String cidade = trimmed(row.get(16));
        if (nome.isEmpty() || endereco.isEmpty() || cidade.isEmpty()) {
            throw new IOException("Endereco incompleto na linha " + lineNumber + ".");
        }

        String uf = upper(trimmed(row.get(17)));
        if (uf.length() != 2 || !Character.isLetter(uf.charAt(0)) || !Character.isLetter(uf.charAt(1))) {
            throw new IOException("UF invalida na linha " + lineNumber + ".");
        }
        row.set(17, uf);

        String cep = digitsOnly(trimmed(row.get(18)));
        if (!cep.isEmpty() && cep.length() != 8) {
            throw new IOException("CEP invalido na linha " + lineNumber + ".");
        }
        row.set(18, cep);
    }

    private static String resolveLote(TableData table, String loteOverride) throws IOException {
        String lote = trimmed(loteOverride);
        if (!lote.isEmpty()) {
            lote = digitsOnly(lote);
        } else {
            lote = digitsOnly(trimmed(table.rows.get(0).get(1)));
        }
        if (lote.isEmpty() || lote.length() > 9 || lote.startsWith("0")) {
            throw new IOException("Nao foi possivel identificar um lote valido na planilha.");
        }
        return lote;
    }

    private static TableData readTable(Path file) throws Exception {
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcel(file);
        }
        return readCsv(file);
    }

    private static TableData readExcel(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            int firstRowNum = sheet.getFirstRowNum();
            int lastRowNum = sheet.getLastRowNum();
            Row headerRow = sheet.getRow(firstRowNum);
            if (headerRow == null) {
                return new TableData(0, new ArrayList<>());
            }
            int colCount = headerRow.getLastCellNum();
            DataFormatter formatter = new DataFormatter(Locale.forLanguageTag("pt-BR"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> rows = new ArrayList<>();
            for (int r = firstRowNum + 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                boolean hasAny = false;
                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell != null ? formatter.formatCellValue(cell, evaluator) : "";
                    value = normalizeCellValue(value);
                    if (!value.isBlank()) {
                        hasAny = true;
                    }
                    values.add(value);
                }
                if (hasAny) {
                    rows.add(values);
                }
            }
            return new TableData(colCount, rows);
        }
    }

    private static TableData readCsv(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Charset charset = StandardCharsets.UTF_8;
        String text = new String(bytes, charset);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0) {
            return new TableData(0, new ArrayList<>());
        }
        char delimiter = count(lines[0], ';') >= count(lines[0], ',') ? ';' : ',';
        List<String> header = parseCsvLine(lines[0], delimiter);
        List<List<String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> values = parseCsvLine(line, delimiter);
            ensureSize(values, header.size());
            boolean hasAny = false;
            for (int c = 0; c < values.size(); c++) {
                String normalized = normalizeCellValue(values.get(c));
                values.set(c, normalized);
                if (!normalized.isBlank()) {
                    hasAny = true;
                }
            }
            if (hasAny) {
                rows.add(values);
            }
        }
        return new TableData(header.size(), rows);
    }

    private static Path resolvePdf(Path defaultDir, Path optionalDir, String fileName) throws IOException {
        Path direct = defaultDir.resolve(fileName);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        if (optionalDir != null) {
            Path candidate = optionalDir.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IOException("PDF complementar nao encontrado: " + fileName);
    }

    private static int countPdfPages(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            return document.getNumberOfPages();
        }
    }

    private static void writePipeFile(Path target, List<List<String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (List<String> row : rows) {
                ensureSize(row, MIN_COLUMNS);
                writer.write(String.join("|", row));
                writer.newLine();
            }
        }
    }

    private static void writeSemicolonCsv(Path target, List<List<String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            for (List<String> row : rows) {
                writer.write(joinEscaped(row, ';'));
                writer.newLine();
            }
        }
    }

    private static void writeResponseTxt(Path target, String lote) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8)) {
            writer.write("1|" + lote + "|A");
            writer.newLine();
        }
    }

    private static void zipServiceBundle(Path zipFile, Path serviceTxt, Path workDir, List<List<String>> rows) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addZipEntry(zip, serviceTxt, serviceTxt.getFileName().toString());
            for (List<String> row : rows) {
                String pdfName = trimmed(row.get(8));
                if (pdfName.isEmpty()) {
                    continue;
                }
                Path pdf = workDir.resolve(pdfName);
                if (Files.isRegularFile(pdf)) {
                    addZipEntry(zip, pdf, pdfName);
                }
            }
        }
    }

    private static void zipSingleFile(Path zipFile, Path file) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipFile);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            addZipEntry(zip, file, file.getFileName().toString());
        }
    }

    private static void addZipEntry(ZipOutputStream zip, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static String joinEscaped(List<String> row, char delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            String value = row.get(i) == null ? "" : row.get(i);
            boolean mustQuote = value.indexOf(delimiter) >= 0 || value.contains("\"");
            if (mustQuote) {
                sb.append('"').append(value.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private static List<String> parseCsvLine(String line, char delimiter) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == delimiter && !inQuotes) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private static int count(String line, char target) {
        int total = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == target) {
                total++;
            }
        }
        return total;
    }

    private static void ensureSize(List<String> row, int size) {
        while (row.size() < size) {
            row.add("");
        }
    }

    private static String normalizeCellValue(String value) {
        String v = value == null ? "" : value.trim();
        if (v.endsWith(".0") && v.length() > 2 && v.substring(0, v.length() - 2).chars().allMatch(Character::isDigit)) {
            return v.substring(0, v.length() - 2);
        }
        return v;
    }

    private static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D+", "");
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String upper(String value) {
        return value == null ? "" : value.toUpperCase(Locale.ROOT);
    }

    private static String leftPad(String value, int size) {
        if (value.length() >= size) {
            return value;
        }
        char[] padding = new char[size - value.length()];
        Arrays.fill(padding, '0');
        return new String(padding) + value;
    }

    static final class ProcessResult {
        String baseName;
        String lote;
        String matrixNumber;
        MatrixType matrixType;
        Path serviceZip;
        Path responseZip;
        Path workDir;
        Path processedCsv;
        int copiedPdfCount;
    }

    private record TableData(int columnCount, List<List<String>> rows) {
    }
}
