# QipiGo · Piloto Android 0.1

Cambio de ruta: desarrollo y prueba en Android, sin Apple Developer ni publicación en Google Play. Proyecto nativo Java con Android Gradle Plugin 8.9.2, Gradle 8.11.1 y JDK 17. Compatible como objetivo con Android 8.0/API 26 o superior y hardware BLE, sujeto a compilación y prueba física. No contiene aún un APK: el workflow lo genera en tu repositorio.

## Subir al repositorio existente qipigo

Conserva el piloto iOS. Añade la carpeta `android` a la raíz del repositorio: al arrastrarla en Upload files deben aparecer rutas `android/app/...` y `android/settings.gradle`. No subas los archivos internos sueltos a la raíz.

Si el navegador no permite subir carpetas, usa GitHub Desktop para clonar tu repositorio, copia `android` dentro de la carpeta clonada y copia `build-android.yml` dentro de `.github/workflows`. Haz Commit y Push. Alternativamente crea archivos desde Add file > Create new file usando las rutas completas de este paquete.

El archivo `.github/workflows/build-android.yml` se puede crear directamente desde GitHub como hicimos con iOS: copia exactamente el contenido del archivo incluido. No reemplaces build-ios.yml.

En Actions elige «Generar APK Android» > Run workflow > main > Run workflow. La acción ejecuta pruebas del decodificador y compila un APK debug firmado automáticamente con clave de prueba. Cuando esté verde, descarga el artefacto «QipiGo-Android-APK» desde la sección Artifacts y extrae `app-debug.apk`.

Los runners y condiciones de uso de GitHub corresponden a tu cuenta; no se contrata ningún servicio con este paquete. Si compilas localmente con Android Studio, abre `android`, configura Gradle 8.11.1/JDK17 y ejecuta assembleDebug. El wrapper no se incluye; el workflow instala Gradle explícitamente.

## Instalar en el teléfono Android

1. Transfiere app-debug.apk a tu Android por USB o un medio propio.
2. Abre el APK y autoriza la instalación desde esa fuente cuando Android lo solicite. Completa la instalación y después puedes desactivar esa autorización.
3. Abre QipiGo Piloto. Activa Bluetooth y Localización.
4. Pulsa Iniciar detección y permite Ubicación precisa mientras usas la app, y Dispositivos cercanos en Android 12+.
5. Sal de la conexión de configuración de KBeaconPro antes de probar.

No es necesario pagar una cuenta de Google Play para esta prueba por APK. El APK debug es exclusivamente para el piloto. La clave debug del runner puede variar entre ejecuciones; si una actualización da conflicto de firma, exporta primero tus eventos y desinstala la anterior (esto borra su historial) antes de instalar. Para una distribución estable habrá que crear y custodiar una clave release propia.

## Identidades y comportamiento

UUID: `7777772E-6B6B-6D63-6E2E-636F6D000001`

| Baliza | Major | Minor |
|---|---:|---:|
| K8 ID398745 | 1 | 1 |
| K5 ID508522 | 1 | 2 |

Lee paquetes iBeacon Apple 0x004c; valida cabecera 0x0215 y UUID/Major/Minor. No usa MAC como identidad ni necesita emparejarse con la baliza. Se elige la señal elegible más fuerte entre las recibidas en los últimos 5 segundos. Umbral inicial -75dBm y espera por campaña de 60 segundos durante la sesión. Estos valores no equivalen a un radio exacto en metros.

Cada modal visible registra una vista; «Me interesa» registra un clic. Los eventos tienen ID único, fecha UTC, UUID, Major y Minor, guardado local con AtomicFile y exportación JSON. No hay INTERNET en el manifiesto; no se transmiten datos ni se captura GPS. Se pide ubicación para la detección de proximidad y se evita neverForLocation porque puede filtrar balizas.

El escaneo se detiene al salir de la app, abrir otro diálogo de sistema o bloquear la pantalla. Al volver pulsa Iniciar. No incluye segundo plano, notificaciones, servidor, sincronización, campañas remotas ni dashboard. Las campañas de ejemplo vienen incluidas y funcionan sin internet.

## Verificación en campo

- Una baliza a la vez: comprobar campaña K8 y después campaña K5.
- Ambas presentes: comprobar diferenciación y selección por señal.
- Cerrar modal: no repetir la misma campaña antes de 60s; sí permitir la otra.
- Apagar baliza: sin detección reciente tras 5s sin lecturas válidas.
- Sin conexión: registrar, reiniciar la app y verificar persistencia/exportación.
- Denegar permisos o apagar Bluetooth: comprobar mensajes y recuperación.
- Bloquear pantalla: comprobar detención y reanudación manual.

## Estado de validación

Se revisaron XML, YAML y estructura localmente. Este entorno no tiene JDK, Gradle ni SDK Android: no se ejecutó compilación ni JUnit aquí. El workflow ejecutará ambos. La recepción BLE real y compatibilidad con el teléfono del usuario siguen pendientes. No afirmar que ya existe un APK hasta que finalice la acción.

Documentación: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions y https://developer.android.com/build/releases/agp-8-9-0-release-notes
