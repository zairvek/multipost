package app.multipost;

import android.content.Context;
import android.content.SharedPreferences;

/** Ajustes y credenciales guardados en el almacenamiento privado de la app. */
final class Store {

    // YouTube
    static final String YT_CLIENT_ID = "yt_client_id";
    static final String YT_CLIENT_SECRET = "yt_client_secret";
    static final String YT_REFRESH = "yt_refresh";
    static final String YT_ACCESS = "yt_access";
    static final String YT_ACCESS_EXP = "yt_access_exp";
    static final String YT_PRIVACY = "yt_privacy";

    // Facebook
    static final String FB_APP_ID = "fb_app_id";
    static final String FB_CLIENT_TOKEN = "fb_client_token";
    static final String FB_APP_SECRET = "fb_app_secret";
    static final String FB_PAGE_ID = "fb_page_id";
    static final String FB_PAGE_NAME = "fb_page_name";
    static final String FB_PAGE_TOKEN = "fb_page_token";

    // TikTok
    static final String TT_CLIENT_KEY = "tt_client_key";
    static final String TT_CLIENT_SECRET = "tt_client_secret";
    static final String TT_REDIRECT = "tt_redirect";
    static final String TT_REFRESH = "tt_refresh";
    static final String TT_ACCESS = "tt_access";
    static final String TT_ACCESS_EXP = "tt_access_exp";
    static final String TT_MODE = "tt_mode";
    static final String TT_PRIVACY = "tt_privacy";

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("multipost", Context.MODE_PRIVATE);
    }

    String get(String key) {
        return p.getString(key, "");
    }

    String get(String key, String def) {
        String v = p.getString(key, "");
        return v.isEmpty() ? def : v;
    }

    long getLong(String key) {
        return p.getLong(key, 0L);
    }

    void put(String key, String value) {
        p.edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    void putLong(String key, long value) {
        p.edit().putLong(key, value).apply();
    }

    boolean has(String key) {
        return !get(key).isEmpty();
    }

    boolean youtubeReady() {
        return has(YT_CLIENT_ID) && has(YT_CLIENT_SECRET) && has(YT_REFRESH);
    }

    boolean facebookReady() {
        return has(FB_PAGE_ID) && has(FB_PAGE_TOKEN);
    }

    boolean tiktokReady() {
        return has(TT_CLIENT_KEY) && has(TT_CLIENT_SECRET) && has(TT_REFRESH);
    }
}
