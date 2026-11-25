package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import pe.pdflisto.exception.HAPdfListoException;

import java.awt.Color;
import java.io.IOException;

public record HANumerar(String haFormato, HAPosicion haPosicion, int haDesdePagina, int haPrimerNumero, float haTamano)
        implements HAOperacionPdf {
    static final float HA_MARGEN = 28f;

    @Override
    public String haDescripcion() {
        return "Numerar páginas" + (haDesdePagina > 1 ? " desde la " + haDesdePagina : "");
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException {
        int haTotal = haDocumento.getNumberOfPages();
        if (haDesdePagina < 1 || haDesdePagina > haTotal) {
            throw new HAPdfListoException("La página inicial debe estar entre 1 y " + haTotal + ".");
        }
        if (haFormato == null || !haFormato.contains("{n}")) {
            throw new HAPdfListoException("El formato debe incluir {n}, que es donde va el número.");
        }
        PDFont haFuente = HAFuentes.haCargar(haDocumento, false);
        int haUltimoNumero = haPrimerNumero + (haTotal - haDesdePagina);
        for (int haI = haDesdePagina - 1; haI < haTotal; haI++) {
            haRecursos.haControl().haAvance(haI, haTotal, "Numerando página " + (haI + 1) + " de " + haTotal);
            int haN = haPrimerNumero + (haI - (haDesdePagina - 1));
            String haTexto = haFormato.replace("{n}", String.valueOf(haN)).replace("{total}", String.valueOf(haUltimoNumero));
            float haW = HAFuentes.haAncho(haFuente, haTexto, haTamano);
            try (HALienzo haLienzo = new HALienzo(haDocumento, haDocumento.getPage(haI))) {
                float haX = switch (haPosicion.haHorizontal()) {
                    case -1 -> HA_MARGEN;
                    case 1 -> haLienzo.haAncho() - HA_MARGEN - haW;
                    default -> (haLienzo.haAncho() - haW) / 2;
                };
                float haY = haPosicion.haArriba() ? haLienzo.haAlto() - HA_MARGEN - haTamano : HA_MARGEN;
                haLienzo.haTexto(haTexto, haFuente, haTamano, haX, haY, new Color(40, 40, 40), 1f, 0);
            }
        }
        return haDocumento;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HANumeracion(
                new pe.pdflisto.verificacion.HARequisitos.HANumeracion(haFormato, haDesdePagina, haPrimerNumero, haPosicion.haArriba())));
    }
}
