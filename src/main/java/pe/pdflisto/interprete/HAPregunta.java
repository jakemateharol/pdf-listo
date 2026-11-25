package pe.pdflisto.interprete;

import java.util.List;

public record HAPregunta(String haTexto, List<HAOpcion> haOpciones) {
    public record HAOpcion(String haEtiqueta, int haPosicion, String haInsertar, int haFin) {
        public HAOpcion(String haEtiqueta, int haPosicion, String haInsertar) {
            this(haEtiqueta, haPosicion, haInsertar, haPosicion);
        }

        public String haAplicarA(String haPedido) {
            int haP = Math.max(0, Math.min(haPosicion, haPedido.length()));
            int haF = Math.max(haP, Math.min(haFin, haPedido.length()));
            return haPedido.substring(0, haP) + haInsertar + haPedido.substring(haF);
        }
    }
}
