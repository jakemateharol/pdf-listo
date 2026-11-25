package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.pdf.HACargaPdf;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class HARecursos implements AutoCloseable {
    private final List<Closeable> haAbiertos = new ArrayList<>();
    private final List<String> haNotas = new ArrayList<>();
    private final HAControl haControl;

    public HARecursos() {
        this(HAControl.haNinguno());
    }

    public HARecursos(HAControl haControl) {
        this.haControl = haControl;
    }

    public HAControl haControl() {
        return haControl;
    }

    public void haNota(String haTexto) {
        haNotas.add(haTexto);
    }

    public List<String> haNotas() {
        return List.copyOf(haNotas);
    }

    public PDDocument haAbrir(File haArchivo, String haContrasena) throws IOException {
        try {
            return haRegistrar(HACargaPdf.haAbrir(haArchivo, haContrasena));
        } catch (InvalidPasswordException haE) {
            throw new HAPdfListoException("«" + haArchivo.getName() + "» tiene contraseña. Ábrelo primero en PDF Listo "
                    + "(te la pedirá) y vuelve a intentarlo eligiéndolo de la lista de documentos abiertos.");
        }
    }

    public <T extends Closeable> T haRegistrar(T haRecurso) {
        haAbiertos.add(haRecurso);
        return haRecurso;
    }

    @Override
    public void close() {
        for (int haI = haAbiertos.size() - 1; haI >= 0; haI--) {
            try {
                haAbiertos.get(haI).close();
            } catch (IOException haIgnorado) {
            }
        }
        haAbiertos.clear();
    }
}
