package pe.pdflisto.verificacion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.conversiones.HAConversor;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HADivisor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAPlanDivision;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAVerificadorArchivosTest {
    private final HAVerificadorArchivos haVerificador = new HAVerificadorArchivos();
    private final HADivisor haDivisor = new HADivisor(new HAMotorPdf());
    private final HAConversor haConversor = new HAConversor();

    @TempDir
    Path haCarpeta;

    @Test
    void haDividirYExtraer() throws Exception {
        File haPdf = haPdfConTexto("seis.pdf", "Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis");
        File haDestino = Files.createDirectories(haCarpeta.resolve("partes")).toFile();
        List<HAPlanDivision.HAParte> haPlan = HAPlanDivision.haCadaN(2, 6, "parte");
        List<File> haPartes = haDivisor.haDividir(haPdf, null, haPlan, haDestino);
        List<List<Integer>> haPaginas = haPlan.stream().map(HAPlanDivision.HAParte::haPaginas).toList();
        haCumple(haVerificador.haVerificar("Dividir", haPartes, new HAEsperadoArchivos.HAPartes(haPdf, null, haPaginas)));
        haRevisar(haVerificador.haVerificar("Dividir", haPartes, new HAEsperadoArchivos.HAPartes(haPdf, null,
                List.of(List.of(0, 1), List.of(3, 2), List.of(4, 5)))), "Partes");
        haRevisar(haVerificador.haVerificar("Dividir", haPartes.subList(0, 2), new HAEsperadoArchivos.HAPartes(haPdf, null, haPaginas)), "Partes");

        File haExtraido = haDivisor.haExtraer(haPdf, null, List.of(4, 1), haCarpeta.resolve("extraido.pdf").toFile());
        haCumple(haVerificador.haVerificar("Extraer", List.of(haExtraido), new HAEsperadoArchivos.HAPartes(haPdf, null, List.of(List.of(4, 1)))));
        haRevisar(haVerificador.haVerificar("Extraer", List.of(haExtraido), new HAEsperadoArchivos.HAPartes(haPdf, null, List.of(List.of(1, 4)))),
                "Páginas extraídas");
    }

    @Test
    void haImagenesAPdf() throws Exception {
        File haA = haImagen("a.png", 300, 200, Color.RED);
        File haB = haImagen("b.png", 120, 400, Color.BLUE);
        File haPdf = haConversor.haImagenesAPdf(List.of(haA, haB), HAConversor.HATamanoHoja.A4, haCarpeta.resolve("fotos.pdf").toFile(),
                HAControl.haNinguno());
        haCumple(haVerificador.haVerificar("Imágenes a PDF", List.of(haPdf), new HAEsperadoArchivos.HAImagenesAPdf(List.of(haA, haB))));
        haRevisar(haVerificador.haVerificar("Imágenes a PDF", List.of(haPdf), new HAEsperadoArchivos.HAImagenesAPdf(List.of(haB, haA))), "Imágenes");
        haRevisar(haVerificador.haVerificar("Imágenes a PDF", List.of(haPdf), new HAEsperadoArchivos.HAImagenesAPdf(List.of(haA))), "Imágenes");
    }

    @Test
    void haTextoAPdfYPdfATexto() throws Exception {
        File haTxt = haCarpeta.resolve("nota.txt").toFile();
        Files.writeString(haTxt.toPath(), "Informe de prueba con tildes: canción, año, pingüino.\n\n\tSegundo párrafo ★ con símbolo.\n"
                + "Una línea bastante larga ".repeat(20), StandardCharsets.UTF_8);
        File haPdf = haConversor.haTextoAPdf(haTxt, haCarpeta.resolve("nota.pdf").toFile(), HAControl.haNinguno());
        haCumple(haVerificador.haVerificar("Texto a PDF", List.of(haPdf), new HAEsperadoArchivos.HATextoAPdf(haTxt)));
        File haOtro = haCarpeta.resolve("otro.txt").toFile();
        Files.writeString(haOtro.toPath(), "Un texto distinto", StandardCharsets.UTF_8);
        haRevisar(haVerificador.haVerificar("Texto a PDF", List.of(haPdf), new HAEsperadoArchivos.HATextoAPdf(haOtro)), "Texto");

        File haOrigen = haPdfConTexto("origen.pdf", "Primera hoja del documento", "Segunda hoja del documento");
        File haSalida = haConversor.haPdfATexto(haOrigen, null, haCarpeta.resolve("origen.txt").toFile(), HAControl.haNinguno());
        haCumple(haVerificador.haVerificar("PDF a texto", List.of(haSalida), new HAEsperadoArchivos.HAPdfATexto(haOrigen, null)));
        Files.writeString(haSalida.toPath(), Files.readString(haSalida.toPath()).replace("Segunda", "S"), StandardCharsets.UTF_8);
        haRevisar(haVerificador.haVerificar("PDF a texto", List.of(haSalida), new HAEsperadoArchivos.HAPdfATexto(haOrigen, null)), "Texto");
    }

    @Test
    void haPdfAImagenesYExtraerImagenes() throws Exception {
        File haOrigen = haPdfConTexto("tres.pdf", "A", "B", "C");
        File haDestino = Files.createDirectories(haCarpeta.resolve("png")).toFile();
        List<File> haImagenes = haConversor.haPdfAImagenes(haOrigen, null, 72, false, haDestino, HAControl.haNinguno());
        haCumple(haVerificador.haVerificar("PDF a imágenes", haImagenes, new HAEsperadoArchivos.HAPdfAImagenes(haOrigen, null, 72)));
        haRevisar(haVerificador.haVerificar("PDF a imágenes", haImagenes, new HAEsperadoArchivos.HAPdfAImagenes(haOrigen, null, 150)), "Imágenes");
        haRevisar(haVerificador.haVerificar("PDF a imágenes", haImagenes.subList(0, 2),
                new HAEsperadoArchivos.HAPdfAImagenes(haOrigen, null, 72)), "Imágenes");

        File haConFotos = haPdfConImagenes("fotos.pdf");
        File haDest2 = Files.createDirectories(haCarpeta.resolve("extraidas")).toFile();
        List<File> haExtraidas = haConversor.haExtraerImagenes(haConFotos, null, haDest2, HAControl.haNinguno());
        haCumple(haVerificador.haVerificar("Extraer imágenes", haExtraidas, new HAEsperadoArchivos.HAExtraerImagenes(haConFotos, null)));
        haRevisar(haVerificador.haVerificar("Extraer imágenes", haExtraidas.subList(0, 1),
                new HAEsperadoArchivos.HAExtraerImagenes(haConFotos, null)), "Imágenes");
    }

    @Test
    void haWordAPdfSoloPuedeComprobarQueAbreYTieneTexto() throws Exception {
        File haPdf = haPdfConTexto("word.pdf", "Contenido del documento de Word");
        HAInformeVerificacion haI = haVerificador.haVerificar("Word a PDF", List.of(haPdf), new HAEsperadoArchivos.HAWordAPdf(new File("x.docx")));
        haCumple(haI);
        assertEquals(HAEstado.INFO, haFila(haI, "Diseño").haEstado(), "el diseño no se puede comprobar: se informa");
    }

    @Test
    void haArchivosVaciosOFaltantesDanRevisarSinError() throws Exception {
        File haVacio = haCarpeta.resolve("vacio.pdf").toFile();
        Files.createFile(haVacio.toPath());
        File haOrigen = haPdfConTexto("o.pdf", "Hola");
        haRevisar(haVerificador.haVerificar("Extraer", List.of(haVacio), new HAEsperadoArchivos.HAPartes(haOrigen, null, List.of(List.of(0)))),
                "Archivos creados");
        haRevisar(haVerificador.haVerificar("Extraer", List.of(), new HAEsperadoArchivos.HAPartes(haOrigen, null, List.of(List.of(0)))),
                "Archivos creados");
    }

    private static void haCumple(HAInformeVerificacion haI) {
        assertTrue(haI.haCumple(), () -> "debería cumplir:\n" + haTexto(haI));
        assertTrue(haI.haComprobaciones().stream().filter(haC -> haC.haEstado() == HAEstado.CUMPLE).count() >= 2,
                () -> "además de «Archivos creados», algo propio debe quedar comprobado:\n" + haTexto(haI));
    }

    private static void haRevisar(HAInformeVerificacion haI, String haNombre) {
        assertFalse(haI.haCumple(), () -> "debería dar Revisar:\n" + haTexto(haI));
        assertEquals(HAEstado.REVISAR, haFila(haI, haNombre).haEstado(), () -> haTexto(haI));
    }

    private static HAComprobacion haFila(HAInformeVerificacion haI, String haNombre) {
        return haI.haComprobaciones().stream().filter(haC -> haC.haNombre().equals(haNombre)).findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila «" + haNombre + "»:\n" + haTexto(haI)));
    }

    private static String haTexto(HAInformeVerificacion haI) {
        StringBuilder haSb = new StringBuilder();
        haI.haComprobaciones().forEach(haC -> haSb.append(haC.haNombre()).append(" · ").append(haC.haEstado()).append(": ")
                .append(haC.haDetalle()).append('\n'));
        return haSb.toString();
    }

    private File haPdfConTexto(String haNombre, String... haPaginas) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haD = new PDDocument()) {
            for (String haT : haPaginas) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haD.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                    haCs.beginText();
                    haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                    haCs.newLineAtOffset(60, 740);
                    haCs.showText(haT);
                    haCs.endText();
                }
            }
            haD.save(haF);
        }
        return haF;
    }

    private File haImagen(String haNombre, int haAncho, int haAlto, Color haColor) throws IOException {
        BufferedImage haImg = new BufferedImage(haAncho, haAlto, BufferedImage.TYPE_INT_RGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(haColor);
        haG.fillRect(0, 0, haAncho, haAlto);
        haG.dispose();
        File haF = haCarpeta.resolve(haNombre).toFile();
        ImageIO.write(haImg, "png", haF);
        return haF;
    }

    private File haPdfConImagenes(String haNombre) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haD = new PDDocument()) {
            PDImageXObject haLogo = LosslessFactory.createFromImage(haD, ImageIO.read(haImagen("logo.png", 64, 64, Color.GREEN)));
            PDImageXObject haFoto = LosslessFactory.createFromImage(haD, ImageIO.read(haImagen("foto.png", 200, 150, Color.ORANGE)));
            for (int haI = 0; haI < 2; haI++) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haD.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                    haCs.drawImage(haLogo, 40, 760, 40, 40);
                    if (haI == 1) {
                        haCs.drawImage(haFoto, 100, 400, 200, 150);
                    }
                }
            }
            haD.save(haF);
        }
        return haF;
    }
}
