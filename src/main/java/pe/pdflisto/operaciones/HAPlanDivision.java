package pe.pdflisto.operaciones;

import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.pdf.HAMarcador;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.IntStream;

public final class HAPlanDivision {
    public record HAParte(String haNombre, List<Integer> haPaginas) {
    }

    private HAPlanDivision() {
    }

    public static List<HAParte> haPorRangos(String haTexto, int haTotal, String haBase) {
        List<HAParte> haPartes = new ArrayList<>();
        for (List<Integer> haGrupo : HARangoPaginas.haGrupos(haTexto, haTotal)) {
            haPartes.add(new HAParte(haBase + " - págs " + HARangoPaginas.haFormatear(haGrupo).replace(", ", "_"), haGrupo));
        }
        return haPartes;
    }

    public static List<HAParte> haCadaN(int haN, int haTotal, String haBase) {
        if (haN < 1) {
            throw new HAPdfListoException("El número de páginas por archivo debe ser 1 o más.");
        }
        if (haN >= haTotal) {
            throw new HAPdfListoException("Con " + haN + " páginas por archivo saldría un solo archivo igual al original.");
        }
        List<HAParte> haPartes = new ArrayList<>();
        int haK = 1;
        int haDigitos = String.valueOf((haTotal + haN - 1) / haN).length();
        for (int haDesde = 0; haDesde < haTotal; haDesde += haN, haK++) {
            int haHasta = Math.min(haTotal, haDesde + haN);
            haPartes.add(new HAParte(haBase + " - parte " + String.format("%0" + haDigitos + "d", haK),
                    IntStream.range(haDesde, haHasta).boxed().toList()));
        }
        return haPartes;
    }

    public static List<HAParte> haPorMarcadores(List<HAMarcador> haMarcadores, int haTotal, String haBase) {
        TreeMap<Integer, String> haInicios = new TreeMap<>();
        for (HAMarcador haM : haMarcadores) {
            if (haM.haPagina() >= 0) {
                haInicios.putIfAbsent(haM.haPagina(), haM.haTitulo());
            }
        }
        if (haInicios.isEmpty()) {
            throw new HAPdfListoException("Este PDF no tiene marcadores que apunten a páginas.");
        }
        if (haInicios.firstKey() > 0) {
            haInicios.put(0, "Inicio");
        }
        List<HAParte> haPartes = new ArrayList<>();
        List<Integer> haClaves = new ArrayList<>(haInicios.keySet());
        int haDigitos = String.valueOf(haClaves.size()).length();
        for (int haK = 0; haK < haClaves.size(); haK++) {
            int haDesde = haClaves.get(haK);
            int haHasta = haK + 1 < haClaves.size() ? haClaves.get(haK + 1) : haTotal;
            String haNumero = String.format("%0" + haDigitos + "d", haK + 1);
            haPartes.add(new HAParte(haBase + " - " + haNumero + " " + haInicios.get(haDesde),
                    IntStream.range(haDesde, haHasta).boxed().toList()));
        }
        if (haPartes.size() == 1) {
            throw new HAPdfListoException("Todos los marcadores llevan a la misma página; no hay en qué dividir.");
        }
        return haPartes;
    }
}
