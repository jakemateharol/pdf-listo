package pe.pdflisto.edicion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HACombinacionTest {
    @TempDir
    Path haCarpeta;

    @Test
    void haCombinacionDeLaVersionActual() throws Exception {
        File haOriginal = Files.writeString(haCarpeta.resolve("a.pdf"), "x").toFile();
        HASesionEdicion haS = new HASesionEdicion(haOriginal, null, haCarpeta.resolve("trabajo"));
        assertNull(haS.haCodigosAplicados());
        haS.haAgregar(new HASesionEdicion.HAVersion(haArchivo(haS), "Numerar", null, false, "NUMERAR:x"));
        haS.haAgregar(new HASesionEdicion.HAVersion(haArchivo(haS), "Rotar página 3", null, false, null));
        haS.haAgregar(new HASesionEdicion.HAVersion(haArchivo(haS), "Comprimir", null, false, "COMPRIMIR:y"));
        assertEquals("NUMERAR:x;COMPRIMIR:y", haS.haCodigosAplicados());
        haS.haDeshacer();
        assertEquals("NUMERAR:x", haS.haCodigosAplicados(), "lo deshecho no cuenta");
        haS.haCerrar();
    }

    private static File haArchivo(HASesionEdicion haS) throws Exception {
        File haF = haS.haNuevoArchivo();
        Files.writeString(haF.toPath(), "v");
        return haF;
    }
}
