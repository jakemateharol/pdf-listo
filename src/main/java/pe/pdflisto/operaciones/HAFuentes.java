package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import pe.pdflisto.exception.HAPdfListoException;

import java.io.File;
import java.io.IOException;

public final class HAFuentes {
    private HAFuentes() {
    }

    public static PDFont haCargar(PDDocument haDocumento, boolean haNegrita) {
        String haWindows = System.getenv().getOrDefault("WINDIR", "C:\\Windows");
        File haArial = new File(haWindows, "Fonts" + File.separator + (haNegrita ? "arialbd.ttf" : "arial.ttf"));
        if (haArial.isFile()) {
            try {
                return PDType0Font.load(haDocumento, haArial);
            } catch (IOException | RuntimeException haIgnorado) {
            }
        }
        return new PDType1Font(haNegrita ? Standard14Fonts.FontName.HELVETICA_BOLD : Standard14Fonts.FontName.HELVETICA);
    }

    public static boolean haPuedeEscribir(PDFont haFuente, char haC) {
        try {
            haFuente.encode(String.valueOf(haC));
            return true;
        } catch (IOException | IllegalArgumentException | IllegalStateException haE) {
            return false;
        }
    }

    public static float haAncho(PDFont haFuente, String haTexto, float haTamano) {
        try {
            return haFuente.getStringWidth(haTexto) / 1000f * haTamano;
        } catch (IOException | IllegalArgumentException | IllegalStateException haE) {
            throw new HAPdfListoException("El texto «" + haTexto + "» tiene caracteres que no se pueden escribir en el PDF "
                    + "(por ejemplo emojis). Usa solo letras, números y signos comunes.");
        }
    }
}
