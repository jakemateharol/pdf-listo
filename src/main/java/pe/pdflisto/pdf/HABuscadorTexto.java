package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.Writer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HABuscadorTexto extends PDFTextStripper {
    private final StringBuilder haTexto = new StringBuilder();
    private final List<TextPosition> haPosiciones = new ArrayList<>();

    private HABuscadorTexto() {
        setSortByPosition(true);
    }

    static List<HACoincidencia> haBuscar(PDDocument haPdf, String haConsulta) throws IOException {
        String haBuscado = haNormalizarConsulta(haConsulta);
        if (haBuscado.isEmpty()) {
            return List.of();
        }
        HABuscadorTexto haBuscador = new HABuscadorTexto();
        List<HACoincidencia> haResultados = new ArrayList<>();
        for (int haI = 0; haI < haPdf.getNumberOfPages(); haI++) {
            haBuscador.haLeerPagina(haPdf, haI);
            String haT = haBuscador.haTexto.toString();
            int haDesde = 0;
            int haPos;
            while ((haPos = haT.indexOf(haBuscado, haDesde)) >= 0) {
                List<HAAreaTexto> haAreas = haBuscador.haAreas(haPos, haPos + haBuscado.length());
                if (!haAreas.isEmpty()) {
                    haResultados.add(new HACoincidencia(haI, haAreas));
                }
                haDesde = haPos + haBuscado.length();
            }
        }
        return haResultados;
    }

    static int haContarLetras(PDDocument haPdf, int haMaxPaginas) throws IOException {
        HABuscadorTexto haBuscador = new HABuscadorTexto();
        int haTotal = 0;
        for (int haI = 0; haI < Math.min(haMaxPaginas, haPdf.getNumberOfPages()); haI++) {
            haBuscador.haLeerPagina(haPdf, haI);
            haTotal += (int) haBuscador.haPosiciones.stream().filter(haP -> haP != null).count();
        }
        return haTotal;
    }

    private void haLeerPagina(PDDocument haPdf, int haIndice) throws IOException {
        haTexto.setLength(0);
        haPosiciones.clear();
        setStartPage(haIndice + 1);
        setEndPage(haIndice + 1);
        writeText(haPdf, Writer.nullWriter());
    }

    @Override
    protected void writeString(String haIgnorado, List<TextPosition> haLetras) {
        for (TextPosition haLetra : haLetras) {
            String haUnicode = haLetra.getUnicode();
            if (haUnicode == null) {
                continue;
            }
            for (char haC : haUnicode.toCharArray()) {
                String haNormal = haNormalizar(haC);
                for (char haN : haNormal.toCharArray()) {
                    if (Character.isWhitespace(haN)) {
                        haSeparar();
                    } else {
                        haTexto.append(haN);
                        haPosiciones.add(haLetra);
                    }
                }
            }
        }
    }

    @Override
    protected void writeWordSeparator() {
        haSeparar();
    }

    @Override
    protected void writeLineSeparator() {
        haSeparar();
    }

    private void haSeparar() {
        if (!haTexto.isEmpty() && haTexto.charAt(haTexto.length() - 1) != ' ') {
            haTexto.append(' ');
            haPosiciones.add(null);
        }
    }

    private List<HAAreaTexto> haAreas(int haInicio, int haFin) {
        List<HAAreaTexto> haAreas = new ArrayList<>();
        double haX1 = 0, haY1 = 0, haX2 = 0, haY2 = 0, haBase = 0;
        boolean haAbierta = false;
        for (int haI = haInicio; haI < haFin; haI++) {
            TextPosition haLetra = haPosiciones.get(haI);
            if (haLetra == null) {
                continue;
            }
            double haTam = haLetra.getFontSizeInPt() > 0 ? haLetra.getFontSizeInPt() : Math.max(haLetra.getHeight(), 6);
            double haLx1 = haLetra.getX();
            double haLx2 = haLetra.getX() + Math.max(haLetra.getWidth(), haTam * 0.2);
            double haLy1 = haLetra.getY() - haTam * 0.82;
            double haLy2 = haLetra.getY() + haTam * 0.22;
            if (haAbierta && Math.abs(haLetra.getY() - haBase) > haTam * 0.5) {
                haAreas.add(new HAAreaTexto(haX1, haY1, haX2 - haX1, haY2 - haY1));
                haAbierta = false;
            }
            if (!haAbierta) {
                haX1 = haLx1; haY1 = haLy1; haX2 = haLx2; haY2 = haLy2; haBase = haLetra.getY();
                haAbierta = true;
            } else {
                haX1 = Math.min(haX1, haLx1); haY1 = Math.min(haY1, haLy1);
                haX2 = Math.max(haX2, haLx2); haY2 = Math.max(haY2, haLy2);
            }
        }
        if (haAbierta) {
            haAreas.add(new HAAreaTexto(haX1, haY1, haX2 - haX1, haY2 - haY1));
        }
        return haAreas;
    }

    static String haNormalizar(char haC) {
        String haDescompuesto = Normalizer.normalize(String.valueOf(haC), Normalizer.Form.NFKD);
        return haDescompuesto.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static String haNormalizarConsulta(String haConsulta) {
        if (haConsulta == null) {
            return "";
        }
        StringBuilder haSb = new StringBuilder();
        for (char haC : haConsulta.toCharArray()) {
            for (char haN : haNormalizar(haC).toCharArray()) {
                if (Character.isWhitespace(haN)) {
                    if (!haSb.isEmpty() && haSb.charAt(haSb.length() - 1) != ' ') {
                        haSb.append(' ');
                    }
                } else {
                    haSb.append(haN);
                }
            }
        }
        return haSb.toString().trim();
    }
}
