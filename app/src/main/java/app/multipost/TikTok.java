package app.multipost;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * TikTok Content Posting API.
 * Modo "inbox": el video llega como borrador a la bandeja de TikTok y se publica desde la app de TikTok.
 * Modo "direct": publicación directa (las apps sin auditar solo pueden publicar en privado).
 */
final class TikTok {
    private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String API = "https://open.tiktokapis.com/v2/post/publish";
    static final String SCOPE = "user.info.basic,video.upload,video.publish";

    private static final long MB = 1024L * 1024L;
    private static final long CHUNK = 10 * MB;

    private TikTok() {
    }

    static String authUrl(Store s, String state) {
        return AUTH_URL + "?" + Http.form(
                "client_key", s.get(Store.TT_CLIENT_KEY),
                "scope", SCOPE,
                "response_type", "code",
                "redirect_uri", s.get(Store.TT_REDIRECT),
                "state", state);
    }

    /** Cambia el código de autorización por los tokens y los guarda. */
    static void exchange(Store s, String code) throws IOException {
        Http.Res r = Http.postForm(TOKEN_URL, Http.form(
                "client_key", s.get(Store.TT_CLIENT_KEY),
                "client_secret", s.get(Store.TT_CLIENT_SECRET),
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", s.get(Store.TT_REDIRECT)));
        JSONObject j = r.json();
        if (!r.ok() || !j.has("access_token")) throw Http.fail("TikTok no entregó el acceso", r);
        saveTokens(s, j);
    }

    private static void saveTokens(Store s, JSONObject j) {
        s.put(Store.TT_ACCESS, j.optString("access_token"));
        s.putLong(Store.TT_ACCESS_EXP,
                System.currentTimeMillis() + (j.optLong("expires_in", 86400) - 300) * 1000L);
        String refresh = j.optString("refresh_token");
        if (!refresh.isEmpty()) s.put(Store.TT_REFRESH, refresh);
    }

    private static String accessToken(Store s) throws IOException {
        if (s.has(Store.TT_ACCESS) && System.currentTimeMillis() < s.getLong(Store.TT_ACCESS_EXP)) {
            return s.get(Store.TT_ACCESS);
        }
        Http.Res r = Http.postForm(TOKEN_URL, Http.form(
                "client_key", s.get(Store.TT_CLIENT_KEY),
                "client_secret", s.get(Store.TT_CLIENT_SECRET),
                "grant_type", "refresh_token",
                "refresh_token", s.get(Store.TT_REFRESH)));
        JSONObject j = r.json();
        if (!r.ok() || !j.has("access_token")) {
            throw Http.fail("No se pudo renovar el acceso a TikTok. Vuelve a conectar la cuenta", r);
        }
        saveTokens(s, j);
        return s.get(Store.TT_ACCESS);
    }

    private static HashMap<String, String> auth(String token) {
        HashMap<String, String> h = new HashMap<>();
        h.put("Authorization", "Bearer " + token);
        return h;
    }

    /** Las respuestas de TikTok traen error.code = "ok" cuando todo va bien. */
    private static JSONObject data(String step, Http.Res r) throws IOException {
        JSONObject j = r.json();
        JSONObject err = j.optJSONObject("error");
        String code = err == null ? "" : err.optString("code");
        if (!r.ok() || !"ok".equals(code)) throw Http.fail(step, r);
        JSONObject d = j.optJSONObject("data");
        return d == null ? new JSONObject() : d;
    }

    /**
     * Sube el video. Devuelve un texto con el resultado para mostrar al usuario.
     */
    static String publish(Store s, Video v, String caption, Http.Progress p)
            throws IOException, InterruptedException {
        String token = accessToken(s);
        boolean direct = "direct".equals(s.get(Store.TT_MODE, "inbox"));

        long chunkSize;
        long chunkCount;
        if (v.size <= 2 * CHUNK) {
            chunkSize = v.size;
            chunkCount = 1;
        } else {
            chunkSize = CHUNK;
            chunkCount = v.size / CHUNK; // el último trozo se lleva el resto
        }

        JSONObject body = new JSONObject();
        try {
            JSONObject source = new JSONObject();
            source.put("source", "FILE_UPLOAD");
            source.put("video_size", v.size);
            source.put("chunk_size", chunkSize);
            source.put("total_chunk_count", chunkCount);
            body.put("source_info", source);
            if (direct) {
                JSONObject info = data("TikTok no devolvió los datos de la cuenta",
                        Http.postJson(API + "/creator_info/query/", auth(token), new JSONObject()));
                JSONObject post = new JSONObject();
                String c = caption.length() > 2200 ? caption.substring(0, 2200) : caption;
                if (!c.isEmpty()) post.put("title", c);
                post.put("privacy_level", pickPrivacy(s, info));
                post.put("disable_duet", false);
                post.put("disable_stitch", false);
                post.put("disable_comment", false);
                body.put("post_info", post);
            }
        } catch (JSONException e) {
            throw new IOException(e);
        }

        String initUrl = direct ? API + "/video/init/" : API + "/inbox/video/init/";
        JSONObject init = data("TikTok no aceptó iniciar la subida",
                Http.postJson(initUrl, auth(token), body));
        String publishId = init.optString("publish_id");
        String uploadUrl = init.optString("upload_url");
        if (uploadUrl.isEmpty()) throw new IOException("TikTok no devolvió la dirección de subida");

        String mime = v.mime;
        if (!"video/mp4".equals(mime) && !"video/quicktime".equals(mime)
                && !"video/webm".equals(mime)) {
            mime = "video/mp4";
        }
        try (InputStream in = v.open()) {
            long offset = 0;
            for (long i = 0; i < chunkCount; i++) {
                long len = (i == chunkCount - 1) ? v.size - offset : chunkSize;
                HashMap<String, String> h = new HashMap<>();
                h.put("Content-Type", mime);
                h.put("Content-Range",
                        "bytes " + offset + "-" + (offset + len - 1) + "/" + v.size);
                Http.Res r = Http.sendStream("PUT", uploadUrl, h, null, in, len, null,
                        p, offset, v.size);
                if (!r.ok()) throw Http.fail("TikTok rechazó el trozo " + (i + 1), r);
                offset += len;
            }
        }

        // Espera a que TikTok procese el video (hasta ~3 minutos).
        String status = "";
        JSONObject st = new JSONObject();
        for (int i = 0; i < 36; i++) {
            Thread.sleep(5000);
            JSONObject q = new JSONObject();
            try {
                q.put("publish_id", publishId);
            } catch (JSONException e) {
                throw new IOException(e);
            }
            st = data("TikTok no devolvió el estado",
                    Http.postJson(API + "/status/fetch/", auth(token), q));
            status = st.optString("status");
            if (!status.startsWith("PROCESSING")) break;
        }
        if ("FAILED".equals(status)) {
            throw new IOException("TikTok no pudo procesar el video: " + st.optString("fail_reason"));
        }
        if ("SEND_TO_USER_INBOX".equals(status)) {
            return "enviado a tu bandeja de TikTok. Abre TikTok y toca la notificación para publicarlo.";
        }
        if ("PUBLISH_COMPLETE".equals(status)) {
            return "publicado.";
        }
        return "subido; TikTok lo sigue procesando (" + status + ").";
    }

    private static String pickPrivacy(Store s, JSONObject creatorInfo) {
        String wanted = s.get(Store.TT_PRIVACY, "SELF_ONLY");
        JSONArray opts = creatorInfo.optJSONArray("privacy_level_options");
        if (opts == null || opts.length() == 0) return wanted;
        boolean selfOnly = false;
        for (int i = 0; i < opts.length(); i++) {
            String o = opts.optString(i);
            if (o.equals(wanted)) return wanted;
            if ("SELF_ONLY".equals(o)) selfOnly = true;
        }
        return selfOnly ? "SELF_ONLY" : opts.optString(0);
    }
}
