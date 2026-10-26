# QipiGo · Piloto iPhone v0.1

Código fuente de una prueba nativa SwiftUI/Core Location. No es un IPA instalable ni el producto completo. Preparado para iOS 15 o superior, sujeto a compilación y prueba física. Equipo objetivo principal: iPhone 13 del usuario.

## Qué incluye

- Detección real iBeacon por UUID y Major mediante Core Location.
- K8 ID 398745: Major 1, Minor 1; K5 ID 508522: Major 1, Minor 2.
- UUID común: `7777772E-6B6B-6D63-6E2E-636F6D000001`.
- Dos campañas de ejemplo incluidas en la app, disponibles sin internet.
- Modal para la baliza elegible con mayor RSSI en cada lote recibido (umbral -75 dBm). Espera de 60 segundos por campaña desde su impresión visible.
- Vistas al aparecer el modal y clics explícitos, con UUID de evento y fecha. Historial JSON local persistente y exportable desde Archivos.
- Caducidad de señales tras 5 segundos sin recepción válida. Se ignora RSSI 0.
- Solicitud de Localización mientras se usa la app, errores y acceso a Ajustes.
- Detención al pasar a segundo plano. No se solicita acceso permanente ni se captura GPS.

## Qué queda pendiente

No incluye backend, dashboard, sincronización, campañas remotas, enlaces comerciales, Android, geolocalización ni operación en segundo plano. No ha sido compilado con Xcode en el entorno de creación (Linux sin SDK Apple), instalado ni probado por radio. El workflow permite verificar la compilación después de subir el proyecto a un repositorio propio. Las comprobaciones estructurales no sustituyen esa compilación.

La prueba usa Swift nativo para aislar la validación iBeacon sin depender de una librería BLE multiplataforma. Esta decisión no fija la arquitectura del producto final.

## Desde Windows: comprobar compilación en la nube

1. Descomprime este paquete.
2. Crea un repositorio privado propio en GitHub. Sube **el contenido** de esta carpeta a la raíz, incluyendo `.github/workflows/build-ios.yml` (puedes hacerlo con Git desde Windows).
3. En Actions, ejecuta «Comprobar compilación iOS». Revisa antes las condiciones y posibles cargos de los runners macOS de tu cuenta.
4. La acción genera el proyecto con XcodeGen y ejecuta xcodebuild para iPhone **sin firma**. Su resultado NO se puede instalar en un iPhone. No contiene credenciales ni requiere que compartas contraseñas.
5. Si falla, conserva el log de compilación para corregirlo antes de intentar distribución.

## Instalación real: aún pendiente

Con Windows y sin acceso a Mac, hay que elegir y configurar un servicio de compilación/distribución compatible con proyectos Xcode nativos y una membresía Apple Developer para firma. Este paquete no configura ni paga servicios y no aporta certificados. EAS no se ha configurado para este proyecto Swift nativo.

Una vez disponible una Mac (local o servicio adecuado): instalar Xcode y XcodeGen; ejecutar `xcodegen generate`; abrir `QipiGo.xcodeproj`; seleccionar un Team autorizado, identificador de aplicación propio y el iPhone de prueba; compilar y firmar. Para TestFlight se debe crear el registro en App Store Connect, archivar con firma válida, subir y seguir el procesamiento/revisión que corresponda. Una cuenta personal gratuita permite pruebas locales con Xcode con las limitaciones de Apple; no equivale a distribución TestFlight.

No compres membresías hasta confirmar el servicio y su compatibilidad. No envíes Apple ID, contraseña, claves privadas ni certificados por chat.

## Prueba física de aceptación

1. Comprueba ambas identidades en KBeaconPro, incluido UUID completo. Cierra la conexión de configuración. La captura previa mostraba UUID N/A: sigue pendiente resolverlo con la recepción en esta app.
2. Activa Bluetooth y Localización. Abre QipiGo, pulsa Iniciar y permite Localización mientras usas la app.
3. Prueba primero con una sola baliza encendida. Acércala hasta superar el umbral; debe aparecer su campaña. El modal visible suma una vista, no cada paquete BLE.
4. Pulsa Me interesa: debe registrar un clic. Cierra y vuelve a detectar antes de 60 segundos: no debe repetir esa campaña.
5. Repite con la otra baliza; debe mostrar campaña distinta. Con ambas presentes se escoge la elegible con mejor señal en el lote recibido; esto no garantiza cercanía física exacta.
6. Apaga o aleja la baliza: tras unos 5 segundos sin nuevas lecturas válidas debe indicar Sin detección reciente.
7. Sin internet, repite la prueba. Cierra y vuelve a abrir la app; verifica conservación del historial. Exporta JSON y comprueba IDs únicos y fechas ISO 8601.
8. Bloquea el teléfono: la prueba detiene el escaneo. Al regresar, pulsa Iniciar. Deniega permisos y comprueba el mensaje y acceso a Ajustes.

Registrar por sesión: teléfono/iOS, baliza, estado de app, tiempo hasta detección, RSSI y resultado. No declarar alcance en metros por el valor de potencia.

## Fuentes técnicas

- https://developer.apple.com/documentation/corelocation/cllocationmanager/startrangingbeacons(satisfying:)
- https://developer.apple.com/documentation/corelocation/cllocationmanagerdelegate
- https://developer.apple.com/help/account/basics/about-your-developer-account/
- https://github.com/yonaskolb/XcodeGen/blob/master/Docs/ProjectSpec.md

Estado: fuente inicial preparada; compilación Apple, firma, instalación y validación física pendientes.
