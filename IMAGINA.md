# Imagina Mail

Versión de [Thunderbird para Android](https://github.com/thunderbird/thunderbird-android) con la marca de Imagina y un único acceso: **«Entrar con Imagina»** (móvil y código SMS en el navegador integrado). Los buzones y sus credenciales los da la API de dispositivos de Imagina; la app nunca pide un servidor, un correo ni una contraseña. Forma parte del Cloud de Imagina: `docs/PLAN_CLOUD.md` en el repositorio de Imagina (hitos C7.3, C11.3 y C12; prototipo en C0.9).

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

Los servidores de correo (Migadu) y la credencial de cada buzón no son parámetros: los da la API de dispositivos. El proyecto de Firebase tampoco: sus valores salen de `app-imagina/google-services.json` (ver «Notificaciones»).

`imagina/imagina.local.properties` (fuera de git) solo puede sobrescribir claves de `imagina.properties` en cada máquina; una clave que no esté allí se ignora y nunca llega a la APK. Antes llevaba el buzón de prueba (`IMAGINA_TEST_MAIL_*`): ya no se usa y esas líneas se pueden borrar.

## Cambios en ficheros de Thunderbird

| Fichero | Cambio | Motivo |
| --- | --- | --- |
| `settings.gradle.kts` | `":app-imagina"` en la lista de módulos | Compilar la app de Imagina. |
| `app-common/.../BaseApplication.kt` | Propiedad `allowsDefinitionOverride` (falsa por defecto) que pasa a `DI.start` | Thunderbird prohíbe sustituir piezas de la inyección de dependencias; la app de Imagina lo activa para poner su navegación de acceso en lugar de la de Thundermail. Thunderbird y K-9 no cambian. |
| `.gitignore` | `imagina/imagina.local.properties` y `app-imagina/google-services.json` | Que los valores locales y el fichero de Firebase de la app no entren en git. |
| `backend/imap/.../ImapBackendPusher.kt` | Las carpetas actuales se leen dentro del cerrojo que las escribe (`updateFolders`), marcado «Imagina (IMAGINA.md)». | Fallo de Thunderbird: al arrancar el push, la actualización de su propia corrutina podía leer la lista vacía justo antes de que llegara INBOX y luego parar el push que esta había arrancado; tras «Entrar con Imagina» el correo dejaba de llegar al momento hasta reiniciar la app. Candidato a enviarse a Thunderbird. |

El resto (acceso sin contraseña, bienvenida, avisos al momento, `User-Agent`, sincronización) se hace sin tocar más ficheros de Thunderbird: con piezas de la inyección de dependencias que Imagina sustituye (ver «Cómo engancha») y recursos de `app-imagina`.

## Lo propio de Imagina

| Dónde | Qué |
| --- | --- |
| `app-imagina/` | Módulo de app copiado de `app-thunderbird` (así hace Thunderbird con K-9): id, nombre, icono y versión de Imagina; sin `dependencyGuard` ni `badging`; el `debug` sin sufijo de id para que el OAuth vuelva a la app. De las copias, `ThunderbirdKoinModule.kt` solo incluye `imaginaModule` y `ThunderbirdApp.kt` arranca `ImaginaStartup`. Además: la dependencia de Firebase, los valores del `google-services.json` convertidos en recursos (`build.gradle.kts`) y el servicio de Firebase con sus ajustes sin analítica (`AndroidManifest.xml`). |
| `app-imagina/src/main/kotlin/.../imagina/` | Todo el código de Imagina (abajo). |
| `app-imagina/src/main/res/` | Nombre (`strings.xml`), textos de «Entrar con Imagina» y del aviso «Comprobando tu correo…» (`imagina_*` en `values`, `values-es`, `-de`, `-fr`, `-it`, `-pt-rPT`), `User-Agent` (`constants.xml`), color de la loseta (`imagina_colors.xml`) e imágenes generadas (ver abajo). |
| `app-imagina/src/test/.../imagina/` | Pruebas de la API (contra un servidor local), de la sincronización (con dobles), de la configuración de servidores y de las notificaciones (hash del buzón, a qué cuenta toca un aviso, subida del token, intervalo de comprobación). |
| `imagina/` | Parámetros, fuentes SVG de marca, generador de imágenes y este documento. |

Código de `imagina/`:

| Fichero | Qué hace |
| --- | --- |
| `ImaginaModule.kt` | Inyección de dependencias de Imagina. Se carga la última y sustituye las pantallas de Thunderbird. |
| `ImaginaOAuthConfigurationFactory.kt` | Imagina como proveedor OAuth (`openid cloud.devices`, PKCE, `<id>://oauth2redirect`) y direcciones de la API. |
| `ImaginaSignInScreen.kt`, `ImaginaSignInViewModel.kt`, `ImaginaSignInMode.kt` | «Entrar con Imagina». Dos modos: primera pantalla de la app (`Onboarding`) y volver a entrar (`Reconnect`). Muestra el `message` que da Imagina cuando rechaza algo. |
| `ImaginaNavigation.kt` | Las pantallas que sustituyen a las de Thunderbird (bienvenida, editar servidores, añadir cuenta, rutas de Thundermail). |
| `ImaginaDevicesApi.kt` | Cliente de la API de dispositivos (`POST /`, `GET /{id}`, `POST /{id}/refresh`, `DELETE /{id}`, y `PUT` y `DELETE /{id}/push` para el token de Firebase; el 410 `revoked` es `ImaginaDeviceRevokedException` también ahí). |
| `ImaginaSession.kt` | Token de acceso válido en cada llamada: renueva con AppAuth (refresh token rotativo) guardando el nuevo antes de usarlo. |
| `ImaginaDeviceStore.kt` | Lo que recuerda la app (SharedPreferences propio `imagina_device`, privado y sin copia de seguridad): id del dispositivo, estado OAuth, qué cuentas hizo, si hay que volver a entrar, el último token de Firebase, para qué dispositivo lo tiene Imagina y cada cuántos minutos se dijo a las cuentas que miren el correo. |
| `ImaginaDeviceSynchronizer.kt` | La lógica: registrar el dispositivo (solo si no hay o está revocado), una cuenta por buzón, ponerlas al día, quitar las de buzones retirados, desconectar. También dar a Imagina el token de Firebase (al entrar, en cada sincronización si Imagina no lo tiene y cuando Firebase lo cambia), olvidarlo al desconectar y fijar cada cuánto miran el correo las cuentas. |
| `ImaginaAccountProvisioner.kt` | Crea, actualiza y borra cuentas con las piezas de Thunderbird (`AccountCreator`, `AccountServerSettingsUpdater`, `BackgroundAccountRemover`) y cambia cada cuánto miran el correo (`Account.updateAutomaticCheckIntervalMinutes` y `K9JobManager.scheduleMailSync`, como la pantalla de ajustes): 15 minutos, o 60 con el push activo (`ImaginaCheckFrequency`). Sin carpetas push. |
| `ImaginaSyncScheduler.kt` | WorkManager: la sincronización cada 6 horas, al arrancar la app y tras entrar (`ImaginaSyncWorker`), y la comprobación de una bandeja al llegar un aviso (`ImaginaMailCheckWorker`, trabajo urgente). |
| `ImaginaStartup.kt` | Lo que arranca con la app: sincronización (también si Imagina aún no tiene el token de Firebase), oferta de volver a entrar y desconexión al borrar la última cuenta. |
| `ImaginaMessagingService.kt` | El servicio de Firebase (`FirebaseMessagingService`): solo pasa los avisos y los tokens nuevos a `ImaginaPushHandler`. |
| `ImaginaPushHandler.kt` | Qué hace la app con lo que llega de Firebase: un aviso `new_mail` busca la cuenta de ese buzón y pide comprobar su bandeja; un buzón que no conoce pide sincronizar con Imagina (como mucho cada 15 minutos); un token nuevo se da a Imagina. |
| `ImaginaPushMessage.kt` | El aviso: `{"type": "new_mail", "mailbox": "<16 hex>"}` y el cálculo del buzón, `sha256("{deviceId}:{dirección en minúsculas}")` (16 primeros caracteres hex). |
| `ImaginaPushRegistrar.kt` | Mantiene el token en Imagina (`PUT`/`DELETE /{id}/push`) y dice si el push está activo: Firebase configurado y token aceptado por Imagina para el dispositivo actual. |
| `ImaginaPushTokenSource.kt` | De dónde sale el token (`FirebaseMessaging`), y si el build lleva Firebase (`FirebaseApp.getApps`). |
| `ImaginaInboxChecker.kt` | Comprobar la bandeja de una cuenta: `ThunderbirdImaginaInbox` llama a `MessagingController.synchronizeMailboxBlocking`, lo mismo que hace el push de Thunderbird, y `ImaginaInboxChecker` las hace de una en una por cuenta y junta las que esperan. |
| `ImaginaErrors.kt` | Errores tipados (API, red, sesión, dispositivo revocado…). |

**Cómo engancha con Thunderbird**: el lanzador (`feature/launcher`) saca todas sus pantallas de objetos de navegación de la inyección de dependencias. `BaseApplication.allowsDefinitionOverride` deja a Imagina sustituir cuatro:

- `OnboardingNavigation` → `ImaginaOnboardingNavigation`: la primera pantalla es «Entrar con Imagina», sin la bienvenida con los logos y textos de Thunderbird y Mozilla; después, el paso de permisos (notificaciones) y la bandeja.
- `AccountEditNavigation` → `ImaginaAccountEditNavigation`: **gancho de «nunca pedir contraseña»**. Todo lo que lleva a editar los servidores de una cuenta (el aviso «fallo de autenticación» de las notificaciones, los ajustes de la cuenta y la lista de mensajes) abre `FeatureLauncherTarget.AccountEdit*Settings`, que ya no muestra campos de servidor y contraseña sino «Entrar con Imagina» en modo `Reconnect`.
- `AccountSetupNavigation` → `ImaginaAccountSetupNavigation`: «Añadir cuenta» abre también «Entrar con Imagina» (trae los buzones que falten) en lugar del asistente manual.
- `ThundermailNavigation` → `ImaginaNavigation`: cualquier ruta de Thundermail acaba en lo mismo.

**Entrar** (`ImaginaDeviceSynchronizer.connect`): tras el OAuth (AppAuth de Thunderbird, `AccountOAuthViewModel`), con el token se llama a la API. Si el móvil no tiene dispositivo guardado o Imagina lo revocó (o no lo conoce, 404), `POST /` y se crea **una cuenta por buzón** (nombre: la dirección, o `dirección · empresa` si la persona tiene buzones de varias empresas; remitente: `name`). Si el dispositivo sigue vigente se reutiliza su id: `GET /{id}` y, si `changed`, `POST /{id}/refresh`. Una dirección que ya tiene cuenta recibe la credencial nueva en lugar de duplicarse. Si algo falla a medias se deshace (se desconecta el dispositivo y se quitan las cuentas hechas) para no dejar buzones sin credencial.

**Sincronizar** (C7.3, `sync`): token de acceso (15 minutos) renovado con el refresh token (7 días, rotativo) → `GET /devices/{id}`. `changed` → `POST /refresh`: cuenta nueva por cada `added`, se quita la de cada `removed`. `revoked` (o 410 al refrescar) → se quitan las cuentas de Imagina y las credenciales, se cancela la sincronización y, sin cuentas, la app vuelve a «Entrar con Imagina». Si el refresh token ya no vale (o Imagina rechaza el token con 401, 403 o 404) el correo sigue funcionando: se anota `needsSignIn` y la primera vez que se abre la bandeja en la siguiente ejecución de la app se ofrece «Entrar con Imagina» (con «Ahora no»); entrar de nuevo reutiliza el dispositivo si no está revocado. Sin red, WorkManager reintenta con espera creciente.

**Desconectar**: si la persona borra la última cuenta de Imagina (se oye con `Preferences.addAccountRemovedListener`), `DELETE /devices/{id}` (lo mejor posible) y se olvida todo.

**Correo nuevo, sin push en el móvil**: el push de Thunderbird (IMAP IDLE desde el móvil) necesita, desde Android 14, que la persona permita «Alarmas y recordatorios» a mano, y una notificación fija mientras escucha. Decisión del 9 oct 2026: no pedirlo. Las cuentas de Imagina no marcan ninguna carpeta como push; el correo llega al momento por el aviso de Imagina (ver «Notificaciones») y, por si falla, cada cuenta mira el correo cada 60 minutos con el push activo, o cada 15 sin él.

**`User-Agent`**: `message_header_mua` en `app-imagina/src/main/res/values/constants.xml` («Imagina Mail»), que gana al de `legacy/ui/legacy` («Thunderbird for Android») igual que hace `app-k9mail`.

**Trabajador de WorkManager**: `K9WorkerFactory` solo construye clases de `com.fsck.k9` y devuelve `null` para el resto, así que `ImaginaSyncWorker` lo crea la fábrica por defecto de WorkManager (constructor `(Context, WorkerParameters)`) y toma sus piezas de Koin.

## Notificaciones

Imagina vigila cada buzón por IMAP IDLE desde su servidor (C12 del plan del Cloud) y, cuando llega correo, manda un **mensaje de datos de Firebase Cloud Messaging con prioridad alta** a cada móvil que tenga ese buzón. Un mensaje de prioridad alta deja que la app empiece trabajo desde segundo plano, sin alarmas exactas ni notificación fija, y por eso el móvil no mantiene ninguna conexión IDLE.

- **Proyecto de Firebase**: `imagina-84bd9` (el mismo que el de Imagina Cloud). La app es `build.imagina.mail` y su `google-services.json` se descarga de la consola de Firebase (Configuración del proyecto › Tus apps › `build.imagina.mail`) y va en `app-imagina/`, **fuera de git** (está en `.gitignore`). Como en Imagina Cloud, el plugin de Google Services no se usa: `app-imagina/build.gradle.kts` lee el fichero, elige el cliente cuyo paquete es `IMAGINA_APPLICATION_ID` y pone sus valores como recursos de cadena (`google_app_id`, `google_api_key`, `gcm_defaultSenderId`, `project_id`, `google_storage_bucket`, con `resValue`), que es de donde Firebase se inicia solo. Un fichero sin ese cliente o mal formado rompe la compilación con el motivo. **Sin el fichero la app compila y funciona como antes**: sin push, comprobando el correo cada 15 minutos.
- **Registro**: tras «Entrar con Imagina» (y en cada sincronización, mientras Imagina no tenga el token del dispositivo en uso, y cuando Firebase lo cambia, `onNewToken`) la app hace `PUT /api/v1/cloud/devices/{id}/push` con `{"token": "…"}` y el token de acceso de siempre (204). Al desconectar el móvil (borrar la última cuenta) hace `DELETE …/push` lo mejor posible y olvida el token; si Imagina revocó el dispositivo (410 `revoked`) lo olvida sin llamar. Si Imagina rechaza el token (4xx sobre el token) el push queda apagado y no se reintenta hasta la siguiente sincronización; si no se le puede preguntar (sin red, 5xx) la sincronización se reintenta con espera creciente.
- **El aviso**: `{"type": "new_mail", "mailbox": "<16 hex>"}`, sin contenido. `mailbox` son los 16 primeros caracteres hexadecimales en minúsculas de `sha256("{idDelDispositivo}:{dirección del buzón en minúsculas}")`: solo este móvil sabe de qué buzón se trata, Firebase no. `ImaginaPushHandler` busca la cuenta de Imagina cuyo hash coincide y pide comprobar su bandeja; un hash que no conoce se ignora (y, si la última sincronización con Imagina tiene más de 15 minutos, pide una, por si es un buzón nuevo).
- **Cómo despierta la app**: `ImaginaMessagingService.onMessageReceived` → `ImaginaSyncScheduler.checkInbox(uuid)` → trabajo **urgente** de WorkManager (`ImaginaMailCheckWorker`, `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`) → `ImaginaInboxChecker` → `MessagingController.synchronizeMailboxBlocking(cuenta, INBOX)`: lo mismo que hace el push de Thunderbird al oír un mensaje nuevo, así que descarga el correo y muestra su notificación de correo nuevo de siempre. Respeta el ajuste «nunca sincronizar en segundo plano» como la comprobación periódica. Si llegan muchos avisos seguidos, las comprobaciones que esperan se juntan (una que empezó después de recibir el aviso ya vio ese correo). Antes de Android 12 el trabajo urgente es un servicio en primer plano y enseña la notificación de «trabajo en segundo plano» de Thunderbird con «Comprobando tu correo…» mientras dura.
- **Intervalo de comprobación**: con el push activo (Firebase configurado y token aceptado por Imagina para este dispositivo) las cuentas de Imagina miran el correo cada **60 minutos**, como red de seguridad por si un aviso no llega; sin push, cada **15**. Se aplica a las cuentas nuevas al crearlas y a las que ya existen cuando el estado cambia (no se toca si la persona lo cambió a mano y el estado no ha cambiado).
- **Privacidad**: Firebase solo entrega; el manifiesto desactiva la analítica (`firebase_analytics_collection_deactivated`, `google_analytics_adid_collection_enabled=false`, `google_analytics_ssaid_collection_enabled=false`), como el manifiesto de Imagina Cloud.
- **Dependencia**: `com.google.firebase:firebase-messaging:25.1.3` en `app-imagina/build.gradle.kts` (todos los sabores, fijada ahí y no en el catálogo de versiones de Thunderbird para que actualizar Thunderbird no la toque). La 26.0.0 (7 oct 2026) exige API 24 y Thunderbird llega a la 23, así que no se sube hasta que cambie su `sdkMin`. Motivo: recibir el mensaje de prioridad alta y el token; no hay otra forma de que Imagina despierte la app sin una conexión abierta desde el móvil. Trae los servicios de Google Play (`play-services-*`) de forma transitiva; sin ellos (móviles sin Google) no hay token y todo sigue como sin push.
- **Qué no hace**: no abre ninguna conexión IDLE ni pide «Alarmas y recordatorios», no usa las carpetas push de Thunderbird, y no manda el correo por Firebase (el mensaje no lleva contenido: el correo se descarga por IMAP como siempre).

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
2. Cambiar `THUNDERBIRD_ANDROID_TAG` en `imagina/imagina.properties`. Los conflictos solo pueden venir de los ficheros de la tabla de arriba (si Thunderbird ya corrigió el de `ImapBackendPusher`, quedarse con el suyo).
3. Comparar `app-thunderbird` entre la versión anterior y la nueva (`git diff THUNDERBIRD_<anterior> THUNDERBIRD_<nueva> -- app-thunderbird`) y llevar a `app-imagina` lo que cambie (dependencias, módulos de Koin, manifest), salvo nombre, id e icono.
4. Comprobar que siguen existiendo los puntos de enganche: `OnboardingNavigation`/`OnboardingRoute.Onboarding`, `AccountEditNavigation`, `AccountSetupNavigation`, `ThundermailNavigation`, `AccountSetupExternalContract.AccountCreator`, `AccountEditExternalContract.AccountServerSettingsUpdater`, `BackgroundAccountRemover`, `Preferences.addAccountRemovedListener`, la clave `<uuid>.<carpeta>.pushEnabled` de `com.fsck.k9.mailstore.FolderSettingsProvider`, el recurso `message_header_mua`, que `K9WorkerFactory` siga dejando pasar a las clases que no son de `com.fsck.k9` y, para las notificaciones, `MessagingController.synchronizeMailboxBlocking` (lo que usa `AccountBackendPusherCallback`), `LegacyAccountDto.inboxFolderId` y `updateAutomaticCheckIntervalMinutes`, `K9JobManager.scheduleMailSync`, `FolderRepository.getFolderServerId`, `GeneralSettingsManager` (`network.backgroundOps`) y `BackgroundWorkNotificationController`.
5. Subir la versión de `firebase-messaging` si procede (una publicada hace al menos dos semanas) y compilar con y sin `google-services.json`.
6. Compilar, pasar `./gradlew :app-imagina:testFossDebugUnitTest` y probar en el emulador o el móvil: «Entrar con Imagina», cuentas creadas, recibir y enviar, aviso al momento.

## Pendiente para la app de verdad

- Probado en el emulador contra producción el 9 oct 2026: «Entrar con Imagina» con la sesión que ya tenía el navegador (sin volver a pedir el código), la cuenta del buzón personal con su identidad propia y sus carpetas por IMAP. Falta probar varios buzones de varias empresas, revocar el móvil desde Imagina y la sincronización de cada 6 horas.
- Notificaciones (C12): falta dar de alta la app `build.imagina.mail` en el proyecto `imagina-84bd9` de Firebase, descargar su `google-services.json` a `app-imagina/` y probar de punta a punta en el emulador con servicios de Google (registro del token en producción, un aviso real, la notificación de correo nuevo con la app cerrada, el cambio a 60 minutos). El CI no lleva el fichero (compila sin push); falta, como en Imagina Cloud, un secreto `GOOGLE_SERVICES_JSON` que lo escriba antes de compilar. Los sabores `beta` y `daily` tienen otro id de aplicación (`.beta`, `.daily`) que no está dado de alta en Firebase: su `google-services.json` no los incluye y no tendrían push. Desde `firebase-messaging` 25.1 `getToken`, `deleteToken` y `onNewToken` están marcados como obsoletos («se quitarán en una versión futura») a favor de registrar con el id de instalación de Firebase (`register()`, `onRegistered()` y el metadato `firebase_messaging_installation_id_enabled`); la app sigue con el token porque es lo que dice el contrato con Imagina (`PUT …/push` con `token`): cuando Firebase lo quite, hay que acordar con Imagina el cambio y llevarlo a `ImaginaMessagingService` y `ImaginaPushTokenSource`. Probar también el trabajo urgente antes de Android 12 (servicio en primer plano), Doze y el ahorro de batería de los fabricantes, y qué pasa en un móvil sin Google (sin token: todo como sin push).
- Colores de Imagina: los botones y la barra siguen con la paleta de Thunderbird (su tema de Compose `ThunderbirdBoltTheme` y los temas XML de las pantallas antiguas); hace falta un tema propio con el acento de Imagina.
- Una cuenta de Imagina que la persona borra a mano (habiendo otras) no se recupera con «Entrar con Imagina»: la API solo reenvía credenciales de buzones `added`. Hay que desconectar el móvil en Imagina y entrar de nuevo, o que la API ofrezca reenviar credenciales.
- Si una credencial deja de valer pero Imagina dice que nada ha cambiado (`changed: false`), «Entrar con Imagina» no la arregla: no se crea otro dispositivo con uno vigente. Decidir si `POST /refresh` debe rotar y reenviar la credencial en ese caso.
- Avisar a la persona (notificación) cuando Imagina revoca el móvil y se quitan las cuentas; hoy simplemente aparece «Entrar con Imagina» al volver a abrir la app.
- Los demás textos de Thunderbird y Mozilla (Acerca de, enlaces de ayuda y foro `app_webpage_url`, `user_forum_url`, ajustes) siguen siendo los suyos; los textos de Imagina están en inglés, español, alemán, francés, italiano y portugués de Portugal (el resto de idiomas cae al inglés).
- Idioma de la app por defecto (hoy sigue al del móvil), iconos PNG para Android antiguos (los de las carpetas `mipmap-*dpi` aún son de Thunderbird) y firma de publicación.
