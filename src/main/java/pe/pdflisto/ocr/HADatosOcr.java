package pe.pdflisto.ocr;

import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.exception.HAPdfListoException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class HADatosOcr {
    public enum HAIdioma {
        ESPANOL("Español", "spa"),
        ESPANOL_INGLES("Español + inglés", "spa+eng"),
        INGLES("Inglés", "eng");

        private final String haNombre;
        private final String haCodigo;

        HAIdioma(String haNombre, String haCodigo) {
            this.haNombre = haNombre;
            this.haCodigo = haCodigo;
        }

        public String haCodigo() {
            return haCodigo;
        }

        @Override
        public String toString() {
            return haNombre;
        }
    }

    private static final String[] HA_ARCHIVOS = {"spa.traineddata", "eng.traineddata"};
    private static final String HA_OSD = "osd.traineddata";
    private static Path haPreparada;
    private static boolean haHayOrientacion;

    private HADatosOcr() {
    }

    public static synchronized Path haCarpeta() {
        if (haPreparada != null) {
            return haPreparada;
        }
        Path haDestino = HACarpetaDatos.haRuta().resolve("tessdata");
        try {
            Files.createDirectories(haDestino);
            for (String haNombre : HA_ARCHIVOS) {
                Path haArchivo = haDestino.resolve(haNombre);
                try (InputStream haIn = HADatosOcr.class.getResourceAsStream("/tessdata/" + haNombre)) {
                    if (haIn == null) {
                        throw new HAPdfListoException("Faltan los datos de idioma del OCR (" + haNombre + ") dentro de la aplicación.");
                    }
                    byte[] haDatos = haIn.readAllBytes();
                    if (!Files.isRegularFile(haArchivo) || Files.size(haArchivo) != haDatos.length) {
                        Path haTemporal = haDestino.resolve(haNombre + ".tmp");
                        Files.write(haTemporal, haDatos);
                        Files.move(haTemporal, haArchivo, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            haHayOrientacion = haCopiarOpcional(haDestino, HA_OSD);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudieron preparar los datos del OCR: " + haE.getMessage(), haE);
        }
        haPreparada = haDestino;
        return haDestino;
    }

    public static synchronized boolean haHayOrientacion() {
        haCarpeta();
        return haHayOrientacion;
    }

    private static boolean haCopiarOpcional(Path haDestino, String haNombre) throws IOException {
        Path haArchivo = haDestino.resolve(haNombre);
        try (InputStream haIn = HADatosOcr.class.getResourceAsStream("/tessdata/" + haNombre)) {
            if (haIn == null) {
                return Files.isRegularFile(haArchivo);
            }
            byte[] haDatos = haIn.readAllBytes();
            if (!Files.isRegularFile(haArchivo) || Files.size(haArchivo) != haDatos.length) {
                Path haTemporal = haDestino.resolve(haNombre + ".tmp");
                Files.write(haTemporal, haDatos);
                Files.move(haTemporal, haArchivo, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        }
    }
}
