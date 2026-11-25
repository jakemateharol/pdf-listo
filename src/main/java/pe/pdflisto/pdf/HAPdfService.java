package pe.pdflisto.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAContrasenaRequeridaException;
import pe.pdflisto.exception.HAPdfListoException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@Slf4j
public class HAPdfService {
    public HADocumentoPdf haAbrir(File haArchivo, String haContrasena) {
        if (haArchivo == null || !haArchivo.isFile()) {
            throw new HAPdfListoException("No se encontró el archivo " + (haArchivo == null ? "" : haArchivo.getName()) + ".");
        }
        if (!haArchivo.canRead()) {
            throw new HAPdfListoException("No hay permiso para leer «" + haArchivo.getName() + "».");
        }
        PDDocument haPdf = null;
        try {
            haPdf = HACargaPdf.haAbrir(haArchivo, haContrasena);
            if (haPdf.getNumberOfPages() == 0) {
                throw new HAPdfListoException("«" + haArchivo.getName() + "» no tiene páginas.");
            }
            return new HADocumentoPdf(haArchivo, haContrasena, haPdf);
        } catch (InvalidPasswordException haE) {
            throw new HAContrasenaRequeridaException(haContrasena != null);
        } catch (IOException haE) {
            log.warn("No se pudo abrir {}", haArchivo, haE);
            throw new HAPdfListoException(HADiagnosticoPdf.haExplicar(haArchivo), haE);
        } catch (OutOfMemoryError haE) {
            haCerrarSilencioso(haPdf);
            log.error("Sin memoria al abrir {}", haArchivo, haE);
            throw new HAPdfListoException("«" + haArchivo.getName() + "» es demasiado grande para la memoria disponible. "
                    + "Cierra otros documentos o programas y vuelve a intentarlo.");
        } catch (HAPdfListoException haE) {
            haCerrarSilencioso(haPdf);
            throw haE;
        } catch (RuntimeException haE) {
            haCerrarSilencioso(haPdf);
            log.error("Error inesperado al abrir {}", haArchivo, haE);
            throw new HAPdfListoException("No se pudo abrir «" + haArchivo.getName() + "». El detalle quedó en el registro.", haE);
        }
    }

    public void haGuardarCopia(HADocumentoPdf haDocumento, File haDestino, File... haProtegidos) {
        Path haFinal_ = haDestino.toPath().toAbsolutePath();
        if (haEsElMismoArchivo(haDocumento.haArchivo().toPath(), haFinal_)) {
            throw new HAPdfListoException("Por seguridad el original nunca se sobrescribe. Elige otro nombre.");
        }
        for (File haProtegido : haProtegidos) {
            if (haProtegido != null && haEsElMismoArchivo(haProtegido.toPath(), haFinal_)) {
                throw new HAPdfListoException("Por seguridad el original nunca se sobrescribe. Elige otro nombre.");
            }
        }
        try {
            HAEscrituraSegura.haCopiar(haDocumento.haArchivo(), haDocumento.haContrasena(), haDocumento.haPaginas(), haDestino);
        } catch (IOException haE) {
            log.warn("No se pudo guardar la copia en {}", haFinal_, haE);
            throw new HAPdfListoException("No se pudo guardar la copia: " + haE.getMessage(), haE);
        }
    }

    public boolean haEsPropietario(File haArchivo, String haContrasena) {
        try (PDDocument haPdf = Loader.loadPDF(haArchivo, haContrasena)) {
            return haPdf.getCurrentAccessPermission().isOwnerPermission();
        } catch (IOException haE) {
            return false;
        }
    }

    static boolean haEsElMismoArchivo(Path haA, Path haB) {
        try {
            return Files.exists(haB) && Files.isSameFile(haA, haB);
        } catch (IOException haE) {
            return true;
        }
    }

    private static void haCerrarSilencioso(PDDocument haPdf) {
        if (haPdf != null) {
            try {
                haPdf.close();
            } catch (IOException haIgnorado) {
            }
        }
    }
}
