package pe.pdflisto.edicion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HASesionEdicionTest {
    @TempDir
    Path haCarpeta;

    @Test
    void haDeshacerRehacerYDescartarRamas() throws Exception {
        File haOriginal = Files.writeString(haCarpeta.resolve("original.pdf"), "x").toFile();
        HASesionEdicion haS = new HASesionEdicion(haOriginal, "secreta", haCarpeta.resolve("trabajo"));
        Files.createDirectories(haCarpeta.resolve("trabajo"));

        assertFalse(haS.haPuedeDeshacer());
        assertFalse(haS.haHayCambios());
        assertEquals("secreta", haS.haContrasenaActual());

        File haV1 = Files.writeString(haS.haNuevoArchivo().toPath(), "1").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV1, "Rotar"));
        File haV2 = Files.writeString(haS.haNuevoArchivo().toPath(), "2").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV2, "Numerar"));

        assertTrue(haS.haHayCambios());
        assertNull(haS.haContrasenaActual(), "las versiones de trabajo no llevan contraseña");
        assertEquals("Numerar", haS.haQueDeshace());

        haS.haDeshacer();
        assertEquals(haV1, haS.haArchivoActual());
        assertEquals("Numerar", haS.haQueRehace());

        File haV3 = Files.writeString(haS.haNuevoArchivo().toPath(), "3").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV3, "Comprimir"));
        assertFalse(haS.haPuedeRehacer());
        assertFalse(haV2.exists());

        haS.haMarcarGuardado();
        assertFalse(haS.haHayCambios());
        haS.haDeshacer();
        haS.haDeshacer();
        assertEquals(haOriginal, haS.haArchivoActual());
        assertTrue(haS.haHayCambios());

        haS.haCerrar();
        assertTrue(haOriginal.exists(), "cerrar nunca borra el original");
        assertFalse(Files.exists(haCarpeta.resolve("trabajo")));
    }
}
