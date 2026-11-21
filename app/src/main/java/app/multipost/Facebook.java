package app.multipost;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Facebook: publica videos en una página con la Graph API.
 * Acceso: "Facebook Login for Devices" (código en facebook.com/device) o un token pegado a mano.
 */
final class Facebook {
    private static final String V = "v25.0";
    private static final String GRAPH = "https://graph.facebook.com/" + V;
    private static final String GRAPH_VIDEO = "https://graph-video.facebook.com/" + V;
    private static final String SCOPE = "pages_show_list,pages_read_engagement,pages_manage_posts";

    static final int PENDING = 0;
    static final int DONE = 1;
    static final int SLOW_DOWN = 2;

    private Facebook() {
    }

    private static String appToken(Store s) {
        return s.get(Store.FB_APP_ID) + "|" + s.get(Store.FB_CLIENT_TOKEN);
    }

    /** Devuelve code, user_code, verification_uri, expires_in, interval. */
    static JSONObject startDevice(Store s) throws IOException {
        Http.Res r = Http.postForm(GRAPH + "/device/login",
                Http.form("access_token", appToken(s), "scope", SCOPE));
        if (!r.ok() || !r.json().has("code")) {
            throw Http.fail("Facebook no aceptó la solicitud de código", r);
        }
        return r.json();
    }

    /** Si termina bien, deja el token de usuario en out[0]. */
    static int pollDevice(Store s, String code, String[] out) throws IOException {
        Http.Res r = Http.postForm(GRAPH + "/device/login_status",
                Http.form("access_token", appToken(s), "code", code));
        JSONObject j = r.json();
        if (j.has("access_token")) {
            out[0] = j.optString("access_token");
            return DONE;
        }
        JSONObject err = j.optJSONObject("error");
        int sub = err == null ? 0 : err.optInt("error_subcode");
        if (sub == 1349174) return PENDING;
        if (sub == 1349172) return SLOW_DOWN;
        throw Http.fail("Facebook rechazó el acceso", r);
    }

    /** Cambia un token corto por uno de larga duración. Necesita la clave secreta de la app. */
    static String longLived(Store s, String userToken) throws IOException {
        Http.Res r = Http.get(GRAPH + "/oauth/access_token?" + Http.form(
                "grant_type", "fb_exchange_token",
                "client_id", s.get(Store.FB_APP_ID),
                "client_secret", s.get(Store.FB_APP_SECRET),
                "fb_exchange_token", userToken));
        String t = r.json().optString("access_token");
        if (!r.ok() || t.isEmpty()) throw Http.fail("Facebook no alargó el token", r);
        return t;
    }

    /** Paginas que administra el usuario: objetos con id, name y access_token. */
    static JSONArray pages(String userToken) throws IOException {
        Http.Res r = Http.get(GRAPH + "/me/accounts?" + Http.form(
                "fields", "id,name,access_token", "limit", "100", "access_token", userToken));
        if (!r.ok()) throw Http.fail("Facebook no devolvió tus páginas", r);
        JSONArray a = r.json().optJSONArray("data");
        return a == null ? new JSONArray() : a;
    }

    /** Sube el video a la página y devuelve su id. */
    static String upload(Store s, Video v, String title, String description, Http.Progress p)
            throws IOException {
        String boundary = "----multipost" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        field(sb, boundary, "access_token", s.get(Store.FB_PAGE_TOKEN));
        if (!title.trim().isEmpty()) field(sb, boundary, "title", title.trim());
        if (!description.trim().isEmpty()) field(sb, boundary, "description", description.trim());
        sb.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"source\"; filename=\"video.mp4\"\r\n")
                .append("Content-Type: ").append(v.mime).append("\r\n\r\n");
        byte[] prefix = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        HashMap<String, String> h = new HashMap<>();
        h.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        String url = GRAPH_VIDEO + "/" + s.get(Store.FB_PAGE_ID) + "/videos";
        Http.Res r;
        try (InputStream in = v.open()) {
            r = Http.sendStream("POST", url, h, prefix, in, v.size, suffix, p, 0, v.size);
        }
        String id = r.json().optString("id");
        if (!r.ok() || id.isEmpty()) throw Http.fail("Facebook rechazó el video", r);
        return id;
    }

    private static void field(StringBuilder sb, String boundary, String name, String value) {
        sb.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                .append(value).append("\r\n");
    }
}
