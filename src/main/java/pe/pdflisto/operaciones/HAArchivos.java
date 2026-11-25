package pe.pdflisto.operaciones;

import java.io.File;

public final class HAArchivos {
    private HAArchivos() {
    }

    public static String haNombreSeguro(String haNombre) {
        String haLimpio = haNombre.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        haLimpio = haLimpio.replaceAll("[. ]+$", "");
        if (haLimpio.length() > 120) {
            haLimpio = haLimpio.substring(0, 120).trim();
        }
        return haLimpio.isEmpty() ? "documento" : haLimpio;
    }

    public static String haSinExtension(String haNombre) {
        return haNombre.replaceFirst("(?i)\\.pdf$", "");
    }

    public static File haLibre(File haCarpeta, String haNombre, String haExtension) {
        String haBase = haNombreSeguro(haNombre);
        File haF = new File(haCarpeta, haBase + haExtension);
        for (int haI = 2; haF.exists(); haI++) {
            haF = new File(haCarpeta, haBase + " (" + haI + ")" + haExtension);
        }
        return haF;
    }

    public static String haTamanoLegible(long haBytes) {
        if (haBytes < 1024) {
            return haBytes + " B";
        }
        if (haBytes < 1024 * 1024) {
            return Math.round(haBytes / 1024.0) + " KB";
        }
        return String.format("%.1f MB", haBytes / (1024.0 * 1024.0));
    }
}
