package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;

public interface HAOperacionPdf {
    String haDescripcion();

    PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException;

    default boolean haConservaSeguridad() {
        return false;
    }

    default String haContrasenaResultado() {
        return null;
    }

    default java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas());
    }
}
