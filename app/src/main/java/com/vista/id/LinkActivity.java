package com.vista.id;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

/**
 * User-approved, local-only profile linking.
 * Returns public profile information via Android activity result, NOT a login token.
 * The calling app must not treat this as a verified cloud session.
 */
public final class LinkActivity extends Activity {
    private VistaIdStore store;
    private String caller;
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(8), 0, dp(8)); return view;
    }
    private EditText field(String hint, boolean password) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint); edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(Color.LTGRAY);
        edit.setInputType(password ?
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        return edit;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        caller = getCallingPackage();
        // A caller must use startActivityForResult / ActivityResultContracts.
        if (caller == null || caller.isEmpty()) { setResult(RESULT_CANCELED); finish(); return; }
        try { store = new VistaIdStore(this); }
        catch (Exception e) { Toast.makeText(this, "Vista ID indisponível: " + e.getMessage(), Toast.LENGTH_LONG).show(); finish(); return; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 14, 25));
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
        TextView title = label("VISTA ID", 28); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        root.addView(label("Ligar perfil local a outra aplicação", 18));
        root.addView(label("Pedido de: " + caller, 13));
        root.addView(label("Apenas o teu código e nome serão partilhados com esta aplicação, após confirmares a palavra-passe. Histórico, playlists e palavra-passe não são transmitidos.", 14));
        root.addView(label("Não é uma sessão cloud. Funciona apenas neste dispositivo; podes continuar a usar cada app sem conta.", 13));
        EditText id = field("Vista ID (13 caracteres)", false); root.addView(id);
        EditText password = field("Palavra-passe", true); root.addView(password);
        Button confirm = new Button(this); confirm.setText("Autorizar ligação local");
        root.addView(confirm);
        Button cancel = new Button(this); cancel.setText("Cancelar");
        root.addView(cancel);
        cancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        confirm.setOnClickListener(v -> {
            String code = id.getText().toString();
            String secret = password.getText().toString();
            confirm.setEnabled(false);
            new Thread(() -> {
                try {
                    JSONObject profile = store.authenticate(code, secret);
                    if (profile == null) throw new Exception("Código ou palavra-passe inválidos neste dispositivo.");
                    Intent result = new Intent();
                    result.putExtra("com.vista.id.profile_id", profile.getString("id"));
                    result.putExtra("com.vista.id.profile_name", profile.getString("name"));
                    result.putExtra("com.vista.id.mode", "local");
                    runOnUiThread(() -> { setResult(RESULT_OK, result); finish(); });
                } catch (Exception ex) {
                    runOnUiThread(() -> {
                        confirm.setEnabled(true);
                        Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            }, "VistaId-Link").start();
        });
    }
}
