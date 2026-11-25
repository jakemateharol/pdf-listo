package pe.pdflisto.interprete;

import org.springframework.stereotype.Component;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HAPosicion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class HAInterpretePedidos {
    public record HAContexto(int haPaginas, List<String> haOtrosAbiertos) {
    }

    private record HAClave(Pattern haPatron, HAAccion haAccion, String haNoDisponible) {
    }

    private record HAHallazgo(int haInicio, int haFin, HAClave haClave) {
    }

    private static final String HA_NO_ENTENDI = "No entendí el pedido. Puedo: unir, rotar, eliminar páginas, "
            + "cambiar el tamaño a A4 o carta, reconocer texto (OCR), poner marca de agua, numerar, limpiar metadatos, "
            + "quitar la contraseña y comprimir. Ejemplo: «une, comprime a menos de 2 MB y numera».";

    private static final List<HAClave> HA_CLAVES = List.of(
            haClave("\\bmarcas? de agua\\b|\\bsello de agua\\b|\\bwatermark\\b", HAAccion.MARCA_AGUA),
            haClave("\\bun(e|ir|elos|elas|irlos|irlas|an)\\b|\\bjunt(a|ar|alos|alas|en)\\b|\\bcombin\\w*|\\bfusion\\w*"
                    + "|\\bunific\\w*|\\bconcaten\\w*", HAAccion.UNIR),
            haClave("\\bcomprim\\w*|\\breduc\\w* (el |su )?(peso|tamano)|\\baligera\\w*|\\bachica\\w*|\\bque pesen?\\b"
                    + "|\\bpese menos\\b|\\bbaja\\w* (el |su )?peso|\\bmenos peso\\b", HAAccion.COMPRIMIR),
            haClave("\\bnumer(a|ar|ala|alas|alo|en|ando|acion|adas?|ados?)\\b|\\benumer\\w*"
                    + "|\\bfoli(a|ar|ado|ada|ados|adas)\\b|\\bpaginar\\b|\\bpaginacion\\b"
                    + "|\\bpon\\w* (el |los )?numeros?( de pagina)?\\b", HAAccion.NUMERAR),
            haClave("\\brot(a|ar|alo|ala|alas|alos|en|e)\\b|\\bgir(a|ar|alo|ala|alas|alos|e)\\b"
                    + "|\\bvolte(a|ar|ala|alas)\\b|\\bendereza\\w*", HAAccion.ROTAR),
            haClave("\\belimin\\w*|\\bborr(a|ar|ale|ales|en)\\b|\\bquit(a|ar|ale|ales|en)\\b"
                    + "|\\bsac(a|ar|ale|ales|en)\\b|\\bsuprim\\w*", HAAccion.ELIMINAR),
            haClave("\\b(quit|elimin|sac|borr)\\w* (la |el |su )?(contrasena|clave|password|proteccion)\\w*",
                    HAAccion.QUITAR_CONTRASENA),
            haClave("\\b(tamano|hoja|papel|formato)( de (pagina|hoja))? (a4|carta)\\b|\\ba (tamano |hoja |formato )?(a4|carta)\\b"
                    + "|\\ba4\\b|\\bcambia\\w* (el )?tamano( de (la )?(pagina|hoja))?", HAAccion.CAMBIAR_TAMANO),
            haClave("\\bmetadato\\w*|\\bpropiedades del (archivo|documento|pdf)", HAAccion.LIMPIAR_METADATOS),
            haNoDisponible("\\btach\\w*|\\banonimiz\\w*|\\bcensur\\w*|\\bocult\\w*",
                    "Para tachar usa la pestaña Tachado: ahí revisas y confirmas cada dato antes de borrarlo."),
            haNoDisponible("\\bcontrasena\\w*|\\bclave\\b|\\bprotege\\w*|\\bpassword\\b|\\bencript\\w*|\\bcifr(a|ar|alo)\\b",
                    "Para PONER contraseña usa la pestaña Tachado → Contraseña (no conviene escribirla en el pedido)."),
            haNoDisponible("\\bfirm\\w*", "Para firmar usa la pestaña Anotar → Firma: ahí eliges la firma y dónde ponerla."),
            haNoDisponible("\\bresalt\\w*|\\bsubray\\w*|\\banot\\w*|\\bformularios?\\b",
                    "Para resaltar, subrayar o anotar usa la pestaña Anotar; para llenar formularios, la pestaña Formulario."),
            haClave("\\bocr\\b|\\bbuscable\\b|\\breconoc\\w* (el )?texto|\\bescane\\w*", HAAccion.OCR),
            haNoDisponible("\\bexcel\\b|\\bcsv\\b|\\bfacturas?\\b|\\btablas?\\b",
                    "Para pasar facturas a Excel usa Herramientas → Leer facturas (QR) o la pestaña Entrega "
                            + "(no entra en un plan porque crea un archivo aparte). Tablas a Excel llega en la versión 1.1."),
            haNoDisponible("\\bconvert\\w*|\\bpasa\\w* a (word|jpg|png|imagen(es)?|texto)\\b",
                    "Las conversiones crean un archivo aparte: usa el botón Convertir de la barra superior."),
            haNoDisponible("\\bdivid\\w*|\\bsepar(a|ar|alo|ala)\\b|\\bpartir\\b",
                    "Dividir crea varios archivos, así que no entra en un plan: usa Herramientas → Dividir."),
            haNoDisponible("\\bextra(e|er|ele|elas|igas?)\\b",
                    "Extraer crea un archivo aparte: usa Herramientas → Extraer páginas.")
    );

    private static final Pattern HA_DATOS_PERSONALES =
            Pattern.compile("\\b(dni|ruc|datos|celulares?|telefonos?|correos?|cuentas?|direccion(es)?)\\b");
    private static final Pattern HA_PESO = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(mb|megas?|megabytes?|kb|kilobytes?)\\b");
    private static final Pattern HA_DESDE = Pattern.compile(
            "\\b(desde|a partir de|empezando (en|por|desde)|comenzando (en|por|desde))\\s+(la\\s+)?(pagina\\s+|hoja\\s+)?(\\d+)");
    private static final Pattern HA_SIN_CARATULA = Pattern.compile(
            "\\b(sin|excepto|menos|salvo|no)\\s+(numerar\\s+)?(la\\s+)?(caratula|portada|primera)");
    private static final Pattern HA_COMILLAS = Pattern.compile("[\"“«']([^\"”»']{1,60})[\"”»']");
    private static final Pattern HA_QUE_DIGA = Pattern.compile(
            "\\b(que diga|con el texto|con la palabra|diciendo|con texto|de texto|con)\\s+([a-z0-9]+(?:\\s+[a-z0-9]+){0,2})");
    private static final Set<String> HA_PALABRAS_MARCA = Set.of(
            "confidencial", "borrador", "copia", "muestra", "urgente", "privado", "anulado", "original", "reservado");
    private static final Set<String> HA_CONECTORES = Set.of("y", "e", "luego", "despues", "tambien", "ademas", "el", "la", "pdf");

    public HAInterpretacion haInterpretar(String haEscrito, HAContexto haContexto) {
        List<HAPaso> haPasos = new ArrayList<>();
        List<HAPregunta> haPreguntas = new ArrayList<>();
        Set<String> haAvisos = new LinkedHashSet<>();
        if (haEscrito == null || haEscrito.isBlank()) {
            haAvisos.add("Escribe qué quieres hacer, por ejemplo: «comprime a menos de 2 MB y numera».");
            return new HAInterpretacion(haPasos, haPreguntas, List.copyOf(haAvisos));
        }
        HACorrectorTipeo.HAResultado haTipeo = HACorrectorTipeo.haCorregir(haEscrito);
        String haPedido = haTipeo.haTexto();
        if (!haTipeo.haCorregidos().isEmpty()) {
            haAvisos.add("Corregí " + (haTipeo.haCorregidos().size() == 1 ? "un error" : "errores") + " de tipeo: "
                    + String.join(", ", haTipeo.haCorregidos().stream()
                    .map(haC -> "«" + haC.haOriginal() + "» → «" + haC.haSugerencias().get(0) + "»").toList())
                    + ". Si no era eso, corrige la frase y pulsa «Ver plan».");
        }
        for (HACorrectorTipeo.HACambio haD : haTipeo.haDudosos()) {
            List<HAPregunta.HAOpcion> haOpciones = new ArrayList<>();
            for (String haS : haD.haSugerencias().subList(0, Math.min(3, haD.haSugerencias().size()))) {
                haOpciones.add(new HAPregunta.HAOpcion("Sí: «" + haS + "»", haD.haInicio(), haS, haD.haFin()));
            }
            String haSugerida = haD.haSugerencias().size() == 1 ? "«" + haD.haSugerencias().get(0) + "»"
                    : String.join(" o ", haD.haSugerencias().subList(0, Math.min(3, haD.haSugerencias().size())).stream()
                    .map(haS -> "«" + haS + "»").toList());
            haPreguntas.add(new HAPregunta("No conozco la palabra «" + haD.haOriginal() + "». ¿Quisiste decir " + haSugerida + "?",
                    haOpciones));
        }
        String haTexto = haNormalizar(haPedido);
        List<HAHallazgo> haHallazgos = haBuscarClaves(haTexto);

        for (int haI = 0; haI < haHallazgos.size(); haI++) {
            HAHallazgo haH = haHallazgos.get(haI);
            HAHallazgo haSiguiente = haI + 1 < haHallazgos.size() ? haHallazgos.get(haI + 1) : null;
            int haFinSegmento = haSiguiente != null ? haSiguiente.haInicio() : haTexto.length();
            String haSegmento = haTexto.substring(haH.haInicio(), haFinSegmento);

            if (haH.haClave().haAccion() == null) {
                haAvisos.add(haH.haClave().haNoDisponible());
                continue;
            }
            if (haH.haClave().haAccion() == HAAccion.ELIMINAR && haSiguiente != null
                    && haSiguiente.haInicio() - haH.haFin() < 20) {
                if (haSiguiente.haClave().haAccion() == HAAccion.MARCA_AGUA) {
                    haAvisos.add("Quitar una marca de agua que el PDF ya trae no es posible de forma confiable "
                            + "(suele estar mezclada con el contenido).");
                    haI++;
                    continue;
                }
                if (haSiguiente.haClave().haAccion() == null || haSiguiente.haClave().haAccion() == HAAccion.LIMPIAR_METADATOS) {
                    continue;
                }
            }
            switch (haH.haClave().haAccion()) {
                case UNIR -> haUnir(haContexto, haPasos, haPreguntas);
                case COMPRIMIR -> haPasos.add(haComprimir(haSegmento));
                case NUMERAR -> haNumerar(haSegmento, haContexto, haTexto, haPasos, haAvisos);
                case MARCA_AGUA -> haMarcaDeAgua(haPedido, haSegmento, haH, haPasos, haPreguntas);
                case ROTAR -> haRotar(haSegmento, haH, haPasos, haPreguntas);
                case ELIMINAR -> haEliminar(haSegmento, haH, haPasos, haPreguntas, haAvisos);
                case LIMPIAR_METADATOS -> haPasos.add(new HAPaso.HALimpiarMetadatos());
                case OCR -> haPasos.add(new HAPaso.HAOcr());
                case QUITAR_CONTRASENA -> haPasos.add(new HAPaso.HAQuitarClave());
                case CAMBIAR_TAMANO -> haTamano(haSegmento, haH, haPasos, haPreguntas);
            }
        }
        if (haPasos.isEmpty() && haPreguntas.isEmpty() && haAvisos.stream().allMatch(haA -> haA.startsWith("Corregí"))) {
            haAvisos.add(HA_NO_ENTENDI);
        }
        return new HAInterpretacion(haOrdenar(haPasos), haPreguntas, List.copyOf(haAvisos),
                haTipeo.haCorregidos().isEmpty() ? null : haPedido);
    }

    private void haTamano(String haSegmento, HAHallazgo haH, List<HAPaso> haPasos, List<HAPregunta> haPreguntas) {
        pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion haOrientacion = haSegmento.contains("horizontal")
                ? pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion.HORIZONTAL
                : haSegmento.contains("vertical") ? pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion.VERTICAL
                : pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion.MANTENER;
        if (haSegmento.matches("(?s).*\\ba4\\b.*")) {
            haPasos.add(new HAPaso.HATamano(pe.pdflisto.operaciones.HATamanoPapel.A4, haOrientacion));
        } else if (haSegmento.contains("carta")) {
            haPasos.add(new HAPaso.HATamano(pe.pdflisto.operaciones.HATamanoPapel.CARTA, haOrientacion));
        } else {
            haPreguntas.add(new HAPregunta("¿A qué tamaño de hoja?", List.of(
                    new HAPregunta.HAOpcion("A4", haH.haFin(), " a A4"),
                    new HAPregunta.HAOpcion("Carta", haH.haFin(), " a tamaño carta"))));
        }
    }

    private void haUnir(HAContexto haContexto, List<HAPaso> haPasos, List<HAPregunta> haPreguntas) {
        if (haContexto.haOtrosAbiertos().isEmpty()) {
            haPreguntas.add(new HAPregunta("¿Con qué PDF lo uno? Abre los otros PDF (o arrástralos a la ventana) "
                    + "y pulsa «Ver plan» otra vez.", List.of()));
        } else {
            haPasos.add(new HAPaso.HAUnir(List.copyOf(haContexto.haOtrosAbiertos())));
        }
    }

    private HAPaso haComprimir(String haSegmento) {
        Matcher haPeso = HA_PESO.matcher(haSegmento);
        if (haPeso.find()) {
            double haValor = Double.parseDouble(haPeso.group(1).replace(',', '.'));
            long haBytes = (long) (haValor * (haPeso.group(2).startsWith("k") ? 1024 : 1024 * 1024));
            return new HAPaso.HAComprimir(HANivelCompresion.MEDIA, haBytes);
        }
        HANivelCompresion haNivel = HANivelCompresion.MEDIA;
        if (haSegmento.matches("(?s).*\\b(suave|ligera(mente)?|poco|un poco)\\b.*")) {
            haNivel = HANivelCompresion.SUAVE;
        } else if (haSegmento.matches("(?s).*\\b(fuerte|maxim\\w*|mucho|al maximo|lo mas posible|todo lo posible)\\b.*")) {
            haNivel = HANivelCompresion.FUERTE;
        }
        return new HAPaso.HAComprimir(haNivel, null);
    }

    private void haNumerar(String haSegmento, HAContexto haContexto, String haTextoCompleto, List<HAPaso> haPasos, Set<String> haAvisos) {
        String haFormato = "{n}";
        if (haSegmento.matches("(?s).*\\b(\\d+|n|x) de (\\d+|n|total|y)\\b.*") || haSegmento.contains(" de total")) {
            haFormato = "Página {n} de {total}";
        } else if (haSegmento.matches("(?s).*\\b(con (la palabra )?pagina|formato pagina|que diga pagina|pagina \\d+)\\b.*")) {
            haFormato = "Página {n}";
        }
        boolean haArriba = haSegmento.matches("(?s).*\\b(arriba|superior|encabezado|cabecera)\\b.*");
        int haHorizontal = haSegmento.contains("derecha") ? 1 : haSegmento.contains("izquierda") ? -1 : 0;
        HAPosicion haPosicion = HAPosicion.ABAJO_CENTRO;
        for (HAPosicion haP : HAPosicion.values()) {
            if (haP.haArriba() == haArriba && haP.haHorizontal() == haHorizontal) {
                haPosicion = haP;
            }
        }
        int haDesde = 1;
        Matcher haD = HA_DESDE.matcher(haSegmento);
        if (haD.find()) {
            haDesde = Integer.parseInt(haD.group(6));
        } else if (HA_SIN_CARATULA.matcher(haSegmento).find()) {
            haDesde = 2;
        }
        boolean haUneAntes = haTextoCompleto.matches("(?s).*\\b(une|unir|junta|juntar|combina|fusiona)\\w*.*");
        if (haDesde > haContexto.haPaginas() && !haUneAntes) {
            haAvisos.add("No puedo numerar desde la página " + haDesde + ": el PDF tiene " + haContexto.haPaginas() + " páginas.");
            return;
        }
        haPasos.add(new HAPaso.HANumerar(haFormato, haPosicion, Math.max(1, haDesde)));
    }

    private void haMarcaDeAgua(String haOriginal, String haSegmento, HAHallazgo haH, List<HAPaso> haPasos, List<HAPregunta> haPreguntas) {
        boolean haDiagonal = !haSegmento.contains("horizontal");
        String haSegmentoOriginal = haOriginal.substring(haH.haInicio(), haH.haInicio() + haSegmento.length());
        String haTexto = null;
        Matcher haComillas = HA_COMILLAS.matcher(haSegmentoOriginal);
        if (haComillas.find()) {
            haTexto = haComillas.group(1).trim();
        }
        if (haTexto == null) {
            Matcher haQ = HA_QUE_DIGA.matcher(haSegmento);
            while (haQ.find() && haTexto == null) {
                String haCandidato = haRecortarConectores(haSegmentoOriginal.substring(haQ.start(2), haQ.end(2)));
                if (!haCandidato.isBlank() && !haCandidato.equalsIgnoreCase("diagonal") && !haCandidato.equalsIgnoreCase("horizontal")) {
                    haTexto = haCandidato.toUpperCase();
                }
            }
        }
        if (haTexto == null) {
            for (String haPalabra : HA_PALABRAS_MARCA) {
                Matcher haM = Pattern.compile("\\b" + haPalabra + "\\b").matcher(haSegmento);
                if (haM.find()) {
                    haTexto = haPalabra.toUpperCase();
                    break;
                }
            }
        }
        if (haTexto == null) {
            haPreguntas.add(new HAPregunta("¿Qué texto lleva la marca de agua?", List.of(
                    new HAPregunta.HAOpcion("CONFIDENCIAL", haH.haFin(), " «CONFIDENCIAL»"),
                    new HAPregunta.HAOpcion("BORRADOR", haH.haFin(), " «BORRADOR»"),
                    new HAPregunta.HAOpcion("COPIA", haH.haFin(), " «COPIA»"))));
            return;
        }
        haPasos.add(new HAPaso.HAMarcaAgua(haTexto, haDiagonal));
    }

    private void haRotar(String haSegmento, HAHallazgo haH, List<HAPaso> haPasos, List<HAPregunta> haPreguntas) {
        Integer haGrados = null;
        if (haSegmento.matches("(?s).*\\b(derecha|horario|sentido horario)\\b.*") && !haSegmento.contains("antihorario")) {
            haGrados = 90;
        } else if (haSegmento.matches("(?s).*\\b(izquierda|antihorario|contrario)\\b.*")) {
            haGrados = 270;
        } else if (haSegmento.matches("(?s).*(\\b180\\b|de cabeza|al reves|media vuelta|\\bvolte).*")) {
            haGrados = 180;
        } else if (haSegmento.matches("(?s).*\\b90\\b.*")) {
            haGrados = 90;
        }
        String haPaginas = HAPaginasPedido.haExtraer(haSegmento);
        if (haGrados == null) {
            haPreguntas.add(new HAPregunta("¿Hacia dónde roto " + HAPaginasPedido.haDescribir(haVacioATodas(haPaginas)) + "?", List.of(
                    new HAPregunta.HAOpcion("A la derecha", haH.haFin(), " a la derecha"),
                    new HAPregunta.HAOpcion("A la izquierda", haH.haFin(), " a la izquierda"),
                    new HAPregunta.HAOpcion("De cabeza (180°)", haH.haFin(), " 180 grados"))));
            return;
        }
        haPasos.add(new HAPaso.HARotar(haVacioATodas(haPaginas), haGrados));
    }

    private void haEliminar(String haSegmento, HAHallazgo haH, List<HAPaso> haPasos, List<HAPregunta> haPreguntas, Set<String> haAvisos) {
        String haPaginas = HAPaginasPedido.haExtraer(haSegmento);
        if (haPaginas == null) {
            haAvisos.add("No puedo eliminar todas las páginas: el PDF quedaría vacío.");
            return;
        }
        if (haPaginas.isEmpty()) {
            if (HA_DATOS_PERSONALES.matcher(haSegmento).find()) {
                haAvisos.add("Borrar datos personales (DNI, RUC, celulares...) es «tachar»: usa la pestaña Tachado, "
                        + "donde revisas y confirmas cada dato.");
                return;
            }
            if (haSegmento.matches("(?s).*\\b(en blanco|blancas?|vacias?)\\b.*")) {
                haAvisos.add("Todavía no detecto páginas en blanco. Dime cuáles, por ejemplo «elimina la página 4».");
                return;
            }
            haPreguntas.add(new HAPregunta("¿Qué páginas elimino? Puedes escribirlas en el pedido (p. ej. «elimina las páginas 2 y 5»).",
                    List.of(new HAPregunta.HAOpcion("La primera", haH.haFin(), " la primera página"),
                            new HAPregunta.HAOpcion("La última", haH.haFin(), " la última página"))));
            return;
        }
        for (int haI = 0; haI < haPasos.size(); haI++) {
            if (haPasos.get(haI) instanceof HAPaso.HAEliminar haPrevio) {
                haPasos.set(haI, new HAPaso.HAEliminar(haPrevio.haPaginas() + "," + haPaginas));
                return;
            }
        }
        haPasos.add(new HAPaso.HAEliminar(haPaginas));
    }

    private static List<HAHallazgo> haBuscarClaves(String haTexto) {
        List<HAHallazgo> haTodos = new ArrayList<>();
        for (HAClave haC : HA_CLAVES) {
            Matcher haM = haC.haPatron().matcher(haTexto);
            while (haM.find()) {
                haTodos.add(new HAHallazgo(haM.start(), haM.end(), haC));
            }
        }
        haTodos.sort(Comparator.comparingInt(HAHallazgo::haInicio).thenComparing(haH -> -(haH.haFin() - haH.haInicio())));
        List<HAHallazgo> haSinSolapes = new ArrayList<>();
        int haFin = -1;
        for (HAHallazgo haH : haTodos) {
            if (haH.haInicio() >= haFin) {
                haSinSolapes.add(haH);
                haFin = haH.haFin();
            }
        }
        return haSinSolapes;
    }

    private static List<HAPaso> haOrdenar(List<HAPaso> haPasos) {
        List<HAPaso> haUnicos = new ArrayList<>();
        for (HAPaso haP : haPasos) {
            if (haP.haAccion() != HAAccion.ROTAR) {
                haUnicos.removeIf(haQ -> haQ.haAccion() == haP.haAccion());
            }
            haUnicos.add(haP);
        }
        haUnicos.sort(Comparator.comparingInt(haP -> haP.haAccion().ordinal()));
        return haUnicos;
    }

    private static String haVacioATodas(String haPaginas) {
        return haPaginas == null || haPaginas.isEmpty() ? null : haPaginas;
    }

    private static String haRecortarConectores(String haTexto) {
        List<String> haPalabras = new ArrayList<>(List.of(haTexto.trim().split("\\s+")));
        while (!haPalabras.isEmpty() && HA_CONECTORES.contains(haNormalizar(haPalabras.get(haPalabras.size() - 1)))) {
            haPalabras.remove(haPalabras.size() - 1);
        }
        return String.join(" ", haPalabras);
    }

    static String haNormalizar(String haTexto) {
        StringBuilder haSb = new StringBuilder(haTexto.length());
        for (char haC : haTexto.toCharArray()) {
            String haD = Normalizer.normalize(String.valueOf(haC), Normalizer.Form.NFD);
            haSb.append(Character.toLowerCase(haD.isEmpty() ? haC : haD.charAt(0)));
        }
        return haSb.toString();
    }

    private static HAClave haClave(String haRegex, HAAccion haAccion) {
        return new HAClave(Pattern.compile(haRegex), haAccion, null);
    }

    private static HAClave haNoDisponible(String haRegex, String haMensaje) {
        return new HAClave(Pattern.compile(haRegex), null, haMensaje);
    }
}
