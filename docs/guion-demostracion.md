# PDF Listo — Guion de demostración (10 minutos)

Todos los PDF usados están en la carpeta `ejemplos/` y tienen **datos ficticios**.

## Antes de empezar (preparación, 5 minutos antes)

- Abrir PDF Listo una vez y cerrar la bienvenida, o dejarla para mostrarla (ver minuto 0).
- Tener a mano la carpeta `ejemplos/` en el Explorador de Windows.
- Opcional: **desconectar el wifi** para demostrar que todo funciona sin internet.
- Pantalla al 100 % de zoom de Windows y la ventana maximizada.

| Minuto | Qué mostrar | Qué decir |
|---|---|---|
| 0:00 | Abrir la app (se ve la bienvenida). Avanzar 2 pantallas y pulsar «Saltar». | «Es un editor de PDF de escritorio que funciona **sin internet**. Tres ideas: pido con palabras, veo un plan antes, y la app verifica el resultado. El original nunca se toca.» |
| 0:45 | Arrastrar `demo-12-paginas.pdf` a la ventana. Mostrar miniaturas, zoom, buscar «Capitulo 5» (<kbd>Ctrl</kbd>+<kbd>F</kbd>). | «Se abre al instante aunque sea grande: las páginas se dibujan en segundo plano.» |
| 1:30 | Pestaña **Pedido**: escribir `comprime a menos de 2 MB y numera` → <kbd>Enter</kbd>. | «No hace nada todavía: me muestra el **plan**, en orden, y lo que espera al final. Si faltara un dato, me preguntaría.» |
| 2:15 | **Aprobar y ejecutar**. Mostrar la tarjeta antes/después y el **Informe de verificación**: el peso sale «Revisar». | «Volvió a **abrir el archivo final** y comprobó cada punto: 12 páginas, cada número en el margen… y se dio cuenta de que **pesa más que antes**: el PDF ya estaba optimizado y la numeración agregó la letra de los números. Pedí comprimir y no se logró, así que **no me dice "Cumple"**: me explica por qué.» |
| 3:00 | <kbd>Ctrl</kbd>+<kbd>Z</kbd> y <kbd>Ctrl</kbd>+<kbd>Y</kbd>. Arrastrar una miniatura para reordenar. | «Cada cambio es una versión: deshacer es volver a la anterior. Si se va la luz, al abrir de nuevo ofrece recuperar el trabajo.» |
| 3:45 | Abrir `demo-datos-sensibles.pdf`. Pestaña **Tachado** → **Buscar datos sensibles**. | «Encontró DNI, RUC validado con su dígito verificador, celular, correo, dirección y cuenta. Fíjense en el "87654321": no tiene la palabra DNI cerca, así que lo deja como "Posible" y sin marcar. **Yo decido.**» |
| 4:45 | **Tachar y verificar** → confirmar. Intentar buscar `45678912` con <kbd>Ctrl</kbd>+<kbd>F</kbd> (no aparece). | «El dato se **borra del archivo**, no solo se tapa con un recuadro negro. Luego se relee el resultado: si algo quedara recuperable, la app no lo declara seguro.» |
| 5:30 | En la misma pestaña: **Informe de privacidad** y **Limpiar metadatos…** | «Los PDF guardan autor, programa y fechas ocultos; aquí se ven y se limpian.» |
| 6:00 | Abrir `demo-escaneado.pdf` (aparece el aviso de escaneado). **Herramientas → Reconocer texto (OCR)** → Reconocer. Luego buscar «arrendamiento». | «Era solo una foto; ahora se puede buscar y copiar, y la página se ve igual. El OCR es Tesseract y corre en la computadora.» |
| 7:15 | Abrir `demo-formulario.pdf`. Pestaña **Formulario**: llenar nombre y DNI. Pestaña **Anotar**: poner una firma y un sello «RECIBIDO» → Aplicar. | «Llena formularios y firma (firma visual). Las firmas se guardan con su posición para la próxima vez.» |
| 8:15 | Pestaña **Entrega** → **Leer facturas con QR…** → elegir los 6 PDF de `ejemplos/facturas`. | «Lee el QR de SUNAT: detecta el IGV mal calculado («Revisar»), avisa cuando el QR no basta para comprobarlo porque hay montos exonerados («No se puede comprobar»), la factura duplicada, y lee incluso la escaneada. Lo exporta a Excel con fórmulas.» |
| 9:15 | **Convertir** (barra) y **Ayuda → Comprobar componentes**. Cambiar a modo oscuro. | «Convierte imágenes y texto a PDF y al revés. La comprobación muestra que el OCR y todo lo necesario están presentes, sin internet.» |
| 9:45 | Cierre. | «Resumen: original intacto, plan antes de actuar, verificación real al final, y privacidad de verdad. Todo con 107 pruebas automáticas.» |

## Si algo sale mal durante la demostración

- **El OCR tarda:** es normal (unos segundos por página); mientras tanto mostrar la barra de progreso y el botón Cancelar.
- **Una verificación sale «Revisar»:** es parte de la demostración; leer el detalle en voz alta y deshacer con
  <kbd>Ctrl</kbd>+<kbd>Z</kbd>.
- **La app no abre:** puede haber otra ventana de PDF Listo abierta; cerrarla. Para diagnóstico:
  **Ayuda → Comprobar componentes**.

## Preguntas probables del profesor

- **¿Por qué no usar inteligencia artificial para entender los pedidos?** Las reglas funcionan sin internet, no pueden
  inventar acciones y se prueban automáticamente. Un modelo de lenguaje opcional está planeado para la versión 1.3
  (ver `decisiones-tecnicas.md`, punto 4).
- **¿Cómo sé que el tachado es seguro?** La página se convierte en imagen y se verifica releyendo el archivo (punto 6).
- **¿Qué pasa si se corta la luz?** El original nunca se toca y el trabajo se recupera al volver a abrir (puntos 2 y 3).
- **¿Qué no hace?** Ver «Limitaciones» en el manual técnico: no edita el texto original, la firma no es digital con
  certificado y las facturas deben tener QR.
