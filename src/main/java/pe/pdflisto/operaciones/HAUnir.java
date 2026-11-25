package pe.pdflisto.operaciones;

import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.File;
import java.io.IOException;
import java.util.List;

public record HAUnir(List<HAFuente> haFuentes) implements HAOperacionPdf {
    public record HAFuente(String haNombre, File haArchivo, String haContrasena, boolean haEsActual) {
    }

    @Override
    public String haDescripcion() {
        return "Unir " + haFuentes.size() + " PDF";
    }

    @Override
    public PDDocument haAplicar(PDDocument haActual, HARecursos haRecursos) throws IOException {
        PDDocument haDestino = haRecursos.haRegistrar(new PDDocument());
        PDFMergerUtility haUnidor = new PDFMergerUtility();
        int haI = 0;
        for (HAFuente haF : haFuentes) {
            haRecursos.haControl().haAvance(haI++, haFuentes.size(), "Uniendo " + haF.haNombre() + " (" + haI + " de " + haFuentes.size() + ")");
            PDDocument haOrigen = haF.haEsActual() ? haActual : haRecursos.haAbrir(haF.haArchivo(), haF.haContrasena());
            haUnidor.appendDocument(haDestino, haOrigen);
        }
        return haDestino;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAUnidas(haFuentes));
    }
}
