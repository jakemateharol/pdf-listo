package pe.pdflisto.pdf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public final class HADiagnosticoPdf {
    private HADiagnosticoPdf() {
    }

    public static String haExplicar(File haArchivo) {
        String haNombre = "«" + haArchivo.getName() + "»";
        long haLargo = haArchivo.length();
        if (haLargo == 0) {
            return haNombre + " está vacío (0 bytes). Puede que la descarga o la copia no haya terminado.";
        }
        try (RandomAccessFile haF = new RandomAccessFile(haArchivo, "r")) {
            byte[] haInicio = new byte[(int) Math.min(1024, haLargo)];
            haF.readFully(haInicio);
            String haCabecera = new String(haInicio, StandardCharsets.ISO_8859_1);
            if (!haCabecera.contains("%PDF-")) {
                return haNombre + " no es un PDF aunque termine en .pdf: " + haQueEs(haInicio, haCabecera);
            }
            byte[] haFin = new byte[(int) Math.min(2048, haLargo)];
            haF.seek(haLargo - haFin.length);
            haF.readFully(haFin);
            if (!new String(haFin, StandardCharsets.ISO_8859_1).contains("%%EOF")) {
                return haNombre + " está incompleto (cortado al final): probablemente la descarga o la copia "
                        + "no terminó. Vuelve a descargarlo o pide el archivo de nuevo.";
            }
        } catch (IOException haE) {
            return "No se pudo leer " + haNombre + ": " + haE.getMessage();
        }
        return haNombre + " está dañado por dentro y no se pudo reparar. Si lo tienes en otro lugar "
                + "(correo, USB), prueba con esa copia.";
    }

    private static String haQueEs(byte[] haB, String haTexto) {
        if (haB.length >= 2 && haB[0] == 'P' && haB[1] == 'K') {
            return "parece un documento de Word/Excel o un ZIP renombrado.";
        }
        if (haB.length >= 3 && (haB[0] & 0xFF) == 0xFF && (haB[1] & 0xFF) == 0xD8) {
            return "parece una imagen JPG renombrada.";
        }
        if (haB.length >= 4 && (haB[0] & 0xFF) == 0x89 && haB[1] == 'P' && haB[2] == 'N' && haB[3] == 'G') {
            return "parece una imagen PNG renombrada.";
        }
        String haMinus = haTexto.toLowerCase();
        if (haMinus.contains("<html") || haMinus.contains("<!doctype")) {
            return "es una página web (la descarga falló y se guardó la página de error).";
        }
        if (haB.length >= 4 && (haB[0] & 0xFF) == 0xD0 && (haB[1] & 0xFF) == 0xCF) {
            return "parece un documento antiguo de Word o Excel (.doc/.xls) renombrado.";
        }
        return "su contenido no corresponde a un PDF.";
    }
}
