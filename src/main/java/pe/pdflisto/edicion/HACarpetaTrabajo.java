package pe.pdflisto.edicion;

import lombok.extern.slf4j.Slf4j;
import pe.pdflisto.config.HACarpetaDatos;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
public final class HACarpetaTrabajo {
    private HACarpetaTrabajo() {
    }

    static final Duration HA_TEMPORAL_VIEJO = Duration.ofDays(1);

    static Path haBase() {
        return HACarpetaDatos.haRuta().resolve("trabajo");
    }

    public static Path haRuta() {
        return haBase();
    }

    public static Path haNueva() {
        try {
            Files.createDirectories(haBase());
            return Files.createTempDirectory(haBase(), "sesion-");
        } catch (IOException haE) {
            throw new UncheckedIOException("No se pudo crear la carpeta de trabajo", haE);
        }
    }

    public static void haBorrar(Path haCarpeta) {
        if (haCarpeta == null || !Files.exists(haCarpeta)) {
            return;
        }
        try {
            Files.deleteIfExists(haCarpeta.resolve(HADiarioSesion.HA_ARCHIVO));
        } catch (IOException haE) {
            log.debug("No se pudo borrar el diario de {}", haCarpeta);
        }
        try (Stream<Path> haTodo = Files.walk(haCarpeta)) {
            haTodo.sorted(Comparator.reverseOrder()).forEach(haP -> {
                try {
                    Files.deleteIfExists(haP);
                } catch (IOException haE) {
                    log.debug("No se pudo borrar {}", haP);
                }
            });
        } catch (IOException haE) {
            log.warn("No se pudo borrar la carpeta de trabajo {}", haCarpeta, haE);
        }
    }

    public static void haLimpiarSobrantes() {
        haLimpiarSobrantes(haBase(), Path.of(System.getProperty("java.io.tmpdir")));
    }

    static void haLimpiarSobrantes(Path haBase, Path haTemporales) {
        try {
            HARecuperacion.haBuscar(haBase);
            if (Files.isDirectory(haBase)) {
                try (DirectoryStream<Path> haHijos = Files.newDirectoryStream(haBase)) {
                    for (Path haP : haHijos) {
                        if (Files.isRegularFile(haP)) {
                            Files.deleteIfExists(haP);
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException haE) {
            log.debug("No se pudo limpiar la carpeta de trabajo: {}", haE.getMessage());
        }
        if (haTemporales == null || !Files.isDirectory(haTemporales)) {
            return;
        }
        Instant haLimite = Instant.now().minus(HA_TEMPORAL_VIEJO);
        try (DirectoryStream<Path> haHijos = Files.newDirectoryStream(haTemporales, "pdflisto*")) {
            for (Path haP : haHijos) {
                if (Files.isDirectory(haP) && Files.getLastModifiedTime(haP).toInstant().isBefore(haLimite)) {
                    haBorrar(haP);
                }
            }
        } catch (IOException | RuntimeException haE) {
            log.debug("No se pudieron revisar los temporales: {}", haE.getMessage());
        }
    }
}
