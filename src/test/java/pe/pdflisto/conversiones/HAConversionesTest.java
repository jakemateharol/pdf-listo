package pe.pdflisto.conversiones;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAControl;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAConversionesTest {
    private final HAConversor haConversor = new HAConversor();

    @TempDir
    Path haCarpeta;

    @Test
    void haImagenesAPdfEnA4() throws Exception {
        File haVertical = haImagen("foto1.jpg", 1200, 1600, "jpg");
        File haHorizontal = haImagen("foto2.png", 1600, 900, "png");
        File haPdf = haCarpeta.resolve("album.pdf").toFile();
        haConversor.haImagenesAPdf(List.of(haVertical, haHorizontal), HAConversor.HATamanoHoja.A4, haPdf, HAControl.haNinguno());
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            assertEquals(2, haD.getNumberOfPages());
            assertEquals(595, haD.getPage(0).getMediaBox().getWidth(), 1);
            assertEquals(842, haD.getPage(1).getMediaBox().getWidth(), 1, "la foto horizontal va en hoja horizontal");
        }
        assertThrows(HAPdfListoException.class, () -> haConversor.haImagenesAPdf(
                List.of(Files.writeString(haCarpeta.resolve("no.png"), "x").toFile()), HAConversor.HATamanoHoja.A4,
                haCarpeta.resolve("x.pdf").toFile(), HAControl.haNinguno()));
    }

    @Test
    void haPdfAImagenesYATexto() throws Exception {
        File haPdf = haPdfTexto("informe.pdf", "Primera página con acentos: año, señal", "Segunda página");
        File haSalida = Files.createDirectory(haCarpeta.resolve("imagenes")).toFile();
        List<File> haImgs = haConversor.haPdfAImagenes(haPdf, null, 72, false, haSalida, HAControl.haNinguno());
        assertEquals(2, haImgs.size());
        assertEquals("informe - página 01.png", haImgs.get(0).getName());
        assertEquals(595, ImageIO.read(haImgs.get(0)).getWidth(), 2);

        File haTxt = haCarpeta.resolve("informe.txt").toFile();
        haConversor.haPdfATexto(haPdf, null, haTxt, HAControl.haNinguno());
        String haTexto = Files.readString(haTxt.toPath(), StandardCharsets.UTF_8);
        assertTrue(haTexto.contains("===== Página 2 ====="));
        assertTrue(haTexto.contains("año, señal"), haTexto);
    }

    @Test
    void haTextoAPdfConLineasLargasYCodificacionDeWindows() throws Exception {
        String haLargo = "Esta es una línea muy larga ".repeat(20);
        File haTxt = haCarpeta.resolve("notas.txt").toFile();
        Files.writeString(haTxt.toPath(), "Título con ñ\n\n" + haLargo + "\n" + "renglón\n".repeat(120),
                Charset.forName("windows-1252"));
        File haPdf = haCarpeta.resolve("notas.pdf").toFile();
        haConversor.haTextoAPdf(haTxt, haPdf, HAControl.haNinguno());
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            assertTrue(haD.getNumberOfPages() >= 3, "el texto largo pasa a varias páginas");
            String haT = new PDFTextStripper().getText(haD);
            assertTrue(haT.contains("Título con ñ"), "se detectó que el .txt era de Windows (no UTF-8)");
        }
    }

    @Test
    void haExtraeLasImagenesSinRepetirLasQueSeUsanVariasVeces() throws Exception {
        File haPdf = haCarpeta.resolve("con-fotos.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            var haFoto = JPEGFactory.createFromImage(haD, haLienzo(800, 600));
            var haLogo = LosslessFactory.createFromImage(haD, haLienzo(200, 200));
            for (int haI = 0; haI < 3; haI++) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haD.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                    haCs.drawImage(haLogo, 20, 760, 60, 60);
                    if (haI == 1) {
                        haCs.drawImage(haFoto, 100, 300, 400, 300);
                    }
                }
            }
            haD.save(haPdf);
        }
        File haSalida = Files.createDirectory(haCarpeta.resolve("extraidas")).toFile();
        List<File> haImgs = haConversor.haExtraerImagenes(haPdf, null, haSalida, HAControl.haNinguno());
        assertEquals(2, haImgs.size(), "logo una sola vez + la foto");
        assertTrue(haImgs.stream().anyMatch(haF -> haF.getName().endsWith(".jpg")), "la foto JPEG se guarda tal cual");
        assertThrows(HAPdfListoException.class, () -> haConversor.haExtraerImagenes(haPdfTexto("solo-texto.pdf", "x"), null,
                haSalida, HAControl.haNinguno()));
    }

    @Test
    void haWordSinLibreOfficeDaUnMensajeClaro() {
        HAConversorWord haWord = new HAConversorWord();
        if (haWord.haDisponible()) {
            return;
        }
        HAPdfListoException haE = assertThrows(HAPdfListoException.class, () -> haWord.haConvertir(
                haCarpeta.resolve("carta.docx").toFile(), haCarpeta.resolve("carta.pdf").toFile(), HAControl.haNinguno()));
        assertTrue(haE.getMessage().contains("instala LibreOffice"));
        List<String> haCmd = HAConversorWord.haComando(new File("C:/LO/soffice.exe"), new File("C:/docs/carta.docx"),
                haCarpeta, haCarpeta.resolve("perfil"));
        assertTrue(haCmd.containsAll(List.of("--headless", "--convert-to", "pdf")));
    }

    private static BufferedImage haLienzo(int haW, int haH) {
        BufferedImage haImg = new BufferedImage(haW, haH, BufferedImage.TYPE_INT_RGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(new Color(40, 120, 200));
        haG.fillRect(0, 0, haW, haH);
        haG.setColor(Color.WHITE);
        haG.fillOval(haW / 4, haH / 4, haW / 2, haH / 2);
        haG.dispose();
        return haImg;
    }

    private File haImagen(String haNombre, int haW, int haH, String haFormato) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        ImageIO.write(haLienzo(haW, haH), haFormato, haF);
        return haF;
    }

    private File haPdfTexto(String haNombre, String... haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String haT : haPaginas) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haDoc.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, 12);
                    haCs.newLineAtOffset(72, 700);
                    haCs.showText(haT);
                    haCs.endText();
                }
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
