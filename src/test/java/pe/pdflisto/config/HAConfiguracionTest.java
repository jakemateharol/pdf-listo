package pe.pdflisto.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HAConfiguracionTest {
    @TempDir
    Path haCarpeta;

    @Test
    void haLaBienvenidaSeMuestraHastaQueElUsuarioDigaQueNo() {
        Path haArchivo = haCarpeta.resolve("config.properties");
        HAConfiguracion haPrimera = new HAConfiguracion(haArchivo);
        assertTrue(haPrimera.haMostrarBienvenida(), "la primera vez se muestra");
        haPrimera.setMostrarBienvenida(false);
        assertFalse(new HAConfiguracion(haArchivo).haMostrarBienvenida(), "se recuerda al volver a abrir");
        new HAConfiguracion(haArchivo).setMostrarBienvenida(true);
        assertTrue(new HAConfiguracion(haArchivo).haMostrarBienvenida());
    }
}
