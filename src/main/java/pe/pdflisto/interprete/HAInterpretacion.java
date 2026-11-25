package pe.pdflisto.interprete;

import java.util.List;

public record HAInterpretacion(List<HAPaso> haPasos, List<HAPregunta> haPreguntas, List<String> haAvisos, String haTextoCorregido) {
    public HAInterpretacion(List<HAPaso> haPasos, List<HAPregunta> haPreguntas, List<String> haAvisos) {
        this(haPasos, haPreguntas, haAvisos, null);
    }
}
