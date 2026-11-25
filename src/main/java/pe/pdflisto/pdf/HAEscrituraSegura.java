package pe.pdflisto.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import pe.pdflisto.exception.HAPdfListoException;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HAEscrituraSegura {
    private HAEscrituraSegura() {
    }

    public static void haGuardar(PDDocument haDocumento, File haDestino) throws IOException {
        haGuardar(haDocumento, haDestino, null);
    }

    public static void haGuardar(PDDocument haDocumento, File haDestino, String haContrasena) throws IOException {
        int haPaginas = haDocumento.getNumberOfPages();
        haEscribir(haDestino, haTemporal -> haDocumento.save(haTemporal.toFile()), haContrasena, haPaginas);
    }

    public static void haCopiar(File haOrigen, String haContrasena, int haPaginas, File haDestino) throws IOException {
        haEscribir(haDestino, haTemporal -> Files.copy(haOrigen.toPath(), haTemporal, StandardCopyOption.REPLACE_EXISTING),
                haContrasena, haPaginas);
    }

    public static void haVerificar(File haArchivo, String haContrasena, int haPaginasEsperadas) {
        try (PDDocument haCopia = Loader.loadPDF(haArchivo, haContrasena)) {
            if (haCopia.getNumberOfPages() != haPaginasEsperadas) {
                throw new HAPdfListoException("El resultado no superó la verificación (tiene "
                        + haCopia.getNumberOfPages() + " páginas en vez de " + haPaginasEsperadas + "). No se guardó.");
            }
        } catch (IOException haE) {
            throw new HAPdfListoException("El resultado no superó la verificación: el archivo no se puede abrir. No se guardó.", haE);
        }
    }

    private interface HAEscritor {
        void haEscribir(Path haTemporal) throws IOException;
    }

    private static void haEscribir(File haDestino, HAEscritor haEscritor, String haContrasena, int haPaginas) throws IOException {
        Path haFinal_ = haDestino.toPath().toAbsolutePath();
        Files.createDirectories(haFinal_.getParent());
        Path haTemporal = Files.createTempFile(haFinal_.getParent(), ".pdflisto-", ".tmp");
        try {
            haEscritor.haEscribir(haTemporal);
            haVerificar(haTemporal.toFile(), haContrasena, haPaginas);
            try {
                Files.move(haTemporal, haFinal_, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException haE) {
                Files.move(haTemporal, haFinal_, StandardCopyOption.REPLACE_EXISTING);
            }
            haTemporal = null;
        } finally {
            if (haTemporal != null) {
                Files.deleteIfExists(haTemporal);
            }
        }
    }
}
