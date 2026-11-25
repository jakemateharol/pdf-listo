package pe.pdflisto.operaciones;

import pe.pdflisto.exception.HAPdfListoException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

public final class HAOrdenPaginas {
    private HAOrdenPaginas() {
    }

    public static List<Integer> haIdentidad(int haTotal) {
        return IntStream.range(0, haTotal).boxed().toList();
    }

    public static List<Integer> haEliminar(int haTotal, Collection<Integer> haQuitar) {
        Set<Integer> haQ = new HashSet<>(haQuitar);
        List<Integer> haOrden = haIdentidad(haTotal).stream().filter(haI -> !haQ.contains(haI)).toList();
        if (haOrden.isEmpty()) {
            throw new HAPdfListoException("No se pueden eliminar todas las páginas: el PDF quedaría vacío.");
        }
        return haOrden;
    }

    public static List<Integer> haDuplicar(int haTotal, Collection<Integer> haElegidas) {
        Set<Integer> haE = new HashSet<>(haElegidas);
        List<Integer> haOrden = new ArrayList<>();
        for (int haI = 0; haI < haTotal; haI++) {
            haOrden.add(haI);
            if (haE.contains(haI)) {
                haOrden.add(haI);
            }
        }
        return haOrden;
    }

    public static List<Integer> haMover(int haTotal, Collection<Integer> haElegidas, int haDestino) {
        TreeSet<Integer> haE = new TreeSet<>(haElegidas);
        List<Integer> haResto = new ArrayList<>();
        int haInsertarEn = 0;
        for (int haI = 0; haI < haTotal; haI++) {
            if (!haE.contains(haI)) {
                haResto.add(haI);
                if (haI < haDestino) {
                    haInsertarEn++;
                }
            }
        }
        haResto.addAll(haInsertarEn, haE);
        return haResto;
    }

    public static List<Integer> haSubir(int haTotal, Collection<Integer> haElegidas) {
        Set<Integer> haE = new HashSet<>(haElegidas);
        List<Integer> haOrden = new ArrayList<>(haIdentidad(haTotal));
        for (int haI = 1; haI < haOrden.size(); haI++) {
            if (haE.contains(haOrden.get(haI)) && !haE.contains(haOrden.get(haI - 1))) {
                haOrden.set(haI - 1, haOrden.set(haI, haOrden.get(haI - 1)));
            }
        }
        return haOrden;
    }

    public static List<Integer> haBajar(int haTotal, Collection<Integer> haElegidas) {
        Set<Integer> haE = new HashSet<>(haElegidas);
        List<Integer> haOrden = new ArrayList<>(haIdentidad(haTotal));
        for (int haI = haOrden.size() - 2; haI >= 0; haI--) {
            if (haE.contains(haOrden.get(haI)) && !haE.contains(haOrden.get(haI + 1))) {
                haOrden.set(haI + 1, haOrden.set(haI, haOrden.get(haI + 1)));
            }
        }
        return haOrden;
    }

    public static List<Integer> haPosiciones(List<Integer> haOrden, Collection<Integer> haElegidas) {
        Set<Integer> haPendientes = new HashSet<>(haElegidas);
        List<Integer> haPosiciones = new ArrayList<>();
        for (int haK = 0; haK < haOrden.size(); haK++) {
            if (haPendientes.remove(haOrden.get(haK))) {
                haPosiciones.add(haK);
            }
        }
        return haPosiciones;
    }
}
