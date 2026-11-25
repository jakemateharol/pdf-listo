package pe.pdflisto.operaciones;

import pe.pdflisto.exception.HAPdfListoException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;

public final class HARangoPaginas {
    private HARangoPaginas() {
    }

    public static List<List<Integer>> haGrupos(String haTexto, int haTotal) {
        if (haTexto == null || haTexto.isBlank()) {
            throw new HAPdfListoException("Escribe qué páginas, por ejemplo: 1-3, 5, 8-");
        }
        List<List<Integer>> haGrupos = new ArrayList<>();
        for (String haParte : haTexto.split("[,;]")) {
            String haP = haParte.replace(" ", "").replace('–', '-');
            if (haP.isEmpty()) {
                continue;
            }
            int haDesde;
            int haHasta;
            if (haP.contains("-")) {
                String[] haAb = haP.split("-", -1);
                if (haAb.length != 2) {
                    throw new HAPdfListoException("No entiendo «" + haParte.trim() + "». Usa la forma 2-5.");
                }
                haDesde = haAb[0].isEmpty() ? 1 : haNumero(haAb[0]);
                haHasta = haAb[1].isEmpty() ? haTotal : haNumero(haAb[1]);
            } else {
                haDesde = haHasta = haNumero(haP);
            }
            if (haDesde < 1 || haHasta < 1) {
                throw new HAPdfListoException("Las páginas se cuentan desde 1.");
            }
            int haMayor = Math.max(haDesde, haHasta);
            if (haMayor > haTotal) {
                throw new HAPdfListoException("La página " + haMayor + " no existe: el PDF tiene " + haTotal
                        + (haTotal == 1 ? " página." : " páginas."));
            }
            if (haDesde > haHasta) {
                throw new HAPdfListoException("El rango «" + haParte.trim() + "» está al revés; escribe " + haHasta + "-" + haDesde + ".");
            }
            List<Integer> haGrupo = new ArrayList<>();
            for (int haI = haDesde; haI <= haHasta; haI++) {
                haGrupo.add(haI - 1);
            }
            haGrupos.add(haGrupo);
        }
        if (haGrupos.isEmpty()) {
            throw new HAPdfListoException("Escribe qué páginas, por ejemplo: 1-3, 5, 8-");
        }
        return haGrupos;
    }

    public static List<Integer> haSeleccion(String haTexto, int haTotal) {
        LinkedHashSet<Integer> haTodas = new LinkedHashSet<>();
        haGrupos(haTexto, haTotal).forEach(haTodas::addAll);
        return List.copyOf(haTodas);
    }

    public static String haFormatear(List<Integer> haIndices) {
        TreeSet<Integer> haOrden = new TreeSet<>(haIndices);
        StringBuilder haSb = new StringBuilder();
        Integer haInicio = null;
        Integer haAnterior = null;
        for (int haI : haOrden) {
            if (haInicio == null) {
                haInicio = haAnterior = haI;
            } else if (haI == haAnterior + 1) {
                haAnterior = haI;
            } else {
                haAgregar(haSb, haInicio, haAnterior);
                haInicio = haAnterior = haI;
            }
        }
        if (haInicio != null) {
            haAgregar(haSb, haInicio, haAnterior);
        }
        return haSb.toString();
    }

    private static void haAgregar(StringBuilder haSb, int haInicio, int haFin) {
        if (!haSb.isEmpty()) {
            haSb.append(", ");
        }
        haSb.append(haInicio + 1);
        if (haFin > haInicio) {
            haSb.append('-').append(haFin + 1);
        }
    }

    private static int haNumero(String haTexto) {
        try {
            return Integer.parseInt(haTexto);
        } catch (NumberFormatException haE) {
            throw new HAPdfListoException("«" + haTexto + "» no es un número de página válido.");
        }
    }
}
