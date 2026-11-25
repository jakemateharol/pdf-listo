package pe.pdflisto.interprete;

import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HAPosicion;

import java.util.List;

public sealed interface HAPaso {
    HAAccion haAccion();

    String haDescripcion();

    record HAUnir(List<String> haOtros) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.UNIR; }
        public String haDescripcion() {
            return "Unir con " + String.join(", ", haOtros) + " (en el orden en que se abrieron; este documento va primero)";
        }
    }

    record HARotar(String haPaginas, int haGrados) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.ROTAR; }
        public String haDescripcion() {
            String haSentido = haGrados == 90 ? "a la derecha" : haGrados == 270 ? "a la izquierda" : "180° (de cabeza)";
            return "Rotar " + HAPaginasPedido.haDescribir(haPaginas) + " " + haSentido;
        }
    }

    record HAEliminar(String haPaginas) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.ELIMINAR; }
        public String haDescripcion() { return "Eliminar " + HAPaginasPedido.haDescribir(haPaginas); }
    }

    record HAMarcaAgua(String haTexto, boolean haDiagonal) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.MARCA_AGUA; }
        public String haDescripcion() {
            return "Poner marca de agua «" + haTexto + "»" + (haDiagonal ? " en diagonal" : " horizontal") + " en todas las páginas";
        }
    }

    record HANumerar(String haFormato, HAPosicion haPosicion, int haDesde) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.NUMERAR; }
        public String haDescripcion() {
            String haEjemplo = haFormato.replace("{n}", "1").replace("{total}", "N");
            return "Numerar «" + haEjemplo + "» " + haPosicion.toString().toLowerCase()
                    + (haDesde > 1 ? ", desde la página " + haDesde : "");
        }
    }

    record HATamano(HATamanoPapel haPapel, HACambiarTamano.HAOrientacion haOrientacion) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.CAMBIAR_TAMANO; }
        public String haDescripcion() {
            return "Cambiar el tamaño de las páginas a " + haPapel
                    + (haOrientacion == HACambiarTamano.HAOrientacion.MANTENER ? "" : " " + haOrientacion.toString().toLowerCase())
                    + " (el contenido se ajusta sin deformarse)";
        }
    }

    record HAQuitarClave() implements HAPaso {
        public HAAccion haAccion() { return HAAccion.QUITAR_CONTRASENA; }
        public String haDescripcion() { return "Quitar la contraseña (el resultado se podrá abrir sin ella)"; }
    }

    record HAOcr() implements HAPaso {
        public HAAccion haAccion() { return HAAccion.OCR; }
        public String haDescripcion() {
            return "Reconocer el texto de las páginas escaneadas (OCR, español) para poder buscar y copiar";
        }
    }

    record HALimpiarMetadatos() implements HAPaso {
        public HAAccion haAccion() { return HAAccion.LIMPIAR_METADATOS; }
        public String haDescripcion() {
            return "Limpiar metadatos (autor, programa, fechas, comentarios, adjuntos, JavaScript y versiones anteriores)";
        }
    }

    record HAComprimir(HANivelCompresion haNivel, Long haObjetivoBytes) implements HAPaso {
        public HAAccion haAccion() { return HAAccion.COMPRIMIR; }
        public String haDescripcion() {
            if (haObjetivoBytes != null) {
                return "Comprimir para que pese menos de " + HAArchivos.haTamanoLegible(haObjetivoBytes)
                        + " (prueba de suave a fuerte y elige el primero que cumpla)";
            }
            return "Comprimir con nivel " + haNivel.toString().toLowerCase();
        }
    }
}
