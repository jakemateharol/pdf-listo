# PDF Listo — Decisiones técnicas

Cada decisión explica **qué se eligió**, **qué alternativas había** y **por qué**. Lo que se dejó para versiones
posteriores aparece al final.

---

## 1. Todo local, sin internet

- **Elegido:** todo el procesamiento (PDF, OCR, QR, Excel, base de datos) corre en el equipo del usuario.
- **Alternativas:** servicios en la nube (APIs de OCR, conversores en línea, modelos de lenguaje).
- **Por qué:** los PDF que se editan suelen tener datos personales (DNI, contratos, facturas). Enviarlos a un
  servidor sería un riesgo de privacidad y además obligaría a tener conexión. Lo que en el futuro necesite internet
  (versión 1.3) vendrá **desactivado por defecto y con aviso**.

## 2. El original nunca se modifica

- **Elegido:** cada operación escribe un archivo **nuevo** con `MotorPdf` + `EscrituraSegura`: se guarda en un
  temporal, **se vuelve a abrir para verificarlo** y recién entonces se renombra.
- **Alternativa:** modificar el archivo en su lugar (como muchos editores).
- **Por qué:** si algo falla a la mitad (corte de luz, PDF raro, error de programa), el original queda intacto y nunca se
  entrega un archivo dañado. Es la regla más importante del producto.

## 3. Versiones en archivos temporales (deshacer = cambiar de versión)

- **Elegido:** `SesionEdicion` guarda cada resultado como una versión en `%APPDATA%\PDFListo\trabajo`. Deshacer y
  rehacer solo cambian cuál versión se muestra. Un diario (`sesion.properties`) y un bloqueo (`sesion.lock`) permiten
  **recuperar el trabajo** si la app se cerró de golpe.
- **Alternativa:** deshacer en memoria (guardar el documento modificado en RAM o una lista de comandos inversos).
- **Por qué:** deshacer en memoria es frágil con PDF grandes (consume mucha RAM) y difícil de hacer bien para operaciones
  que no tienen inversa (comprimir, tachar, OCR). Con archivos, deshacer siempre es exacto y sobrevive a un cierre
  inesperado. Costo: usa disco (se limpia al cerrar; lo de más de 7 días se borra).

## 4. Intérprete de pedidos por reglas, con lista cerrada de acciones

- **Elegido:** `InterpretePedidos` reconoce frases con reglas (expresiones regulares, números, rangos de páginas) y solo
  puede elegir entre las acciones del enum `Accion` (unir, rotar, eliminar, tamaño, OCR, marca de agua, numerar,
  metadatos, quitar contraseña, comprimir). Si falta un dato, **pregunta**. Siempre muestra un **plan** antes de actuar.
- **Alternativa:** un modelo de lenguaje (LLM) que interprete libremente.
- **Por qué:** las reglas funcionan sin internet, son instantáneas, se pueden probar con pruebas automáticas y **no pueden
  inventar acciones** ni hacer algo distinto de lo que muestra el plan. El precio es que frases muy rebuscadas pueden no
  entenderse; el plan visible y las preguntas lo compensan. Un LLM opcional queda para la versión 1.3.

## 5. Verificación releyendo el archivo final

- **Elegido:** `Verificador` abre el resultado y comprueba cada requisito leyendo el archivo: peso real, páginas, que el
  número de página esté **en el margen pedido** (no en cualquier parte), marca de agua, texto buscable, datos sensibles,
  metadatos y contraseña.
- **Alternativa:** confiar en que la operación "debió" funcionar.
- **Por qué:** una operación puede terminar sin error y aun así no cumplir (p. ej. la compresión no alcanza el peso, o un
  número queda tapado). Releer el archivo es la única forma honesta de decir «Cumple». Ejemplo real encontrado al
  preparar este manual: la verificación juntaba la última línea del texto con el número de página («129» + «1») y
  marcaba «Revisar» aunque la numeración estaba bien; se corrigió y quedó una prueba automática.

## 6. Tachado = página a imagen + texto invisible

- **Elegido:** `Tachar` convierte cada página tachada en imagen (con los recuadros ya quemados) y le agrega encima una capa
  de texto invisible con el texto que **no** se tachó. `VerificadorTachado` relee el resultado y, si el dato sigue siendo
  recuperable, la app **no** lo declara seguro.
- **Alternativa:** editar el contenido del PDF para borrar solo las letras del dato.
- **Por qué:** el borrado fino falla **en silencio** con texto dibujado como trazos, fuentes raras, contenido compartido
  entre páginas o letras sueltas. Un tachado que parece hecho pero deja el dato copiable es peor que no tachar. Costo: las
  páginas tachadas pesan más y pierden enlaces (se informa). El tachado «híbrido» queda para la versión 1.2.

## 7. Spring Boot + SQLite

- **Elegido:** PDF Listo usa Spring Boot y JavaFX: Spring (inyección de dependencias, sin servidor web) y JPA,
  con la base de datos en **SQLite** (un archivo local).
- **Alternativa:** guardar todo en archivos de texto o JSON; no usar Spring.
- **Por qué:** historial, recientes, recetas, perfiles y reglas de detección son datos con consultas (p. ej. contar
  combinaciones repetidas para aprender recetas). SQLite no necesita instalar un servidor y Spring ordena el código en
  servicios pequeños y fáciles de probar. Costo: la app tarda un par de segundos más en abrir.

## 8. Apache PDFBox 3 como motor de PDF

- **Elegido:** PDFBox 3.0.3 (Apache 2.0) para leer, dibujar páginas, buscar texto, modificar y cifrar.
- **Alternativas:** iText (licencia AGPL o comercial), MuPDF (AGPL, nativo), pdf.js (navegador).
- **Por qué:** licencia libre sin obligaciones de abrir el código del producto, 100 % Java, mantiene la posición de cada
  letra (necesaria para tachar y verificar márgenes). Las páginas se dibujan en un hilo propio con prioridades
  (`DocumentoPdf`) para que la ventana nunca se congele. Los PDF grandes usan disco como apoyo (`CargaPdf`).

## 9. OCR con Tesseract (Tess4J) y datos `tessdata_fast`

- **Elegido:** Tesseract 5.4 mediante Tess4J, con idiomas español e inglés `tessdata_fast` (Apache 2.0) dentro de la app.
  El texto reconocido se agrega como capa invisible: la página se ve igual.
- **Alternativas:** OCR en la nube; `tessdata_best` (más preciso pero ~10 veces más pesado y lento).
- **Por qué:** funciona sin internet y con calidad suficiente en escaneos normales. Se descartó el PDFBox 2 que trae Tess4J
  para no tener dos versiones. Tesseract necesita las librerías de Visual C++: el instalador las incluye (ver 11).

## 10. Facturas solo con QR (ZXing)

- **Elegido:** leer el código QR de los comprobantes electrónicos de SUNAT con ZXing (Apache 2.0), a 200 y 300 dpi.
- **Alternativa:** leer el texto impreso de la factura (OCR + reglas por formato).
- **Por qué:** el QR tiene un formato fijo (RUC, tipo, serie, número, IGV, total, fecha, cliente): es exacto y validable.
  Leer el texto impreso cambia con cada emisor y es poco confiable; queda para la versión 1.1.

## 11. Instalador con jpackage, Java incluido y carpeta `native`

- **Elegido:** `packaging/construir-instalador.ps1` usa **jpackage** (incluido en el JDK) para crear una aplicación con su
  propio Java recortado (jlink), un `.zip` portable y, si hay WiX 3, un instalador `.exe` por usuario (sin permisos de
  administrador). La carpeta `app\native` lleva las DLL de Tesseract/Leptonica y `msvcp140`, `vcruntime140`,
  `vcruntime140_1`; la app arranca con `-Djna.library.path=$APPDIR\native`. Memoria: `-XX:MaxRAMPercentage=60`.
- **Alternativas:** pedir al usuario que instale Java y Visual C++; instaladores de terceros (Inno Setup, launch4j).
- **Por qué:** el usuario final no debe instalar nada técnico. Windows busca las DLL que necesita Tesseract primero en la
  carpeta de la propia DLL, así el OCR funciona aunque el equipo no tenga Visual C++. jpackage es la herramienta oficial
  de Java. Costo: el instalador pesa ~130 MB. Además, la app avisa al iniciar si faltan esas librerías y tiene
  «Ayuda → Comprobar componentes».

## 12. Word a PDF solo con LibreOffice

- **Elegido:** si LibreOffice está instalado, se usa en modo sin ventana con un perfil temporal propio. Si no, la opción
  aparece desactivada con un mensaje de qué instalar.
- **Alternativa:** convertir Word con bibliotecas Java (Apache POI + dibujo propio).
- **Por qué:** reproducir con fidelidad tablas, fuentes, encabezados y saltos de página de Word no es confiable sin un
  procesador de textos real. Es mejor desactivarlo que entregar un PDF mal convertido. Limitación: no se pudo probar la
  conversión real en el equipo de desarrollo porque no tiene LibreOffice.

## 13. Interfaz: JavaFX + AtlantaFX, FXML + código

- **Elegido:** JavaFX 21 con el tema AtlantaFX (Primer claro/oscuro) e íconos Feather (Ikonli). La ventana principal en
  FXML; los paneles y formularios en código.
- **Por qué:** JavaFX es la interfaz moderna de Java de escritorio. AtlantaFX da modo oscuro automático usando variables
  de color (`-color-*`), sin duplicar estilos. Todo lo lento corre en segundo plano y vuelve con `Platform.runLater`.

## 14. Pruebas automáticas en cada fase

- **Elegido:** JUnit 5 sobre las partes críticas: operaciones, intérprete, verificación, tachado, recuperación, OCR,
  facturas, conversiones, componentes. 107 pruebas al cerrar la fase 10.
- **Por qué:** el usuario no programa; las pruebas son la garantía de que un cambio no rompe lo anterior.
- **Capturas del manual:** se generan con una herramienta de prueba (`herramientas/CapturasManual`) que arma la ventana
  real **sin mostrarla** y la maneja por código, con una carpeta de datos temporal. Así no se toca el ratón ni el teclado
  del usuario y las capturas se pueden regenerar cuando cambie la interfaz.

---

## Dejado para después (y por qué)

| Versión | Qué | Por qué no en la 1.0 |
|---|---|---|
| 1.1 | Facturas sin QR, ítems, procesamiento por lotes, carpeta vigilada | Requieren leer texto impreso con formatos variables |
| 1.2 | Comparar versiones, modo estudio, referencias APA, tachado híbrido | Funciones nuevas; el tachado actual ya es seguro |
| 1.3 | Funciones con modelo de lenguaje (opcionales, apagadas por defecto) | Necesitan internet o un modelo local pesado |
