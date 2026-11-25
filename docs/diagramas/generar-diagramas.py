# Genera arquitectura.svg y casos-de-uso.svg (python generar-diagramas.py). Sin dependencias.
from xml.sax.saxutils import escape as e

FUENTE = 'font-family="Segoe UI, Arial, sans-serif"'

def caja(x, y, w, h, titulo, lineas=(), relleno="#ffffff", borde="#57606a", negrita=True, tam=13):
    s = f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="{relleno}" stroke="{borde}" stroke-width="1.2"/>'
    s += f'<text x="{x + w/2}" y="{y + 20}" text-anchor="middle" font-size="{tam}" {"font-weight=\"bold\"" if negrita else ""} fill="#1f2328">{e(titulo)}</text>'
    for i, l in enumerate(lineas):
        s += f'<text x="{x + w/2}" y="{y + 38 + i*15}" text-anchor="middle" font-size="11" fill="#424a53">{e(l)}</text>'
    return s

def capa(y, h, titulo, color):
    return (f'<rect x="20" y="{y}" width="1160" height="{h}" rx="12" fill="{color}" stroke="none"/>'
            f'<text x="36" y="{y + 22}" font-size="14" font-weight="bold" fill="#1f2328">{e(titulo)}</text>')

def flecha(x1, y1, x2, y2, texto=None, discontinua=False):
    d = ' stroke-dasharray="5 4"' if discontinua else ''
    s = f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="#57606a" stroke-width="1.4" marker-end="url(#punta)"{d}/>'
    if texto:
        s += f'<text x="{(x1+x2)/2 + 6}" y="{(y1+y2)/2}" font-size="11" fill="#57606a">{e(texto)}</text>'
    return s

def svg(w, h, cuerpo, titulo):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}" {FUENTE}>'
            f'<title>{e(titulo)}</title>'
            '<defs><marker id="punta" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">'
            '<path d="M0,0 L10,5 L0,10 z" fill="#57606a"/></marker></defs>'
            f'<rect width="{w}" height="{h}" fill="#ffffff"/>{cuerpo}</svg>\n')

# ------------------------------------------------------------------ arquitectura
c = f'<text x="600" y="34" text-anchor="middle" font-size="20" font-weight="bold" fill="#1f2328">PDF Listo — arquitectura (todo local, sin internet)</text>'
c += capa(50, 130, "1. Interfaz (JavaFX + AtlantaFX, FXML)", "#ddf4ff")
c += caja(40, 82, 260, 84, "HAMainController (ventana)", ["visor, miniaturas, barra, atajos", "implementa HAEditor"])
c += caja(320, 82, 270, 84, "Paneles", ["Pedido · Anotar · Formulario", "Entrega · Tachado · Historial"])
c += caja(610, 82, 270, 84, "Formularios y menús", ["Herramientas · Convertir · Ayuda", "HABienvenida · HAVentanaFacturas"])
c += caja(900, 82, 260, 84, "ui", ["HAVisorPagina · HAMiniaturas", "HANotificador · HAExportador"])

c += capa(200, 300, "2. Lógica de PDF Listo (Spring: inyección de dependencias)", "#fff8c5")
fila1 = [("interprete", ["HAInterpretePedidos (reglas)", "HAPaso → HAEjecutorPlan", "lista cerrada de HAAccion"]),
         ("operaciones", ["HAMotorPdf + HAOperacionPdf", "HARotar, HAUnir, HAComprimir,", "HANumerar, HAMarcaDeAgua…"]),
         ("edicion", ["HASesionEdicion (versiones", "= deshacer/rehacer)", "HADiarioSesion, HARecuperacion"]),
         ("verificacion", ["HAVerificador relee el", "resultado → Informe", "HAInformePdf (OpenPDF)"])]
for i, (t, l) in enumerate(fila1):
    c += caja(40 + i*285, 232, 265, 100, t, l)
fila2 = [("privacidad", ["HADetectorDatos, HATachar,", "HAVerificadorTachado, HAProteger"]),
         ("anotaciones · formularios", ["HAAnotar, HABibliotecaFirmas,", "HARellenarFormulario"]),
         ("ocr · facturas · conversiones", ["HAReconocerTexto, HALectorFacturas,", "HAExportadorFacturas, HAConversor"]),
         ("entrega · sistema", ["Perfiles, Recetas, HAPatronNombre,", "HAComprobadorComponentes"])]
for i, (t, l) in enumerate(fila2):
    c += caja(40 + i*285, 350, 265, 72, t, l)
c += caja(40, 436, 1120, 50, "pdf: HAPdfService · HACargaPdf · HADocumentoPdf (hilo propio) · HAEscrituraSegura (temporal → reabrir → renombrar) · HALectorTexto", [], "#ffffff", "#57606a", False, 12)

c += capa(520, 120, "3. Motores y bibliotecas (dentro de la app)", "#dafbe1")
libs = [("Apache PDFBox 3", ["leer, dibujar, modificar"]), ("Tesseract 5 (Tess4J)", ["OCR, DLL en native\\"]),
        ("ZXing", ["códigos QR"]), ("OpenPDF · Apache POI", ["informes · Excel"]), ("LibreOffice (opcional)", ["externo: Word → PDF"])]
for i, (t, l) in enumerate(libs):
    c += caja(40 + i*226, 552, 210, 70, t, l, "#ffffff", "#57606a" if i < 4 else "#8c959f")

c += capa(660, 120, "4. Datos (en el equipo del usuario)", "#f6f8fa")
c += caja(40, 692, 360, 72, "SQLite (pdflisto.db, JPA)", ["historial · recientes · recetas", "perfiles · reglas de detección"])
c += caja(420, 692, 360, 72, "%APPDATA%\\PDFListo", ["config.properties · logs · firmas", "trabajo\\ (versiones + diario) · tessdata"])
c += caja(800, 692, 360, 72, "Archivos del usuario", ["original: SOLO LECTURA", "resultado: siempre un archivo nuevo"])

c += flecha(600, 166, 600, 230, "llama a")
c += flecha(600, 486, 600, 550, "usa")
c += flecha(258, 486, 258, 690, None, True)
c += flecha(936, 486, 936, 690, None, True)
open("arquitectura.svg", "w", encoding="utf-8").write(svg(1200, 800, c, "Arquitectura de PDF Listo"))

# ------------------------------------------------------------------ casos de uso
def caso(cx, cy, texto, w=230):
    return (f'<ellipse cx="{cx}" cy="{cy}" rx="{w/2}" ry="24" fill="#ffffff" stroke="#57606a" stroke-width="1.2"/>'
            f'<text x="{cx}" y="{cy + 4}" text-anchor="middle" font-size="12" fill="#1f2328">{e(texto)}</text>')

def actor(x, y, nombre):
    return (f'<circle cx="{x}" cy="{y}" r="13" fill="none" stroke="#1f2328" stroke-width="1.6"/>'
            f'<line x1="{x}" y1="{y+13}" x2="{x}" y2="{y+50}" stroke="#1f2328" stroke-width="1.6"/>'
            f'<line x1="{x-20}" y1="{y+26}" x2="{x+20}" y2="{y+26}" stroke="#1f2328" stroke-width="1.6"/>'
            f'<line x1="{x}" y1="{y+50}" x2="{x-16}" y2="{y+78}" stroke="#1f2328" stroke-width="1.6"/>'
            f'<line x1="{x}" y1="{y+50}" x2="{x+16}" y2="{y+78}" stroke="#1f2328" stroke-width="1.6"/>'
            f'<text x="{x}" y="{y+98}" text-anchor="middle" font-size="13" font-weight="bold" fill="#1f2328">{e(nombre)}</text>')

c = '<text x="600" y="34" text-anchor="middle" font-size="20" font-weight="bold" fill="#1f2328">PDF Listo — diagrama de casos de uso</text>'
c += '<rect x="120" y="56" width="960" height="680" rx="14" fill="#f6f8fa" stroke="#8c959f"/>'
c += '<text x="136" y="80" font-size="13" font-weight="bold" fill="#57606a">Sistema PDF Listo</text>'
colA = ["Abrir y ver PDF", "Organizar páginas", "Comprimir, numerar, marca de agua", "Anotar y firmar",
        "Llenar formulario", "Hacer buscable (OCR)", "Tachar datos sensibles", "Pedir con palabras"]
colB = ["Proteger y limpiar metadatos", "Validar perfil de entrega", "Usar o aprender receta",
        "Convertir (imágenes, texto, Word)", "Leer facturas a Excel", "Deshacer / recuperar trabajo",
        "Comprobar componentes", "Guardar copia"]
pos = {}
W = 250
for i, t in enumerate(colA):
    pos[t] = (300, 120 + i*62); c += caso(300, 120 + i*62, t, W)
for i, t in enumerate(colB):
    pos[t] = (900, 120 + i*62); c += caso(900, 120 + i*62, t, W)
pos["Ver plan antes de actuar"] = (470, 680); c += caso(470, 680, "Ver plan antes de actuar", W)
pos["Verificar resultado"] = (760, 680); c += caso(760, 680, "Verificar resultado", W)
c += actor(600, 230, "Usuario")
for t in colA:
    x, y = pos[t]
    c += f'<line x1="582" y1="262" x2="{x + W/2}" y2="{y}" stroke="#8c959f" stroke-width="1"/>'
for t in colB:
    x, y = pos[t]
    c += f'<line x1="618" y1="262" x2="{x - W/2}" y2="{y}" stroke="#8c959f" stroke-width="1"/>'
def incluye(a, b, dx1=0, dx2=0):
    (x1, y1), (x2, y2) = pos[a], pos[b]
    return (flecha(x1 + dx1, y1 + 24, x2 + dx2, y2 - 24, None, True)
            + f'<text x="{(x1 + dx1 + x2 + dx2)/2 + 6}" y="{(y1 + y2)/2 + 8}" font-size="10" fill="#57606a">«include»</text>')
c += incluye("Pedir con palabras", "Ver plan antes de actuar", 40, -40)
c += incluye("Pedir con palabras", "Verificar resultado", 90, -60)
c += flecha(425, 492, 690, 656, None, True)  # tachar -> verificar
c += '<text x="560" y="560" font-size="10" fill="#57606a">«include»</text>'
c += incluye("Guardar copia", "Verificar resultado", -40, 50)
c += actor(1140, 250, "LibreOffice")
c += '<text x="1140" y="370" text-anchor="middle" font-size="11" fill="#57606a">(opcional)</text>'
x, y = pos["Convertir (imágenes, texto, Word)"]
c += f'<line x1="{x + W/2}" y1="{y}" x2="1120" y2="290" stroke="#8c959f" stroke-width="1" stroke-dasharray="4 3"/>'
open("casos-de-uso.svg", "w", encoding="utf-8").write(svg(1200, 780, c, "Casos de uso de PDF Listo"))
print("ok")
