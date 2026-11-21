package app.multipost;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Petición HTTP sin librerias externas. */
final class Http {

    /** Respuesta de una peticion. */
    static final class Res {
        int code;
        String body = "";
        String location;

        boolean ok() {
            return code >= 200 && code < 300;
        }

        JSONObject json() {
            try {
                return new JSONObject(body);
            } catch (JSONException e) {
                return new JSONObject();
            }
        }
    }

    /** Avance de una subida. */
    interface Progress {
        void on(long sent, long total);
    }

    private Http() {
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pares clave, valor, clave, valor... en formato de formulario. */
    static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    static Res get(String url) throws IOException {
        return send("GET", url, null, null);
    }

    static Res postForm(String url, String form) throws IOException {
        java.util.HashMap<String, String> h = new java.util.HashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded");
        return send("POST", url, h, form.getBytes(StandardCharsets.UTF_8));
    }

    static Res postJson(String url, Map<String, String> headers, JSONObject json) throws IOException {
        java.util.HashMap<String, String> h = new java.util.HashMap<>();
        if (headers != null) h.putAll(headers);
        h.put("Content-Type", "application/json; charset=UTF-8");
        return send("POST", url, h, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    static Res send(String method, String url, Map<String, String> headers, byte[] body)
            throws IOException {
        HttpURLConnection c = open(method, url, headers);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body);
                }
            }
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    /**
     * Envia: prefijo + (length bytes leidos de in) + sufijo, sin cargar el video en memoria.
     * No cierra el InputStream.
     */
    static Res sendStream(String method, String url, Map<String, String> headers,
                          byte[] prefix, InputStream in, long length, byte[] suffix,
                          Progress progress, long progressBase, long progressTotal)
            throws IOException {
        HttpURLConnection c = open(method, url, headers);
        try {
            long pre = prefix == null ? 0 : prefix.length;
            long suf = suffix == null ? 0 : suffix.length;
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(pre + length + suf);
            try (OutputStream out = c.getOutputStream()) {
                if (prefix != null) out.write(prefix);
                byte[] buf = new byte[256 * 1024];
                long left = length;
                long sent = 0;
                long lastReport = 0;
                while (left > 0) {
                    int n = in.read(buf, 0, (int) Math.min(buf.length, left));
                    if (n < 0) throw new IOException("El video se acabó antes de lo esperado");
                    out.write(buf, 0, n);
                    left -= n;
                    sent += n;
                    if (progress != null && sent - lastReport >= 512 * 1024) {
                        lastReport = sent;
                        progress.on(progressBase + sent, progressTotal);
                    }
                }
                if (suffix != null) out.write(suffix);
                out.flush();
            }
            if (progress != null) progress.on(progressBase + length, progressTotal);
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String method, String url, Map<String, String> headers)
            throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(30_000);
        c.setReadTimeout(10 * 60_000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                c.setRequestProperty(e.getKey(), e.getValue());
            }
        }
        return c;
    }

    private static Res read(HttpURLConnection c) throws IOException {
        Res r = new Res();
        r.code = c.getResponseCode();
        r.location = c.getHeaderField("Location");
        InputStream in = r.code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in != null) {
            try (InputStream s = in) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = s.read(buf)) > 0) bo.write(buf, 0, n);
                r.body = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            }
        }
        return r;
    }

    /** Error legible con el paso que fallo y lo que respondio el servidor. */
    static IOException fail(String step, Res r) {
        String b = r.body == null ? "" : r.body.trim();
        if (b.length() > 400) b = b.substring(0, 400) + "...";
        return new IOException(step + " (HTTP " + r.code + ")" + (b.isEmpty() ? "" : ": " + b));
    }
}
