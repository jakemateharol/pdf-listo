package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAContrasenaRequeridaException;
import pe.pdflisto.exception.HAPdfListoException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HAPdfServiceTest {
    private final HAPdfService haServicio = new HAPdfService();

    @TempDir
    Path haCarpeta;

    @Test
    void haAbreUnPdfYCuentaSusPaginas() throws Exception {
        File haPdf = haCrearPdf("ejemplo.pdf", null, "Hola mundo", "Segunda página con Acción", "Tercera");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haPdf, null)) {
            assertEquals(3, haDoc.haPaginas());
            assertEquals("ejemplo.pdf", haDoc.haNombre());
            assertEquals(595, haDoc.haTamano(0).haAncho(), 1);
        }
    }

    @Test
    void haDibujaUnaPaginaComoImagen() throws Exception {
        File haPdf = haCrearPdf("dibujo.pdf", null, "Hola");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haPdf, null)) {
            BufferedImage haImg = haDoc.haRenderizar(0, 36, HADocumentoPdf.HAPrioridad.ALTA).get(10, TimeUnit.SECONDS);
            assertEquals(298, haImg.getWidth(), 2);
        }
    }

    @Test
    void haLaBusquedaIgnoraMayusculasYTildes() throws Exception {
        File haPdf = haCrearPdf("busqueda.pdf", null, "Hola mundo", "Segunda página con Acción", "Otra acción más");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haPdf, null)) {
            List<HACoincidencia> haR = haDoc.haBuscar("ACCION").get(10, TimeUnit.SECONDS);
            assertEquals(2, haR.size());
            assertEquals(1, haR.get(0).haPagina());
            assertEquals(2, haR.get(1).haPagina());
            HAAreaTexto haArea = haR.get(0).haAreas().get(0);
            assertTrue(haArea.haX() > 0 && haArea.haAncho() > 10, "el resaltado debe tener posicion y ancho");
            assertTrue(haDoc.haBuscar("no existe").get(10, TimeUnit.SECONDS).isEmpty());
        }
    }

    @Test
    void haDetectaPdfSinTexto() throws Exception {
        File haVacio = haCrearPdf("escaneado.pdf", null, "");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haVacio, null)) {
            assertTrue(haDoc.haPareceEscaneado().get(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void haUnArchivoQueNoEsPdfDaUnMensajeClaro() throws Exception {
        File haFalso = haCarpeta.resolve("falso.pdf").toFile();
        Files.writeString(haFalso.toPath(), "esto no es un pdf");
        HAPdfListoException haE = assertThrows(HAPdfListoException.class, () -> haServicio.haAbrir(haFalso, null));
        assertTrue(haE.getMessage().contains("no es un PDF"), haE.getMessage());
    }

    @Test
    void haPideContrasenaYLaValida() throws Exception {
        File haProtegido = haCrearPdf("protegido.pdf", "1234", "Secreto");
        HAContrasenaRequeridaException haSin = assertThrows(HAContrasenaRequeridaException.class,
                () -> haServicio.haAbrir(haProtegido, null));
        assertFalse(haSin.isIncorrecta());
        HAContrasenaRequeridaException haMala = assertThrows(HAContrasenaRequeridaException.class,
                () -> haServicio.haAbrir(haProtegido, "0000"));
        assertTrue(haMala.isIncorrecta());
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haProtegido, "1234")) {
            assertEquals(1, haDoc.haPaginas());
            assertTrue(haDoc.haCifrado());
        }
    }

    @Test
    void haGuardaUnaCopiaVerificadaYElOriginalNoCambia() throws Exception {
        File haOriginal = haCrearPdf("original.pdf", null, "Uno", "Dos");
        byte[] haAntes = Files.readAllBytes(haOriginal.toPath());
        File haCopia = haCarpeta.resolve("salida/copia.pdf").toFile();
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haOriginal, null)) {
            haServicio.haGuardarCopia(haDoc, haCopia);
        }
        assertTrue(haCopia.isFile());
        assertArrayEquals(haAntes, Files.readAllBytes(haOriginal.toPath()), "el original no debe cambiar");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haCopia, null)) {
            assertEquals(2, haDoc.haPaginas());
        }
    }

    @Test
    void haNuncaSobrescribeElOriginal() throws Exception {
        File haOriginal = haCrearPdf("intocable.pdf", null, "Uno");
        try (HADocumentoPdf haDoc = haServicio.haAbrir(haOriginal, null)) {
            HAPdfListoException haE = assertThrows(HAPdfListoException.class, () -> haServicio.haGuardarCopia(haDoc, haOriginal));
            assertTrue(haE.getMessage().contains("nunca se sobrescribe"));
        }
    }

    private File haCrearPdf(String haNombre, String haContrasena, String... haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String haTexto : haPaginas) {
                PDPage haPagina = new PDPage(PDRectangle.A4);
                haDoc.addPage(haPagina);
                if (!haTexto.isEmpty()) {
                    try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                        haCs.beginText();
                        haCs.setFont(haFuente, 14);
                        haCs.newLineAtOffset(72, 700);
                        haCs.showText(haTexto);
                        haCs.endText();
                    }
                }
            }
            if (haContrasena != null) {
                StandardProtectionPolicy haPolitica =
                        new StandardProtectionPolicy("dueño-" + haContrasena, haContrasena, new AccessPermission());
                haPolitica.setEncryptionKeyLength(128);
                haDoc.protect(haPolitica);
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
