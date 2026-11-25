package pe.pdflisto.interprete;

import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HATamanoPapel;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HASerializadorPasos {
    private HASerializadorPasos() {
    }

    public static String haCodificar(List<HAPaso> haPasos) {
        List<String> haPartes = new ArrayList<>();
        for (HAPaso haP : haPasos) {
            haPartes.add(haCodificar(haP));
        }
        return String.join(";", haPartes);
    }

    public static String haCodificar(HAPaso haP) {
        Map<String, String> haV = new LinkedHashMap<>();
        if (haP instanceof HAPaso.HARotar haR) {
            haV.put("paginas", haR.haPaginas() == null ? "*" : haR.haPaginas());
            haV.put("grados", String.valueOf(haR.haGrados()));
        } else if (haP instanceof HAPaso.HAEliminar haE) {
            haV.put("paginas", haE.haPaginas());
        } else if (haP instanceof HAPaso.HATamano haT) {
            haV.put("papel", haT.haPapel().name());
            haV.put("orientacion", haT.haOrientacion().name());
        } else if (haP instanceof HAPaso.HAMarcaAgua haM) {
            haV.put("texto", haM.haTexto());
            haV.put("diagonal", String.valueOf(haM.haDiagonal()));
        } else if (haP instanceof HAPaso.HANumerar haN) {
            haV.put("formato", haN.haFormato());
            haV.put("posicion", haN.haPosicion().name());
            haV.put("desde", String.valueOf(haN.haDesde()));
        } else if (haP instanceof HAPaso.HAComprimir haC) {
            haV.put("nivel", haC.haNivel().name());
            haV.put("objetivo", haC.haObjetivoBytes() == null ? "-" : String.valueOf(haC.haObjetivoBytes()));
        }
        StringBuilder haSb = new StringBuilder(haP.haAccion().name());
        if (!haV.isEmpty()) {
            haSb.append(':');
            List<String> haPares = new ArrayList<>();
            haV.forEach((haK, haVal) -> haPares.add(haK + "=" + URLEncoder.encode(haVal, StandardCharsets.UTF_8)));
            haSb.append(String.join(",", haPares));
        }
        return haSb.toString();
    }

    public static List<HAPaso> haDecodificar(String haCodigo, List<String> haOtrosAbiertos) {
        List<HAPaso> haPasos = new ArrayList<>();
        if (haCodigo == null || haCodigo.isBlank()) {
            return haPasos;
        }
        try {
            for (String haParte : haCodigo.split(";")) {
                String[] haCabeza = haParte.split(":", 2);
                Map<String, String> haV = new LinkedHashMap<>();
                if (haCabeza.length == 2 && !haCabeza[1].isBlank()) {
                    for (String haPar : haCabeza[1].split(",")) {
                        String[] haKv = haPar.split("=", 2);
                        haV.put(haKv[0], haKv.length == 2 ? URLDecoder.decode(haKv[1], StandardCharsets.UTF_8) : "");
                    }
                }
                haPasos.add(switch (HAAccion.valueOf(haCabeza[0])) {
                    case UNIR -> new HAPaso.HAUnir(List.copyOf(haOtrosAbiertos));
                    case ROTAR -> new HAPaso.HARotar("*".equals(haV.get("paginas")) ? null : haV.get("paginas"),
                            Integer.parseInt(haV.get("grados")));
                    case ELIMINAR -> new HAPaso.HAEliminar(haV.get("paginas"));
                    case CAMBIAR_TAMANO -> new HAPaso.HATamano(HATamanoPapel.valueOf(haV.get("papel")),
                            HACambiarTamano.HAOrientacion.valueOf(haV.get("orientacion")));
                    case OCR -> new HAPaso.HAOcr();
                    case MARCA_AGUA -> new HAPaso.HAMarcaAgua(haV.get("texto"), Boolean.parseBoolean(haV.get("diagonal")));
                    case NUMERAR -> new HAPaso.HANumerar(haV.get("formato"), HAPosicion.valueOf(haV.get("posicion")),
                            Integer.parseInt(haV.get("desde")));
                    case LIMPIAR_METADATOS -> new HAPaso.HALimpiarMetadatos();
                    case QUITAR_CONTRASENA -> new HAPaso.HAQuitarClave();
                    case COMPRIMIR -> new HAPaso.HAComprimir(HANivelCompresion.valueOf(haV.get("nivel")),
                            "-".equals(haV.get("objetivo")) ? null : Long.parseLong(haV.get("objetivo")));
                });
            }
        } catch (RuntimeException haE) {
            throw new HAPdfListoException("Esta receta tiene un paso que no se reconoce; puede que sea de otra versión.", haE);
        }
        return haPasos;
    }

    public static String haResumen(List<HAPaso> haPasos) {
        List<String> haNombres = new ArrayList<>();
        for (HAPaso haP : haPasos) {
            haNombres.add(switch (haP.haAccion()) {
                case UNIR -> "Unir";
                case ROTAR -> "Rotar";
                case ELIMINAR -> "Eliminar páginas";
                case CAMBIAR_TAMANO -> "Tamaño " + ((HAPaso.HATamano) haP).haPapel();
                case OCR -> "OCR";
                case MARCA_AGUA -> "Marca de agua";
                case NUMERAR -> "Numerar";
                case LIMPIAR_METADATOS -> "Limpiar metadatos";
                case QUITAR_CONTRASENA -> "Quitar contraseña";
                case COMPRIMIR -> ((HAPaso.HAComprimir) haP).haObjetivoBytes() != null
                        ? "Comprimir a menos de " + pe.pdflisto.operaciones.HAArchivos.haTamanoLegible(((HAPaso.HAComprimir) haP).haObjetivoBytes())
                        : "Comprimir";
            });
        }
        return String.join(" + ", haNombres);
    }
}
