package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public final class HALectorTexto extends PDFTextStripper {
    public record HAPaginaTexto(int haIndice, String haTexto, List<TextPosition> haLetras, double haAncho, double haAlto) {
        public List<HAAreaTexto> haAreas(int haInicio, int haFin) {
            List<HAAreaTexto> haAreas = new ArrayList<>();
            double haX1 = 0, haY1 = 0, haX2 = 0, haY2 = 0, haBase = 0;
            boolean haAbierta = false;
            for (int haI = haInicio; haI < haFin && haI < haLetras.size(); haI++) {
                TextPosition haL = haLetras.get(haI);
                if (haL == null) {
                    continue;
                }
                HAAreaTexto haA = haAreaDe(haL);
                if (haAbierta && Math.abs(haL.getY() - haBase) > haTamano(haL) * 0.5) {
                    haAreas.add(new HAAreaTexto(haX1, haY1, haX2 - haX1, haY2 - haY1));
                    haAbierta = false;
                }
                if (!haAbierta) {
                    haX1 = haA.haX(); haY1 = haA.haY(); haX2 = haA.haX() + haA.haAncho(); haY2 = haA.haY() + haA.haAlto(); haBase = haL.getY();
                    haAbierta = true;
                } else {
                    haX1 = Math.min(haX1, haA.haX()); haY1 = Math.min(haY1, haA.haY());
                    haX2 = Math.max(haX2, haA.haX() + haA.haAncho()); haY2 = Math.max(haY2, haA.haY() + haA.haAlto());
                }
            }
            if (haAbierta) {
                haAreas.add(new HAAreaTexto(haX1, haY1, haX2 - haX1, haY2 - haY1));
            }
            return haAreas;
        }
    }

    private final StringBuilder haTexto = new StringBuilder();
    private final List<TextPosition> haLetras = new ArrayList<>();
    private double haAncho;
    private double haAlto;

    private HALectorTexto() {
        setSortByPosition(true);
    }

    public static List<HAPaginaTexto> haLeer(PDDocument haPdf) throws IOException {
        return haLeer(haPdf, pe.pdflisto.operaciones.HAControl.haNinguno());
    }

    public static List<HAPaginaTexto> haLeer(PDDocument haPdf, pe.pdflisto.operaciones.HAControl haControl) throws IOException {
        HALectorTexto haLector = new HALectorTexto();
        List<HAPaginaTexto> haPaginas = new ArrayList<>();
        int haTotal = haPdf.getNumberOfPages();
        for (int haI = 0; haI < haTotal; haI++) {
            haControl.haAvance(haI, haTotal, "Leyendo página " + (haI + 1) + " de " + haTotal);
            haPaginas.add(haLector.haPagina(haPdf, haI));
        }
        return haPaginas;
    }

    public static HAPaginaTexto haLeerPagina(PDDocument haPdf, int haIndice) throws IOException {
        return new HALectorTexto().haPagina(haPdf, haIndice);
    }

    public static HAAreaTexto haAreaDe(TextPosition haL) {
        double haTam = haTamano(haL);
        double haAncho = Math.max(haL.getWidth(), haTam * 0.2);
        return new HAAreaTexto(haL.getX(), haL.getY() - haTam * 0.82, haAncho, haTam * 1.04);
    }

    private static double haTamano(TextPosition haL) {
        return haL.getFontSizeInPt() > 0 ? haL.getFontSizeInPt() : Math.max(haL.getHeight(), 6);
    }

    private HAPaginaTexto haPagina(PDDocument haPdf, int haIndice) throws IOException {
        haTexto.setLength(0);
        haLetras.clear();
        setStartPage(haIndice + 1);
        setEndPage(haIndice + 1);
        writeText(haPdf, Writer.nullWriter());
        return new HAPaginaTexto(haIndice, haTexto.toString(), haLetrasConNulos(), haAncho, haAlto);
    }

    private List<TextPosition> haLetrasConNulos() {
        return java.util.Collections.unmodifiableList(new ArrayList<>(haLetras));
    }

    @Override
    protected void startPage(PDPage haPagina) throws IOException {
        super.startPage(haPagina);
        HATamanoPagina haT = HATamanoPagina.haDe(haPagina);
        haAncho = haT.haAncho();
        haAlto = haT.haAlto();
    }

    @Override
    protected void writeString(String haIgnorado, List<TextPosition> haPosiciones) {
        for (TextPosition haL : haPosiciones) {
            String haU = haL.getUnicode();
            if (haU == null) {
                continue;
            }
            for (char haC : haU.toCharArray()) {
                if (Character.isWhitespace(haC)) {
                    haSeparar(' ');
                } else {
                    haTexto.append(haC);
                    haLetras.add(haL);
                }
            }
        }
    }

    @Override
    protected void writeWordSeparator() {
        haSeparar(' ');
    }

    @Override
    protected void writeLineSeparator() {
        haSeparar('\n');
    }

    private void haSeparar(char haC) {
        if (haTexto.isEmpty()) {
            return;
        }
        char haUltimo = haTexto.charAt(haTexto.length() - 1);
        if (haUltimo == '\n') {
            return;
        }
        if (haUltimo == ' ') {
            if (haC == '\n') {
                haTexto.setCharAt(haTexto.length() - 1, '\n');
            }
            return;
        }
        haTexto.append(haC);
        haLetras.add(null);
    }
}
