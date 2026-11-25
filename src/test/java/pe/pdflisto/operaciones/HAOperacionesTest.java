package pe.pdflisto.operaciones;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HAOperacionesTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HACompresor haCompresor = new HACompresor(haMotor);
    private final HADivisor haDivisor = new HADivisor(haMotor);

    @TempDir
    Path haCarpeta;

    @Test
    void haRotaSoloLasPaginasElegidas() throws Exception {
        File haEntrada = haPdfConTexto("rotar.pdf", "A", "B", "C");
        byte[] haAntes = Files.readAllBytes(haEntrada.toPath());
        File haSalida = haSalida("rotado.pdf");
        haMotor.haEjecutar(haEntrada, null, new HARotar(List.of(1), 90), haSalida);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            assertEquals(0, haD.getPage(0).getRotation());
            assertEquals(90, haD.getPage(1).getRotation());
        }
        assertArrayEquals(haAntes, Files.readAllBytes(haEntrada.toPath()), "la entrada no debe cambiar");
    }

    @Test
    void haReordenaEliminaYDuplica() throws Exception {
        File haEntrada = haPdfConTexto("orden.pdf", "Uno", "Dos", "Tres");
        File haSalida = haSalida("reordenado.pdf");
        haMotor.haEjecutar(haEntrada, null, new HAReorganizar(List.of(2, 0, 0), "prueba"), haSalida);
        assertEquals(List.of("Tres", "Uno", "Uno"), haTextosPorPagina(haSalida));
    }

    @Test
    void haUneVariosPdfEnOrden() throws Exception {
        File haA = haPdfConTexto("a.pdf", "A1", "A2");
        File haB = haPdfConTexto("b.pdf", "B1");
        File haSalida = haSalida("unido.pdf");
        HAUnir haOp = new HAUnir(List.of(
                new HAUnir.HAFuente("b", haB, null, false),
                new HAUnir.HAFuente("a", null, null, true)));
        HAResultado haR = haMotor.haEjecutar(haA, null, haOp, haSalida);
        assertEquals(3, haR.haPaginas());
        assertEquals(List.of("B1", "A1", "A2"), haTextosPorPagina(haSalida));
    }

    @Test
    void haNumeraDesdeLaPaginaIndicada() throws Exception {
        File haEntrada = haPdfConTexto("tesis.pdf", "Caratula", "Indice", "Intro", "Cap");
        File haSalida = haSalida("numerado.pdf");
        haMotor.haEjecutar(haEntrada, null, new HANumerar("Página {n} de {total}", HAPosicion.ABAJO_CENTRO, 3, 1, 11), haSalida);
        List<String> haTextos = haTextosPorPagina(haSalida);
        assertFalse(haTextos.get(0).contains("Página"), "la carátula no lleva número");
        assertTrue(haTextos.get(2).contains("Página 1 de 2"));
        assertTrue(haTextos.get(3).contains("Página 2 de 2"));
    }

    @Test
    void haNumeraBienPaginasGiradas() throws Exception {
        File haEntrada = haPdfConTexto("girada.pdf", "Hola");
        File haGirada = haSalida("girada2.pdf");
        haMotor.haEjecutar(haEntrada, null, new HARotar(List.of(0), 90), haGirada);
        File haSalida = haSalida("girada-num.pdf");
        haMotor.haEjecutar(haGirada, null, new HANumerar("{n}", HAPosicion.ABAJO_CENTRO, 1, 7, 11), haSalida);
        assertTrue(haTextosPorPagina(haSalida).get(0).contains("7"));
    }

    @Test
    void haMarcaDeAguaDeTextoEnPaginasElegidas() throws Exception {
        File haEntrada = haPdfConTexto("marca.pdf", "Uno", "Dos");
        File haSalida = haSalida("marcado.pdf");
        haMotor.haEjecutar(haEntrada, null,
                new HAMarcaDeAgua("BORRADOR", null, Color.GRAY, 0.3f, true, List.of(1)), haSalida);
        List<String> haTextos = haTextosPorPagina(haSalida);
        assertFalse(haTextos.get(0).contains("BORRADOR"));
        assertTrue(haTextos.get(1).replaceAll("\\s", "").contains("BORRADOR"), haTextos.get(1));
    }

    @Test
    void haComprimeImagenesPesadasEInformaElAhorro() throws Exception {
        File haEntrada = haPdfConFoto("foto.pdf");
        long haAntes = haEntrada.length();
        HAResultado haR = haCompresor.haComprimir(haEntrada, null, HANivelCompresion.MEDIA, haSalida("comprimido.pdf"));
        assertFalse(haR.haSinCambios());
        assertTrue(haR.haBytes() < haAntes / 2, "debería pesar menos de la mitad: " + haAntes + " -> " + haR.haBytes());
        assertTrue(haR.haDetalle().contains("%"));
    }

    @Test
    void haCompresionHastaUnObjetivo() throws Exception {
        File haEntrada = haPdfConFoto("objetivo.pdf");
        long haObjetivo = haEntrada.length() / 3;
        HAResultado haR = haCompresor.haHastaObjetivo(haEntrada, null, haObjetivo, haSalida("objetivo-out.pdf"));
        assertTrue(haR.haCumple(), haR.haDetalle());
        assertTrue(haR.haBytes() <= haObjetivo);
    }

    @Test
    void haPdfSoloTextoNoSeAgrandaAlComprimir() throws Exception {
        File haEntrada = haPdfConTexto("texto.pdf", "Solo texto");
        HAResultado haR = haCompresor.haComprimir(haEntrada, null, HANivelCompresion.FUERTE, haSalida("texto-out.pdf"));
        if (haR.haSinCambios()) {
            assertTrue(haR.haDetalle().contains("optimizado"));
        } else {
            assertTrue(haR.haBytes() < haEntrada.length());
        }
    }

    @Test
    void haDivideYExtraeSinTocarElOriginal() throws Exception {
        File haEntrada = haPdfConTexto("dividir.pdf", "1", "2", "3", "4", "5");
        byte[] haAntes = Files.readAllBytes(haEntrada.toPath());
        File haDestino = haCarpeta.resolve("partes").toFile();
        List<File> haPartes = haDivisor.haDividir(haEntrada, null, HAPlanDivision.haCadaN(2, 5, "dividir"), haDestino);
        assertEquals(3, haPartes.size());
        assertEquals(List.of("5"), haTextosPorPagina(haPartes.get(2)));

        List<File> haOtraVez = haDivisor.haDividir(haEntrada, null, HAPlanDivision.haCadaN(2, 5, "dividir"), haDestino);
        assertTrue(haOtraVez.get(0).getName().contains("(2)"));

        File haExtraido = haDivisor.haExtraer(haEntrada, null, List.of(3, 1), haSalida("extraido.pdf"));
        assertEquals(List.of("4", "2"), haTextosPorPagina(haExtraido));
        assertArrayEquals(haAntes, Files.readAllBytes(haEntrada.toPath()));
    }

    @Test
    void haElMotorSeNiegaASobrescribirLaEntrada() throws Exception {
        File haEntrada = haPdfConTexto("propio.pdf", "x");
        assertThrows(HAPdfListoException.class,
                () -> haMotor.haEjecutar(haEntrada, null, new HARotar(List.of(0), 90), haEntrada));
    }

    @Test
    void haSiLaOperacionFallaNoQuedaArchivoDeSalida() throws Exception {
        File haEntrada = haPdfConTexto("falla.pdf", "x");
        File haSalida = haSalida("no-debe-existir.pdf");
        assertThrows(HAPdfListoException.class,
                () -> haMotor.haEjecutar(haEntrada, null, new HANumerar("sin numero", HAPosicion.ABAJO_CENTRO, 1, 1, 11), haSalida));
        assertFalse(haSalida.exists());
        try (var haArchivos = Files.list(haCarpeta)) {
            assertTrue(haArchivos.noneMatch(haP -> haP.getFileName().toString().endsWith(".tmp")), "no deben quedar temporales");
        }
    }

    private File haSalida(String haNombre) {
        return haCarpeta.resolve(haNombre).toFile();
    }

    private File haPdfConTexto(String haNombre, String... haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String haTexto : haPaginas) {
                PDPage haPagina = new PDPage(PDRectangle.A4);
                haDoc.addPage(haPagina);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, 14);
                    haCs.newLineAtOffset(72, 700);
                    haCs.showText(haTexto);
                    haCs.endText();
                }
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private File haPdfConFoto(String haNombre) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        BufferedImage haFoto = new BufferedImage(2400, 2400, BufferedImage.TYPE_INT_RGB);
        Random haR = new Random(7);
        for (int haY = 0; haY < haFoto.getHeight(); haY++) {
            for (int haX = 0; haX < haFoto.getWidth(); haX++) {
                int haBase = (haX / 8 + haY / 8) % 255;
                haFoto.setRGB(haX, haY, new Color(haBase, (haBase + haR.nextInt(30)) % 255, (haX * haY) % 255).getRGB());
            }
        }
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haPagina = new PDPage(PDRectangle.A4);
            haDoc.addPage(haPagina);
            PDImageXObject haImg = LosslessFactory.createFromImage(haDoc, haFoto);
            try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                haCs.drawImage(haImg, 40, 100, 515, 515);
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private static List<String> haTextosPorPagina(File haPdf) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            PDFTextStripper haLector = new PDFTextStripper();
            List<String> haTextos = new java.util.ArrayList<>();
            for (int haI = 1; haI <= haD.getNumberOfPages(); haI++) {
                haLector.setStartPage(haI);
                haLector.setEndPage(haI);
                haTextos.add(haLector.getText(haD).trim());
            }
            return haTextos;
        }
    }
}
