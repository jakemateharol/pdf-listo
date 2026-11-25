package pe.pdflisto.interprete;

import pe.pdflisto.operaciones.HARangoPaginas;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HAPaginasPedido {
    private static final Pattern HA_TOKEN = Pattern.compile("\\b(\\d+|primera|ultima)\\b");
    private static final Pattern HA_UNIDAD = Pattern.compile("^\\s*(mb|kb|megas?|grados?|°|%)");
    private static final Pattern HA_CONTEXTO = Pattern.compile(
            "(?:(?:^|[^a-z0-9])(?:pagina|paginas|pag|pags|hoja|hojas|la|las|y|a|al|hasta|de|e)|[,\\-])\\s*$");
    private static final Pattern HA_RANGO = Pattern.compile("^\\s*(a|al|hasta|-)\\s*(la\\s+|las\\s+|pagina\\s+)?$");

    private HAPaginasPedido() {
    }

    public static String haExtraer(String haSegmento) {
        if (haSegmento.matches("(?s).*\\btodas\\b.*")) {
            return null;
        }
        List<String> haPartes = new ArrayList<>();
        Matcher haM = HA_TOKEN.matcher(haSegmento);
        int haFinAnterior = -1;
        while (haM.find()) {
            String haToken = haM.group(1);
            String haDespues = haSegmento.substring(haM.end());
            if (HA_UNIDAD.matcher(haDespues).find()) {
                continue;
            }
            String haAntes = haSegmento.substring(Math.max(0, haM.start() - 15), haM.start());
            boolean haEsNumero = Character.isDigit(haToken.charAt(0));
            if (haEsNumero && !HA_CONTEXTO.matcher(haAntes).find()) {
                continue;
            }
            String haValor = switch (haToken) {
                case "primera" -> "1";
                case "ultima" -> "u";
                default -> haToken;
            };
            if (haFinAnterior >= 0 && !haPartes.isEmpty()
                    && HA_RANGO.matcher(haSegmento.substring(haFinAnterior, haM.start())).matches()) {
                String haDesde = haPartes.remove(haPartes.size() - 1);
                haPartes.add(haDesde + "-" + haValor);
            } else {
                haPartes.add(haValor);
            }
            haFinAnterior = haM.end();
        }
        return String.join(",", haPartes);
    }

    public static List<Integer> haResolver(String haPaginas, int haTotal) {
        if (haPaginas == null) {
            return java.util.stream.IntStream.range(0, haTotal).boxed().toList();
        }
        return HARangoPaginas.haSeleccion(haPaginas.replace("u", String.valueOf(haTotal)), haTotal);
    }

    public static String haDescribir(String haPaginas) {
        if (haPaginas == null) {
            return "todas las páginas";
        }
        String[] haPartes = haPaginas.split(",");
        if (haPartes.length == 1 && !haPartes[0].contains("-")) {
            return haPartes[0].equals("u") ? "la última página" : "la página " + haPartes[0];
        }
        List<String> haTextos = new ArrayList<>();
        for (String haP : haPartes) {
            haTextos.add(haP.equals("u") ? "la última" : haP.replace("-u", " hasta la última"));
        }
        String haLista = haTextos.size() == 1 ? haTextos.get(0)
                : String.join(", ", haTextos.subList(0, haTextos.size() - 1)) + " y " + haTextos.get(haTextos.size() - 1);
        return "las páginas " + haLista;
    }
}
