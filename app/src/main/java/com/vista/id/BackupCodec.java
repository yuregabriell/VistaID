package com.vista.id;

import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypted portable account backup. No plain-text password is exported. */
public final class BackupCodec {
    private static final byte[] AAD = "VistaID-local-backup-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private BackupCodec() {}

    private static byte[] rand(int len) { byte[] b = new byte[len]; RANDOM.nextBytes(b); return b; }
    private static SecretKey key(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(bytes, "AES");
        } finally { spec.clearPassword(); }
    }
    private static String b64(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
    private static byte[] decode(String value) { return Base64.decode(value, Base64.DEFAULT); }

    public static byte[] exportAccount(JSONObject account, String password) throws Exception {
        byte[] salt = rand(16), iv = rand(12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, VistaIdStore.ITERATIONS), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        JSONObject body = new JSONObject().put("format", "vistaid-local").put("account", account);
        byte[] encrypted = cipher.doFinal(body.toString().getBytes(StandardCharsets.UTF_8));
        JSONObject envelope = new JSONObject().put("version", 1).put("type", "vistaid-encrypted-backup")
            .put("iterations", VistaIdStore.ITERATIONS).put("salt", b64(salt)).put("iv", b64(iv))
            .put("data", b64(encrypted));
        return envelope.toString(2).getBytes(StandardCharsets.UTF_8);
    }
    public static JSONObject importAccount(byte[] contents, String password) throws Exception {
        if (contents.length > 512_000) throw new Exception("Backup demasiado grande.");
        JSONObject envelope = new JSONObject(new String(contents, StandardCharsets.UTF_8));
        if (!"vistaid-encrypted-backup".equals(envelope.getString("type")) || envelope.getInt("version") != 1)
            throw new Exception("Formato de backup incompatível.");
        int iterations = envelope.getInt("iterations");
        if (iterations < 100_000 || iterations > 1_000_000) throw new Exception("Parâmetros de segurança inválidos.");
        byte[] salt = decode(envelope.getString("salt"));
        byte[] iv = decode(envelope.getString("iv"));
        if (salt.length != 16 || iv.length != 12) throw new Exception("Backup inválido.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] decrypted = cipher.doFinal(decode(envelope.getString("data")));
        JSONObject body = new JSONObject(new String(decrypted, StandardCharsets.UTF_8));
        if (!"vistaid-local".equals(body.getString("format"))) throw new Exception("Backup incompatível.");
        return body.getJSONObject("account");
    }
}
