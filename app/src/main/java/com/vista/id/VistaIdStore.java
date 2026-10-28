package com.vista.id;

import android.content.Context;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local-only preview. IDs are not reserved globally. */
public final class VistaIdStore {
    public static final int ITERATIONS = 260_000;
    private static final String CHARSET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final File file;
    private JSONObject data;

    public VistaIdStore(Context context) throws Exception {
        file = new File(context.getFilesDir(), "vista_local_accounts.json");
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(file)) {
                byte[] bytes = new byte[(int) file.length()];
                int off = 0, n;
                while (off < bytes.length && (n = in.read(bytes, off, bytes.length - off)) > 0) off += n;
                if (off != bytes.length) throw new Exception("Ficheiro de conta incompleto");
                data = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            }
        } else {
            data = new JSONObject().put("version", 1).put("accounts", new JSONArray());
        }
    }

    public static boolean validPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) return false;
        boolean lower = false, upper = false, digit = false, symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else if (!Character.isWhitespace(c)) symbol = true;
        }
        return lower && upper && digit && symbol;
    }
    public static String normalizeId(String raw) {
        return raw == null ? "" : raw.replaceAll("[-\\s]", "").toUpperCase(java.util.Locale.ROOT);
    }
    public static String displayId(String id) {
        return id.substring(0, 4) + "-" + id.substring(4, 8) + "-" + id.substring(8);
    }
    private static String newId() {
        StringBuilder out = new StringBuilder("V");
        for (int i = 0; i < 12; i++) out.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        return out.toString();
    }
    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size]; RANDOM.nextBytes(bytes); return bytes;
    }
    private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
    public synchronized JSONObject register(String name, String password) throws Exception {
        String clean = name == null ? "" : name.trim();
        if (clean.length() < 2 || clean.length() > 48) throw new IllegalArgumentException("Usa um nome de 2 a 48 caracteres.");
        if (!validPassword(password)) throw new IllegalArgumentException("A palavra-passe precisa de 8+ caracteres, maiúscula, minúscula, número e símbolo.");
        String id;
        do { id = newId(); } while (find(id) != null);
        byte[] salt = randomBytes(16);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);
        JSONObject account = new JSONObject()
                .put("id", id).put("name", clean).put("createdAt", Long.toString(System.currentTimeMillis()))
                .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .put("hash", Base64.encodeToString(hash, Base64.NO_WRAP))
                .put("iterations", ITERATIONS);
        data.getJSONArray("accounts").put(account);
        persist();
        return account;
    }
    public synchronized JSONObject authenticate(String id, String password) throws Exception {
        JSONObject account = find(normalizeId(id));
        if (account == null || password == null) return null;
        byte[] salt = Base64.decode(account.getString("salt"), Base64.DEFAULT);
        byte[] expected = Base64.decode(account.getString("hash"), Base64.DEFAULT);
        byte[] actual = pbkdf2(password, salt, account.getInt("iterations"));
        return MessageDigest.isEqual(expected, actual) ? account : null;
    }
    public synchronized JSONObject find(String normalizedId) throws JSONException {
        JSONArray accounts = data.getJSONArray("accounts");
        for (int i = 0; i < accounts.length(); i++) {
            JSONObject account = accounts.getJSONObject(i);
            if (normalizedId.equals(account.getString("id"))) return account;
        }
        return null;
    }
    public synchronized void importAccount(JSONObject account) throws Exception {
        String id = normalizeId(account.getString("id"));
        if (!id.matches("[A-Z2-9]{13}") || !id.startsWith("V")) throw new Exception("Vista ID inválido no backup.");
        if (find(id) != null) throw new Exception("Esta conta já existe neste dispositivo.");
        if (account.getString("name").length() > 48 || account.getString("name").length() < 2)
            throw new Exception("Dados de perfil inválidos.");
        int iterations = account.getInt("iterations");
        if (iterations < 100_000 || iterations > 1_000_000) throw new Exception("Parâmetros de segurança inválidos.");
        if (Base64.decode(account.getString("salt"), Base64.DEFAULT).length != 16 ||
            Base64.decode(account.getString("hash"), Base64.DEFAULT).length != 32)
            throw new Exception("Credenciais inválidas no backup.");
        data.getJSONArray("accounts").put(new JSONObject(account.toString()));
        persist();
    }
    private void persist() throws Exception {
        File tmp = new File(file.getParentFile(), "vista_local_accounts.tmp");
        byte[] payload = data.toString().getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(payload); out.getFD().sync();
        }
        if (!tmp.renameTo(file)) {
            if (file.exists() && !file.delete()) throw new Exception("Falha ao atualizar os dados.");
            if (!tmp.renameTo(file)) throw new Exception("Falha ao guardar o perfil.");
        }
    }
}
