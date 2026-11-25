package pe.pdflisto.formularios;

import java.util.List;

public record HACampoFormulario(String haNombre, String haEtiqueta, HATipo haTipo, String haValor, List<String> haOpciones,
                              boolean haSoloLectura, boolean haObligatorio, int haPagina) {
    public enum HATipo { TEXTO, CASILLA, OPCION, LISTA, FIRMA_DIGITAL }
}
