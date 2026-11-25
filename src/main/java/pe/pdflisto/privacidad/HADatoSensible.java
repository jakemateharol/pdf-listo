package pe.pdflisto.privacidad;

import pe.pdflisto.pdf.HAAreaTexto;

import java.util.List;

public record HADatoSensible(HATipoDato haTipo, String haRegla, String haTexto, String haValor, int haPagina,
                           List<HAAreaTexto> haAreas, boolean haProbable, String haNota) {
}
