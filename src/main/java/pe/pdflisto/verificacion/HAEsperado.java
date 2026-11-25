package pe.pdflisto.verificacion;

import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.operaciones.HAUnir;

import java.util.List;
import java.util.Map;

public sealed interface HAEsperado {
    record HAMismasPaginas() implements HAEsperado {
    }

    record HAOrden(List<Integer> haOrden) implements HAEsperado {
    }

    record HAUnidas(List<HAUnir.HAFuente> haFuentes) implements HAEsperado {
    }

    record HARotacion(List<Integer> haPaginas, int haGrados) implements HAEsperado {
    }

    record HANumeracion(HARequisitos.HANumeracion haNumeracion) implements HAEsperado {
    }

    record HATexto(String haNombre, List<String> haTextos, List<Integer> haPaginas) implements HAEsperado {
    }

    record HAEncabezadoPie(List<String> haTextos, int haDesde, String haArchivo) implements HAEsperado {
    }

    record HAContenidoCambiado(String haNombre, List<Integer> haPaginas) implements HAEsperado {
    }

    record HAAnotaciones(Map<Integer, Integer> haPorPagina) implements HAEsperado {
    }

    record HATamano(HATamanoPapel haPapel, HACambiarTamano.HAOrientacion haOrientacion) implements HAEsperado {
    }

    record HATextoBuscable() implements HAEsperado {
    }

    record HASinMetadatos(boolean haInformacion, boolean haXmp, boolean haComentarios, boolean haAdjuntos, boolean haJavascript)
            implements HAEsperado {
    }

    record HAContrasena(boolean haProtegido) implements HAEsperado {
    }

    record HAPeso(Long haMaximo) implements HAEsperado {
    }

    record HAFormulario(Map<String, String> haValores, boolean haAplanado) implements HAEsperado {
    }

    record HATachado(List<Integer> haPaginas) implements HAEsperado {
    }
}
