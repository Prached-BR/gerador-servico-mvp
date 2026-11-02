package br.com.cdip.ecartaftp;

import br.com.cdip.ecartaftp.AppStateStore.AppState;
import br.com.cdip.ecartaftp.AppStateStore.PendingTransfer;
import br.com.cdip.ecartaftp.FtpClientService.FtpConfig;
import br.com.cdip.ecartaftp.SpreadsheetProcessor.ProcessResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

final class MainFrame extends JFrame {
    private static final DateTimeFormatter DAY_FOLDER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path appHome;
    private final Path enviarDir;
    private final Path enviadosDir;
    private final Path recebidosDir;
    private final AppStateStore stateStore;
    private final SpreadsheetProcessor processor = new SpreadsheetProcessor();
    private final FtpClientService ftpService = new FtpClientService();

    private AppState state;
    private boolean schedulerBusy;

    private final JTextField spreadsheetField = new JTextField();
    private final JTextField pdfFolderField = new JTextField();
    private final JTextField loteOverrideField = new JTextField();
    private final JComboBox<MatrixType> matrixTypeCombo = new JComboBox<>(MatrixType.values());
    private final JTextField simplexField = new JTextField();
    private final JTextField duplexField = new JTextField();
    private final JTextField multiplexField = new JTextField();
    private final JTextField insercaoField = new JTextField();
    private final JTextField ftpHostField = new JTextField();
    private final JTextField ftpPortField = new JTextField();
    private final JTextField ftpUserField = new JTextField();
    private final JPasswordField ftpPasswordField = new JPasswordField();
    private final JTextField ftpSendDirField = new JTextField();
    private final JTextField ftpReceiveDirField = new JTextField();
    private final JLabel pendingLabel = new JLabel();
    private final JTextArea logArea = new JTextArea();
    private final Timer schedulerTimer;

    MainFrame(Path appHome, AppStateStore stateStore, AppState initialState) {
        super("CDIP e-Carta FTP Client");
        this.appHome = appHome;
        this.enviarDir = appHome.resolve("ENVIAR");
        this.enviadosDir = appHome.resolve("ENVIADOS");
        this.recebidosDir = appHome.resolve("RECEBIDOS");
        this.stateStore = stateStore;
        this.state = initialState;
        ensureBaseDirs();
        initUi();
        loadStateIntoFields();
        updatePendingLabel();
        appendLog("Aplicacao iniciada em " + appHome.toAbsolutePath());
        schedulerTimer = new Timer(60_000, e -> schedulerTick());
        schedulerTimer.start();
        schedulerTick();
    }

    private void initUi() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 760));
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 0, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;

        int row = 0;
        addRow(form, c, row++, "Planilha", spreadsheetField, button("Selecionar...", e -> chooseSpreadsheet()));
        addRow(form, c, row++, "Pasta PDFs", pdfFolderField, button("Selecionar...", e -> choosePdfFolder()));
        addRow(form, c, row++, "Tipo matriz", matrixTypeCombo, new JLabel("Selecione o tipo para executar"));
        addRow(form, c, row++, "Lote manual", loteOverrideField, new JLabel("Opcional: sobrescreve o lote da planilha"));

        addRow(form, c, row++, "Matriz Simplex", simplexField, new JLabel(MatrixType.SIMPLEX.getRuleText()));
        addRow(form, c, row++, "Matriz Duplex", duplexField, new JLabel(MatrixType.DUPLEX.getRuleText()));
        addRow(form, c, row++, "Matriz Multiplex", multiplexField, new JLabel(MatrixType.MULTIPLEX.getRuleText()));
        addRow(form, c, row++, "Matriz Insercao", insercaoField, new JLabel(MatrixType.INSERCAO.getRuleText()));

        addRow(form, c, row++, "FTP host", ftpHostField, new JLabel("Servidor de envio/recebimento"));
        addRow(form, c, row++, "FTP porta", ftpPortField, new JLabel("Padrao 21"));
        addRow(form, c, row++, "FTP usuario", ftpUserField, new JLabel(""));
        addRow(form, c, row++, "FTP senha", ftpPasswordField, new JLabel("Armazenada no arquivo criptografado"));
        addRow(form, c, row++, "FTP pasta envio", ftpSendDirField, new JLabel("Remoto para servico/resposta"));
        addRow(form, c, row++, "FTP pasta baixa", ftpReceiveDirField, new JLabel("Remoto para Recibo/Inconsistencia"));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(button("Processar", e -> processSpreadsheet()));
        actions.add(button("Enviar pendentes", e -> sendPendingServices()));
        actions.add(button("Enviar respostas", e -> sendPendingResponses()));
        actions.add(button("Baixar recibos agora", e -> attemptDownloads(true)));
        actions.add(button("Testar FTP", e -> testFtp()));
        actions.add(pendingLabel);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        add(form, BorderLayout.NORTH);
        add(actions, BorderLayout.CENTER);
        add(new JScrollPane(logArea), BorderLayout.SOUTH);
        ((JScrollPane) getContentPane().getComponent(2)).setPreferredSize(new Dimension(1000, 300));
    }

    private JButton button(String label, java.awt.event.ActionListener listener) {
        JButton button = new JButton(label);
        button.addActionListener(listener);
        return button;
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, java.awt.Component field, java.awt.Component side) {
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);

        c.gridx = 2;
        c.weightx = 0.4;
        panel.add(side, c);
    }

    private void chooseSpreadsheet() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Planilhas", "xls", "xlsx", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            spreadsheetField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void choosePdfFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            pdfFolderField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void processSpreadsheet() {
        Path spreadsheet = pathFromField(spreadsheetField);
        if (spreadsheet == null || !Files.isRegularFile(spreadsheet)) {
            showWarning("Selecione um arquivo .xls, .xlsx ou .csv.");
            return;
        }
        MatrixType matrixType = (MatrixType) matrixTypeCombo.getSelectedItem();
        String matrixNumber = resolveMatrixNumber(matrixType);
        if (matrixNumber == null) {
            return;
        }
        saveStateFromUi();
        runWorker(
            "Processando planilha...",
            () -> processor.process(
                spreadsheet,
                matrixType,
                matrixNumber,
                loteOverrideField.getText(),
                pathFromField(pdfFolderField),
                enviarDir
            ),
            result -> onProcessed(result, matrixType, matrixNumber),
            "Falha ao processar a planilha"
        );
    }

    private void onProcessed(ProcessResult result, MatrixType matrixType, String matrixNumber) {
        PendingTransfer pending = new PendingTransfer();
        pending.id = UUID.randomUUID().toString();
        pending.matrixType = matrixType.name();
        pending.matrixNumber = matrixNumber;
        pending.loteNumber = result.lote;
        pending.baseName = result.baseName;
        pending.serviceZipPath = result.serviceZip.toString();
        pending.responseZipPath = result.responseZip.toString();
        pending.createdAt = System.currentTimeMillis();

        state.lastLoteByMatrix.put(matrixNumber, result.lote);
        state.pendingTransfers.removeIf(item -> item.baseName.equalsIgnoreCase(result.baseName));
        state.pendingTransfers.add(pending);
        sortPending();
        saveStateQuietly();

        appendLog("ZIP servico gerado em " + result.serviceZip);
        appendLog("ZIP resposta gerado em " + result.responseZip);
        appendLog("Pasta de trabalho: " + result.workDir);
        appendLog("PDFs complementares processados: " + result.copiedPdfCount);
        updatePendingLabel();

        int answer = JOptionPane.showConfirmDialog(
            this,
            "Arquivos gerados em ENVIAR.\nDeseja enviar o arquivo de servico agora?",
            "Enviar servico",
            JOptionPane.YES_NO_OPTION
        );
        if (answer == JOptionPane.YES_OPTION) {
            uploadService(pending, true);
        }
    }

    private void sendPendingServices() {
        List<PendingTransfer> pending = state.pendingTransfers.stream()
            .filter(item -> !item.serviceSent)
            .toList();
        if (pending.isEmpty()) {
            showInfo("Nao ha arquivos de servico pendentes.");
            return;
        }
        PendingTransfer selected = choosePending("Selecione o servico para envio", pending);
        if (selected != null) {
            uploadService(selected, true);
        }
    }

    private void sendPendingResponses() {
        List<PendingTransfer> pending = state.pendingTransfers.stream()
            .filter(item -> item.serviceSent && !item.responseSent)
            .toList();
        if (pending.isEmpty()) {
            showInfo("Nao ha arquivos de resposta pendentes.");
            return;
        }
        PendingTransfer selected = choosePending("Selecione a resposta para envio", pending);
        if (selected != null) {
            uploadResponse(selected);
        }
    }

    private void testFtp() {
        saveStateFromUi();
        FtpConfig config;
        try {
            config = buildFtpConfig();
        } catch (Exception e) {
            showWarning(e.getMessage());
            return;
        }
        runWorker(
            "Testando FTP...",
            () -> {
                ftpService.testConnection(config);
                return "Conexao FTP validada com sucesso.";
            },
            message -> {
                appendLog(message);
                showInfo(message);
            },
            "Falha ao testar FTP"
        );
    }

    private void uploadService(PendingTransfer pending, boolean askResponseNow) {
        FtpConfig config;
        try {
            saveStateFromUi();
            config = buildFtpConfig();
        } catch (Exception e) {
            showWarning(e.getMessage());
            return;
        }
        runWorker(
            "Enviando servico...",
            () -> {
                Path local = Path.of(pending.serviceZipPath);
                ftpService.upload(local, local.getFileName().toString(), config);
                Path sentPath = moveToSent(local);
                pending.serviceZipPath = sentPath.toString();
                pending.serviceSent = true;
                pending.serviceSentAt = System.currentTimeMillis();
                pending.nextDownloadAt = pending.serviceSentAt + Duration.ofHours(2).toMillis();
                saveStateQuietly();
                return sentPath;
            },
            sentPath -> {
                appendLog("Servico enviado com sucesso: " + sentPath);
                updatePendingLabel();
                if (askResponseNow) {
                    int answer = JOptionPane.showConfirmDialog(
                        this,
                        "Servico enviado com sucesso.\nDeseja enviar a Resposta agora?",
                        "Enviar Resposta",
                        JOptionPane.YES_NO_OPTION
                    );
                    if (answer == JOptionPane.YES_OPTION) {
                        uploadResponse(pending);
                    }
                }
            },
            "Falha ao enviar arquivo de servico"
        );
    }

    private void uploadResponse(PendingTransfer pending) {
        FtpConfig config;
        try {
            saveStateFromUi();
            config = buildFtpConfig();
        } catch (Exception e) {
            showWarning(e.getMessage());
            return;
        }
        runWorker(
            "Enviando resposta...",
            () -> {
                Path local = Path.of(pending.responseZipPath);
                ftpService.upload(local, local.getFileName().toString(), config);
                Path sentPath = moveToSent(local);
                pending.responseZipPath = sentPath.toString();
                pending.responseSent = true;
                cleanupFinished();
                saveStateQuietly();
                return sentPath;
            },
            sentPath -> {
                appendLog("Resposta enviada com sucesso: " + sentPath);
                updatePendingLabel();
            },
            "Falha ao enviar arquivo de resposta"
        );
    }

    private void attemptDownloads(boolean force) {
        List<PendingTransfer> due = state.pendingTransfers.stream()
            .filter(item -> item.serviceSent && !item.downloadAttempted && (force || item.nextDownloadAt <= System.currentTimeMillis()))
            .toList();
        if (due.isEmpty()) {
            if (force) {
                showInfo("Nao ha arquivos prontos para baixar neste momento.");
            }
            return;
        }
        if (schedulerBusy) {
            return;
        }
        schedulerBusy = true;
        PendingTransfer selected = force ? choosePending("Selecione o servico para baixar Recibo/Inconsistencia", due) : due.get(0);
        if (selected == null) {
            schedulerBusy = false;
            return;
        }
        FtpConfig config;
        try {
            saveStateFromUi();
            config = buildFtpConfig();
        } catch (Exception e) {
            schedulerBusy = false;
            showWarning(e.getMessage());
            return;
        }
        runWorker(
            "Baixando Recibo/Inconsistencia...",
            () -> downloadArtifacts(selected, config),
            summary -> {
                schedulerBusy = false;
                appendLog(summary);
                updatePendingLabel();
                if (selected.serviceSent && !selected.responseSent) {
                    int answer = JOptionPane.showConfirmDialog(
                        this,
                        "Tentativa de baixa concluida.\nDeseja enviar o arquivo de Resposta agora?",
                        "Enviar Resposta",
                        JOptionPane.YES_NO_OPTION
                    );
                    if (answer == JOptionPane.YES_OPTION) {
                        uploadResponse(selected);
                    }
                }
            },
            "Falha ao baixar Recibo/Inconsistencia"
        );
    }

    private String downloadArtifacts(PendingTransfer pending, FtpConfig config) throws Exception {
        Path targetDir = recebidosDir.resolve(DAY_FOLDER.format(LocalDate.now()));
        Files.createDirectories(targetDir);
        String reciboName = pending.baseName + "_Recibo.zip";
        String inconsistenciaName = pending.baseName + "_Inconsistencia.zip";
        boolean recibo = ftpService.download(reciboName, targetDir.resolve(reciboName), config);
        boolean inconsistencia = ftpService.download(inconsistenciaName, targetDir.resolve(inconsistenciaName), config);
        pending.receiptDownloaded = recibo;
        pending.inconsistencyDownloaded = inconsistencia;
        pending.downloadAttempted = true;
        saveStateQuietly();
        return "Baixa do lote " + pending.loteNumber + ": Recibo=" + (recibo ? "OK" : "NAO ENCONTRADO")
            + " | Inconsistencia=" + (inconsistencia ? "OK" : "NAO ENCONTRADA")
            + " | pasta=" + targetDir;
    }

    private void schedulerTick() {
        if (!isDisplayable()) {
            return;
        }
        checkResponseReminders();
        attemptDownloads(false);
    }

    private void checkResponseReminders() {
        long now = System.currentTimeMillis();
        int count = 0;
        boolean changed = false;
        for (PendingTransfer pending : state.pendingTransfers) {
            if (!pending.serviceSent || pending.responseSent || pending.serviceSentAt <= 0) {
                continue;
            }
            count++;
            long elapsed = now - pending.serviceSentAt;
            if (elapsed >= Duration.ofHours(2).toMillis() && !pending.reminder2hShown) {
                pending.reminder2hShown = true;
                changed = true;
            }
            if (elapsed >= Duration.ofHours(24).toMillis() && !pending.reminder24hShown) {
                pending.reminder24hShown = true;
                changed = true;
            }
            if (elapsed >= Duration.ofHours(44).toMillis() && !pending.reminder44hShown) {
                pending.reminder44hShown = true;
                changed = true;
            }
        }
        if (changed) {
            saveStateQuietly();
            updatePendingLabel();
            final int pendingCount = count;
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                this,
                "HA " + pendingCount + " ARQUIVOS RESPOSTA A SEREM ENVIADOS.",
                "Pendencias de Resposta",
                JOptionPane.WARNING_MESSAGE
            ));
        }
    }

    private PendingTransfer choosePending(String title, List<PendingTransfer> items) {
        if (items.isEmpty()) {
            return null;
        }
        Object choice = JOptionPane.showInputDialog(
            this,
            title,
            "Pendencias",
            JOptionPane.PLAIN_MESSAGE,
            null,
            items.stream().map(this::describePending).toArray(),
            describePending(items.get(0))
        );
        if (choice == null) {
            return null;
        }
        String selected = String.valueOf(choice);
        for (PendingTransfer item : items) {
            if (describePending(item).equals(selected)) {
                return item;
            }
        }
        return null;
    }

    private String describePending(PendingTransfer pending) {
        return pending.baseName + " | lote=" + pending.loteNumber
            + " | servico=" + (pending.serviceSent ? "enviado" : "pendente")
            + " | resposta=" + (pending.responseSent ? "enviada" : "pendente");
    }

    private void loadStateIntoFields() {
        ftpHostField.setText(state.ftpHost);
        ftpPortField.setText(String.valueOf(state.ftpPort <= 0 ? 21 : state.ftpPort));
        ftpUserField.setText(state.ftpUser);
        ftpPasswordField.setText(state.ftpPassword);
        ftpSendDirField.setText(state.ftpRemoteSendDir);
        ftpReceiveDirField.setText(state.ftpRemoteReceiveDir);
        simplexField.setText(state.matrixNumbers.getOrDefault(MatrixType.SIMPLEX, ""));
        duplexField.setText(state.matrixNumbers.getOrDefault(MatrixType.DUPLEX, ""));
        multiplexField.setText(state.matrixNumbers.getOrDefault(MatrixType.MULTIPLEX, ""));
        insercaoField.setText(state.matrixNumbers.getOrDefault(MatrixType.INSERCAO, ""));
    }

    private void saveStateFromUi() {
        state.ftpHost = ftpHostField.getText().trim();
        state.ftpPort = safeParsePort(ftpPortField.getText());
        state.ftpUser = ftpUserField.getText().trim();
        state.ftpPassword = new String(ftpPasswordField.getPassword());
        state.ftpRemoteSendDir = ftpSendDirField.getText().trim();
        state.ftpRemoteReceiveDir = ftpReceiveDirField.getText().trim();
        state.matrixNumbers.put(MatrixType.SIMPLEX, simplexField.getText().trim());
        state.matrixNumbers.put(MatrixType.DUPLEX, duplexField.getText().trim());
        state.matrixNumbers.put(MatrixType.MULTIPLEX, multiplexField.getText().trim());
        state.matrixNumbers.put(MatrixType.INSERCAO, insercaoField.getText().trim());
        saveStateQuietly();
    }

    private void saveStateQuietly() {
        try {
            stateStore.save(state);
        } catch (Exception e) {
            appendLog("Falha ao salvar estado criptografado: " + e.getMessage());
        }
    }

    private FtpConfig buildFtpConfig() {
        String host = ftpHostField.getText().trim();
        String user = ftpUserField.getText().trim();
        String password = new String(ftpPasswordField.getPassword());
        if (host.isBlank()) {
            throw new IllegalArgumentException("Informe o host FTP.");
        }
        if (user.isBlank()) {
            throw new IllegalArgumentException("Informe o usuario FTP.");
        }
        return new FtpConfig(
            host,
            safeParsePort(ftpPortField.getText()),
            user,
            password,
            ftpSendDirField.getText().trim(),
            ftpReceiveDirField.getText().trim()
        );
    }

    private String resolveMatrixNumber(MatrixType type) {
        JTextField field = switch (type) {
            case SIMPLEX -> simplexField;
            case DUPLEX -> duplexField;
            case MULTIPLEX -> multiplexField;
            case INSERCAO -> insercaoField;
        };
        String value = field.getText().trim();
        if (!value.isBlank()) {
            return value;
        }
        String last = state.matrixNumbers.getOrDefault(type, "");
        String answer = JOptionPane.showInputDialog(
            this,
            "Informe o numero da matriz para " + type.getLabel() + ":",
            last
        );
        if (answer == null) {
            return null;
        }
        answer = answer.trim();
        if (answer.isBlank()) {
            showWarning("Numero da matriz obrigatorio para executar.");
            return null;
        }
        field.setText(answer);
        state.matrixNumbers.put(type, answer);
        saveStateQuietly();
        return answer;
    }

    private Path moveToSent(Path file) throws Exception {
        Files.createDirectories(enviadosDir);
        Path target = enviadosDir.resolve(file.getFileName());
        if (Files.exists(target)) {
            String stamped = Instant.now().toString().replace(":", "").replace("-", "");
            target = enviadosDir.resolve(stamped + "_" + file.getFileName());
        }
        return Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void ensureBaseDirs() {
        try {
            Files.createDirectories(appHome);
            Files.createDirectories(enviarDir);
            Files.createDirectories(enviadosDir);
            Files.createDirectories(recebidosDir);
        } catch (Exception e) {
            throw new IllegalStateException("Nao foi possivel preparar as pastas da aplicacao.", e);
        }
    }

    private void cleanupFinished() {
        state.pendingTransfers.removeIf(PendingTransfer::isFinished);
    }

    private void sortPending() {
        state.pendingTransfers.sort(Comparator.comparing(item -> item.createdAt));
    }

    private void updatePendingLabel() {
        long services = state.pendingTransfers.stream().filter(item -> !item.serviceSent).count();
        long responses = state.pendingTransfers.stream().filter(item -> item.serviceSent && !item.responseSent).count();
        pendingLabel.setText("Pendencias: servico=" + services + " | resposta=" + responses);
    }

    private Path pathFromField(JTextField field) {
        String text = field.getText().trim();
        return text.isEmpty() ? null : Path.of(text);
    }

    private int safeParsePort(String text) {
        try {
            int port = Integer.parseInt(text.trim());
            return port > 0 ? port : 21;
        } catch (Exception e) {
            return 21;
        }
    }

    private void appendLog(String message) {
        logArea.append(message + System.lineSeparator());
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void showInfo(String message) {
        JOptionPane.showMessageDialog(this, message, "CDIP e-Carta FTP Client", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showWarning(String message) {
        JOptionPane.showMessageDialog(this, message, "CDIP e-Carta FTP Client", JOptionPane.WARNING_MESSAGE);
    }

    private <T> void runWorker(String waitingMessage, CheckedSupplier<T> supplier, java.util.function.Consumer<T> onSuccess, String errorPrefix) {
        appendLog(waitingMessage);
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override
            protected T doInBackground() throws Exception {
                return supplier.get();
            }

            @Override
            protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (Exception e) {
                    appendLog(errorPrefix + ": " + e.getMessage());
                    JOptionPane.showMessageDialog(
                        MainFrame.this,
                        errorPrefix + ":\n" + e.getMessage(),
                        "CDIP e-Carta FTP Client",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }

    @Override
    public void dispose() {
        if (schedulerTimer != null) {
            schedulerTimer.stop();
        }
        saveStateFromUi();
        super.dispose();
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
