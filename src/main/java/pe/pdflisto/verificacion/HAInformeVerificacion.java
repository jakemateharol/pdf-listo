package pe.pdflisto.verificacion;

import java.time.LocalDateTime;
import java.util.List;

public record HAInformeVerificacion(String haDocumento, LocalDateTime haFecha, String haPedido, List<String> haPasos,
                                  List<HAComprobacion> haComprobaciones) {
    public boolean haCumple() {
        return haComprobaciones.stream().noneMatch(haC -> haC.haEstado() == HAComprobacion.HAEstado.REVISAR);
    }
}
