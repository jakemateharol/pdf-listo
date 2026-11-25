package pe.pdflisto.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;

public final class HACargaPdf {
    public static final long HA_GRANDE = 40L * 1024 * 1024;

    private HACargaPdf() {
    }

    public static PDDocument haAbrir(File haArchivo, String haContrasena) throws IOException {
        if (haArchivo.length() >= HA_GRANDE) {
            return Loader.loadPDF(haArchivo, haContrasena, null, null, IOUtils.createTempFileOnlyStreamCache());
        }
        return Loader.loadPDF(haArchivo, haContrasena);
    }

    public static boolean haEsPesado(long haBytes, int haPaginas) {
        return haBytes >= 100L * 1024 * 1024 || haPaginas >= 500;
    }
}
