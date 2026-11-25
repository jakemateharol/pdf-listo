package pe.pdflisto.pdf;

import java.util.List;

public record HAMarcador(String haTitulo, int haPagina, List<HAMarcador> haHijos) {
}
