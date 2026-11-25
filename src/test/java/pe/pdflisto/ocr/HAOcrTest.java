package pe.pdflisto.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.privacidad.HADatoSensible;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HATipoDato;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAOcrTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();

    @TempDir
    Path haCarpeta;

    @Test
    void haElEscaneoQuedaBuscableYEnSuLugar() throws Exception {
        File haEscaneo = haPdfEscaneado("escaneo.pdf", 0, false);
        assertFalse(haTexto(haEscaneo).contains("Contrato"), "antes del OCR no hay texto");
        File haSalida = haCarpeta.resolve("ocr.pdf").toFile();
        haMotor.haEjecutar(haEscaneo, null, new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haSalida);

        String haTexto = haTexto(haSalida);
        assertTrue(haTexto.contains("Contrato"), haTexto);
        assertTrue(haTexto.contains("45678912"), haTexto);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            HALectorTexto.HAPaginaTexto haP = HALectorTexto.haLeerPagina(haD, 0);
            int haI = haP.haTexto().indexOf("Contrato");
            var haArea = haP.haAreas(haI, haI + 8).get(0);
            assertEquals(72, haArea.haX(), 8);
            assertEquals(140, haArea.haY() + haArea.haAlto(), 15);
        }
        List<HADatoSensible> haDatos = new HADetectorDatos(null).haDetectar(haSalida, null);
        assertTrue(haDatos.stream().anyMatch(haD -> haD.haTipo() == HATipoDato.DNI && haD.haValor().equals("45678912")));
    }

    @Test
    void haLasPaginasConTextoNoSeTocan() throws Exception {
        File haMixto = haCarpeta.resolve("mixto.pdf").toFile();
        try (PDDocument haD = new PDDocument();
             PDDocument haEscaneo = Loader.loadPDF(haPdfEscaneado("solo-escaneo.pdf", 0, false))) {
            PDPage haConTexto = new PDPage(PDRectangle.A4);
            haD.addPage(haConTexto);
            try (PDPageContentStream haCs = new PDPageContentStream(haD, haConTexto)) {
                haCs.beginText();
                haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                haCs.newLineAtOffset(72, 700);
                haCs.showText("Esta pagina ya tiene texto digital suficiente para no pasar por OCR");
                haCs.endText();
            }
            haD.importPage(haEscaneo.getPage(0));
            haD.save(haMixto);
        }
        File haSalida = haCarpeta.resolve("mixto-ocr.pdf").toFile();
        haMotor.haEjecutar(haMixto, null, new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haSalida);
        String haTexto = haTexto(haSalida);
        assertEquals(1, haContar(haTexto, "Esta pagina ya tiene texto"), "no se duplica el texto existente");
        assertTrue(haTexto.contains("Contrato"));

        assertThrows(HAPdfListoException.class, () -> haMotor.haEjecutar(haSalida, null,
                new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haCarpeta.resolve("otra.pdf").toFile()));
    }

    @Test
    void haConMejorasUnEscaneoTorcidoYConBordeSigueSiendoLegible() throws Exception {
        File haTorcido = haPdfEscaneado("torcido.pdf", 3.0, true);
        File haSalida = haCarpeta.resolve("torcido-ocr.pdf").toFile();
        haMotor.haEjecutar(haTorcido, null, new HAReconocerTexto(
                new HAReconocerTexto.HAOpciones(HADatosOcr.HAIdioma.ESPANOL, true, true, true)), haSalida);
        String haTexto = haTexto(haSalida);
        assertTrue(haTexto.contains("Contrato"), haTexto);
        assertTrue(haTexto.contains("45678912"), haTexto);
    }

    @Test
    void haDesdeUnPedidoEscritoHastaElInformeDeVerificacion() throws Exception {
        var haInterprete = new pe.pdflisto.interprete.HAInterpretePedidos();
        var haPlan = haInterprete.haInterpretar("hazlo buscable y numera",
                new pe.pdflisto.interprete.HAInterpretePedidos.HAContexto(1, List.of()));
        assertEquals(List.of(pe.pdflisto.interprete.HAAccion.OCR, pe.pdflisto.interprete.HAAccion.NUMERAR),
                haPlan.haPasos().stream().map(pe.pdflisto.interprete.HAPaso::haAccion).toList());

        File haEscaneo = haPdfEscaneado("pedido.pdf", 0, false);
        var haEjecutor = new pe.pdflisto.interprete.HAEjecutorPlan(haMotor, new pe.pdflisto.operaciones.HACompresor(haMotor));
        var haReq = haEjecutor.haRequisitos(haPlan.haPasos(), 1, 0, haEscaneo.length());
        assertTrue(haReq.haTextoBuscable());
        File haSalida = haCarpeta.resolve("pedido-ocr.pdf").toFile();
        haEjecutor.haEjecutar(haPlan.haPasos(), haEscaneo, null, haSalida, List.of());
        var haInforme = new pe.pdflisto.verificacion.HAVerificador(new HADetectorDatos(null))
                .haVerificar(haSalida, null, haReq, "pedido.pdf", "hazlo buscable y numera", List.of());
        assertTrue(haInforme.haCumple(), haInforme.haComprobaciones().toString());
        assertEquals(pe.pdflisto.verificacion.HAComprobacion.HAEstado.CUMPLE, haInforme.haComprobaciones().stream()
                .filter(haC -> haC.haNombre().equals("Texto buscable")).findFirst().orElseThrow().haEstado());
    }

    @Test
    void haDetectaLaInclinacionYLaCorrige() {
        BufferedImage haRecta = haHoja(0, false);
        BufferedImage haTorcida = HAMejoraEscaneo.haAGris(haHoja(4.0, false));
        assertEquals(0, HAMejoraEscaneo.haInclinacion(HAMejoraEscaneo.haAGris(haRecta)), 0.6);
        double haAngulo = HAMejoraEscaneo.haInclinacion(haTorcida);
        assertEquals(4.0, Math.abs(haAngulo), 1.0, "detecta unos 4 grados");
        double haDespues = HAMejoraEscaneo.haInclinacion(HAMejoraEscaneo.haEnderezar(haTorcida));
        assertTrue(Math.abs(haDespues) < 1.0, "queda derecha: " + haDespues);
    }

    @Test
    void haLimpiaBordesYMejoraContraste() {
        BufferedImage haImg = new BufferedImage(400, 400, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(new Color(150, 150, 150));
        haG.fillRect(0, 0, 400, 400);
        haG.setColor(new Color(110, 110, 110));
        haG.fillRect(100, 150, 200, 40);
        haG.setColor(Color.BLACK);
        haG.fillRect(0, 0, 20, 400);
        haG.dispose();

        BufferedImage haLimpia = HAMejoraEscaneo.haLimpiarBordes(haImg);
        assertEquals(255, haLimpia.getRaster().getSample(5, 200, 0), "la franja negra se vuelve blanca");
        assertEquals(110, haLimpia.getRaster().getSample(150, 160, 0), "el contenido no se toca");

        BufferedImage haContrastada = HAMejoraEscaneo.haMejorarContraste(haLimpia);
        assertTrue(haContrastada.getRaster().getSample(150, 160, 0) < 40, "el texto queda casi negro");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "hoja escaneada girada {0}° queda derecha")
    @org.junit.jupiter.params.provider.ValueSource(ints = {90, 180, 270})
    void haPaginaGiradaSeDetectaYSePoneDerecha(int haGrados) throws Exception {
        File haGirada = haPdfConImagen("girada-" + haGrados + ".pdf", HAOrientacion.haGirar(haHoja(0, false), haGrados));
        File haSalida = haCarpeta.resolve("girada-" + haGrados + "-ocr.pdf").toFile();
        var haR = haMotor.haEjecutar(haGirada, null, new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haSalida);

        assertNotNull(haR.haDetalle(), "avisa que la puso derecha");
        assertTrue(haR.haDetalle().contains("girada"), haR.haDetalle());
        String haTexto = haTexto(haSalida);
        assertTrue(haTexto.contains("Contrato de arrendamiento"), haTexto);
        assertTrue(haTexto.contains("45678912"), haTexto);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            PDPage haP = haD.getPage(0);
            pe.pdflisto.pdf.HATamanoPagina haT = pe.pdflisto.pdf.HATamanoPagina.haDe(haP);
            assertTrue(haT.haAlto() > haT.haAncho(), "se ve vertical, como la hoja original");
            HALectorTexto.HAPaginaTexto haPt = HALectorTexto.haLeerPagina(haD, 0);
            int haI = haPt.haTexto().indexOf("Contrato");
            var haArea = haPt.haAreas(haI, haI + 8).get(0);
            assertEquals(72, haArea.haX(), 10);
            assertEquals(140, haArea.haY() + haArea.haAlto(), 18);
        }
        assertTrue(new HADetectorDatos(null).haDetectar(haSalida, null).stream()
                .anyMatch(haD -> haD.haTipo() == HATipoDato.DNI && haD.haValor().equals("45678912")));
    }

    @Test
    void haUnaHojaDerechaNoSeGiraYSinLaOpcionNoSeTocaElGiro() throws Exception {
        File haDerecha = haPdfEscaneado("derecha.pdf", 0, false);
        File haSalida = haCarpeta.resolve("derecha-ocr.pdf").toFile();
        var haR = haMotor.haEjecutar(haDerecha, null, new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haSalida);
        assertNull(haR.haDetalle(), "no hubo nada que girar");
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            assertEquals(0, haD.getPage(0).getRotation());
        }

        File haGirada = haPdfConImagen("girada-sin-opcion.pdf", HAOrientacion.haGirar(haHoja(0, false), 180));
        File haSinOpcion = haCarpeta.resolve("sin-opcion.pdf").toFile();
        haMotor.haEjecutar(haGirada, null, new HAReconocerTexto(
                new HAReconocerTexto.HAOpciones(HADatosOcr.HAIdioma.ESPANOL, false, false, false, false)), haSinOpcion);
        try (PDDocument haD = Loader.loadPDF(haSinOpcion)) {
            assertEquals(0, haD.getPage(0).getRotation(), "sin la opción no se gira");
        }
    }

    @Test
    void haReglaDeCorreccion() {
        assertEquals(0, HAOrientacion.haCorreccionDesde(0, 20f));
        assertEquals(270, HAOrientacion.haCorreccionDesde(90, 20f));
        assertEquals(180, HAOrientacion.haCorreccionDesde(180, 20f));
        assertEquals(90, HAOrientacion.haCorreccionDesde(270, 20f));
        assertEquals(0, HAOrientacion.haCorreccionDesde(90, 0.5f), "poca confianza: no se gira");
    }

    private File haPdfConImagen(String haNombre, BufferedImage haImg) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        float haAncho = haImg.getWidth() * 72f / 300f;
        float haAlto = haImg.getHeight() * 72f / 300f;
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haP = new PDPage(new PDRectangle(haAncho, haAlto));
            haDoc.addPage(haP);
            var haImagen = JPEGFactory.createFromImage(haDoc, haImg, 0.85f);
            try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                haCs.drawImage(haImagen, 0, 0, haAncho, haAlto);
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private static BufferedImage haHoja(double haGrados, boolean haBordeNegro) {
        BufferedImage haImg = new BufferedImage(2480, 3508, BufferedImage.TYPE_INT_RGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(Color.WHITE);
        haG.fillRect(0, 0, haImg.getWidth(), haImg.getHeight());
        haG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        haG.rotate(Math.toRadians(haGrados), haImg.getWidth() / 2.0, haImg.getHeight() / 2.0);
        haG.setColor(new Color(25, 25, 25));
        haG.setFont(new Font("Arial", Font.PLAIN, 58));
        String[] haLineas = {"Contrato de arrendamiento de vivienda",
                "Arrendatario: Juan Perez Quispe, DNI 45678912",
                "El presente contrato se firma en la ciudad de Lima.",
                "Ambas partes declaran estar de acuerdo con las clausulas."};
        int haY = 583;
        for (String haL : haLineas) {
            haG.drawString(haL, 300, haY);
            haY += 110;
        }
        haG.dispose();
        if (haBordeNegro) {
            Graphics2D haB = haImg.createGraphics();
            haB.setColor(Color.BLACK);
            haB.fillRect(0, 0, 60, haImg.getHeight());
            haB.dispose();
        }
        return haImg;
    }

    private File haPdfEscaneado(String haNombre, double haGrados, boolean haBordeNegro) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haP = new PDPage(PDRectangle.A4);
            haDoc.addPage(haP);
            var haImagen = JPEGFactory.createFromImage(haDoc, haHoja(haGrados, haBordeNegro), 0.85f);
            try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                haCs.drawImage(haImagen, 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private static String haTexto(File haPdf) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            return new PDFTextStripper().getText(haD);
        }
    }

    private static int haContar(String haTexto, String haBuscado) {
        int haN = 0;
        for (int haI = haTexto.indexOf(haBuscado); haI >= 0; haI = haTexto.indexOf(haBuscado, haI + 1)) {
            haN++;
        }
        return haN;
    }
}
