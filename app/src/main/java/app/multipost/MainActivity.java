package app.multipost;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Pantalla principal: elegir video, escribir titulo y descripción, publicar. */
public class MainActivity extends Activity {
    private static final int PICK_VIDEO = 1;

    private Store store;
    private Video video;
    private volatile boolean busy;

    private TextView videoInfo;
    private EditText title;
    private EditText description;
    private CheckBox cbYouTube;
    private CheckBox cbFacebook;
    private CheckBox cbTikTok;
    private Button pick;
    private Button publish;
    private TextView stYouTube;
    private TextView stFacebook;
    private TextView stTikTok;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);

        LinearLayout col = Ui.page(this);
        pick = Ui.button(col, "Elegir video");
        videoInfo = Ui.text(col, "Ningún video elegido.");
        title = Ui.input(col, "Título", false);
        description = Ui.input(col, "Descripción y hashtags", true);

        Ui.heading(col, "Publicar en");
        cbYouTube = check(col, "YouTube");
        stYouTube = Ui.text(col, "");
        cbFacebook = check(col, "Facebook");
        stFacebook = Ui.text(col, "");
        cbTikTok = check(col, "TikTok");
        stTikTok = Ui.text(col, "");

        publish = Ui.button(col, "Publicar");
        Ui.text(col, "Deja la app abierta mientras sube. Los videos se suben uno tras otro.");

        setContentView((View) col.getParent());

        pick.setOnClickListener(v -> pickVideo());
        publish.setOnClickListener(v -> startPublish());
        handleShare(getIntent());
    }

    private CheckBox check(LinearLayout col, String label) {
        CheckBox c = new CheckBox(this);
        c.setText(label);
        c.setTextSize(16);
        Ui.add(col, c);
        return c;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!busy) refreshAccounts();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShare(intent);
    }

    private void refreshAccounts() {
        account(cbYouTube, "YouTube", store.youtubeReady());
        account(cbFacebook, store.facebookReady()
                ? "Facebook (" + store.get(Store.FB_PAGE_NAME, "página") + ")" : "Facebook",
                store.facebookReady());
        account(cbTikTok, "TikTok", store.tiktokReady());
    }

    private void account(CheckBox cb, String label, boolean ready) {
        boolean wasEnabled = cb.isEnabled();
        cb.setEnabled(ready);
        cb.setText(ready ? label : label + " (sin conectar: menú Cuentas)");
        if (!ready) cb.setChecked(false);
        else if (!wasEnabled || cb.getTag() == null) cb.setChecked(true);
        cb.setTag(Boolean.TRUE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Cuentas").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            if (!busy) startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void pickVideo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        try {
            startActivityForResult(i, PICK_VIDEO);
        } catch (RuntimeException e) {
            videoInfo.setText("No hay ninguna app para elegir archivos.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            setVideo(data.getData());
        }
    }

    /** Video compartido desde la galería u otra app ("Compartir" > MultiPost). */
    private void handleShare(Intent intent) {
        if (intent == null || busy || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u != null) setVideo(u);
    }

    private void setVideo(Uri uri) {
        try {
            video = Video.from(this, uri);
            videoInfo.setText(video.name + " - " + video.sizeText());
            stYouTube.setText("");
            stFacebook.setText("");
            stTikTok.setText("");
        } catch (Exception e) {
            video = null;
            videoInfo.setText(String.valueOf(e.getMessage()));
        }
    }

    private void startPublish() {
        if (busy) return;
        if (video == null) {
            videoInfo.setText("Primero elige un video.");
            return;
        }
        final boolean yt = cbYouTube.isChecked();
        final boolean fb = cbFacebook.isChecked();
        final boolean tt = cbTikTok.isChecked();
        if (!yt && !fb && !tt) {
            stYouTube.setText("Marca al menos una plataforma. Si ninguna está conectada, abre Cuentas.");
            return;
        }
        final Video v = video;
        final String t = title.getText().toString().trim();
        final String d = description.getText().toString().trim();

        setBusy(true);
        stYouTube.setText(yt ? "En espera..." : "");
        stFacebook.setText(fb ? "En espera..." : "");
        stTikTok.setText(tt ? "En espera..." : "");

        new Thread(() -> {
            if (yt) {
                try {
                    String id = YouTube.upload(store, v, t, d, progress(stYouTube));
                    show(stYouTube, "Listo: https://youtu.be/" + id);
                } catch (Exception e) {
                    show(stYouTube, "Error: " + e.getMessage());
                }
            }
            if (fb) {
                try {
                    String id = Facebook.upload(store, v, t, d, progress(stFacebook));
                    show(stFacebook, "Listo. Facebook lo está procesando (id " + id + ").");
                } catch (Exception e) {
                    show(stFacebook, "Error: " + e.getMessage());
                }
            }
            if (tt) {
                try {
                    String caption = d.isEmpty() ? t : (t.isEmpty() ? d : t + "\n" + d);
                    String result = TikTok.publish(store, v, caption, progress(stTikTok));
                    show(stTikTok, "Listo: " + result);
                } catch (Exception e) {
                    show(stTikTok, "Error: " + e.getMessage());
                }
            }
            runOnUiThread(() -> setBusy(false));
        }, "publish").start();
    }

    private Http.Progress progress(final TextView target) {
        return new Http.Progress() {
            private int last = -1;

            @Override
            public void on(long sent, long total) {
                int pct = total <= 0 ? 0 : (int) (sent * 100 / total);
                if (pct == last) return;
                last = pct;
                show(target, pct >= 100 ? "Subido. Esperando respuesta..." : "Subiendo " + pct + " %");
            }
        };
    }

    private void show(final TextView target, final String text) {
        runOnUiThread(() -> target.setText(text));
    }

    private void setBusy(boolean b) {
        busy = b;
        publish.setEnabled(!b);
        pick.setEnabled(!b);
        publish.setText(b ? "Publicando..." : "Publicar");
        if (b) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onBackPressed() {
        if (busy) moveTaskToBack(true);
        else super.onBackPressed();
    }
}
