package pe.pdflisto.privacidad;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAReglaDeteccion;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.pdf.HACargaPdf;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.service.HAReglasService;

import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class HADetectorDatos {
    private static final Pattern HA_CORREO = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern HA_RUC = Pattern.compile("(?<![\\d.,])((?:10|15|16|17|20)\\d{9})(?![\\d]|[.,]\\d)");
    private static final Pattern HA_CCI = Pattern.compile("(?<![\\d-])(\\d{3}[-\\s]?\\d{3}[-\\s]?\\d{12}[-\\s]?\\d{2})(?![\\d-])");
    private static final Pattern HA_CELULAR = Pattern.compile("(?<![\\d+])(?:\\+?51[\\s-]?)?(9\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3})(?![\\d])");
    private static final Pattern HA_CUENTA = Pattern.compile("(?<![\\d-])(\\d{3,4}(?:[-\\s]\\d{1,10}){1,4}|\\d{10,18})(?![\\d-])");
    private static final Pattern HA_DNI = Pattern.compile("(?<![\\d.,/-])(\\d{8})(?![\\d]|[.,/-]\\d)");
    private static final Pattern HA_DIRECCION = Pattern.compile(
            "(?i)(?<![a-z])(av\\.|avenida|jr\\.|jir[oó]n|calle|ca\\.|psje\\.|pasaje|mz\\.|manzana|urb\\.|urbanizaci[oó]n"
                    + "|asoc\\.|aa\\.?\\s?hh\\.?|prolongaci[oó]n|carretera)\\s+[^\\n;]{3,60}");

    private static final Pattern HA_CONTEXTO_DNI = Pattern.compile("(dni|d\\.n\\.i|documento|identidad|doc\\.|carnet|c\\.e\\.)");
    private static final Pattern HA_CONTEXTO_CCI = Pattern.compile("(cci|interbancari)");
    private static final Pattern HA_CONTEXTO_CUENTA = Pattern.compile(
            "(cuenta|cta|ahorro|corriente|bcp|bbva|interbank|scotiabank|banco|nacion|caja|n[°º] de cuenta)");

    private final HAReglasService haReglasService;

    @Autowired
    public HADetectorDatos(HAReglasService haReglasService) {
        this.haReglasService = haReglasService;
    }

    public static List<HAReglaDeteccion> haReglasIncorporadas() {
        return List.of(
                HAReglaDeteccion.haIncorporada(HATipoDato.DNI, "DNI (8 dígitos)"),
                HAReglaDeteccion.haIncorporada(HATipoDato.RUC, "RUC (valida el dígito verificador)"),
                HAReglaDeteccion.haIncorporada(HATipoDato.CELULAR, "Celulares (9 dígitos que empiezan en 9)"),
                HAReglaDeteccion.haIncorporada(HATipoDato.CORREO, "Correos electrónicos"),
                HAReglaDeteccion.haIncorporada(HATipoDato.CUENTA, "Cuentas bancarias (con «cuenta» o un banco cerca)"),
                HAReglaDeteccion.haIncorporada(HATipoDato.CCI, "CCI (20 dígitos)"),
                HAReglaDeteccion.haIncorporada(HATipoDato.DIRECCION, "Direcciones (Av., Jr., Calle, Mz., Urb....)"));
    }

    public List<HAReglaDeteccion> haReglasActivas() {
        return haReglasService == null ? haReglasIncorporadas() : haReglasService.haActivas();
    }

    public List<HADatoSensible> haDetectar(File haArchivo, String haContrasena) {
        return haDetectar(haArchivo, haContrasena, HAControl.haNinguno());
    }

    public List<HADatoSensible> haDetectar(File haArchivo, String haContrasena, HAControl haControl) {
        try (PDDocument haPdf = HACargaPdf.haAbrir(haArchivo, haContrasena)) {
            return haDetectar(HALectorTexto.haLeer(haPdf, haControl), haReglasActivas(), true);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo leer el PDF para buscar datos sensibles.", haE);
        }
    }

    public List<HADatoSensible> haDetectar(List<HALectorTexto.HAPaginaTexto> haPaginas, List<HAReglaDeteccion> haReglas) {
        return haDetectar(haPaginas, haReglas, false);
    }

    public List<HADatoSensible> haDetectar(List<HALectorTexto.HAPaginaTexto> haPaginas, List<HAReglaDeteccion> haReglas,
                                       boolean haSugerirNombres) {
        List<HADatoSensible> haEncontrados = new ArrayList<>();
        List<HAReglaDeteccion> haOrdenadas = new ArrayList<>(haReglas);
        haOrdenadas.sort(Comparator.comparingInt(haR -> haPrioridad(haR.getTipo())));
        for (HALectorTexto.HAPaginaTexto haPagina : haPaginas) {
            boolean[] haUsado = new boolean[haPagina.haTexto().length()];
            String haMinus = haNormalizar(haPagina.haTexto());
            for (HAReglaDeteccion haRegla : haOrdenadas) {
                try {
                    haAplicar(haRegla, haPagina, haMinus, haUsado, haEncontrados);
                } catch (RuntimeException haE) {
                    log.warn("La regla '{}' falló en la página {}", haRegla.getNombre(), haPagina.haIndice() + 1, haE);
                }
            }
            if (haSugerirNombres) {
                try {
                    HAReglaDeteccion haSugerencia = HAReglaDeteccion.haIncorporada(HATipoDato.NOMBRE, "Nombres de personas (sugerencia)");
                    for (HASugerenciaNombres.HAHallazgo haH : HASugerenciaNombres.haBuscar(haPagina.haTexto())) {
                        haAgregar(haSugerencia, haPagina, haH.haInicio(), haH.haFin(), false,
                                "nombre de persona (" + haH.haPista() + "). Márcalo solo si quieres tacharlo.",
                                haUsado, haEncontrados);
                    }
                } catch (RuntimeException haE) {
                    log.warn("No se pudieron sugerir nombres en la página {}", haPagina.haIndice() + 1, haE);
                }
            }
        }
        haEncontrados.sort(Comparator.comparingInt(HADatoSensible::haPagina)
                .thenComparing(haD -> haD.haAreas().isEmpty() ? 0 : haD.haAreas().get(0).haY()));
        return haEncontrados;
    }

    private void haAplicar(HAReglaDeteccion haRegla, HALectorTexto.HAPaginaTexto haPagina, String haMinus, boolean[] haUsado,
                         List<HADatoSensible> haSalida) {
        String haTexto = haPagina.haTexto();
        switch (haRegla.getModo()) {
            case PALABRA -> {
                String haBuscado = haNormalizar(haRegla.getValor().trim());
                int haDesde = 0;
                int haPos;
                while (!haBuscado.isEmpty() && (haPos = haMinus.indexOf(haBuscado, haDesde)) >= 0) {
                    haAgregar(haRegla, haPagina, haPos, haPos + haBuscado.length(), true, null, haUsado, haSalida);
                    haDesde = haPos + haBuscado.length();
                }
            }
            case PATRON -> {
                Matcher haM = Pattern.compile(haRegla.getValor(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(haTexto);
                while (haM.find()) {
                    if (haM.end() > haM.start()) {
                        haAgregar(haRegla, haPagina, haM.start(), haM.end(), true, null, haUsado, haSalida);
                    }
                }
            }
            case INCORPORADA -> haIncorporada(haRegla, haPagina, haTexto, haMinus, haUsado, haSalida);
        }
    }

    private void haIncorporada(HAReglaDeteccion haRegla, HALectorTexto.HAPaginaTexto haPagina, String haTexto, String haMinus,
                             boolean[] haUsado, List<HADatoSensible> haSalida) {
        switch (haRegla.getTipo()) {
            case CORREO -> {
                Matcher haM = HA_CORREO.matcher(haTexto);
                while (haM.find()) {
                    haAgregar(haRegla, haPagina, haM.start(), haM.end(), true, null, haUsado, haSalida);
                }
            }
            case RUC -> {
                Matcher haM = HA_RUC.matcher(haTexto);
                while (haM.find()) {
                    if (HAValidadorRuc.haValido(haM.group(1))) {
                        haAgregar(haRegla, haPagina, haM.start(1), haM.end(1), true, null, haUsado, haSalida);
                    }
                }
            }
            case CCI -> {
                Matcher haM = HA_CCI.matcher(haTexto);
                while (haM.find()) {
                    boolean haContexto = haCerca(haMinus, haM.start(), HA_CONTEXTO_CCI, 45);
                    haAgregar(haRegla, haPagina, haM.start(1), haM.end(1), haContexto,
                            haContexto ? null : "20 dígitos sin la palabra «CCI» cerca", haUsado, haSalida);
                }
            }
            case CELULAR -> {
                Matcher haM = HA_CELULAR.matcher(haTexto);
                while (haM.find()) {
                    haAgregar(haRegla, haPagina, haM.start(), haM.end(), true, null, haUsado, haSalida);
                }
            }
            case CUENTA -> {
                Matcher haM = HA_CUENTA.matcher(haTexto);
                while (haM.find()) {
                    int haDigitos = haM.group(1).replaceAll("\\D", "").length();
                    if (haDigitos < 10 || haDigitos > 18) {
                        continue;
                    }
                    boolean haContexto = haCerca(haMinus, haM.start(), HA_CONTEXTO_CUENTA, 45);
                    boolean haConGuiones = haM.group(1).contains("-");
                    if (haContexto || haConGuiones) {
                        haAgregar(haRegla, haPagina, haM.start(1), haM.end(1), haContexto,
                                haContexto ? null : "número con guiones sin la palabra «cuenta» cerca", haUsado, haSalida);
                    }
                }
            }
            case DNI -> {
                Matcher haM = HA_DNI.matcher(haTexto);
                while (haM.find()) {
                    boolean haContexto = haCerca(haMinus, haM.start(), HA_CONTEXTO_DNI, 30);
                    if (!haContexto && haPareceFecha(haM.group(1))) {
                        continue;
                    }
                    haAgregar(haRegla, haPagina, haM.start(1), haM.end(1), haContexto,
                            haContexto ? null : "8 dígitos sin la palabra «DNI» cerca: revisa si lo es", haUsado, haSalida);
                }
            }
            case DIRECCION -> {
                Matcher haM = HA_DIRECCION.matcher(haTexto);
                while (haM.find()) {
                    int haFin = haM.end();
                    while (haFin > haM.start() && ",.: ".indexOf(haTexto.charAt(haFin - 1)) >= 0) {
                        haFin--;
                    }
                    boolean haConNumero = haTexto.substring(haM.start(), haFin).matches("(?s).*\\d.*");
                    haAgregar(haRegla, haPagina, haM.start(), haFin, haConNumero,
                            haConNumero ? null : "parece una dirección, pero sin número", haUsado, haSalida);
                }
            }
            default -> {
            }
        }
    }

    private static void haAgregar(HAReglaDeteccion haRegla, HALectorTexto.HAPaginaTexto haPagina, int haInicio, int haFin,
                                boolean haProbable, String haNota, boolean[] haUsado, List<HADatoSensible> haSalida) {
        for (int haI = haInicio; haI < haFin; haI++) {
            if (haUsado[haI]) {
                return;
            }
        }
        var haAreas = haPagina.haAreas(haInicio, haFin);
        if (haAreas.isEmpty()) {
            return;
        }
        for (int haI = haInicio; haI < haFin; haI++) {
            haUsado[haI] = true;
        }
        String haTexto = haPagina.haTexto().substring(haInicio, haFin).replace('\n', ' ').trim();
        haSalida.add(new HADatoSensible(haRegla.getTipo(), haRegla.getNombre(), haTexto, haNormalizarValor(haRegla.getTipo(), haTexto),
                haPagina.haIndice(), haAreas, haProbable, haNota));
    }

    public static String haNormalizarValor(HATipoDato haTipo, String haTexto) {
        if (haTipo.haNumerico()) {
            String haDigitos = haTexto.replaceAll("\\D", "");
            return haTipo == HATipoDato.CELULAR && haDigitos.length() == 11 && haDigitos.startsWith("51") ? haDigitos.substring(2) : haDigitos;
        }
        return haNormalizar(haTexto).replaceAll("\\s", "");
    }

    private static boolean haCerca(String haMinus, int haPosicion, Pattern haContexto, int haDistancia) {
        String haAntes = haMinus.substring(Math.max(0, haPosicion - haDistancia), haPosicion);
        return haContexto.matcher(haAntes).find();
    }

    private static boolean haPareceFecha(String haOchoDigitos) {
        return haEsFecha(haOchoDigitos.substring(0, 4), haOchoDigitos.substring(4, 6), haOchoDigitos.substring(6, 8))
                || haEsFecha(haOchoDigitos.substring(4, 8), haOchoDigitos.substring(2, 4), haOchoDigitos.substring(0, 2));
    }

    private static boolean haEsFecha(String haAnio, String haMes, String haDia) {
        int haA = Integer.parseInt(haAnio);
        if (haA < 1900 || haA > 2100) {
            return false;
        }
        try {
            LocalDate.of(haA, Integer.parseInt(haMes), Integer.parseInt(haDia));
            return true;
        } catch (DateTimeException haE) {
            return false;
        }
    }

    private static int haPrioridad(HATipoDato haT) {
        return switch (haT) {
            case CORREO -> 0;
            case RUC -> 1;
            case CCI -> 2;
            case CELULAR -> 3;
            case CUENTA -> 4;
            case DNI -> 5;
            case DIRECCION -> 6;
            case PERSONALIZADO -> 7;
            case NOMBRE -> 8;
        };
    }

    static String haNormalizar(String haTexto) {
        StringBuilder haSb = new StringBuilder(haTexto.length());
        for (char haC : haTexto.toCharArray()) {
            String haD = Normalizer.normalize(String.valueOf(haC), Normalizer.Form.NFD);
            haSb.append(Character.toLowerCase(haD.isEmpty() ? haC : haD.charAt(0)));
        }
        return haSb.toString();
    }
}
