package pe.pdflisto.privacidad;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HASugerenciaNombres {
    record HAHallazgo(int haInicio, int haFin, String haPista) {
    }

    private static final String HA_MAYUS = "A-ZÁÉÍÓÚÜÑ";
    private static final String HA_LETRA = "A-Za-zÁÉÍÓÚÜÑáéíóúüñ";
    private static final String HA_PALABRA = "[" + HA_MAYUS + "][" + HA_LETRA + "'’]*(?:-[" + HA_MAYUS + "][" + HA_LETRA + "'’]*)?";
    private static final String HA_UNION = "(?:\\s+(?:de|del|de\\s+la|de\\s+los|de\\s+las|la|las|los|y|e|DE|DEL|LA|Y)(?=\\s))?";
    private static final Pattern HA_NOMBRE = Pattern.compile(
            HA_PALABRA + "(?:" + HA_UNION.replace("\\s", "[ \\t]") + "[ \\t]+" + HA_PALABRA + "){0,4}");

    private static final Pattern HA_ETIQUETA = Pattern.compile(
            "(?iu)(?<![\\p{L}])(nombres?\\s+y\\s+apellidos?|apellidos?\\s+y\\s+nombres?|nombres?\\s+completos?|nombres?"
                    + "|apellidos?|alumn[oa]|estudiante|cliente|arrendatari[oa]|arrendador[a]?|solicitante|titular"
                    + "|trabajador[a]?|paciente|representante(?:\\s+legal)?|propietari[oa]|beneficiari[oa]"
                    + "|apoderad[oa]|firmado\\s+por|atendido\\s+por|a\\s+favor\\s+de|interesad[oa]|postulante"
                    + "|tesista|autor[a]?|asesor[a]?|docente|profesor[a]?|deudor[a]?|acreedor[a]?|garante|testigo)"
                    + "\\s*[:：]\\s*");
    private static final Pattern HA_TRATAMIENTO = Pattern.compile(
            "(?u)(?<![\\p{L}])(Sr\\.|Sra\\.|Srta\\.|Sres\\.|SR\\.|SRA\\.|Señor|Señora|Señorita|SEÑOR|SEÑORA|Don|Doña"
                    + "|DON|DOÑA|Dr\\.|Dra\\.|DR\\.|DRA\\.|Lic\\.|Ing\\.|Abog\\.|Mg\\.|Prof\\.)\\s+");
    private static final Pattern HA_ANTES_DE_DNI = Pattern.compile(
            "(?iu)^\\s*(?:,\\s*|\\(\\s*|-\\s*|–\\s*)?(?:(?:identificad[oa]|con|portador[a]?\\s+del?)\\s+)*"
                    + "(?:con\\s+)?(?:dni|d\\.n\\.i\\.?|documento\\s+(?:nacional\\s+)?de\\s+identidad)");
    private static final Pattern HA_DNI_Y_NUMERO = Pattern.compile(
            "(?iu)(?:dni|d\\.n\\.i\\.?)\\s*(?:n[°º.]?\\s*)?:?\\s*\\d{8}\\s*(?:[-–,:]\\s*)");

    private static final Set<String> HA_NO_SON_NOMBRES = Set.of(
            "el", "la", "los", "las", "un", "una", "de", "del", "en", "con", "por", "para", "que", "se", "su", "sus",
            "dni", "ruc", "documento", "identidad", "fecha", "firma", "nombre", "nombres", "apellidos", "apellido",
            "direccion", "domicilio", "telefono", "celular", "correo", "cargo", "codigo", "numero", "total", "monto",
            "lima", "peru", "callao", "arequipa", "cusco", "trujillo", "piura", "chiclayo", "sac", "s.a.c", "eirl",
            "srl", "sa", "empresa", "universidad", "facultad", "escuela", "colegio", "ministerio", "municipalidad",
            "banco", "gerente", "general", "jefe", "area", "presente", "atentamente", "asunto", "referencia",
            "senor", "senora", "senores", "don", "dona", "contrato", "clausula", "primera", "segunda", "tercera",
            "pagina", "masculino", "femenino", "soltero", "soltera", "casado", "casada", "peruano", "peruana",
            "mayor", "edad", "estado", "civil", "otro", "otra", "ninguno", "si", "no");

    private HASugerenciaNombres() {
    }

    static List<HAHallazgo> haBuscar(String haTexto) {
        List<HAHallazgo> haHallazgos = new ArrayList<>();
        Matcher haE = HA_ETIQUETA.matcher(haTexto);
        while (haE.find()) {
            haAgregar(haTexto, haE.end(), 2, "después de «" + haE.group(1).trim() + ":»", haHallazgos);
        }
        Matcher haT = HA_TRATAMIENTO.matcher(haTexto);
        while (haT.find()) {
            haAgregar(haTexto, haT.end(), 1, "después de «" + haT.group(1).trim() + "»", haHallazgos);
        }
        Matcher haN = HA_NOMBRE.matcher(haTexto);
        while (haN.find()) {
            String haSiguiente = haTexto.substring(haN.end(), Math.min(haTexto.length(), haN.end() + 45));
            if (HA_ANTES_DE_DNI.matcher(haSiguiente).find()) {
                haAgregar(haTexto, haN.start(), 2, "junto a un DNI", haHallazgos);
            }
        }
        Matcher haD = HA_DNI_Y_NUMERO.matcher(haTexto);
        while (haD.find()) {
            haAgregar(haTexto, haD.end(), 2, "junto a un DNI", haHallazgos);
        }
        return haHallazgos;
    }

    private static void haAgregar(String haTexto, int haDesde, int haMinimoPalabras, String haPista, List<HAHallazgo> haSalida) {
        Matcher haM = HA_NOMBRE.matcher(haTexto);
        haM.region(haDesde, haTexto.length());
        if (!haM.lookingAt()) {
            return;
        }
        List<int[]> haPalabras = new ArrayList<>();
        Matcher haP = Pattern.compile(HA_PALABRA).matcher(haTexto);
        haP.region(haM.start(), haM.end());
        while (haP.find()) {
            haPalabras.add(new int[]{haP.start(), haP.end()});
        }
        int haPrimero = 0;
        int haUltimo = haPalabras.size() - 1;
        while (haPrimero <= haUltimo && haNoEsNombre(haTexto, haPalabras.get(haPrimero))) {
            haPrimero++;
        }
        while (haUltimo >= haPrimero && haNoEsNombre(haTexto, haPalabras.get(haUltimo))) {
            haUltimo--;
        }
        int haCantidad = haUltimo - haPrimero + 1;
        if (haCantidad < haMinimoPalabras) {
            return;
        }
        for (int haI = haPrimero; haI <= haUltimo; haI++) {
            if (haNoEsNombre(haTexto, haPalabras.get(haI))) {
                return;
            }
        }
        int haInicio = haPalabras.get(haPrimero)[0];
        int haFin = haPalabras.get(haUltimo)[1];
        for (HAHallazgo haH : haSalida) {
            if (haInicio < haH.haFin() && haH.haInicio() < haFin) {
                return;
            }
        }
        haSalida.add(new HAHallazgo(haInicio, haFin, haPista));
    }

    private static boolean haNoEsNombre(String haTexto, int[] haPalabra) {
        String haW = haTexto.substring(haPalabra[0], haPalabra[1]);
        String haNormal = java.text.Normalizer.normalize(haW, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return HA_NO_SON_NOMBRES.contains(haNormal) || haW.length() < 2;
    }
}
