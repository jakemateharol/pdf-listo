package pe.pdflisto.sistema;

import pe.pdflisto.operaciones.HAArchivos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public final class HAEspacioDisco {
    public static final long HA_MB = 1024L * 1024L;
    public static final long HA_POCO = 500 * HA_MB;
    static final long HA_RESERVA = 100 * HA_MB;
    static final int HA_FACTOR = 4;

    private HAEspacioDisco() {
    }

    public static long haLibre(Path haRuta) {
        File haF = haRuta.toAbsolutePath().toFile();
        while (haF != null && !haF.exists()) {
            haF = haF.getParentFile();
        }
        return haF == null ? Long.MAX_VALUE : haF.getUsableSpace();
    }

    public static long haNecesarioPara(long haBytesEntrada) {
        return Math.max(haBytesEntrada, 0) * HA_FACTOR + HA_RESERVA;
    }

    public static Optional<String> haFaltaPara(Path haCarpeta, long haBytesEntrada) {
        long haLibre = haLibre(haCarpeta);
        long haNecesario = haNecesarioPara(haBytesEntrada);
        if (haLibre >= haNecesario) {
            return Optional.empty();
        }
        return Optional.of("No hay espacio suficiente en el disco " + haUnidad(haCarpeta) + " para hacer esta operación: quedan "
                + HAArchivos.haTamanoLegible(haLibre) + " libres y podría necesitar hasta " + HAArchivos.haTamanoLegible(haNecesario)
                + ". Libera espacio (vacía la Papelera o borra archivos que no uses) e inténtalo de nuevo. "
                + "El documento no se modificó.");
    }

    public static Optional<String> haAvisoInicio(Path haCarpeta) {
        long haLibre = haLibre(haCarpeta);
        if (haLibre >= HA_POCO) {
            return Optional.empty();
        }
        return Optional.of("Queda poco espacio en el disco " + haUnidad(haCarpeta) + " (" + HAArchivos.haTamanoLegible(haLibre)
                + " libres). PDF Listo guarda ahí las versiones mientras editas; con PDF grandes podría no alcanzar. "
                + "Te conviene liberar espacio.");
    }

    public static boolean haEsDiscoLleno(Throwable haError, Path haCarpeta) {
        for (Throwable haT = haError; haT != null; haT = haT.getCause() == haT ? null : haT.getCause()) {
            String haM = haT.getMessage() == null ? "" : haT.getMessage().toLowerCase(Locale.ROOT);
            if ((haM.contains("espacio") && haM.contains("disco")) || haM.contains("not enough space")
                    || haM.contains("no space left") || haM.contains("disk full") || haM.contains("disco está lleno")) {
                return true;
            }
            if (haT instanceof IOException && haCarpeta != null && haLibre(haCarpeta) < 20 * HA_MB) {
                return true;
            }
        }
        return false;
    }

    public static String haMensajeDiscoLleno(String haDescripcion, Path haCarpeta) {
        return "No se pudo completar «" + haDescripcion + "» porque el disco " + haUnidad(haCarpeta) + " se llenó (quedan "
                + HAArchivos.haTamanoLegible(haLibre(haCarpeta)) + " libres). El documento quedó como estaba. "
                + "Libera espacio e inténtalo de nuevo.";
    }

    static String haUnidad(Path haCarpeta) {
        Path haRaiz = haCarpeta.toAbsolutePath().getRoot();
        return haRaiz == null ? haCarpeta.toString() : haRaiz.toString().replace("\\", "");
    }
}
