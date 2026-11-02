package br.com.cdip.ecartaftp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class ConfigCrypto {
    private static final byte[] MAGIC = "ECARTADB1".getBytes(StandardCharsets.US_ASCII);
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String MASTER_SECRET = part1() + part2() + part3();

    private ConfigCrypto() {
    }

    static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        if (file == null || !Files.exists(file)) {
            return props;
        }
        try {
            byte[] encrypted = Files.readAllBytes(file);
            byte[] plain = decrypt(encrypted);
            try (Reader reader = new InputStreamReader(new ByteArrayInputStream(plain), StandardCharsets.UTF_8)) {
                props.load(reader);
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
            return props;
        } catch (GeneralSecurityException e) {
            throw new IOException("Nao foi possivel descriptografar o arquivo de estado: " + file.getFileName(), e);
        }
    }

    static void save(Path file, Properties props) throws IOException {
        if (file == null) {
            throw new IOException("Arquivo de saida nao informado.");
        }
        Files.createDirectories(file.getParent());
        byte[] plain = null;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             Writer writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            props.store(writer, "CDIP e-Carta FTP Client");
            writer.flush();
            plain = baos.toByteArray();
            byte[] encrypted = encrypt(plain);
            Files.write(file, encrypted);
        } catch (GeneralSecurityException e) {
            throw new IOException("Nao foi possivel criptografar o arquivo de estado: " + file.getFileName(), e);
        } finally {
            if (plain != null) {
                Arrays.fill(plain, (byte) 0);
            }
        }
    }

    private static byte[] encrypt(byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain);
        byte[] out = new byte[MAGIC.length + iv.length + encrypted.length];
        System.arraycopy(MAGIC, 0, out, 0, MAGIC.length);
        System.arraycopy(iv, 0, out, MAGIC.length, iv.length);
        System.arraycopy(encrypted, 0, out, MAGIC.length + iv.length, encrypted.length);
        return out;
    }

    private static byte[] decrypt(byte[] payload) throws GeneralSecurityException {
        if (payload == null || payload.length < MAGIC.length + IV_LENGTH + 1) {
            throw new GeneralSecurityException("Formato invalido do arquivo criptografado.");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (payload[i] != MAGIC[i]) {
                throw new GeneralSecurityException("Cabecalho invalido do arquivo criptografado.");
            }
        }
        byte[] iv = Arrays.copyOfRange(payload, MAGIC.length, MAGIC.length + IV_LENGTH);
        byte[] data = Arrays.copyOfRange(payload, MAGIC.length + IV_LENGTH, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, buildKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
        return cipher.doFinal(data);
    }

    private static SecretKeySpec buildKey() throws GeneralSecurityException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(MASTER_SECRET.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private static String part1() {
        return new String(Base64.getDecoder().decode("Q0RJUCBIVUIjMjAyNg=="), StandardCharsets.UTF_8);
    }

    private static String part2() {
        return new String(Base64.getDecoder().decode("ZUNhcnRh"), StandardCharsets.UTF_8);
    }

    private static String part3() {
        return new String(Base64.getDecoder().decode("IUZUUC1MT0NBTA=="), StandardCharsets.UTF_8);
    }
}
