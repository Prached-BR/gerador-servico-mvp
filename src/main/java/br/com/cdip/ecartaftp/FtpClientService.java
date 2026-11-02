package br.com.cdip.ecartaftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

final class FtpClientService {
    void testConnection(FtpConfig config) throws IOException {
        FTPClient client = connect(config);
        try {
            if (!config.remoteSendDir().isBlank()) {
                ensureRemoteDirectory(client, config.remoteSendDir());
            }
            if (!config.remoteReceiveDir().isBlank()) {
                ensureRemoteDirectory(client, config.remoteReceiveDir());
            }
        } finally {
            disconnectQuietly(client);
        }
    }

    void upload(Path localFile, String remoteName, FtpConfig config) throws IOException {
        if (localFile == null || !Files.isRegularFile(localFile)) {
            throw new IOException("Arquivo local nao encontrado para envio.");
        }
        FTPClient client = connect(config);
        try (InputStream in = Files.newInputStream(localFile)) {
            changeToRemoteDir(client, config.remoteSendDir());
            if (!client.storeFile(remoteName, in)) {
                throw new IOException("Falha ao enviar arquivo via FTP: " + client.getReplyString());
            }
        } finally {
            disconnectQuietly(client);
        }
    }

    boolean download(String remoteName, Path targetFile, FtpConfig config) throws IOException {
        Files.createDirectories(targetFile.getParent());
        FTPClient client = connect(config);
        try (OutputStream out = Files.newOutputStream(targetFile)) {
            changeToRemoteDir(client, config.remoteReceiveDir());
            boolean ok = client.retrieveFile(remoteName, out);
            if (!ok) {
                Files.deleteIfExists(targetFile);
            }
            return ok;
        } finally {
            disconnectQuietly(client);
        }
    }

    private FTPClient connect(FtpConfig config) throws IOException {
        if (config == null || config.host().isBlank()) {
            throw new IOException("Host FTP nao informado.");
        }
        FTPClient client = new FTPClient();
        client.setConnectTimeout(15000);
        client.setDefaultTimeout(15000);
        client.setDataTimeout(30000);
        client.connect(config.host(), config.port());
        if (!FTPReply.isPositiveCompletion(client.getReplyCode())) {
            disconnectQuietly(client);
            throw new IOException("Conexao FTP recusada: " + client.getReplyString());
        }
        if (!client.login(config.user(), config.password())) {
            disconnectQuietly(client);
            throw new IOException("Falha no login FTP: " + client.getReplyString());
        }
        client.enterLocalPassiveMode();
        client.setFileType(FTP.BINARY_FILE_TYPE);
        return client;
    }

    private void changeToRemoteDir(FTPClient client, String remoteDir) throws IOException {
        if (remoteDir == null || remoteDir.isBlank()) {
            return;
        }
        ensureRemoteDirectory(client, remoteDir);
    }

    private void ensureRemoteDirectory(FTPClient client, String remoteDir) throws IOException {
        if (remoteDir == null || remoteDir.isBlank()) {
            return;
        }
        String normalized = remoteDir.replace('\\', '/');
        String[] parts = normalized.split("/");
        String current = normalized.startsWith("/") ? "/" : "";
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            current = current.endsWith("/") || current.isEmpty() ? current + part : current + "/" + part;
            if (!client.changeWorkingDirectory(current)) {
                if (!client.makeDirectory(current) && !client.changeWorkingDirectory(current)) {
                    throw new IOException("Nao foi possivel criar a pasta remota: " + current);
                }
            }
        }
    }

    private void disconnectQuietly(FTPClient client) {
        try {
            client.logout();
        } catch (Exception ignored) {
        }
        try {
            client.disconnect();
        } catch (Exception ignored) {
        }
    }

    record FtpConfig(String host, int port, String user, String password, String remoteSendDir, String remoteReceiveDir) {
    }
}
