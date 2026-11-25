package pe.pdflisto.edicion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HARecuperacionTest {
    @TempDir
    Path haBase;

    @Test
    void haRecuperaUnaSesionAbandonadaConSusVersiones() throws Exception {
        File haOriginal = Files.writeString(haBase.resolve("contrato.pdf"), "x").toFile();
        Path haTrabajo = haBase.resolve("trabajo");
        HASesionEdicion haS = new HASesionEdicion(haOriginal, null, haTrabajo.resolve("sesion-1"));
        File haV1 = Files.writeString(haS.haNuevoArchivo().toPath(), "1").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV1, "Rotar página 2"));
        File haV2 = Files.writeString(haS.haNuevoArchivo().toPath(), "2").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV2, "Comprimir", "clave", true));
        haS.haDeshacer();

        assertTrue(HARecuperacion.haBuscar(haTrabajo).isEmpty());

        haS.haLiberarSinBorrar();
        List<HARecuperacion.HAAbandonada> haLista = HARecuperacion.haBuscar(haTrabajo);
        assertEquals(1, haLista.size());
        HADiarioSesion.HADatos haD = haLista.get(0).haDatos();
        assertEquals("contrato.pdf", haD.haNombre());
        assertEquals(2, haD.haCambios());
        assertEquals(haV1.getAbsoluteFile(), haD.haArchivoActual().getAbsoluteFile());

        HASesionEdicion haR = HASesionEdicion.haRecuperar(haLista.get(0).haCarpeta(), haD);
        assertEquals(haV1.getAbsoluteFile(), haR.haArchivoActual().getAbsoluteFile());
        assertEquals("Rotar página 2", haR.haQueDeshace());
        assertEquals("Comprimir", haR.haQueRehace());
        assertTrue(haR.haHayCambios());
        haR.haRehacer();
        assertNull(haR.haContrasenaActual(), "las contraseñas nunca se guardan en el diario");
        haR.haPonerContrasenaActual("clave");
        assertEquals("clave", haR.haContrasenaActual());

        haR.haCerrar();
        assertTrue(haOriginal.exists());
        assertTrue(HARecuperacion.haBuscar(haTrabajo).isEmpty());
    }

    @Test
    void haSesionesSinCambiosSeBorranSinPreguntar() throws Exception {
        File haOriginal = Files.writeString(haBase.resolve("a.pdf"), "x").toFile();
        Path haTrabajo = haBase.resolve("trabajo");
        HASesionEdicion haS = new HASesionEdicion(haOriginal, null, haTrabajo.resolve("sesion-2"));
        File haV1 = Files.writeString(haS.haNuevoArchivo().toPath(), "1").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV1, "Rotar"));
        haS.haMarcarGuardado();
        haS.haLiberarSinBorrar();
        assertTrue(HARecuperacion.haBuscar(haTrabajo).isEmpty());
        assertFalse(Files.exists(haTrabajo.resolve("sesion-2")), "se limpia sola");
    }

    @Test
    void haUnDiarioDanadoNoRompeElInicio() throws Exception {
        Path haTrabajo = haBase.resolve("trabajo");
        Path haRara = Files.createDirectories(haTrabajo.resolve("sesion-rara"));
        Files.writeString(haRara.resolve(HADiarioSesion.HA_ARCHIVO), "esto no es un diario");
        assertTrue(HARecuperacion.haBuscar(haTrabajo).isEmpty());
        assertFalse(Files.exists(haRara));
    }
}
