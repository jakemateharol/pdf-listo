package pe.pdflisto.edicion;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Slf4j
public final class HADiarioSesion {
    static final String HA_ARCHIVO = "sesion.properties";

    public record HAVersionGuardada(String haArchivo, String haDescripcion, boolean haProtegida, String haCodigo) {
        public HAVersionGuardada(String haArchivo, String haDescripcion, boolean haProtegida) {
            this(haArchivo, haDescripcion, haProtegida, null);
        }
    }

    public record HADatos(String haOriginal, boolean haRestringido, boolean haOriginalProtegido, int haIndice,
                        int haIndiceGuardado, int haContador, List<HAVersionGuardada> haVersiones, long haFecha) {
        public String haNombre() {
            return new File(haOriginal).getName();
        }

        public int haCambios() {
            return haVersiones.size();
        }

        public boolean haHayCambios() {
            return haIndice != haIndiceGuardado;
        }

        public File haArchivoActual() {
            return haIndice == 0 ? new File(haOriginal) : new File(haVersiones.get(haIndice - 1).haArchivo());
        }
    }

    private HADiarioSesion() {
    }

    static void haGuardar(Path haCarpeta, HADatos haD) {
        Properties haP = new Properties();
        haP.setProperty("original", haD.haOriginal());
        haP.setProperty("restringido", String.valueOf(haD.haRestringido()));
        haP.setProperty("originalProtegido", String.valueOf(haD.haOriginalProtegido()));
        haP.setProperty("indice", String.valueOf(haD.haIndice()));
        haP.setProperty("indiceGuardado", String.valueOf(haD.haIndiceGuardado()));
        haP.setProperty("contador", String.valueOf(haD.haContador()));
        haP.setProperty("fecha", String.valueOf(haD.haFecha()));
        haP.setProperty("versiones", String.valueOf(haD.haVersiones().size()));
        for (int haI = 0; haI < haD.haVersiones().size(); haI++) {
            HAVersionGuardada haV = haD.haVersiones().get(haI);
            haP.setProperty("v" + haI + ".archivo", haV.haArchivo());
            haP.setProperty("v" + haI + ".descripcion", haV.haDescripcion());
            haP.setProperty("v" + haI + ".protegida", String.valueOf(haV.haProtegida()));
            if (haV.haCodigo() != null) {
                haP.setProperty("v" + haI + ".codigo", haV.haCodigo());
            }
        }
        try {
            Files.createDirectories(haCarpeta);
            Path haTemporal = haCarpeta.resolve(HA_ARCHIVO + ".tmp");
            try (Writer haW = Files.newBufferedWriter(haTemporal, StandardCharsets.UTF_8)) {
                haP.store(haW, "Diario de PDF Listo (recuperacion tras un cierre inesperado)");
            }
            try {
                Files.move(haTemporal, haCarpeta.resolve(HA_ARCHIVO), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException haE) {
                Files.move(haTemporal, haCarpeta.resolve(HA_ARCHIVO), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException haE) {
            log.warn("No se pudo guardar el diario de la sesión en {}", haCarpeta, haE);
        }
    }

    static HADatos haLeer(Path haCarpeta) throws IOException {
        Properties haP = new Properties();
        try (Reader haR = Files.newBufferedReader(haCarpeta.resolve(HA_ARCHIVO), StandardCharsets.UTF_8)) {
            haP.load(haR);
        }
        try {
            int haN = Integer.parseInt(haP.getProperty("versiones", "0"));
            List<HAVersionGuardada> haVersiones = new ArrayList<>();
            for (int haI = 0; haI < haN; haI++) {
                haVersiones.add(new HAVersionGuardada(haP.getProperty("v" + haI + ".archivo"),
                        haP.getProperty("v" + haI + ".descripcion", "Cambio"),
                        Boolean.parseBoolean(haP.getProperty("v" + haI + ".protegida")), haP.getProperty("v" + haI + ".codigo")));
            }
            int haIndice = Integer.parseInt(haP.getProperty("indice"));
            if (haP.getProperty("original") == null || haIndice < 0 || haIndice > haN) {
                throw new IOException("Diario incoherente");
            }
            return new HADatos(haP.getProperty("original"), Boolean.parseBoolean(haP.getProperty("restringido")),
                    Boolean.parseBoolean(haP.getProperty("originalProtegido")), haIndice,
                    Integer.parseInt(haP.getProperty("indiceGuardado", "0")), Integer.parseInt(haP.getProperty("contador", "0")),
                    haVersiones, Long.parseLong(haP.getProperty("fecha", "0")));
        } catch (NumberFormatException haE) {
            throw new IOException("Diario dañado", haE);
        }
    }
}
