package pe.pdflisto.facturas;

import pe.pdflisto.privacidad.HAValidadorRuc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HAValidadorComprobantes {
    private static final BigDecimal HA_TASA = new BigDecimal("0.18");
    private static final BigDecimal HA_TASA_MYPE = new BigDecimal("0.10");
    private static final Pattern HA_MONTO = Pattern.compile("\\d{1,3}(?:[,\\s]\\d{3})*(?:\\.\\d{2})|\\d+\\.\\d{2}");

    private HAValidadorComprobantes() {
    }

    private static final Comparator<HAComprobante> HA_ORIGINAL_PRIMERO = Comparator
            .comparingLong(HAComprobante::haFechaArchivo)
            .thenComparingInt(HAComprobante::haOrdenCarga)
            .thenComparingInt(HAComprobante::haPagina);

    public static void haValidar(List<HAComprobante> haComprobantes, LocalDate haHoy) {
        Map<String, HAComprobante> haOriginales = new HashMap<>();
        for (HAComprobante haC : haComprobantes) {
            if (haC.haDatos() != null) {
                haOriginales.merge(haC.haDatos().haClave(), haC, (haA, haB) -> HA_ORIGINAL_PRIMERO.compare(haA, haB) <= 0 ? haA : haB);
            }
        }
        for (HAComprobante haC : haComprobantes) {
            HADatosQr haD = haC.haDatos();
            if (haD == null) {
                continue;
            }
            HAComprobante haOriginal = haOriginales.get(haD.haClave());
            if (haOriginal != haC) {
                haC.haObservar("Repetido: es el mismo comprobante que " + haOriginal.haArchivo()
                        + (haOriginal.haPagina() > 0 ? " (pág. " + haOriginal.haPagina() + ")" : "") + ", que se toma como original.",
                        HAComprobante.HAEstado.DUPLICADO);
                continue;
            }
            if (!HAValidadorRuc.haValido(haD.haRucEmisor())) {
                haC.haObservar("El RUC del emisor (" + haD.haRucEmisor() + ") no es válido.", HAComprobante.HAEstado.REVISAR);
            }
            if ("6".equals(haD.haTipoDocCliente()) && !HAValidadorRuc.haValido(haD.haDocCliente())) {
                haC.haObservar("El RUC del cliente (" + haD.haDocCliente() + ") no es válido.", HAComprobante.HAEstado.REVISAR);
            }
            if ("1".equals(haD.haTipoDocCliente()) && !haD.haDocCliente().matches("\\d{8}")) {
                haC.haObservar("El DNI del cliente no tiene 8 dígitos.", HAComprobante.HAEstado.REVISAR);
            }
            haValidarMontos(haC, haD);
            if (haD.haFecha().isAfter(haHoy)) {
                haC.haObservar("La fecha (" + haD.haFecha() + ") es futura.", HAComprobante.HAEstado.REVISAR);
            }
            haValidarImpreso(haC, haD);
        }
    }

    private static final Pattern HA_EXPLICA_IGV_MENOR = Pattern.compile(
            "exonerad|inafect|exportaci|gratuit|\bisc\b|selectivo|icbper|bolsa|descuento|percepci|rebaja|anticipo",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static void haValidarMontos(HAComprobante haC, HADatosQr haD) {
        if (haD.haTotal().signum() <= 0) {
            haC.haObservar("El total del QR es 0 o negativo.", HAComprobante.HAEstado.REVISAR);
            return;
        }
        if (haD.haIgv().signum() < 0 || haD.haIgv().compareTo(haD.haTotal()) >= 0) {
            haC.haObservar("El IGV del QR no tiene sentido frente al total.", HAComprobante.HAEstado.REVISAR);
            return;
        }
        if (haD.haIgv().signum() == 0) {
            haC.haObservar("Sin IGV (operación exonerada, inafecta o de exportación): el QR no permite comprobar si "
                    + "correspondía no cobrarlo.", HAComprobante.HAEstado.NO_COMPROBABLE);
            return;
        }
        BigDecimal haBase = haD.haTotal().subtract(haD.haIgv());
        BigDecimal haTolerancia = haD.haTotal().multiply(new BigDecimal("0.001")).max(new BigDecimal("0.05"));
        BigDecimal haEsperado = haBase.multiply(HA_TASA).setScale(2, RoundingMode.HALF_UP);
        BigDecimal haDiferencia = haD.haIgv().subtract(haEsperado);
        if (haDiferencia.abs().compareTo(haTolerancia) <= 0) {
            return;
        }
        BigDecimal haEsperadoMype = haBase.multiply(HA_TASA_MYPE).setScale(2, RoundingMode.HALF_UP);
        if (haD.haIgv().subtract(haEsperadoMype).abs().compareTo(haTolerancia) <= 0) {
            haC.haAnotar("IGV al 10 % (régimen MYPE de restaurantes y hoteles).");
            return;
        }
        String haCifras = "es S/ " + haD.haIgv() + " y el 18 % de la base (S/ " + haBase + ") sería S/ " + haEsperado;
        if (haDiferencia.signum() > 0) {
            haC.haObservar("El IGV no cuadra: " + haCifras + ". Es MAYOR de lo posible (ni exonerados, ISC, ICBPER ni "
                    + "descuentos lo explican).", HAComprobante.HAEstado.REVISAR);
            return;
        }
        String haTexto = haC.haTextoPagina();
        boolean haHayTexto = haTexto != null && haTexto.replaceAll("\s", "").length() >= 40;
        if (haHayTexto && !HA_EXPLICA_IGV_MENOR.matcher(haTexto).find()) {
            haC.haObservar("El IGV no cuadra: " + haCifras + ". El comprobante no menciona montos exonerados, inafectos, "
                    + "ISC, ICBPER ni descuentos que lo expliquen.", HAComprobante.HAEstado.REVISAR);
            return;
        }
        haC.haObservar("No se puede comprobar el IGV: " + haCifras + ". Puede ser correcto si hay montos exonerados, "
                + "inafectos, ISC, ICBPER (bolsas) o descuentos" + (haHayTexto ? " (el comprobante los menciona)" : "")
                + "; el QR no trae ese detalle. Revisa el comprobante impreso.", HAComprobante.HAEstado.NO_COMPROBABLE);
    }

    private static void haValidarImpreso(HAComprobante haC, HADatosQr haD) {
        String haTexto = haC.haTextoPagina();
        if (haTexto == null || haTexto.replaceAll("\\s", "").length() < 40) {
            return;
        }
        Matcher haM = HA_MONTO.matcher(haTexto);
        while (haM.find()) {
            String haLimpio = haM.group().replaceAll("[,\\s]", "");
            try {
                if (new BigDecimal(haLimpio).compareTo(haD.haTotal()) == 0) {
                    return;
                }
            } catch (NumberFormatException haIgnorado) {
            }
        }
        haC.haObservar("El total del QR (S/ " + haD.haTotal() + ") no aparece impreso en la página.", HAComprobante.HAEstado.REVISAR);
    }
}
