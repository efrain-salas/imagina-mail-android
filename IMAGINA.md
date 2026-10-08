# Imagina Mail

Versión de [Thunderbird para Android](https://github.com/thunderbird/thunderbird-android) con la marca de Imagina y un único acceso: **«Entrar con Imagina»** (móvil y código SMS en el navegador integrado). Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hito C11.3; prototipo en C0.9).

- **Versión de Thunderbird**: `THUNDERBIRD_24_0`, el remoto `upstream`.
- **Licencia**: Apache 2.0, como Thunderbird. No se puede usar el nombre ni el logo de Thunderbird; la app dice en «Acerca de» y en su ficha que no es la oficial.

## Principio: todo cambio, aparte y parametrizado

Lo de Imagina vive en ficheros propios que Thunderbird no tiene (`imagina/`, `app-imagina/`), así que actualizar Thunderbird casi nunca choca con ellos. Los ficheros de Thunderbird que se tocan están en la lista de abajo, cada uno con el motivo. Los valores que cambian entre entornos van en `imagina/imagina.properties`, nunca escritos en el código.

## Compilar

Requisitos: Java 21 (vale el de Android Studio) y el SDK de Android con la plataforma 37.

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app-imagina:assembleFossDebug
```

La APK queda en `app-imagina/build/outputs/apk/foss/debug/`. La primera compilación tarda unos minutos.

## Parámetros

`imagina/imagina.properties` (en git, sin secretos). Cada clave llega al código como `BuildConfig.<CLAVE>`.

| Clave | Para qué |
| --- | --- |
| `IMAGINA_APPLICATION_ID` | Id de la app. El OAuth vuelve a `<id>://oauth2redirect`, que debe estar dado de alta en el cliente OAuth de Imagina. |
| `IMAGINA_AUTH_HOST` | Servidor de identidad de Imagina (OIDC). |
| `IMAGINA_OAUTH_CLIENT_ID` | Cliente OAuth público de la app en Imagina (`php artisan passport:client --public`). |
| `IMAGINA_IMAP_HOST`, `IMAGINA_SMTP_HOST` | Servidores de correo (Migadu). |

`imagina/imagina.local.properties` (fuera de git) sobrescribe o añade claves en cada máquina. Hoy lleva el buzón de prueba (`IMAGINA_TEST_MAIL_ADDRESS`, `_USERNAME`, `_PASSWORD`) que la app recibe hasta que exista la API de dispositivos de Imagina (C7.1).

## Cambios en ficheros de Thunderbird

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `settings.gradle.kts` | `":app-imagina"` en la lista de módulos | Compilar la app de Imagina. |
| `app-common/.../BaseApplication.kt` | Propiedad `allowsDefinitionOverride` (falsa por defecto) que pasa a `DI.start` | Thunderbird prohíbe sustituir piezas de la inyección de dependencias; la app de Imagina lo activa para poner su navegación de acceso en lugar de la de Thundermail. Thunderbird y K-9 no cambian. |
| `.gitignore` | `imagina/imagina.local.properties` | Que las credenciales de prueba no entren en git. |

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `app-imagina/` | Módulo de app copiado de `app-thunderbird` (así hace Thunderbird con K-9): id, nombre, icono y versión de Imagina; sin `dependencyGuard` ni `badging`; el `debug` sin sufijo de id para que el OAuth vuelva a la app. |
| `app-imagina/.../ThunderbirdKoinModule.kt` | Configuración OAuth de Imagina e `imaginaModule`, cargado el último para sustituir la navegación de Thundermail. |
| `app-imagina/src/main/kotlin/.../imagina/` | `ImaginaOAuthConfigurationFactory` (Imagina como proveedor OAuth), `ImaginaNavigation` (la pantalla tras «Empezar»), `ImaginaSignInScreen` y `ImaginaSignInViewModel` («Entrar con Imagina»), `ImaginaAccountProvisioner` (crea la cuenta de cada buzón con su credencial). |
| `app-imagina/src/main/res/` | Nombre (`strings.xml`), color de la loseta (`imagina_colors.xml`) e imágenes generadas (ver abajo). |
| `imagina/` | Parámetros, fuentes SVG de marca, generador de imágenes y este documento. |

**Cómo engancha con Thunderbird**: tras «Empezar» en la bienvenida, Thunderbird navega a `ThundermailRoute.AddAccount`, cuyas pantallas pone la implementación de `ThundermailNavigation` que haya en la inyección de dependencias. Imagina Mail registra la suya (`ImaginaNavigation`), que muestra «Entrar con Imagina», usa el `AccountOAuthViewModel` de Thunderbird contra `IMAGINA_AUTH_HOST` y crea las cuentas con el `AccountCreator` de Thunderbird.

## Imágenes de marca

Las fuentes son SVG en `imagina/brand/` (`imagina-mail-mark.svg`: la loseta tinta de Imagina con un sobre y el punto teal). Las imágenes de Android se generan, no se editan:

```sh
imagina/generate-resources.sh
```

Genera `ic_app_logo` y `ic_app_logo_monochrome` (icono de la app) e `ic_imagina_mark` (pantalla de acceso) con `imagina/tools/svg_to_vector.py`.

## Actualizar a una nueva versión de Thunderbird

1. `git fetch upstream --tags` y `git merge THUNDERBIRD_<nueva>` sobre `main`.
2. Los conflictos solo pueden venir de los tres ficheros de la tabla de arriba.
3. Comparar `app-thunderbird` entre la versión anterior y la nueva (`git diff THUNDERBIRD_<anterior> THUNDERBIRD_<nueva> -- app-thunderbird`) y llevar a `app-imagina` lo que cambie (dependencias, módulos de Koin, manifest), salvo nombre, id e icono.
4. Comprobar que siguen existiendo los puntos de enganche: `ThundermailNavigation`, `ThundermailRoute.AddAccount` y `AccountSetupExternalContract.AccountCreator`.
5. Compilar y probar en el emulador: «Entrar con Imagina», cuenta creada, recibir y enviar.

## Pendiente para la app de verdad

- Buzones y credenciales desde la API de dispositivos de Imagina (C7.1), varios buzones y puesta al día de los accesos.
- Bienvenida: quitarla o rehacerla (aún muestra el logo y los textos de Thunderbird y Mozilla).
- `User-Agent` de los correos («Thunderbird for Android»): sustituir el texto en los recursos de la app.
- Avisos al momento (IMAP IDLE en la bandeja), español por defecto, iconos PNG para Android antiguos (los de las carpetas `mipmap-*dpi` aún son de Thunderbird) y firma de publicación.
- Si una credencial deja de valer, pedir «Entrar con Imagina» en lugar de una contraseña.
