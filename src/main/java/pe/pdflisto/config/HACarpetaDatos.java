package pe.pdflisto.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HACarpetaDatos {
    public static final String HA_PROPIEDAD = "app.datos";

    private HACarpetaDatos() {
    }

    public static synchronized Path haRuta() {
        String haActual = System.getProperty(HA_PROPIEDAD);
        if (haActual != null) {
            return Path.of(haActual);
        }
        String haAppData = System.getenv("APPDATA");
        Path haBase = haAppData != null && !haAppData.isBlank()
                ? Path.of(haAppData)
                : Path.of(System.getProperty("user.home"), ".config");
        Path haCarpeta = haBase.resolve("PDFListo");
        try {
            Files.createDirectories(haCarpeta.resolve("logs"));
        } catch (IOException haE) {
            throw new UncheckedIOException("No se pudo crear la carpeta de datos " + haCarpeta, haE);
        }
        System.setProperty(HA_PROPIEDAD, haCarpeta.toString().replace('\\', '/'));
        return haCarpeta;
    }
}
