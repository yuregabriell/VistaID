package com.vista.id;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Vista ID local-first preview. Does not simulate a cloud account. */
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 14, 25);
    private static final int SURFACE = Color.rgb(23, 29, 46);
    private static final int SURFACE_LIGHT = Color.rgb(31, 39, 60);
    private static final int TEXT = Color.rgb(246, 247, 255);
    private static final int MUTED = Color.rgb(159, 170, 194);
    private static final int ACCENT = Color.rgb(135, 143, 255);
    private static final int CREATE_BACKUP = 501;
    private static final int RESTORE_BACKUP = 502;
    private VistaIdStore store;
    private String sessionId;
    private boolean creating = true;
    private boolean working = false;
    private String pendingPassword;
    private String pendingId;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        try {
            store = new VistaIdStore(this);
            sessionId = getPreferences(MODE_PRIVATE).getString("session", null);
            if (sessionId != null && store.find(sessionId) == null) sessionId = null;
        } catch (Exception ex) {
            new AlertDialog.Builder(this).setTitle("Erro no armazenamento local")
                .setMessage("Os dados não foram eliminados. " + ex.getMessage())
                .setPositiveButton("Fechar", (dialog, which) -> finish()).show();
            return;
        }
        render();
    }
    private int dp(float n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private GradientDrawable outline() {
        GradientDrawable d = shape(SURFACE, 18);
        d.setStroke(dp(1), Color.rgb(45, 54, 77)); return d;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void gap(LinearLayout root, int h) {
        View v = new View(this); root.addView(v, new LinearLayout.LayoutParams(1, dp(h)));
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextColor(color); view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setLineSpacing(dp(2), 1.0f); return view;
    }
    private void addText(LinearLayout parent, String value, int size, int color, boolean bold) {
        parent.addView(text(value, size, color, bold));
    }
    private Button button(String title, boolean primary, Runnable action) {
        Button b = new Button(this);
        b.setText(title); b.setTextSize(14); b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : TEXT);
        b.setBackground(shape(primary ? ACCENT : SURFACE_LIGHT, 14));
        b.setMinimumHeight(dp(54));
        b.setPadding(dp(15), dp(11), dp(15), dp(11));
        b.setOnClickListener(v -> { if (!working) action.run(); });
        return b;
    }
    private void fullButton(LinearLayout parent, String title, boolean primary, Runnable action) {
        parent.addView(button(title, primary, action), new LinearLayout.LayoutParams(-1, dp(54)));
    }
    private EditText input(LinearLayout parent, String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setTextSize(15); edit.setSingleLine(true); edit.setTextColor(TEXT);
        edit.setHintTextColor(MUTED); edit.setHint(hint);
        edit.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
            : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setPadding(dp(15), dp(8), dp(15), dp(8));
        edit.setBackground(shape(SURFACE_LIGHT, 12));
        parent.addView(edit, new LinearLayout.LayoutParams(-1, dp(52)));
        return edit;
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout box = vertical(); box.setPadding(dp(18), dp(18), dp(18), dp(18));
        box.setBackground(outline());
        parent.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return box;
    }
    private void section(LinearLayout parent, String heading, String caption) {
        addText(parent, heading, 18, TEXT, true); gap(parent, 6);
        addText(parent, caption, 13, MUTED, false); gap(parent, 18);
    }
    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        LinearLayout root = vertical();
        root.setPadding(dp(22), dp(36), dp(22), dp(42));
        scroll.addView(root); setContentView(scroll);
        TextView brand = text("V", 29, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        brand.setBackground(shape(ACCENT, 19));
        root.addView(brand, new LinearLayout.LayoutParams(dp(62), dp(62)));
        gap(root, 19); addText(root, "Vista ID", 32, TEXT, true); gap(root, 5);
        addText(root, "Uma identidade. Todo o ecossistema Vista.", 14, MUTED, false);
        gap(root, 24);
        if (sessionId == null) drawWelcome(root); else drawDashboard(root);
        gap(root, 28);
        addText(root, "VISTA ID 0.1 · PRÉ-VISUALIZAÇÃO LOCAL", 10, MUTED, true);
    }
    private void drawWelcome(LinearLayout root) {
        LinearLayout info = card(root);
        addText(info, "MODO LOCAL ATIVO", 13, ACCENT, true);
        gap(info, 9);
        addText(info, "Podes criar um perfil neste dispositivo sem internet. Ainda não existe um servidor Vista Cloud ativo: o código NÃO faz login noutros dispositivos.", 13, MUTED, false);
        gap(root, 22);
        LinearLayout switcher = new LinearLayout(this);
        Button create = button("Criar conta", creating, () -> { creating = true; render(); });
        Button signIn = button("Entrar", !creating, () -> { creating = false; render(); });
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(50), 1);
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(50), 1); b.leftMargin = dp(9);
        switcher.addView(create, a); switcher.addView(signIn, b);
        root.addView(switcher); gap(root, 18);
        LinearLayout form = card(root);
        if (creating) {
            section(form, "O teu perfil Vista", "Escolhe um nome e uma palavra-passe para o perfil local.");
            EditText name = input(form, "Nome de perfil", false); gap(form, 11);
            EditText pass = input(form, "Palavra-passe", true); gap(form, 11);
            EditText confirm = input(form, "Confirmar palavra-passe", true); gap(form, 13);
            addText(form, "8+ caracteres com maiúscula, minúscula, número e símbolo.", 12, MUTED, false);
            gap(form, 20);
            fullButton(form, "Criar Vista ID local", true, () -> {
                String n = name.getText().toString(), p = pass.getText().toString();
                if (!p.equals(confirm.getText().toString())) { toast("As palavras-passe não coincidem."); return; }
                execute(() -> store.register(n, p), account -> {
                    persistSession(account.optString("id"));
                    toast("Perfil local criado. Guarda uma cópia de segurança!");
                });
            });
        } else {
            section(form, "Bem-vindo de volta", "Inicia sessão num perfil guardado neste dispositivo.");
            EditText id = input(form, "Vista ID · 13 caracteres", false); gap(form, 11);
            EditText pass = input(form, "Palavra-passe", true); gap(form, 20);
            fullButton(form, "Entrar no perfil", true, () -> execute(() -> {
                JSONObject account = store.authenticate(id.getText().toString(), pass.getText().toString());
                if (account == null) throw new Exception("Código ou palavra-passe incorretos neste dispositivo.");
                return account;
            }, account -> persistSession(account.optString("id"))));
        }
        gap(root, 14);
        fullButton(root, "Restaurar cópia de segurança", false, this::askImportPassword);
    }
    private void drawDashboard(LinearLayout root) {
        final JSONObject account;
        try { account = store.find(sessionId); }
        catch (Exception e) { toast("Erro ao abrir perfil."); return; }
        if (account == null) { persistSession(null); return; }
        LinearLayout profile = card(root);
        addText(profile, "PERFIL LOCAL", 12, ACCENT, true); gap(profile, 8);
        addText(profile, "Olá, " + account.optString("name"), 24, TEXT, true);
        gap(profile, 18);
        addText(profile, "O TEU VISTA ID", 12, MUTED, true); gap(profile, 6);
        String id = account.optString("id");
        addText(profile, VistaIdStore.displayId(id), 20, TEXT, true); gap(profile, 16);
        fullButton(profile, "Copiar código", false, () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Vista ID", id)); toast("Código copiado.");
        });
        gap(root, 16);
        LinearLayout sync = card(root);
        section(sync, "Sincronização cloud", "A futura conta universal ligará VistaStream, VistaBrowser, VistaTube, VistaMusic e VistaLauncher.");
        addText(sync, "Ainda não disponível nesta versão local. Não existe sincronização ou recuperação online.", 13, ACCENT, false);
        gap(root, 16);
        LinearLayout backup = card(root);
        section(backup, "Protege o teu perfil", "Exporta um JSON encriptado com AES-GCM. Precisarás da palavra-passe para importar o ficheiro.");
        fullButton(backup, "Exportar backup encriptado", true, () -> askExportPassword(id));
        gap(root, 16);
        fullButton(root, "Terminar sessão", false, () -> persistSession(null));
    }
    private void persistSession(String id) {
        sessionId = id;
        getPreferences(MODE_PRIVATE).edit().putString("session", id).apply();
        creating = false; render();
    }
    private void askExportPassword(String id) {
        passwordDialog("Confirmar palavra-passe", "Confirma a palavra-passe para gerar um backup encriptado.", pass ->
            execute(() -> {
                JSONObject account = store.authenticate(id, pass);
                if (account == null) throw new Exception("Palavra-passe incorreta.");
                return account;
            }, account -> {
                pendingPassword = pass; pendingId = id;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "vistaid-" + id + ".json");
                startActivityForResult(intent, CREATE_BACKUP);
            }));
    }
    private void askImportPassword() {
        passwordDialog("Restaurar perfil", "Introduz a palavra-passe utilizada na criação do backup.", pass -> {
            pendingPassword = pass;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, RESTORE_BACKUP);
        });
    }
    private void passwordDialog(String title, String caption, Consumer<String> done) {
        EditText edit = new EditText(this);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edit.setHint("Palavra-passe");
        LinearLayout wrapper = vertical(); wrapper.setPadding(dp(24), dp(8), dp(24), 0);
        wrapper.addView(edit);
        new AlertDialog.Builder(this).setTitle(title).setMessage(caption).setView(wrapper)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar", (d, w) -> {
                String pass = edit.getText().toString();
                if (pass.isEmpty()) toast("Indica a palavra-passe."); else done.accept(pass);
            }).show();
    }
    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            pendingPassword = null; pendingId = null; return;
        }
        Uri uri = data.getData();
        String secret = pendingPassword, id = pendingId;
        pendingPassword = null; pendingId = null;
        if (secret == null) return;
        if (req == CREATE_BACKUP) {
            execute(() -> {
                JSONObject account = store.authenticate(id, secret);
                if (account == null) throw new Exception("Sessão inválida. Tenta novamente.");
                byte[] content = BackupCodec.exportAccount(account, secret);
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("Não foi possível escrever o ficheiro.");
                    out.write(content); out.flush();
                }
                return account;
            }, acc -> toast("Backup encriptado guardado."));
        } else if (req == RESTORE_BACKUP) {
            execute(() -> {
                byte[] dataBytes;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new Exception("Não foi possível ler o ficheiro.");
                    byte[] buf = new byte[4096]; int n, count = 0;
                    while ((n = in.read(buf)) != -1) {
                        count += n;
                        if (count > 512_000) throw new Exception("Backup demasiado grande.");
                        out.write(buf, 0, n);
                    }
                    dataBytes = out.toByteArray();
                }
                JSONObject account = BackupCodec.importAccount(dataBytes, secret);
                store.importAccount(account);
                return account;
            }, acc -> { persistSession(acc.optString("id")); toast("Perfil restaurado."); });
        }
    }
    private void execute(Callable<JSONObject> job, Consumer<JSONObject> success) {
        if (working) return;
        working = true;
        new Thread(() -> {
            try {
                JSONObject result = job.call();
                runOnUiThread(() -> { working = false; success.accept(result); });
            } catch (Exception e) {
                runOnUiThread(() -> { working = false; toast(e.getMessage() == null ? "Operação não concluída." : e.getMessage()); });
            }
        }, "VistaId-IO").start();
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
