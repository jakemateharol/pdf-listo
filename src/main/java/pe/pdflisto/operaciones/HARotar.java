package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.util.List;

public record HARotar(List<Integer> haPaginas, int haGrados) implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        String haCuales = haPaginas.size() == 1 ? "página " + (haPaginas.get(0) + 1) : haPaginas.size() + " páginas";
        return "Rotar " + haCuales + (haGrados == 90 ? " a la derecha" : haGrados == 270 ? " a la izquierda" : " " + haGrados + "°");
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) {
        for (int haI : haPaginas) {
            PDPage haPagina = haDocumento.getPage(haI);
            haPagina.setRotation(((haPagina.getRotation() + haGrados) % 360 + 360) % 360);
        }
        return haDocumento;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HARotacion(haPaginas, haGrados));
    }
}
