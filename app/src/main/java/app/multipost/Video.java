package app.multipost;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.IOException;
import java.io.InputStream;

/** El video elegido: de donde leerlo, cuanto pesa y de que tipo es. */
final class Video {
    final Uri uri;
    final String name;
    final long size;
    final String mime;
    private final ContentResolver resolver;

    private Video(ContentResolver resolver, Uri uri, String name, long size, String mime) {
        this.resolver = resolver;
        this.uri = uri;
        this.name = name;
        this.size = size;
        this.mime = mime;
    }

    static Video from(Context ctx, Uri uri) throws IOException {
        ContentResolver cr = ctx.getContentResolver();
        String name = "video.mp4";
        long size = -1;
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (RuntimeException ignored) {
            // Algunos proveedores no dejan consultar; se intenta por el descriptor.
        }
        if (size <= 0) {
            try (AssetFileDescriptor fd = cr.openAssetFileDescriptor(uri, "r")) {
                if (fd != null) size = fd.getLength();
            } catch (RuntimeException e) {
                throw new IOException("No se pudo abrir el video: " + e.getMessage());
            }
        }
        if (size <= 0) {
            throw new IOException("No se pudo saber el tamaño del video. "
                    + "Prueba a elegirlo desde la galería o la app Archivos.");
        }
        String mime = cr.getType(uri);
        if (mime == null || !mime.startsWith("video/")) mime = "video/mp4";
        return new Video(cr, uri, name, size, mime);
    }

    InputStream open() throws IOException {
        InputStream in;
        try {
            in = resolver.openInputStream(uri);
        } catch (RuntimeException e) {
            throw new IOException("No se pudo abrir el video: " + e.getMessage());
        }
        if (in == null) throw new IOException("No se pudo abrir el video");
        return in;
    }

    String sizeText() {
        return String.format(java.util.Locale.US, "%.1f MB", size / 1048576.0);
    }
}
