package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class HADiagnosticoPdfTest {
    @TempDir
    Path haCarpeta;

    @Test
    void haArchivoVacio() throws Exception {
        File haF = Files.createFile(haCarpeta.resolve("vacio.pdf")).toFile();
        assertTrue(HADiagnosticoPdf.haExplicar(haF).contains("vacío"));
    }

    @Test
    void haPaginaWebDescargadaPorError() throws Exception {
        File haF = Files.writeString(haCarpeta.resolve("web.pdf"), "<!DOCTYPE html><html><body>Error 404</body></html>").toFile();
        assertTrue(HADiagnosticoPdf.haExplicar(haF).contains("página web"));
    }

    @Test
    void haImagenRenombrada() throws Exception {
        File haF = Files.write(haCarpeta.resolve("foto.pdf"), new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x10}).toFile();
        assertTrue(HADiagnosticoPdf.haExplicar(haF).contains("JPG"));
    }

    @Test
    void haWordRenombrado() throws Exception {
        File haF = Files.write(haCarpeta.resolve("doc.pdf"), new byte[]{'P', 'K', 3, 4, 0, 0}).toFile();
        assertTrue(HADiagnosticoPdf.haExplicar(haF).contains("Word"));
    }

    @Test
    void haPdfCortadoAlFinal() throws Exception {
        File haCompleto = haCarpeta.resolve("completo.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            for (int haI = 0; haI < 30; haI++) {
                haD.addPage(new PDPage());
            }
            haD.save(haCompleto);
        }
        byte[] haBytes = Files.readAllBytes(haCompleto.toPath());
        File haCortado = Files.write(haCarpeta.resolve("cortado.pdf"), Arrays.copyOf(haBytes, haBytes.length / 2)).toFile();
        assertTrue(HADiagnosticoPdf.haExplicar(haCortado).contains("incompleto"));
    }

    @Test
    void haAlAbrirSeMuestraElDiagnostico() throws Exception {
        File haF = Files.writeString(haCarpeta.resolve("falso.pdf"), "<html>no</html>").toFile();
        HAPdfListoException haE = assertThrows(HAPdfListoException.class, () -> new HAPdfService().haAbrir(haF, null));
        assertTrue(haE.getMessage().contains("página web"), haE.getMessage());
    }
}
