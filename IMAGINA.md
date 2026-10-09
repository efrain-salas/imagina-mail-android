# Imagina Mail

Versión de [Thunderbird para Android](https://github.com/thunderbird/thunderbird-android) con la marca de Imagina y un único acceso: **«Entrar con Imagina»** (móvil y código SMS en el navegador integrado). Los buzones y sus credenciales los da la API de dispositivos de Imagina; la app nunca pide un servidor, un correo ni una contraseña. Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hitos C7.3 y C11.3; prototipo en C0.9).

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

`imagina/imagina.properties` (en git, sin secretos) declara todos los parámetros. Cada clave llega al código como `BuildConfig.<CLAVE>`.

| Clave | Para qué |
| --- | --- |
| `IMAGINA_APPLICATION_ID` | Id de la app. El OAuth vuelve a `<id>://oauth2redirect`, que debe estar dado de alta en el cliente OAuth de Imagina. |
| `IMAGINA_AUTH_HOST` | Servidor de identidad de Imagina (OIDC, scope `openid cloud.devices`). La API de dispositivos está en `https://<host>/api/v1/cloud/devices`. |
| `IMAGINA_OAUTH_CLIENT_ID` | Cliente OAuth público de la app en Imagina (`php artisan passport:client --public`). |

Los servidores de correo (Migadu) y la credencial de cada buzón no son parámetros: los da la API de dispositivos.

`imagina/imagina.local.properties` (fuera de git) solo puede sobrescribir claves de `imagina.properties` en cada máquina; una clave que no esté allí se ignora y nunca llega a la APK. Antes llevaba el buzón de prueba (`IMAGINA_TEST_MAIL_*`): ya no se usa y esas líneas se pueden borrar.

## Cambios en ficheros de Thunderbird

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `settings.gradle.kts` | `":app-imagina"` en la lista de módulos | Compilar la app de Imagina. |
| `app-common/.../BaseApplication.kt` | Propiedad `allowsDefinitionOverride` (falsa por defecto) que pasa a `DI.start` | Thunderbird prohíbe sustituir piezas de la inyección de dependencias; la app de Imagina lo activa para poner su navegación de acceso en lugar de la de Thundermail. Thunderbird y K-9 no cambian. |
| `.gitignore` | `imagina/imagina.local.properties` | Que los valores locales no entren en git. |

El resto (acceso sin contraseña, bienvenida, avisos al momento, `User-Agent`, sincronización) se hace sin tocar más ficheros de Thunderbird: con piezas de la inyección de dependencias que Imagina sustituye (ver «Cómo engancha») y recursos de `app-imagina`.

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `app-imagina/` | Módulo de app copiado de `app-thunderbird` (así hace Thunderbird con K-9): id, nombre, icono y versión de Imagina; sin `dependencyGuard` ni `badging`; el `debug` sin sufijo de id para que el OAuth vuelva a la app. De las copias, `ThunderbirdKoinModule.kt` solo incluye `imaginaModule` y `ThunderbirdApp.kt` arranca `ImaginaStartup`. |
| `app-imagina/src/main/kotlin/.../imagina/` | Todo el código de Imagina (abajo). |
| `app-imagina/src/main/res/` | Nombre (`strings.xml`), textos de «Entrar con Imagina» (`imagina_*` en `values`, `values-es`, `-de`, `-fr`, `-it`, `-pt-rPT`), `User-Agent` (`constants.xml`), color de la loseta (`imagina_colors.xml`) e imágenes generadas (ver abajo). |
| `app-imagina/src/test/.../imagina/` | Pruebas de la API (contra un servidor local), de la sincronización (con dobles) y de la configuración de servidores. |
| `imagina/` | Parámetros, fuentes SVG de marca, generador de imágenes y este documento. |

Código de `imagina/`:

| Fichero | Qué hace |
| --- | --- |
| `ImaginaModule.kt` | Inyección de dependencias de Imagina. Se carga la última y sustituye las pantallas de Thunderbird. |
| `ImaginaOAuthConfigurationFactory.kt` | Imagina como proveedor OAuth (`openid cloud.devices`, PKCE, `<id>://oauth2redirect`) y direcciones de la API. |
| `ImaginaSignInScreen.kt`, `ImaginaSignInViewModel.kt`, `ImaginaSignInMode.kt` | «Entrar con Imagina». Dos modos: primera pantalla de la app (`Onboarding`) y volver a entrar (`Reconnect`). Muestra el `message` que da Imagina cuando rechaza algo. |
| `ImaginaNavigation.kt` | Las pantallas que sustituyen a las de Thunderbird (bienvenida, editar servidores, añadir cuenta, rutas de Thundermail). |
| `ImaginaDevicesApi.kt` | Cliente de la API de dispositivos (`POST /`, `GET /{id}`, `POST /{id}/refresh`, `DELETE /{id}`). |
| `ImaginaSession.kt` | Token de acceso válido en cada llamada: renueva con AppAuth (refresh token rotativo) guardando el nuevo antes de usarlo. |
| `ImaginaDeviceStore.kt` | Lo que recuerda la app (SharedPreferences propio `imagina_device`, privado y sin copia de seguridad): id del dispositivo, estado OAuth, qué cuentas hizo y si hay que volver a entrar. |
| `ImaginaDeviceSynchronizer.kt` | La lógica: registrar el dispositivo (solo si no hay o está revocado), una cuenta por buzón, ponerlas al día, quitar las de buzones retirados, desconectar. |
| `ImaginaAccountProvisioner.kt` | Crea, actualiza y borra cuentas con las piezas de Thunderbird (`AccountCreator`, `AccountServerSettingsUpdater`, `BackgroundAccountRemover`) y marca la bandeja de entrada para push. |
| `ImaginaSyncScheduler.kt` | WorkManager: cada 6 horas, al arrancar la app y tras entrar (`ImaginaSyncWorker`). |
| `ImaginaStartup.kt` | Lo que arranca con la app: sincronización, oferta de volver a entrar y desconexión al borrar la última cuenta. |
| `ImaginaErrors.kt` | Errores tipados (API, red, sesión, dispositivo revocado…). |

**Cómo engancha con Thunderbird**: el lanzador (`feature/launcher`) saca todas sus pantallas de objetos de navegación de la inyección de dependencias. `BaseApplication.allowsDefinitionOverride` deja a Imagina sustituir cuatro:

- `OnboardingNavigation` → `ImaginaOnboardingNavigation`: la primera pantalla es «Entrar con Imagina», sin la bienvenida con los logos y textos de Thunderbird y Mozilla; después, el paso de permisos (notificaciones) y la bandeja.
- `AccountEditNavigation` → `ImaginaAccountEditNavigation`: **gancho de «nunca pedir contraseña»**. Todo lo que lleva a editar los servidores de una cuenta (el aviso «fallo de autenticación» de las notificaciones, los ajustes de la cuenta y la lista de mensajes) abre `FeatureLauncherTarget.AccountEdit*Settings`, que ya no muestra campos de servidor y contraseña sino «Entrar con Imagina» en modo `Reconnect`.
- `AccountSetupNavigation` → `ImaginaAccountSetupNavigation`: «Añadir cuenta» abre también «Entrar con Imagina» (trae los buzones que falten) en lugar del asistente manual.
- `ThundermailNavigation` → `ImaginaNavigation`: cualquier ruta de Thundermail acaba en lo mismo.

**Entrar** (`ImaginaDeviceSynchronizer.connect`): tras el OAuth (AppAuth de Thunderbird, `AccountOAuthViewModel`), con el token se llama a la API. Si el móvil no tiene dispositivo guardado o Imagina lo revocó (o no lo conoce, 404), `POST /` y se crea **una cuenta por buzón** (nombre: la dirección, o `dirección · empresa` si la persona tiene buzones de varias empresas; remitente: `name`). Si el dispositivo sigue vigente se reutiliza su id: `GET /{id}` y, si `changed`, `POST /{id}/refresh`. Una dirección que ya tiene cuenta recibe la credencial nueva en lugar de duplicarse. Si algo falla a medias se deshace (se desconecta el dispositivo y se quitan las cuentas hechas) para no dejar buzones sin credencial.

**Sincronizar** (C7.3, `sync`): token de acceso (15 minutos) renovado con el refresh token (7 días, rotativo) → `GET /devices/{id}`. `changed` → `POST /refresh`: cuenta nueva por cada `added`, se quita la de cada `removed`. `revoked` (o 410 al refrescar) → se quitan las cuentas de Imagina y las credenciales, se cancela la sincronización y, sin cuentas, la app vuelve a «Entrar con Imagina». Si el refresh token ya no vale (o Imagina rechaza el token con 401, 403 o 404) el correo sigue funcionando: se anota `needsSignIn` y la primera vez que se abre la bandeja en la siguiente ejecución de la app se ofrece «Entrar con Imagina» (con «Ahora no»); entrar de nuevo reutiliza el dispositivo si no está revocado. Sin red, WorkManager reintenta con espera creciente.

**Desconectar**: si la persona borra la última cuenta de Imagina (se oye con `Preferences.addAccountRemovedListener`), `DELETE /devices/{id}` (lo mejor posible) y se olvida todo.

**Avisos al momento (IMAP IDLE)**: en esta versión de Thunderbird el push es por carpeta (`pushEnabled`; `folderPushMode` de la cuenta ya solo se usa al migrar ajustes viejos). Las carpetas nuevas leen sus ajustes iniciales de las preferencias (`<uuid de la cuenta>.<id de carpeta>.pushEnabled`, lo mismo que hace «Importar ajustes»), así que antes de crear la cuenta se escribe `<uuid>.INBOX.pushEnabled=true` y la bandeja de entrada nace como carpeta push. Migadu admite IDLE; sin servidor de notificaciones.

**`User-Agent`**: `message_header_mua` en `app-imagina/src/main/res/values/constants.xml` («Imagina Mail»), que gana al de `legacy/ui/legacy` («Thunderbird for Android») igual que hace `app-k9mail`.

**Trabajador de WorkManager**: `K9WorkerFactory` solo construye clases de `com.fsck.k9` y devuelve `null` para el resto, así que `ImaginaSyncWorker` lo crea la fábrica por defecto de WorkManager (constructor `(Context, WorkerParameters)`) y toma sus piezas de Koin.

## Imágenes de marca

Las fuentes son SVG en `imagina/brand/` (`imagina-mail-mark.svg`: la loseta tinta de Imagina con un sobre y el punto teal). Las imágenes de Android se generan, no se editan:

```sh
imagina/generate-resources.sh
```

Genera `ic_app_logo` y `ic_app_logo_monochrome` (icono de la app) e `ic_imagina_mark` (pantalla de acceso) con `imagina/tools/svg_to_vector.py`. También sustituye el logo de Thunderbird de su tema de Compose (`bolt_thunderbird_logo`, en la cabecera del paso de permisos y otras pantallas): es un recurso de Compose de `components/ui/bolt` que viaja como asset, y el asset de la app en `app-imagina/src/main/assets/composeResources/net.thunderbird.components.ui.bolt.resources/drawable/` con el mismo nombre lo reemplaza. Al actualizar Thunderbird, comprobar que ese recurso sigue llamándose igual (`unzip -l` de la APK).

## Compilación automática

`.github/workflows/imagina.yml` (GitHub Actions del repositorio público `efrain-salas/imagina-mail-android`): compila la APK de depuración y pasa las pruebas de `app-imagina` en cada subida a `main` (la APK queda como artefacto 14 días), y cada lunes abre una incidencia si Thunderbird ha publicado una versión posterior a `THUNDERBIRD_ANDROID_TAG` (en `imagina/imagina.properties`). Los flujos de Thunderbird que trae el fork están desactivados en GitHub (`gh workflow disable`), porque son para su repositorio. La firma de publicación llegará con la cuenta de Google Play.

## Actualizar a una nueva versión de Thunderbird

1. `git fetch upstream --tags` y `git merge THUNDERBIRD_<nueva>` sobre `main`.
2. Cambiar `THUNDERBIRD_ANDROID_TAG` en `imagina/imagina.properties`. Los conflictos solo pueden venir de los tres ficheros de la tabla de arriba.
3. Comparar `app-thunderbird` entre la versión anterior y la nueva (`git diff THUNDERBIRD_<anterior> THUNDERBIRD_<nueva> -- app-thunderbird`) y llevar a `app-imagina` lo que cambie (dependencias, módulos de Koin, manifest), salvo nombre, id e icono.
4. Comprobar que siguen existiendo los puntos de enganche: `OnboardingNavigation`/`OnboardingRoute.Onboarding`, `AccountEditNavigation`, `AccountSetupNavigation`, `ThundermailNavigation`, `AccountSetupExternalContract.AccountCreator`, `AccountEditExternalContract.AccountServerSettingsUpdater`, `BackgroundAccountRemover`, `Preferences.addAccountRemovedListener`, la clave `<uuid>.<carpeta>.pushEnabled` de `com.fsck.k9.mailstore.FolderSettingsProvider`, el recurso `message_header_mua` y que `K9WorkerFactory` siga dejando pasar a las clases que no son de `com.fsck.k9`.
5. Compilar, pasar `./gradlew :app-imagina:testFossDebugUnitTest` y probar en el emulador o el móvil: «Entrar con Imagina», cuentas creadas, recibir y enviar, aviso al momento.

## Pendiente para la app de verdad

- Probado en el emulador contra producción el 9 oct 2026: «Entrar con Imagina» con la sesión que ya tenía el navegador (sin volver a pedir el código), la cuenta del buzón personal con su identidad propia y sus carpetas por IMAP. Falta probar varios buzones de varias empresas, recibir al momento con la app cerrada, revocar el móvil desde Imagina y la sincronización de cada 6 horas.
- Colores de Imagina: los botones y la barra siguen con la paleta de Thunderbird (su tema de Compose `ThunderbirdBoltTheme` y los temas XML de las pantallas antiguas); hace falta un tema propio con el acento de Imagina.
- Una cuenta de Imagina que la persona borra a mano (habiendo otras) no se recupera con «Entrar con Imagina»: la API solo reenvía credenciales de buzones `added`. Hay que desconectar el móvil en Imagina y entrar de nuevo, o que la API ofrezca reenviar credenciales.
- Si una credencial deja de valer pero Imagina dice que nada ha cambiado (`changed: false`), «Entrar con Imagina» no la arregla: no se crea otro dispositivo con uno vigente. Decidir si `POST /refresh` debe rotar y reenviar la credencial en ese caso.
- Avisar a la persona (notificación) cuando Imagina revoca el móvil y se quitan las cuentas; hoy simplemente aparece «Entrar con Imagina» al volver a abrir la app.
- Los demás textos de Thunderbird y Mozilla (Acerca de, enlaces de ayuda y foro `app_webpage_url`, `user_forum_url`, ajustes) siguen siendo los suyos; los textos de Imagina están en inglés, español, alemán, francés, italiano y portugués de Portugal (el resto de idiomas cae al inglés).
- Idioma de la app por defecto (hoy sigue al del móvil), iconos PNG para Android antiguos (los de las carpetas `mipmap-*dpi` aún son de Thunderbird) y firma de publicación.
