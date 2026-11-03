package com.vista.id;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small no-SDK Supabase HTTPS client. All network calls must run off UI thread. */
final class SupabaseGateway {
    private final Context app;
    SupabaseGateway(Context context) { app = context.getApplicationContext(); }

    static boolean configured() {
        return BuildConfig.VISTA_SUPABASE_URL.startsWith("https://")
            && !BuildConfig.VISTA_SUPABASE_PUBLISHABLE_KEY.isEmpty();
    }
    boolean signedIn() { return EncryptedSession.load(app) != null; }

    private String request(String method, String endpoint, JSONObject payload, String bearer) throws Exception {
        if (!configured()) throw new Exception("Supabase ainda não configurado nesta versão.");
        URL url = new URL(BuildConfig.VISTA_SUPABASE_URL.replaceAll("/+$", "") + endpoint);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new Exception("HTTPS obrigatório");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(18000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("apikey", BuildConfig.VISTA_SUPABASE_PUBLISHABLE_KEY);
            conn.setRequestProperty("Accept", "application/json");
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
            if (payload != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = conn.getOutputStream()) {
                    out.write(payload.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            StringBuilder body = new StringBuilder();
            if (stream != null) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (body.length() > 256_000) throw new Exception("Resposta demasiado grande");
                        body.append(line);
                    }
                }
            }
            if (status >= 400) {
                String message = "Erro de autenticação (" + status + ").";
                try {
                    JSONObject details = new JSONObject(body.toString());
                    message = details.optString("error_description",
                        details.optString("msg", details.optString("message",
                            details.optString("error", message))));
                } catch (Exception ignored) { }
                if (status == 429) message = "Demasiadas tentativas. Tenta novamente mais tarde.";
                throw new Exception(message);
            }
            return body.toString();
        } finally { conn.disconnect(); }
    }
    private JSONObject post(String endpoint, JSONObject payload, String bearer) throws Exception {
        String data = request("POST", endpoint, payload, bearer);
        return data.isEmpty() ? new JSONObject() : new JSONObject(data);
    }
    private JSONArray getRows(String endpoint) throws Exception {
        return new JSONArray(request("GET", endpoint, null, accessToken()));
    }
    private JSONObject save(JSONObject auth) throws Exception {
        if (auth.optString("access_token").isEmpty() || auth.optString("refresh_token").isEmpty())
            throw new Exception("Conta criada, mas o Supabase exige confirmação de e-mail. Desativa 'Confirm email' para o fluxo sem e-mail.");
        if (!auth.has("expires_at")) {
            long expires = (System.currentTimeMillis() / 1000) + auth.optLong("expires_in", 3600);
            auth.put("expires_at", expires);
        }
        EncryptedSession.save(app, auth);
        return auth;
    }
    JSONObject register(String email, String username, String password) throws Exception {
        username = username.trim().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9_]{3,24}")) throw new Exception("Utiliza 3–24 letras, números ou _ no nome de utilizador.");
        if (!email.trim().contains("@") || email.length() > 254) throw new Exception("E-mail inválido.");
        if (password.length() < 10 || password.length() > 128) throw new Exception("A palavra-passe deve ter 10 a 128 caracteres.");
        JSONObject data = new JSONObject().put("username", username);
        return save(post("/auth/v1/signup", new JSONObject()
            .put("email", email.trim().toLowerCase(Locale.ROOT))
            .put("password", password).put("data", data), null));
    }
    JSONObject login(String identifier, String password) throws Exception {
        identifier = identifier.trim().toLowerCase(Locale.ROOT);
        if (password.isEmpty()) throw new Exception("Indica a palavra-passe.");
        JSONObject response;
        if (identifier.contains("@")) {
            response = post("/auth/v1/token?grant_type=password",
                new JSONObject().put("email", identifier).put("password", password), null);
        } else {
            response = action("username_password", new JSONObject()
                .put("username", identifier).put("password", password), false);
        }
        return save(response);
    }
    synchronized String accessToken() throws Exception {
        JSONObject session = EncryptedSession.load(app);
        if (session == null) throw new Exception("Inicia sessão no Vista ID.");
        if (session.optLong("expires_at", 0L) <= (System.currentTimeMillis() / 1000) + 90L) {
            try {
                JSONObject next = post("/auth/v1/token?grant_type=refresh_token",
                    new JSONObject().put("refresh_token", session.getString("refresh_token")), null);
                session = save(next);
            } catch (Exception e) {
                // No silent account deletion on network outages.
                throw new Exception("Não foi possível renovar a sessão: " + e.getMessage());
            }
        }
        return session.getString("access_token");
    }
    void logout() {
        try { request("POST", "/auth/v1/logout", new JSONObject(), accessToken()); }
        catch (Exception ignored) { /* local logout still completes */ }
        EncryptedSession.clear(app);
    }
    JSONObject profile() throws Exception {
        JSONArray rows = getRows("/rest/v1/vid_profiles?select=username,display_name,created_at&limit=1");
        if (rows.length() == 0) throw new Exception("Perfil Vista ID ainda não sincronizado.");
        return rows.getJSONObject(0);
    }
    JSONObject vistaStreamData() throws Exception {
        JSONArray rows = getRows("/rest/v1/vs_user_data?select=settings,favorites,progress,updated_at,connected_at,version&limit=1");
        if (rows.length() == 0) throw new Exception("Dados VistaStream não encontrados.");
        return rows.getJSONObject(0);
    }
    JSONObject action(String action, JSONObject fields, boolean authenticated) throws Exception {
        JSONObject req = fields == null ? new JSONObject() : fields;
        req.put("action", action);
        return post("/functions/v1/vista-auth", req, authenticated ? accessToken() : null);
    }
}
