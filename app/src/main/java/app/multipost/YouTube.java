package app.multipost;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * YouTube Data API v3.
 * Acceso: flujo OAuth "TVs and Limited Input devices" (código que se escribe en google.com/device).
 * Subida: videos.insert con uploadType=resumable.
 */
final class YouTube {
    private static final String DEVICE_URL = "https://oauth2.googleapis.com/device/code";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube";
    private static final String UPLOAD_URL =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";

    static final int PENDING = 0;
    static final int DONE = 1;
    static final int SLOW_DOWN = 2;

    private YouTube() {
    }

    /** Devuelve device_code, user_code, verification_url, expires_in, interval. */
    static JSONObject startDevice(Store s) throws IOException {
        Http.Res r = Http.postForm(DEVICE_URL,
                Http.form("client_id", s.get(Store.YT_CLIENT_ID), "scope", SCOPE));
        if (!r.ok()) throw Http.fail("Google no aceptó la solicitud de código", r);
        return r.json();
    }

    static int pollDevice(Store s, String deviceCode) throws IOException {
        Http.Res r = Http.postForm(TOKEN_URL, Http.form(
                "client_id", s.get(Store.YT_CLIENT_ID),
                "client_secret", s.get(Store.YT_CLIENT_SECRET),
                "device_code", deviceCode,
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code"));
        JSONObject j = r.json();
        if (r.ok() && j.has("access_token")) {
            saveTokens(s, j);
            return DONE;
        }
        String err = j.optString("error");
        if ("authorization_pending".equals(err)) return PENDING;
        if ("slow_down".equals(err)) return SLOW_DOWN;
        throw Http.fail("Google rechazó el acceso", r);
    }

    private static void saveTokens(Store s, JSONObject j) {
        s.put(Store.YT_ACCESS, j.optString("access_token"));
        s.putLong(Store.YT_ACCESS_EXP,
                System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 120) * 1000L);
        String refresh = j.optString("refresh_token");
        if (!refresh.isEmpty()) s.put(Store.YT_REFRESH, refresh);
    }

    private static String accessToken(Store s) throws IOException {
        if (s.has(Store.YT_ACCESS) && System.currentTimeMillis() < s.getLong(Store.YT_ACCESS_EXP)) {
            return s.get(Store.YT_ACCESS);
        }
        Http.Res r = Http.postForm(TOKEN_URL, Http.form(
                "client_id", s.get(Store.YT_CLIENT_ID),
                "client_secret", s.get(Store.YT_CLIENT_SECRET),
                "refresh_token", s.get(Store.YT_REFRESH),
                "grant_type", "refresh_token"));
        JSONObject j = r.json();
        if (!r.ok() || !j.has("access_token")) {
            throw Http.fail("No se pudo renovar el acceso a YouTube. Vuelve a conectar la cuenta", r);
        }
        saveTokens(s, j);
        return s.get(Store.YT_ACCESS);
    }

    /** Sube el video y devuelve su id. */
    static String upload(Store s, Video v, String title, String description, Http.Progress p)
            throws IOException {
        String token = accessToken(s);

        String t = title.replace('<', ' ').replace('>', ' ').trim();
        if (t.isEmpty()) t = "Video";
        if (t.length() > 100) t = t.substring(0, 100);
        String d = description.replace('<', ' ').replace('>', ' ');
        if (d.length() > 4900) d = d.substring(0, 4900);

        JSONObject meta = new JSONObject();
        try {
            JSONObject snippet = new JSONObject();
            snippet.put("title", t);
            snippet.put("description", d);
            snippet.put("categoryId", "22");
            JSONObject status = new JSONObject();
            status.put("privacyStatus", s.get(Store.YT_PRIVACY, "public"));
            status.put("selfDeclaredMadeForKids", false);
            meta.put("snippet", snippet);
            meta.put("status", status);
        } catch (JSONException e) {
            throw new IOException(e);
        }

        HashMap<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        h.put("X-Upload-Content-Length", String.valueOf(v.size));
        h.put("X-Upload-Content-Type", v.mime);
        Http.Res init = Http.postJson(UPLOAD_URL, h, meta);
        if (!init.ok() || init.location == null) {
            throw Http.fail("YouTube no aceptó iniciar la subida", init);
        }

        HashMap<String, String> h2 = new HashMap<>();
        h2.put("Authorization", "Bearer " + token);
        h2.put("Content-Type", v.mime);
        Http.Res up;
        try (InputStream in = v.open()) {
            up = Http.sendStream("PUT", init.location, h2, null, in, v.size, null, p, 0, v.size);
        }
        if (!up.ok()) throw Http.fail("YouTube rechazó el video", up);
        String id = up.json().optString("id");
        if (id.isEmpty()) throw Http.fail("YouTube no devolvió el id del video", up);
        return id;
    }
}
