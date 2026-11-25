package pe.pdflisto.privacidad;

import org.apache.pdfbox.pdmodel.PDDocument;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;

public record HAQuitarContrasena() implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        return "Quitar contraseña";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) {
        return haDocumento;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAContrasena(false));
    }
}
