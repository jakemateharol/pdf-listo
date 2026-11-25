package pe.pdflisto.anotaciones;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationInk;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HARotar;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.pdf.HALectorTexto;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAAnotacionesTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();

    @TempDir
    Path haCarpeta;

    @Test
    void haConversionDeCoordenadasEnPaginasGiradas() {
        PDPage haNormal = new PDPage(PDRectangle.A4);
        HAEspacioPagina haN = HAEspacioPagina.haDe(haNormal);
        assertArrayEquals(new float[]{0, 841.89f}, haN.haAUsuario(0, 0), 0.5f, "arriba-izquierda");
        PDPage haGirada = new PDPage(PDRectangle.A4);
        haGirada.setRotation(90);
        HAEspacioPagina haG = HAEspacioPagina.haDe(haGirada);
        assertEquals(842, haG.haAncho(), 0.5);
        assertEquals(595, haG.haAlto(), 0.5);
        assertArrayEquals(new float[]{0, 0}, haG.haAUsuario(0, 0), 0.5f);
        assertArrayEquals(new float[]{595.28f, 841.89f}, haG.haAUsuario(841.89, 595.28), 0.5f);
    }

    @Test
    void haResaltadoSobreElTextoConApariencia() throws Exception {
        File haPdf = haPdf("texto.pdf", "Resalta esta frase importante");
        File haSalida = haCarpeta.resolve("resaltado.pdf").toFile();
        List<HAAreaTexto> haAreas;
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            HALectorTexto.HAPaginaTexto haP = HALectorTexto.haLeerPagina(haD, 0);
            int haI = haP.haTexto().indexOf("frase");
            haAreas = haP.haAreas(haI, haI + "frase".length());
        }
        HAAnotacion.HAMarcado haM = new HAAnotacion.HAMarcado(HAAnotacion.HATipoMarcado.RESALTADO, 0, haAreas, Color.YELLOW, "frase");
        haMotor.haEjecutar(haPdf, null, new HAAnotar(List.of(haM), "Ana"), haSalida);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            List<PDAnnotation> haAnotaciones = haD.getPage(0).getAnnotations();
            assertEquals(1, haAnotaciones.size());
            PDAnnotationHighlight haH = (PDAnnotationHighlight) haAnotaciones.get(0);
            assertNotNull(haH.getNormalAppearanceStream(), "debe verse en cualquier lector");
            assertEquals("frase", haH.getContents());
            assertEquals("Ana", haH.getTitlePopup());
            assertTrue(haH.getRectangle().getLowerLeftY() < 702 && haH.getRectangle().getUpperRightY() > 700);
        }
    }

    @Test
    void haFormasDibujoTextoSelloYFirmaEnUnaPaginaGirada() throws Exception {
        File haBase = haPdf("base.pdf", "Hola");
        File haGirado = haCarpeta.resolve("girado.pdf").toFile();
        haMotor.haEjecutar(haBase, null, new HARotar(List.of(0), 90), haGirado);
        File haFirma = haFirmaPng();
        List<HAAnotacion> haLista = List.of(
                new HAAnotacion.HAForma(HAAnotacion.HATipoForma.RECTANGULO, 0, 50, 50, 200, 120, Color.RED, 2),
                new HAAnotacion.HAForma(HAAnotacion.HATipoForma.FLECHA, 0, 300, 300, 400, 350, Color.BLUE, 2),
                new HAAnotacion.HATrazo(0, List.of(new double[]{10, 10}, new double[]{20, 25}, new double[]{30, 12}), Color.BLACK, 2),
                new HAAnotacion.HANota(0, 500, 40, "Revisar este punto", Color.ORANGE),
                new HAAnotacion.HATexto(0, 60, 400, "Aprobado por\nGerencia", 12, Color.BLACK),
                new HAAnotacion.HASello(0, new HAAreaTexto(600, 450, 160, 60), "Recibido", Color.RED, "14/11/2026"),
                new HAAnotacion.HAFirma(0, new HAAreaTexto(600, 520, 120, 50), haFirma, "Ana"));
        File haSalida = haCarpeta.resolve("anotado.pdf").toFile();
        haMotor.haEjecutar(haGirado, null, new HAAnotar(haLista, null), haSalida);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            List<PDAnnotation> haAn = haD.getPage(0).getAnnotations();
            assertEquals(4, haAn.size(), "rectángulo, flecha, dibujo y nota son anotaciones PDF");
            assertTrue(haAn.stream().allMatch(haA -> haA.getNormalAppearanceStream() != null));
            assertTrue(haAn.stream().anyMatch(haA -> haA instanceof PDAnnotationInk));
            assertEquals(PDAnnotationLine.LE_OPEN_ARROW,
                    haAn.stream().filter(haA -> haA instanceof PDAnnotationLine).map(haA -> (PDAnnotationLine) haA)
                            .findFirst().orElseThrow().getEndPointEndingStyle());
            String haTexto = new PDFTextStripper().getText(haD).replaceAll("\\s", "");
            assertTrue(haTexto.contains("AprobadoporGerencia"), "el texto agregado se puede buscar: " + haTexto);
            assertTrue(haTexto.contains("RECIBIDO") && haTexto.contains("14/11/2026"));
            long haImagenes = 0;
            for (var haNombre : haD.getPage(0).getResources().getXObjectNames()) {
                if (haD.getPage(0).getResources().getXObject(haNombre) instanceof PDImageXObject) {
                    haImagenes++;
                }
            }
            assertEquals(1, haImagenes, "la firma quedó como imagen en la página");
            HALectorTexto.HAPaginaTexto haP = HALectorTexto.haLeerPagina(haD, 0);
            int haI = haP.haTexto().indexOf("Aprobado");
            HAAreaTexto haA = haP.haAreas(haI, haI + 8).get(0);
            assertEquals(60, haA.haX(), 3);
            assertEquals(400, haA.haY(), 6);
        }
    }

    @Test
    void haBibliotecaDeFirmasGuardaYRecuerdaLaPosicion() throws Exception {
        HABibliotecaFirmas haBiblioteca = new HABibliotecaFirmas(haCarpeta.resolve("firmas"));
        BufferedImage haDibujo = new BufferedImage(300, 120, BufferedImage.TYPE_INT_ARGB);
        Graphics2D haG = haDibujo.createGraphics();
        haG.setColor(Color.BLUE);
        haG.fillOval(100, 40, 80, 30);
        haG.dispose();
        HABibliotecaFirmas.HAFirma haF = haBiblioteca.haGuardar("Ana López", haDibujo);
        BufferedImage haGuardada = ImageIO.read(haF.haArchivo());
        assertTrue(haGuardada.getWidth() < 300, "se recortan los bordes vacíos");
        haBiblioteca.haRecordarPosicion(haF.haNombre(), new HAAreaTexto(59.5, 700, 119, 50), 595, 842);
        HABibliotecaFirmas.HAFirma haLeida = haBiblioteca.haListar().get(0);
        assertEquals("Ana López", haLeida.haNombre());
        HAAreaTexto haEnCarta = haLeida.haPosicion().haEn(612, 792);
        assertEquals(61.2, haEnCarta.haX(), 0.1);
        assertThrows(Exception.class, () -> haBiblioteca.haGuardar("vacía", new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB)));
    }

    @Test
    void haQuitarFondoBlancoDeUnaFirmaEscaneada() {
        BufferedImage haFoto = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int haY = 0; haY < 10; haY++) {
            for (int haX = 0; haX < 10; haX++) {
                haFoto.setRGB(haX, haY, haX < 5 ? 0xFFFFFF : 0x101060);
            }
        }
        BufferedImage haLimpia = HABibliotecaFirmas.haQuitarFondoBlanco(haFoto);
        assertEquals(0, haLimpia.getRGB(1, 1) >>> 24, "el blanco se vuelve transparente");
        assertEquals(255, haLimpia.getRGB(8, 8) >>> 24, "la tinta se conserva");
    }

    private File haFirmaPng() throws IOException {
        BufferedImage haImg = new BufferedImage(200, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(Color.BLACK);
        haG.drawLine(10, 60, 190, 20);
        haG.dispose();
        File haF = haCarpeta.resolve("firma.png").toFile();
        ImageIO.write(haImg, "png", haF);
        return haF;
    }

    private File haPdf(String haNombre, String haTexto) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haP = new PDPage(PDRectangle.A4);
            haDoc.addPage(haP);
            try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                haCs.beginText();
                haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                haCs.newLineAtOffset(72, 700);
                haCs.showText(haTexto);
                haCs.endText();
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
