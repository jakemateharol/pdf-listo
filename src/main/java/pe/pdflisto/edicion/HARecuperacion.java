package pe.pdflisto.edicion;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
public final class HARecuperacion {
    static final String HA_BLOQUEO = "sesion.lock";
    private static final Duration HA_MAXIMO = Duration.ofDays(7);

    public record HAAbandonada(Path haCarpeta, HADiarioSesion.HADatos haDatos) {
    }

    private HARecuperacion() {
    }

    public static List<HAAbandonada> haBuscar() {
        return haBuscar(HACarpetaTrabajo.haBase());
    }

    public static List<HAAbandonada> haBuscar(Path haBase) {
        List<HAAbandonada> haEncontradas = new ArrayList<>();
        if (!Files.isDirectory(haBase)) {
            return haEncontradas;
        }
        Instant haLimite = Instant.now().minus(HA_MAXIMO);
        try (DirectoryStream<Path> haCarpetas = Files.newDirectoryStream(haBase)) {
            for (Path haCarpeta : haCarpetas) {
                if (!Files.isDirectory(haCarpeta) || haEnUso(haCarpeta)) {
                    continue;
                }
                HADiarioSesion.HADatos haDatos = null;
                try {
                    haDatos = HADiarioSesion.haLeer(haCarpeta);
                } catch (IOException haE) {
                    log.debug("Carpeta de trabajo sin diario legible: {}", haCarpeta);
                }
                boolean haVieja = Files.getLastModifiedTime(haCarpeta).toInstant().isBefore(haLimite);
                if (haDatos == null || !haDatos.haHayCambios() || haDatos.haCambios() == 0 || haVieja
                        || !haDatos.haArchivoActual().isFile()) {
                    HACarpetaTrabajo.haBorrar(haCarpeta);
                    continue;
                }
                haEncontradas.add(new HAAbandonada(haCarpeta, haDatos));
            }
        } catch (IOException haE) {
            log.warn("No se pudo revisar la carpeta de trabajo", haE);
        }
        haEncontradas.sort(Comparator.comparingLong((HAAbandonada haA) -> haA.haDatos().haFecha()).reversed());
        return haEncontradas;
    }

    public static void haDescartar(HAAbandonada haA) {
        HACarpetaTrabajo.haBorrar(haA.haCarpeta());
    }

    static boolean haEnUso(Path haCarpeta) {
        Path haArchivo = haCarpeta.resolve(HA_BLOQUEO);
        if (!Files.exists(haArchivo)) {
            return false;
        }
        try (FileChannel haCanal = FileChannel.open(haArchivo, StandardOpenOption.WRITE)) {
            FileLock haPrueba = haCanal.tryLock();
            if (haPrueba == null) {
                return true;
            }
            haPrueba.release();
            return false;
        } catch (OverlappingFileLockException haE) {
            return true;
        } catch (IOException haE) {
            return true;
        }
    }
}
