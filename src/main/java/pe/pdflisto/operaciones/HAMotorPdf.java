package pe.pdflisto.operaciones;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.pdf.HACargaPdf;
import pe.pdflisto.pdf.HAEscrituraSegura;

import java.io.File;
import java.io.IOException;

@Service
@Slf4j
public class HAMotorPdf {
    public HAResultado haEjecutar(File haEntrada, String haContrasena, HAOperacionPdf haOperacion, File haSalida) {
        return haEjecutar(haEntrada, haContrasena, haOperacion, haSalida, HAControl.haNinguno());
    }

    public HAResultado haEjecutar(File haEntrada, String haContrasena, HAOperacionPdf haOperacion, File haSalida, HAControl haControl) {
        if (haSonElMismo(haEntrada, haSalida)) {
            throw new HAPdfListoException("Por seguridad el original nunca se sobrescribe.");
        }
        haControl.haMensaje(haOperacion.haDescripcion() + ": abriendo…");
        try (PDDocument haDocumento = HACargaPdf.haAbrir(haEntrada, haContrasena);
             HARecursos haRecursos = new HARecursos(haControl)) {
            PDDocument haResultado = haOperacion.haAplicar(haDocumento, haRecursos);
            haControl.haMensaje(haOperacion.haDescripcion() + ": guardando y verificando…");
            if (haResultado.getNumberOfPages() == 0) {
                throw new HAPdfListoException("El resultado quedaría sin páginas; no se aplicó.");
            }
            String haClave = null;
            if (haOperacion.haConservaSeguridad()) {
                haClave = haOperacion.haContrasenaResultado();
            } else if (haResultado.isEncrypted()) {
                haResultado.setAllSecurityToBeRemoved(true);
            }
            HAEscrituraSegura.haGuardar(haResultado, haSalida, haClave);
            HAResultado haR = HAResultado.haDe(haSalida, haResultado.getNumberOfPages()).haConContrasena(haClave).haConEsperado(haOperacion.haEsperado());
            return haRecursos.haNotas().isEmpty() ? haR : haR.haConDetalle(false, true, String.join(" ", haRecursos.haNotas()));
        } catch (HAPdfListoException haE) {
            throw haE;
        } catch (InvalidPasswordException haE) {
            throw new HAPdfListoException("No se pudo abrir el PDF para «" + haOperacion.haDescripcion() + "»: contraseña incorrecta.", haE);
        } catch (IOException | RuntimeException haE) {
            log.error("Fallo la operacion '{}' sobre {}", haOperacion.haDescripcion(), haEntrada, haE);
            throw new HAPdfListoException("No se pudo completar «" + haOperacion.haDescripcion()
                    + "». El PDF puede tener partes dañadas; el detalle quedó en el registro.", haE);
        } catch (OutOfMemoryError haE) {
            log.error("Sin memoria en '{}' sobre {}", haOperacion.haDescripcion(), haEntrada, haE);
            throw new HAPdfListoException("No alcanzó la memoria para «" + haOperacion.haDescripcion()
                    + "». Prueba con menos páginas a la vez.");
        }
    }

    private static boolean haSonElMismo(File haA, File haB) {
        try {
            return haB.exists() && haA.getCanonicalFile().equals(haB.getCanonicalFile());
        } catch (IOException haE) {
            return true;
        }
    }
}
