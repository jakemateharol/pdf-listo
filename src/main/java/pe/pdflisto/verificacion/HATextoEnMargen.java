package pe.pdflisto.verificacion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

final class HATextoEnMargen extends PDFTextStripper {
    private static final double HA_FRANJA = 0.12;

    private final boolean haArriba;
    private final StringBuilder haTexto = new StringBuilder();
    private double haAltoPagina;

    private HATextoEnMargen(boolean haArriba) {
        this.haArriba = haArriba;
        setSortByPosition(true);
    }

    static List<String> haLeer(PDDocument haPdf, boolean haArriba) throws IOException {
        HATextoEnMargen haLector = new HATextoEnMargen(haArriba);
        List<String> haResultado = new ArrayList<>();
        for (int haI = 1; haI <= haPdf.getNumberOfPages(); haI++) {
            haLector.haTexto.setLength(0);
            haLector.setStartPage(haI);
            haLector.setEndPage(haI);
            haLector.writeText(haPdf, Writer.nullWriter());
            haResultado.add(haLector.haTexto.toString().replaceAll("[\\s&&[^\\n]]", "").replaceAll("\\n+", "\n").strip());
        }
        return haResultado;
    }

    @Override
    protected void startPage(PDPage haPagina) throws IOException {
        super.startPage(haPagina);
        PDRectangle haCaja = haPagina.getCropBox();
        int haGiro = ((haPagina.getRotation() % 360) + 360) % 360;
        haAltoPagina = haGiro == 90 || haGiro == 270 ? haCaja.getWidth() : haCaja.getHeight();
    }

    @Override
    protected void writeLineSeparator() {
        haTexto.append('\n');
    }

    @Override
    protected void writeString(String haIgnorado, List<TextPosition> haLetras) {
        for (TextPosition haLetra : haLetras) {
            double haY = haLetra.getY();
            boolean haEnFranja = haArriba ? haY <= haAltoPagina * HA_FRANJA : haY >= haAltoPagina * (1 - HA_FRANJA);
            if (haEnFranja && haLetra.getUnicode() != null) {
                haTexto.append(haLetra.getUnicode());
            }
        }
        haTexto.append(' ');
    }
}
