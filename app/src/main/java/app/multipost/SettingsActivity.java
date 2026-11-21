package app.multipost;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pantalla de cuentas: credenciales de cada plataforma y botones para conectar. */
public class SettingsActivity extends Activity {
    private static final int TIKTOK_LOGIN = 2;

    private static final String[] YT_PRIVACY_VALUES = {"public", "unlisted", "private"};
    private static final String[] YT_PRIVACY_LABELS = {"Público", "Oculto (solo con enlace)", "Privado"};
    private static final String[] TT_MODE_VALUES = {"inbox", "direct"};
    private static final String[] TT_MODE_LABELS = {
            "Borrador: llega a mi bandeja de TikTok y lo publico allí",
            "Publicación directa (requiere app auditada para ser pública)"};
    private static final String[] TT_PRIVACY_VALUES = {
            "SELF_ONLY", "PUBLIC_TO_EVERYONE", "FOLLOWER_OF_CREATOR", "MUTUAL_FOLLOW_FRIENDS"};
    private static final String[] TT_PRIVACY_LABELS = {
            "Solo yo", "Todos", "Seguidores", "Amigos"};

    private Store store;
    private volatile boolean alive = true;
    /** Cada intento de conexion tiene un número; los intentos viejos dejan de consultar. */
    private volatile int attempt;

    private EditText ytId;
    private EditText ytSecret;
    private Spinner ytPrivacy;
    private TextView ytStatus;

    private EditText fbAppId;
    private EditText fbClientToken;
    private EditText fbSecret;
    private EditText fbManualToken;
    private TextView fbStatus;

    private EditText ttKey;
    private EditText ttSecret;
    private EditText ttRedirect;
    private Spinner ttMode;
    private Spinner ttPrivacy;
    private TextView ttStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        LinearLayout col = Ui.page(this);

        Ui.text(col, "Los datos se guardan solo en este teléfono. La guía README explica "
                + "de dónde sale cada uno.");

        // ---- YouTube ----
        Ui.heading(col, "YouTube");
        ytId = Ui.input(col, "Client ID (tipo: TVs and Limited Input devices)", false);
        ytSecret = Ui.input(col, "Client secret", false);
        Ui.text(col, "Visibilidad de los videos");
        ytPrivacy = Ui.spinner(col, YT_PRIVACY_LABELS);
        Button ytConnect = Ui.button(col, "Conectar YouTube");
        ytStatus = Ui.text(col, "");

        // ---- Facebook ----
        Ui.heading(col, "Facebook (página)");
        fbAppId = Ui.input(col, "App ID", false);
        fbClientToken = Ui.input(col, "Client token (Configuración > Avanzada)", false);
        Button fbConnect = Ui.button(col, "Conectar Facebook");
        Ui.text(col, "Alternativa: pega un token de usuario del Explorador de la Graph API. "
                + "Con la clave secreta de la app el acceso no caduca.");
        fbSecret = Ui.input(col, "App secret (opcional)", false);
        fbManualToken = Ui.input(col, "Token de usuario (opcional)", false);
        Button fbUseToken = Ui.button(col, "Usar token pegado");
        fbStatus = Ui.text(col, "");

        // ---- TikTok ----
        Ui.heading(col, "TikTok");
        ttKey = Ui.input(col, "Client key", false);
        ttSecret = Ui.input(col, "Client secret", false);
        ttRedirect = Ui.input(col, "Redirect URI registrada (https://...)", false);
        Ui.text(col, "Modo");
        ttMode = Ui.spinner(col, TT_MODE_LABELS);
        Ui.text(col, "Visibilidad en publicación directa");
        ttPrivacy = Ui.spinner(col, TT_PRIVACY_LABELS);
        Button ttConnect = Ui.button(col, "Conectar TikTok");
        ttStatus = Ui.text(col, "");

        setContentView((View) col.getParent());
        load();

        ytConnect.setOnClickListener(v -> connectYouTube());
        fbConnect.setOnClickListener(v -> connectFacebook());
        fbUseToken.setOnClickListener(v -> useFacebookToken());
        ttConnect.setOnClickListener(v -> connectTikTok());
    }

    private void load() {
        ytId.setText(store.get(Store.YT_CLIENT_ID));
        ytSecret.setText(store.get(Store.YT_CLIENT_SECRET));
        ytPrivacy.setSelection(Ui.indexOf(YT_PRIVACY_VALUES, store.get(Store.YT_PRIVACY, "public")));
        fbAppId.setText(store.get(Store.FB_APP_ID));
        fbClientToken.setText(store.get(Store.FB_CLIENT_TOKEN));
        fbSecret.setText(store.get(Store.FB_APP_SECRET));
        ttKey.setText(store.get(Store.TT_CLIENT_KEY));
        ttSecret.setText(store.get(Store.TT_CLIENT_SECRET));
        ttRedirect.setText(store.get(Store.TT_REDIRECT));
        ttMode.setSelection(Ui.indexOf(TT_MODE_VALUES, store.get(Store.TT_MODE, "inbox")));
        ttPrivacy.setSelection(Ui.indexOf(TT_PRIVACY_VALUES, store.get(Store.TT_PRIVACY, "SELF_ONLY")));
        refreshStatus();
    }

    private void save() {
        // Si cambian las credenciales de una plataforma, el acceso anterior deja de valer.
        if (!text(ytId).equals(store.get(Store.YT_CLIENT_ID))) {
            store.put(Store.YT_REFRESH, "");
            store.put(Store.YT_ACCESS, "");
        }
        if (!text(ttKey).equals(store.get(Store.TT_CLIENT_KEY))) {
            store.put(Store.TT_REFRESH, "");
            store.put(Store.TT_ACCESS, "");
        }
        store.put(Store.YT_CLIENT_ID, text(ytId));
        store.put(Store.YT_CLIENT_SECRET, text(ytSecret));
        store.put(Store.YT_PRIVACY, YT_PRIVACY_VALUES[pos(ytPrivacy, YT_PRIVACY_VALUES.length)]);
        store.put(Store.FB_APP_ID, text(fbAppId));
        store.put(Store.FB_CLIENT_TOKEN, text(fbClientToken));
        store.put(Store.FB_APP_SECRET, text(fbSecret));
        store.put(Store.TT_CLIENT_KEY, text(ttKey));
        store.put(Store.TT_CLIENT_SECRET, text(ttSecret));
        store.put(Store.TT_REDIRECT, text(ttRedirect));
        store.put(Store.TT_MODE, TT_MODE_VALUES[pos(ttMode, TT_MODE_VALUES.length)]);
        store.put(Store.TT_PRIVACY, TT_PRIVACY_VALUES[pos(ttPrivacy, TT_PRIVACY_VALUES.length)]);
    }

    private static String text(EditText e) {
        return e.getText().toString().trim();
    }

    private static int pos(Spinner s, int count) {
        int p = s.getSelectedItemPosition();
        return p < 0 || p >= count ? 0 : p;
    }

    private void refreshStatus() {
        ytStatus.setText(store.youtubeReady() ? "Conectado." : "Sin conectar.");
        fbStatus.setText(store.facebookReady()
                ? "Conectado a la página: " + store.get(Store.FB_PAGE_NAME, store.get(Store.FB_PAGE_ID))
                : "Sin conectar.");
        ttStatus.setText(store.tiktokReady() ? "Conectado." : "Sin conectar.");
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private void ui(Runnable r) {
        runOnUiThread(() -> {
            if (alive) r.run();
        });
    }

    private void set(final TextView t, final String s) {
        ui(() -> t.setText(s));
    }

    /** Muestra el código, lo copia y ofrece abrir la página donde se escribe. */
    private void showCode(String service, final String code, final String url) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("código", code));
        new AlertDialog.Builder(this)
                .setTitle("Código para " + service)
                .setMessage(code + "\n\nYa está copiado. Abre la página, pégalo y acepta los "
                        + "permisos. Luego vuelve a esta app.")
                .setPositiveButton("Abrir página", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (RuntimeException ignored) {
                        // Sin navegador: el usuario puede abrir la dirección a mano.
                    }
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    // ---------------- YouTube ----------------

    private void connectYouTube() {
        save();
        if (text(ytId).isEmpty() || text(ytSecret).isEmpty()) {
            ytStatus.setText("Escribe el Client ID y el Client secret.");
            return;
        }
        final int my = ++attempt;
        ytStatus.setText("Pidiendo código a Google...");
        new Thread(() -> {
            try {
                JSONObject j = YouTube.startDevice(store);
                final String userCode = j.optString("user_code");
                final String url = j.optString("verification_url", "https://www.google.com/device");
                final String deviceCode = j.optString("device_code");
                long interval = Math.max(5, j.optLong("interval", 5));
                long deadline = System.currentTimeMillis() + j.optLong("expires_in", 1800) * 1000L;
                ui(() -> {
                    ytStatus.setText("Código: " + userCode + "\nEscribelo en " + url
                            + "\nEsperando a que aceptes...");
                    showCode("YouTube", userCode, url);
                });
                while (alive && attempt == my && System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);
                    int r = YouTube.pollDevice(store, deviceCode);
                    if (r == YouTube.DONE) {
                        set(ytStatus, "Conectado.");
                        return;
                    }
                    if (r == YouTube.SLOW_DOWN) interval += 5;
                }
                if (attempt == my) set(ytStatus, "El código caducó. Vuelve a intentarlo.");
            } catch (Exception e) {
                set(ytStatus, "Error: " + e.getMessage());
            }
        }, "yt-connect").start();
    }

    // ---------------- Facebook ----------------

    private void connectFacebook() {
        save();
        if (text(fbAppId).isEmpty() || text(fbClientToken).isEmpty()) {
            fbStatus.setText("Escribe el App ID y el Client token.");
            return;
        }
        final int my = ++attempt;
        fbStatus.setText("Pidiendo código a Facebook...");
        new Thread(() -> {
            try {
                JSONObject j = Facebook.startDevice(store);
                final String userCode = j.optString("user_code");
                final String url = j.optString("verification_uri", "https://www.facebook.com/device");
                final String code = j.optString("code");
                long interval = Math.max(5, j.optLong("interval", 5));
                long deadline = System.currentTimeMillis() + j.optLong("expires_in", 420) * 1000L;
                ui(() -> {
                    fbStatus.setText("Código: " + userCode + "\nEscribelo en " + url
                            + "\nEsperando a que aceptes...");
                    showCode("Facebook", userCode, url);
                });
                String[] token = new String[1];
                while (alive && attempt == my && System.currentTimeMillis() < deadline) {
                    Thread.sleep(interval * 1000L);
                    int r = Facebook.pollDevice(store, code, token);
                    if (r == Facebook.DONE) {
                        choosePage(token[0]);
                        return;
                    }
                    if (r == Facebook.SLOW_DOWN) interval += 5;
                }
                if (attempt == my) set(fbStatus, "El código caducó. Vuelve a intentarlo.");
            } catch (Exception e) {
                set(fbStatus, "Error: " + e.getMessage());
            }
        }, "fb-connect").start();
    }

    private void useFacebookToken() {
        save();
        final String pasted = text(fbManualToken);
        if (pasted.isEmpty()) {
            fbStatus.setText("Pega primero el token.");
            return;
        }
        fbStatus.setText("Comprobando token...");
        new Thread(() -> {
            try {
                String token = pasted;
                if (store.has(Store.FB_APP_ID) && store.has(Store.FB_APP_SECRET)) {
                    token = Facebook.longLived(store, token);
                }
                choosePage(token);
            } catch (Exception e) {
                set(fbStatus, "Error: " + e.getMessage());
            }
        }, "fb-token").start();
    }

    /** Se llama desde un hilo de fondo. */
    private void choosePage(String userToken) throws Exception {
        final JSONArray pages = Facebook.pages(userToken);
        if (pages.length() == 0) {
            set(fbStatus, "Ese acceso no incluye ninguna página. Al aceptar los permisos, "
                    + "marca la página donde quieres publicar.");
            return;
        }
        if (pages.length() == 1) {
            savePage(pages.optJSONObject(0));
            return;
        }
        final String[] names = new String[pages.length()];
        for (int i = 0; i < names.length; i++) {
            JSONObject p = pages.optJSONObject(i);
            names[i] = p == null ? "?" : p.optString("name");
        }
        ui(() -> new AlertDialog.Builder(this)
                .setTitle("Elige la página")
                .setItems(names, (d, which) -> savePage(pages.optJSONObject(which)))
                .show());
    }

    private void savePage(JSONObject page) {
        if (page == null) return;
        store.put(Store.FB_PAGE_ID, page.optString("id"));
        store.put(Store.FB_PAGE_NAME, page.optString("name"));
        store.put(Store.FB_PAGE_TOKEN, page.optString("access_token"));
        ui(() -> {
            fbManualToken.setText("");
            refreshStatus();
        });
    }

    // ---------------- TikTok ----------------

    private void connectTikTok() {
        save();
        if (text(ttKey).isEmpty() || text(ttSecret).isEmpty() || text(ttRedirect).isEmpty()) {
            ttStatus.setText("Escribe el Client key, el Client secret y la Redirect URI.");
            return;
        }
        startActivityForResult(new Intent(this, TikTokLoginActivity.class), TIKTOK_LOGIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TIKTOK_LOGIN) refreshStatus();
    }
}
