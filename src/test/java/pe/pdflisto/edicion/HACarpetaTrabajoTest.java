package pe.pdflisto.edicion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.sistema.HAEspacioDisco;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class HACarpetaTrabajoTest {
    @TempDir
    Path haBase;

    @Test
    void haAlCerrarSeBorranLasVersionesTemporales() throws Exception {
        File haOriginal = Files.writeString(haBase.resolve("a.pdf"), "x").toFile();
        Path haCarpeta = haBase.resolve("trabajo").resolve("sesion-1");
        HASesionEdicion haS = new HASesionEdicion(haOriginal, null, haCarpeta);
        for (int haI = 0; haI < 3; haI++) {
            File haV = Files.writeString(haS.haNuevoArchivo().toPath(), "version " + haI).toFile();
            haS.haAgregar(new HASesionEdicion.HAVersion(haV, "Paso " + haI));
        }
        assertTrue(Files.exists(haCarpeta.resolve(HADiarioSesion.HA_ARCHIVO)));
        haS.haCerrar();
        assertFalse(Files.exists(haCarpeta), "no queda nada de la sesión");
        assertTrue(haOriginal.exists(), "el original nunca se toca");
    }

    @Test
    void haSiUnArchivoNoSePuedeBorrarNoSeOfreceComoTrabajoSinGuardar() throws Exception {
        File haOriginal = Files.writeString(haBase.resolve("a.pdf"), "x").toFile();
        Path haTrabajo = haBase.resolve("trabajo");
        HASesionEdicion haS = new HASesionEdicion(haOriginal, null, haTrabajo.resolve("sesion-2"));
        File haV1 = Files.writeString(haS.haNuevoArchivo().toPath(), "1").toFile();
        haS.haAgregar(new HASesionEdicion.HAVersion(haV1, "Rotar"));
        try (var haAbierto = java.nio.channels.FileChannel.open(haV1.toPath(), java.nio.file.StandardOpenOption.READ,
                java.nio.file.StandardOpenOption.WRITE)) {
            var haBloqueo = haAbierto.lock();
            haS.haCerrar();
            haBloqueo.release();
        }
        assertTrue(HARecuperacion.haBuscar(haTrabajo).isEmpty());
        assertFalse(Files.exists(haTrabajo.resolve("sesion-2")));
    }

    @Test
    void haLimpiarSobrantesRespetaLoQueSeUsaYLoQueSePuedeRecuperar() throws Exception {
        Path haTrabajo = haBase.resolve("trabajo");
        Path haTemporales = Files.createDirectories(haBase.resolve("temp"));
        File haOriginal = Files.writeString(haBase.resolve("a.pdf"), "x").toFile();

        HASesionEdicion haAbierta = new HASesionEdicion(haOriginal, null, haTrabajo.resolve("sesion-abierta"));
        Files.writeString(haAbierta.haNuevoArchivo().toPath(), "1");
        HASesionEdicion haCaida = new HASesionEdicion(haOriginal, null, haTrabajo.resolve("sesion-caida"));
        File haV = Files.writeString(haCaida.haNuevoArchivo().toPath(), "1").toFile();
        haCaida.haAgregar(new HASesionEdicion.HAVersion(haV, "Rotar"));
        haCaida.haLiberarSinBorrar();
        Files.createDirectories(haTrabajo.resolve("sesion-vacia"));
        Files.writeString(haTrabajo.resolve("suelto.tmp"), "x");
        Path haViejo = Files.createDirectories(haTemporales.resolve("pdflisto123"));
        Files.writeString(haViejo.resolve("informe.pdf"), "x");
        Files.setLastModifiedTime(haViejo, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        Path haReciente = Files.createDirectories(haTemporales.resolve("pdflisto456"));
        Path haAjeno = Files.createDirectories(haTemporales.resolve("otro-programa"));
        Files.setLastModifiedTime(haAjeno, FileTime.from(Instant.now().minus(30, ChronoUnit.DAYS)));

        HACarpetaTrabajo.haLimpiarSobrantes(haTrabajo, haTemporales);

        assertTrue(Files.exists(haTrabajo.resolve("sesion-abierta")));
        assertTrue(Files.exists(haTrabajo.resolve("sesion-caida")));
        assertFalse(Files.exists(haTrabajo.resolve("sesion-vacia")));
        assertFalse(Files.exists(haTrabajo.resolve("suelto.tmp")));
        assertFalse(Files.exists(haViejo));
        assertTrue(Files.exists(haReciente));
        assertTrue(Files.exists(haAjeno), "nunca se tocan temporales de otros programas");
        haAbierta.haCerrar();
    }

    @Test
    void haCalculaElEspacioNecesarioYExplicaCuandoNoAlcanza() {
        assertEquals(4 * 10 * HAEspacioDisco.HA_MB + 100 * HAEspacioDisco.HA_MB, HAEspacioDisco.haNecesarioPara(10 * HAEspacioDisco.HA_MB));
        long haLibre = HAEspacioDisco.haLibre(haBase);
        assertTrue(haLibre > 0);
        assertTrue(HAEspacioDisco.haFaltaPara(haBase, 1024).isEmpty(), "un archivo pequeño siempre cabe en este equipo");
        String haMensaje = HAEspacioDisco.haFaltaPara(haBase, haLibre).orElseThrow();
        assertTrue(haMensaje.contains("No hay espacio suficiente"), haMensaje);
        assertTrue(haMensaje.contains("no se modificó"), haMensaje);
        assertEquals(haLibre, HAEspacioDisco.haLibre(haBase.resolve("no/existe/aun")), 64 * HAEspacioDisco.HA_MB);
    }

    @Test
    void haReconoceElErrorDeDiscoLleno() {
        IOException haWindows = new IOException("No hay espacio suficiente en el disco");
        assertTrue(HAEspacioDisco.haEsDiscoLleno(new RuntimeException(haWindows), haBase));
        assertTrue(HAEspacioDisco.haEsDiscoLleno(new IOException("There is not enough space on the disk"), haBase));
        assertFalse(HAEspacioDisco.haEsDiscoLleno(new IOException("El archivo está dañado"), haBase),
                "con espacio de sobra, otro error no se confunde con disco lleno");
        assertTrue(HAEspacioDisco.haMensajeDiscoLleno("Comprimir", haBase).contains("quedó como estaba"));
    }
}
