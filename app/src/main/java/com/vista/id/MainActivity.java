package com.vista.id;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Vista ID 1.0: real Supabase account, VS management and approved QR sign-in. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 13, 26);
    private static final int PANEL = Color.rgb(21, 28, 47);
    private static final int FIELD = Color.rgb(29, 37, 59);
    private static final int TEXT = Color.rgb(242, 245, 255);
    private static final int MUTED = Color.rgb(159, 172, 196);
    private static final int ACCENT = Color.rgb(145, 128, 255);
    private static final int SUCCESS = Color.rgb(94, 218, 175);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SupabaseGateway gateway;
    private boolean creating = false;
    private boolean working = false;
    private boolean authenticated = false;
    private boolean codeLoading = false;
    private long lastCodeStep = -1;
    private String username = "";
    private JSONObject profile;
    private TextView codeText, codeTimer, vsStatus;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (gateway != null && authenticated && codeText != null) {
                long seconds = System.currentTimeMillis() / 1000;
                if (codeTimer != null) codeTimer.setText("Renova em " + (30 - (seconds % 30)) + " s");
                long step = seconds / 30;
                if (step != lastCodeStep && !codeLoading) {
                    codeLoading = true;
                    codeText.setText("A sincronizar...");
                    new Thread(() -> {
                        try {
                            JSONObject current = gateway.action("code_now", new JSONObject(), true);
                            String value = current.getString("code");
                            runOnUiThread(() -> {
                                codeLoading = false;
                                lastCodeStep = System.currentTimeMillis() / 30000;
                                if (codeText != null) codeText.setText(value);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                codeLoading = false;
                                lastCodeStep = System.currentTimeMillis() / 30000;
                                if (codeText != null) codeText.setText("Indisponível offline");
                            });
                        }
                    }, "VistaID-Code").start();
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        gateway = new SupabaseGateway(this);
        authenticated = gateway.signedIn();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        render();
        if (authenticated) refreshProfile();
    }
    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(tick); handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick); super.onPause(); }

    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int background, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(background);
        d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void space(LinearLayout root, int h) {
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
    }
    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size);
        t.setTextColor(color); t.setLineSpacing(dp(3), 1.0f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }
    private LinearLayout card(LinearLayout root) {
        LinearLayout l = column();
        l.setPadding(dp(20), dp(20), dp(20), dp(20));
        l.setBackground(shape(PANEL, 19));
        root.addView(l, new LinearLayout.LayoutParams(-1, -2));
        return l;
    }
    private void title(LinearLayout root, String headline, String subtitle) {
        root.addView(label(headline, 20, TEXT, true));
        space(root, 6); root.addView(label(subtitle, 13, MUTED, false)); space(root, 18);
    }
    private EditText field(LinearLayout parent, String hint, boolean password) {
        EditText e = new EditText(this); e.setHint(hint);
        e.setTextSize(15); e.setSingleLine(true); e.setTextColor(TEXT);
        e.setHintTextColor(MUTED);
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setPadding(dp(15), 0, dp(15), 0);
        e.setBackground(shape(FIELD, 12));
        parent.addView(e, new LinearLayout.LayoutParams(-1, dp(53)));
        return e;
    }
    private Button button(LinearLayout root, String text, boolean accent, Runnable onClick) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false);
        b.setTextSize(14); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(accent ? BG : TEXT);
        b.setBackground(shape(accent ? ACCENT : FIELD, 12));
        b.setOnClickListener(v -> { if (!working) onClick.run(); });
        root.addView(b, new LinearLayout.LayoutParams(-1, dp(54)));
        return b;
    }
    private void render() {
        lastCodeStep = -1;
        codeText = null; codeTimer = null; vsStatus = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(23), dp(34), dp(23), dp(42));
        scroll.addView(root); setContentView(scroll);
        TextView mark = label("V", 30, Color.WHITE, true);
        mark.setBackground(shape(ACCENT, 18)); mark.setGravity(Gravity.CENTER);
        root.addView(mark, new LinearLayout.LayoutParams(dp(60), dp(60)));
        space(root, 17);
        root.addView(label("Vista ID", 32, TEXT, true));
        space(root, 5);
        root.addView(label("Uma conta. Todos os teus apps Vista.", 14, MUTED, false));
        space(root, 26);
        if (!SupabaseGateway.configured()) {
            LinearLayout box = card(root);
            title(box, "Ligar ao Supabase", "Este APK ainda não recebeu a URL e a chave pública do projeto Supabase. Nenhum login é simulado.");
            box.addView(label("Configura os secrets VISTA_SUPABASE_URL e VISTA_SUPABASE_PUBLISHABLE_KEY no GitHub, aplica a migração SQL e publica a Edge Function vista-auth.", 13, MUTED, false));
        } else if (!authenticated) {
            loginUi(root);
        } else {
            dashboardUi(root);
        }
        space(root, 28);
        root.addView(label("VISTA ID · SUPABASE AUTH", 11, MUTED, true));
    }
    private void loginUi(LinearLayout root) {
        LinearLayout tabs = column();
        button(tabs, creating ? "Já tenho conta · Entrar" : "Ainda não tenho conta · Criar Vista ID",
            false, () -> { creating = !creating; render(); });
        root.addView(tabs); space(root, 18);
        LinearLayout form = card(root);
        title(form, creating ? "Criar a tua identidade" : "Bem-vindo de volta",
            creating ? "O nome de utilizador será único em todos os apps Vista." :
            "Entra com e-mail ou nome de utilizador e palavra-passe.");
        EditText usernameOrEmail = field(form, creating ? "Nome de utilizador" : "E-mail ou nome de utilizador", false);
        space(form, 12);
        EditText email = creating ? field(form, "E-mail (não enviamos códigos)", false) : null;
        if (creating) space(form, 12);
        EditText password = field(form, "Palavra-passe", true);
        EditText confirm = null;
        if (creating) { space(form, 12); confirm = field(form, "Repetir palavra-passe", true); }
        space(form, 17);
        if (creating) {
            form.addView(label("Mínimo de 10 caracteres. Sem códigos por e-mail; guarda a tua palavra-passe, pois a recuperação automática não está ativa.", 12, MUTED, false));
            space(form, 16);
            final EditText check = confirm;
            button(form, "Criar Vista ID", true, () -> {
                String pw = password.getText().toString();
                if (!pw.equals(check.getText().toString())) { toast("As palavras-passe não coincidem."); return; }
                execute(() -> gateway.register(email.getText().toString(),
                    usernameOrEmail.getText().toString(), pw), result -> signedIn());
            });
        } else {
            button(form, "Entrar com segurança", true, () ->
                execute(() -> gateway.login(usernameOrEmail.getText().toString(),
                    password.getText().toString()), result -> signedIn()));
        }
    }
    private void signedIn() {
        authenticated = true;
        creating = false; lastCodeStep = -1; profile = null; username = "";
        render(); refreshProfile();
    }
    private void dashboardUi(LinearLayout root) {
        LinearLayout overview = card(root);
        overview.addView(label("A TUA IDENTIDADE", 12, ACCENT, true)); space(overview, 9);
        overview.addView(label(username.isEmpty() ? "Conta Vista" : "@" + username, 25, TEXT, true));
        space(overview, 11);
        overview.addView(label("Sessão protegida pelo Supabase Auth. A tua palavra-passe nunca é guardada pelo Vista ID.", 13, MUTED, false));
        space(overview, 16);
        button(overview, "Editar nome apresentado", false, this::editDisplayName);
        space(overview, 9);
        button(overview, "Alterar palavra-passe", false, this::editPassword);
        space(overview, 9);
        button(overview, "Atualizar perfil", false, this::refreshProfile);
        space(root, 16);

        LinearLayout connected = card(root);
        connected.addView(label("APLICAÇÕES VISTA", 12, ACCENT, true));
        space(connected, 12);
        connected.addView(label("VistaStream", 22, TEXT, true));
        space(connected, 6);
        vsStatus = label("A verificar ligação...", 13, MUTED, false);
        connected.addView(vsStatus); space(connected, 16);
        button(connected, "Gerir conta e ver dados do VS", true, this::showVsData);
        space(connected, 9);
        button(connected, "Limpar dados sincronizados do VS", false, this::confirmResetVs);
        space(root, 16);

        LinearLayout authenticator = card(root);
        title(authenticator, "Autenticador Vista", "Código de 8 caracteres, renovado a cada 30 segundos. Introduz com o teu nome de utilizador no VS.");
        codeText = label("A sincronizar...", 29, SUCCESS, true);
        codeText.setGravity(Gravity.CENTER);
        authenticator.addView(codeText);
        space(authenticator, 5);
        codeTimer = label("Renova em 30 s", 13, MUTED, false);
        codeTimer.setGravity(Gravity.CENTER);
        authenticator.addView(codeTimer);
        space(authenticator, 16);
        button(authenticator, "Ler QR Code da televisão", true, this::scanQr);
        space(authenticator, 10);
        authenticator.addView(label("Confirma sempre que o código apresentado na televisão é igual ao código da aprovação. Nunca aproves pedidos desconhecidos.", 12, MUTED, false));
        space(root, 18);

        button(root, "Terminar sessão", false, () -> execute(() -> {
            gateway.logout(); return new JSONObject();
        }, unused -> { authenticated = false; username = ""; profile = null; lastCodeStep = -1; render(); }));
        refreshVsStatus();
    }
    private void refreshProfile() {
        execute(() -> gateway.profile(), data -> {
            profile = data;
            username = data.optString("username", "");
            render();
        });
    }
    private void refreshVsStatus() {
        TextView current = vsStatus;
        if (current == null) return;
        new Thread(() -> {
            try {
                JSONObject data = gateway.vistaStreamData();
                String state = data.isNull("connected_at") || data.optString("connected_at").isEmpty()
                    ? "Disponível para ligar ao VistaStream"
                    : "Conectado · última ligação " + data.optString("connected_at", "").replace("T", " ").replace("Z", " UTC");
                runOnUiThread(() -> { if (vsStatus == current) current.setText(state); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (vsStatus == current) current.setText("Não foi possível consultar o VS"); });
            }
        }, "VistaID-VSStatus").start();
    }
    private void editDisplayName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(profile == null ? username : profile.optString("display_name", username));
        new AlertDialog.Builder(this).setTitle("Nome apresentado")
            .setMessage("O teu @nome de utilizador continua igual.")
            .setView(input).setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, which) ->
                execute(() -> gateway.action("profile_update",
                    new JSONObject().put("display_name", input.getText().toString()), true),
                    result -> refreshProfile())).show();
    }
    private void editPassword() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Nova palavra-passe (10+ caracteres)");
        new AlertDialog.Builder(this).setTitle("Alterar palavra-passe")
            .setView(input).setNegativeButton("Cancelar", null)
            .setPositiveButton("Guardar", (d, which) ->
                execute(() -> {
                    gateway.changePassword(input.getText().toString());
                    return new JSONObject();
                }, result -> toast("Palavra-passe atualizada."))).show();
    }
    private void confirmResetVs() {
        new AlertDialog.Builder(this).setTitle("Eliminar dados do VistaStream?")
            .setMessage("Esta ação elimina favoritos, progresso e preferências sincronizadas na nuvem. Não apaga os dados locais que ainda não foram sincronizados. Não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar dados", (d, which) -> execute(
                () -> gateway.action("vs_reset",
                    new JSONObject().put("confirm", "DELETE_MY_VS_DATA"), true),
                result -> { toast("Dados do VistaStream limpos."); refreshVsStatus(); }))
            .show();
    }
    private void showVsData() {
        execute(() -> gateway.vistaStreamData(), data -> {
            int favorites = data.optJSONArray("favorites") == null ? 0 : data.optJSONArray("favorites").length();
            int progress = data.optJSONObject("progress") == null ? 0 : data.optJSONObject("progress").length();
            String details = "Favoritos: " + favorites + "\n" +
                "Títulos com progresso: " + progress + "\n" +
                "Versão dos dados: " + data.optLong("version", 1) + "\n" +
                "Última atualização: " + data.optString("updated_at", "—") + "\n\n" +
                "Preferências:\n" + data.optJSONObject("settings") + "\n\n" +
                "Para sincronizar o histórico, o VistaStream utilizará esta conta com as mesmas políticas RLS.";
            new AlertDialog.Builder(this).setTitle("Dados · VistaStream")
                .setMessage(details).setPositiveButton("Fechar", null).show();
        });
    }
    private void scanQr() {
        new IntentIntegrator(this)
            .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
            .setPrompt("Lê o QR Code do VistaStream")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
            .initiateScan();
    }
    @Override protected void onActivityResult(int req, int result, android.content.Intent data) {
        IntentResult scan = IntentIntegrator.parseActivityResult(req, result, data);
        if (scan != null) {
            if (scan.getContents() != null) reviewQr(scan.getContents());
            return;
        }
        super.onActivityResult(req, result, data);
    }
    private void reviewQr(String raw) {
        Uri uri;
        try { uri = Uri.parse(raw); } catch (Exception e) { toast("QR inválido"); return; }
        if (!"vistaid".equals(uri.getScheme()) || !"pair".equals(uri.getHost())) {
            toast("Este QR não pertence ao Vista ID."); return;
        }
        String id = uri.getQueryParameter("request");
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) { toast("Pedido inválido"); return; }
        execute(() -> gateway.action("pair_details", new JSONObject().put("request_id", id), false), details -> {
            if (!"vs".equals(details.optString("app_id"))) { toast("Aplicação desconhecida."); return; }
            new AlertDialog.Builder(this)
                .setTitle("Autorizar VistaStream?")
                .setMessage("Confirma que a tua televisão mostra este código:\n\n"
                    + details.optString("verification_code") +
                    "\n\nA sessão será ligada à tua conta Vista ID.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Autorizar ligação", (d, which) ->
                    execute(() -> gateway.action("pair_approve",
                        new JSONObject().put("request_id", id), true),
                        response -> {
                            toast("VistaStream autorizado com sucesso.");
                            refreshVsStatus();
                        })).show();
        });
    }
    private void execute(Callable<JSONObject> task, Consumer<JSONObject> done) {
        if (working) return;
        working = true;
        new Thread(() -> {
            try {
                JSONObject result = task.call();
                runOnUiThread(() -> {
                    working = false;
                    if (!isFinishing()) done.accept(result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    working = false;
                    if (!isFinishing()) new AlertDialog.Builder(this)
                        .setTitle("Não foi possível concluir")
                        .setMessage(e.getMessage() == null ? "Verifica a ligação e tenta novamente." : e.getMessage())
                        .setPositiveButton("OK", null).show();
                });
            }
        }, "VistaID-Network").start();
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
