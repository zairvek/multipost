package app.multipost;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Inicio de sesión de TikTok dentro de la app. Cuando TikTok redirige a la URL registrada,
 * se toma el código y se cambia por los tokens. Si la página no carga bien aquí, se puede
 * abrir en el navegador y pegar la URL final.
 */
public class TikTokLoginActivity extends Activity {
    private Store store;
    private String state;
    private TextView status;
    private volatile boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        state = "mp" + Long.toHexString(new java.security.SecureRandom().nextLong());
        final String redirect = store.get(Store.TT_REDIRECT);
        final String authUrl = TikTok.authUrl(store, state);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 8);
        col.setPadding(p, p, p, p);

        status = Ui.text(col, "Inicia sesión en TikTok y acepta los permisos.");
        Button browser = Ui.button(col, "¿No carga? Abrir en el navegador");
        final EditText pasted = Ui.input(col, "Pega aquí la URL final del navegador", false);
        Button use = Ui.button(col, "Usar URL pegada");

        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!redirect.isEmpty() && url.startsWith(redirect)) {
                    finishWith(request.getUrl());
                    return true;
                }
                return false;
            }
        });
        col.addView(web, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(col);

        browser.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)));
                status.setText("Al terminar, copia la dirección de la página a la que llegues "
                        + "y pégala aquí.");
            } catch (RuntimeException e) {
                status.setText("No se pudo abrir el navegador.");
            }
        });
        use.setOnClickListener(v -> {
            String s = pasted.getText().toString().trim();
            if (s.isEmpty()) return;
            if (s.startsWith("http")) finishWith(Uri.parse(s));
            else exchange(s);
        });

        web.loadUrl(authUrl);
    }

    private void finishWith(Uri uri) {
        String code = uri.getQueryParameter("code");
        String error = uri.getQueryParameter("error");
        String st = uri.getQueryParameter("state");
        if (code == null || code.isEmpty()) {
            status.setText("TikTok no dio acceso"
                    + (error == null ? "." : ": " + error + " "
                    + String.valueOf(uri.getQueryParameter("error_description"))));
            return;
        }
        if (st != null && !st.equals(state)) {
            status.setText("La respuesta no corresponde a este intento. Vuelve a empezar.");
            return;
        }
        exchange(code);
    }

    private void exchange(final String code) {
        if (done) return;
        done = true;
        status.setText("Guardando acceso...");
        new Thread(() -> {
            try {
                TikTok.exchange(store, code);
                runOnUiThread(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                done = false;
                final String msg = String.valueOf(e.getMessage());
                runOnUiThread(() -> status.setText("Error: " + msg));
            }
        }, "tiktok-token").start();
    }
}
