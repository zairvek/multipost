# MultiPost

App Android personal: eliges un video, escribes título y descripción, y lo sube a YouTube, a tu página de Facebook y a TikTok.

También aparece en el menú **Compartir** de la galería: compartes un video con MultiPost y queda cargado.

## Estado

- El código compila contra el SDK de Android (API 34).
- La lógica de subida de las tres plataformas pasó una prueba contra un servidor simulado que comprueba cada petición (trozos de TikTok, formulario de Facebook, subida reanudable de YouTube).
- **No se ha probado en un teléfono ni contra los servicios reales.** Si algo falla, la app muestra el paso y la respuesta del servidor; con ese texto se puede corregir.

## Conseguir el APK

Cada vez que se sube código a la rama `main`, GitHub compila el APK (pestaña **Actions**) y lo publica en **Releases** como `MultiPost.apk`. Descárgalo desde el teléfono e instálalo; Android pedirá permiso para instalar apps de origen desconocido.

Mantén el repositorio privado: incluye la clave con la que se firma el APK, para que cada versión nueva se instale encima de la anterior sin perder tus cuentas.

## Configurar las cuentas

Cada plataforma exige que registres tu propia "app de desarrollador". Se hace una sola vez. Los nombres de los menús pueden variar un poco, porque las tres consolas cambian a menudo.

### YouTube

1. Entra en <https://console.cloud.google.com> y crea un proyecto.
2. En **APIs y servicios > Biblioteca**, activa **YouTube Data API v3**.
3. Configura la **pantalla de consentimiento OAuth** como **Externa**. Después pásala a **En producción**; en modo de prueba el acceso caduca a los 7 días.
4. En **Credenciales** (o **Clientes**), crea un cliente OAuth de tipo **TVs and Limited Input devices**. Copia el Client ID y el Client secret.
5. En MultiPost: **Cuentas**, pega los dos datos y toca **Conectar YouTube**. La app muestra un código; escríbelo en la página que se abre y acepta. Google avisará de que la app no está verificada: es tu propia app, puedes continuar.

Límite: 100 subidas al día. Desde el 2 de noviembre de 2026 YouTube ya no fuerza a privado los videos subidos desde proyectos sin verificar.

### Facebook (página)

1. Entra en <https://developers.facebook.com/apps> y crea una app que permita administrar páginas.
2. Añade el producto **Facebook Login** y, en su configuración, activa **Login from Devices**.
3. Copia el **App ID** (Configuración > Básica) y el **Client token** (Configuración > Avanzada).
4. En MultiPost: pega los dos datos y toca **Conectar Facebook**. Escribe el código en facebook.com/device, acepta y marca tu página.
5. Pasa la app de Meta a modo **Live** (pide una URL de política de privacidad). En modo desarrollo, lo que publique solo lo ven las personas con rol en la app.

Si el código no funciona, usa la alternativa: en el **Explorador de la Graph API** genera un token de usuario con los permisos `pages_show_list`, `pages_read_engagement` y `pages_manage_posts`, pégalo en MultiPost y toca **Usar token pegado**. Si además pegas el App secret, el acceso no caduca.

### TikTok

1. Entra en <https://developers.tiktok.com> y crea una app.
2. Añade los productos **Login Kit** y **Content Posting API**, con los permisos `user.info.basic`, `video.upload` y `video.publish`.
3. En Login Kit registra una **Redirect URI** que empiece por `https://`. Sirve cualquier página tuya; la app solo necesita que TikTok redirija ahí para leer el código.
4. Para usarla sin pasar la revisión de TikTok, crea un entorno **Sandbox** y añade tu cuenta de TikTok como usuario de prueba. Usa el Client key y el Client secret de ese entorno.
5. En MultiPost: pega Client key, Client secret y la Redirect URI, y toca **Conectar TikTok**. Si la página de TikTok no carga dentro de la app, usa **Abrir en el navegador** y pega la URL final.

Límites de TikTok, que la app no puede saltarse:

- Una app sin auditar solo puede publicar directamente en modo privado ("Solo yo"), y la cuenta tiene que ser privada en ese momento.
- Por eso el modo por defecto es **Borrador**: el video llega a tu bandeja de TikTok y lo publicas tú con un par de toques, con la visibilidad que quieras.
- Para publicación directa y pública, TikTok tiene que auditar la app, y pide una web, una demostración y una pantalla de publicación con requisitos concretos.

## Uso

1. **Elegir video** (o compartirlo desde la galería).
2. Título y descripción. YouTube usa los dos; Facebook también; TikTok los une como texto del video.
3. Marca las plataformas y toca **Publicar**. Deja la app abierta hasta que termine.

## Seguridad

Las claves y los accesos se guardan en el almacenamiento privado de la app, en tu teléfono, y solo se envían a Google, Meta y TikTok. No los compartas ni subas capturas donde se vean.
