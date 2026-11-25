package pe.pdflisto.interprete;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HACorrectorTipeo {
    static final Set<String> HA_VOCABULARIO = Set.of(
            "comprime", "comprimir", "comprimelo", "comprimela", "comprimelos", "comprimelas", "comprimido", "reduce",
            "reducir", "aligera", "aligerar", "achica", "achicar",
            "numera", "numerar", "numeralo", "numerala", "numeralas", "numeracion", "enumera", "enumerar", "folia",
            "foliar", "paginar", "paginacion",
            "unelos", "unelas", "junta", "juntar", "juntalos", "juntalas", "combina", "combinar", "fusiona", "fusionar",
            "unifica", "unificar", "concatena", "concatenar",
            "rota", "gira", "rotar", "rotalo", "rotala", "rotalas", "girar", "giralo", "girala", "giralas", "voltea", "voltear",
            "endereza", "enderezar",
            "elimina", "eliminar", "borra", "borrar", "borrale", "quita", "quitar", "quitale", "saca", "sacar", "sacale",
            "suprime", "suprimir",
            "marca", "marcas", "agua", "sello", "watermark", "metadatos", "propiedades", "contrasena", "password",
            "proteccion", "tamano", "carta", "papel", "formato", "horizontal", "vertical", "reconoce", "reconocer",
            "texto", "escaneado", "escaneo", "buscable", "tacha", "tachar", "anonimiza", "anonimizar", "censura",
            "firma", "firmar", "resalta", "resaltar", "subraya", "subrayar", "anota", "anotar", "formulario",
            "convierte", "convertir", "divide", "dividir", "separa", "separar", "extrae", "extraer", "facturas",
            "pagina", "paginas", "derecha", "izquierda", "primera", "primeras", "ultima", "ultimas", "caratula",
            "portada", "desde", "partir", "excepto", "salvo", "menos", "fuerte", "suave", "maximo", "ligera", "megas",
            "confidencial", "borrador", "copia", "muestra", "urgente", "privado", "anulado", "original", "reservado",
            "arriba", "abajo", "centro", "centrado", "esquina", "inferior", "superior", "diagonal", "todas", "total",
            "grados", "vuelta", "media", "impares", "pares");

    private static final Set<String> HA_COMUNES = Set.of(
            "nota", "notas", "rato", "ruta", "roto", "rotas", "rata", "toda", "todo", "todos", "cada", "para",
            "pero", "como", "esta", "este", "esto", "esas", "esos", "hola", "gracias", "favor", "porfa", "porfavor",
            "junto", "juntos", "juntas", "unos", "unas", "numero", "numeros", "numerada", "numerado", "numeradas",
            "numerados", "marco", "marcar", "marcado", "aguas", "cartas", "parte", "partes", "pago", "pagar",
            "copias", "textos", "hojas", "hoja", "pide", "pido", "quiero", "quisiera", "necesito", "puedes", "podrias",
            "haz", "hazlo", "dale", "luego", "despues", "tambien", "ademas", "primero", "ultimo", "solo", "sola",
            "otra", "otro", "otras", "otros", "documento", "archivo", "archivos", "documentos", "pdfs",
            "girada", "girado", "giradas", "rotada", "rotado", "rotadas", "derecho", "derechas", "izquierdo", "mismo",
            "misma", "nuevo", "nueva", "menor", "mayor", "peso", "pese", "pesen", "pesa", "pesar", "tamanos", "sacas",
            "quitas", "borras", "debe", "deben", "tiene", "tienen", "quede", "queden", "sale", "salga", "pagino",
            "folio", "folios", "papeles", "sellos", "firmas", "firmado", "firmada", "datos", "ponle", "ponles",
            "pones", "agrega", "agregar", "agregale", "coloca", "colocar", "pongas", "ponga", "pon", "poner",
            "corta", "cortar", "cortas", "fuente", "fuentes", "contrato", "contratos", "imprime", "imprimir",
            "imprimelo", "cambia", "cambiar", "cambialo", "ajusta", "ajustar", "aplica", "aplicar", "anterior",
            "anteriores", "centrar", "invertir", "invierte", "adjunta", "adjuntar", "adjunto", "partida", "girarlas",
            "girarlos", "rotarlas", "rotarlos", "numerarlas", "numerarlos", "borrarlas", "borrarlos", "quitarlas",
            "quitarlos", "sacarlas", "sacarlos", "eliminarlas", "eliminarlos", "comprimirlo", "comprimirla",
            "carga", "cargar", "tarea", "tareas", "rutas", "nada", "nadie", "alguna", "alguno", "algunas",
            "sello", "selle", "salir", "pagan", "pagos", "pasos", "paso", "fecha", "fechas", "bajar", "baja",
            "subir", "sube", "letra", "letras", "texto", "parar", "pares", "mitad", "media", "medias",
            "cuarta", "cuarto", "quinta", "quinto", "sexta", "sexto", "tercera", "tercero", "segunda", "segundo",
            "anexo", "anexos", "informe", "informes", "oficio", "oficios", "factura", "boleta", "boletas", "examen",
            "tesis", "libro", "capitulo", "indice", "titulo", "nombre", "imagen", "imagenes", "color", "colores",
            "blanco", "blancas", "negro", "negras", "grande", "grandes", "margen", "margenes", "borde", "bordes",
            "lados", "orden", "ordena", "ordenar", "mueve", "mover", "duplica", "duplicar", "copiar", "pega",
            "pegar", "recorta", "recortar", "limpia", "limpiar", "revisa", "revisar", "verifica", "verificar",
            "protege", "proteger", "bloquea", "mejora", "mejorar", "escribe", "inserta", "insertar", "final",
            "inicio", "siguiente", "encabezado", "calidad", "version", "versiones", "envia", "enviar", "guarda",
            "guardar", "abre", "abrir", "hacer", "pasar", "pasalo", "sacame", "quitame", "borrame", "respeta",
            "respetar");

    private static final Pattern HA_PALABRA = Pattern.compile("[a-zñ]+");
    private static final Pattern HA_ENTRE_COMILLAS = Pattern.compile("[\"“«'][^\"”»']{0,60}[\"”»']");

    record HACambio(int haInicio, int haFin, String haOriginal, List<String> haSugerencias) {
    }

    record HAResultado(String haTexto, List<HACambio> haCorregidos, List<HACambio> haDudosos) {
    }

    private HACorrectorTipeo() {
    }

    static HAResultado haCorregir(String haPedido) {
        if (haPedido == null || haPedido.isBlank()) {
            return new HAResultado(haPedido, List.of(), List.of());
        }
        String haNormal = HAInterpretePedidos.haNormalizar(haPedido);
        boolean[] haProtegido = new boolean[haNormal.length()];
        Matcher haComillas = HA_ENTRE_COMILLAS.matcher(haNormal);
        while (haComillas.find()) {
            for (int haI = haComillas.start(); haI < haComillas.end(); haI++) {
                haProtegido[haI] = true;
            }
        }
        List<HACambio> haSeguros = new ArrayList<>();
        List<HACambio> haDudosos = new ArrayList<>();
        Matcher haM = HA_PALABRA.matcher(haNormal);
        while (haM.find()) {
            String haPalabra = haM.group();
            if (haPalabra.length() < 4 || haProtegido[haM.start()] || HA_VOCABULARIO.contains(haPalabra) || HA_COMUNES.contains(haPalabra)) {
                continue;
            }
            int haMaximo = haPalabra.length() >= 7 ? 2 : 1;
            int haMejor = Integer.MAX_VALUE;
            TreeSet<String> haCandidatas = new TreeSet<>();
            for (String haV : HA_VOCABULARIO) {
                if (Math.abs(haV.length() - haPalabra.length()) > haMaximo) {
                    continue;
                }
                int haD = haDistancia(haPalabra, haV, haMaximo);
                if (haD > haMaximo) {
                    continue;
                }
                if (haD < haMejor) {
                    haMejor = haD;
                    haCandidatas.clear();
                }
                if (haD == haMejor) {
                    haCandidatas.add(haV);
                }
            }
            if (haCandidatas.isEmpty()) {
                continue;
            }
            String haEscrita = haPedido.substring(haM.start(), haM.end());
            HACambio haCambio = new HACambio(haM.start(), haM.end(), haEscrita, List.copyOf(haCandidatas));
            if (haCandidatas.stream().anyMatch(haV -> haSoloCambiaLaTerminacion(haPalabra, haV))) {
                continue;
            }
            boolean haSeguro = haMejor == 1 && haCandidatas.size() == 1 && haPalabra.length() >= 6;
            (haSeguro ? haSeguros : haDudosos).add(haCambio);
        }
        if (haSeguros.isEmpty()) {
            return new HAResultado(haPedido, List.of(), haDudosos);
        }
        StringBuilder haSb = new StringBuilder(haPedido);
        for (int haI = haSeguros.size() - 1; haI >= 0; haI--) {
            HACambio haC = haSeguros.get(haI);
            haSb.replace(haC.haInicio(), haC.haFin(), haConMayuscula(haC.haOriginal(), haC.haSugerencias().get(0)));
        }
        List<HACambio> haMovidos = new ArrayList<>();
        for (HACambio haD : haDudosos) {
            int haDesplazamiento = 0;
            for (HACambio haS : haSeguros) {
                if (haS.haFin() <= haD.haInicio()) {
                    haDesplazamiento += haS.haSugerencias().get(0).length() - (haS.haFin() - haS.haInicio());
                }
            }
            haMovidos.add(new HACambio(haD.haInicio() + haDesplazamiento, haD.haFin() + haDesplazamiento, haD.haOriginal(), haD.haSugerencias()));
        }
        return new HAResultado(haSb.toString(), haSeguros, haMovidos);
    }

    private static final String HA_TERMINACIONES = "aeiosrnl";

    static boolean haSoloCambiaLaTerminacion(String haPalabra, String haConocida) {
        int haComun = 0;
        int haMin = Math.min(haPalabra.length(), haConocida.length());
        while (haComun < haMin && haPalabra.charAt(haComun) == haConocida.charAt(haComun)) {
            haComun++;
        }
        if (haComun < haMin - 1 || haComun < 4) {
            return false;
        }
        String haColaA = haPalabra.substring(haComun);
        String haColaB = haConocida.substring(haComun);
        return (haColaA + haColaB).chars().allMatch(haC -> HA_TERMINACIONES.indexOf(haC) >= 0);
    }

    private static String haConMayuscula(String haEscrita, String haCorrecta) {
        return !haEscrita.isEmpty() && Character.isUpperCase(haEscrita.charAt(0))
                ? Character.toUpperCase(haCorrecta.charAt(0)) + haCorrecta.substring(1) : haCorrecta;
    }

    static int haDistancia(String haA, String haB, int haMaximo) {
        int haN = haA.length();
        int haK = haB.length();
        int[][] haD = new int[haN + 1][haK + 1];
        for (int haI = 0; haI <= haN; haI++) {
            haD[haI][0] = haI;
        }
        for (int haJ = 0; haJ <= haK; haJ++) {
            haD[0][haJ] = haJ;
        }
        for (int haI = 1; haI <= haN; haI++) {
            int haMinimoFila = Integer.MAX_VALUE;
            for (int haJ = 1; haJ <= haK; haJ++) {
                int haCosto = haA.charAt(haI - 1) == haB.charAt(haJ - 1) ? 0 : 1;
                int haV = Math.min(Math.min(haD[haI - 1][haJ] + 1, haD[haI][haJ - 1] + 1), haD[haI - 1][haJ - 1] + haCosto);
                if (haI > 1 && haJ > 1 && haA.charAt(haI - 1) == haB.charAt(haJ - 2) && haA.charAt(haI - 2) == haB.charAt(haJ - 1)) {
                    haV = Math.min(haV, haD[haI - 2][haJ - 2] + 1);
                }
                haD[haI][haJ] = haV;
                haMinimoFila = Math.min(haMinimoFila, haV);
            }
            if (haMinimoFila > haMaximo) {
                return haMaximo + 1;
            }
        }
        return haD[haN][haK];
    }
}
