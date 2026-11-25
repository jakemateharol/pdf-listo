package pe.pdflisto.facturas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

public final class HAParserQrSunat {
    private static final List<DateTimeFormatter> HA_FECHAS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"), DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"));

    private HAParserQrSunat() {
    }

    public static Optional<HADatosQr> haParsear(String haTexto) {
        if (haTexto == null || !haTexto.contains("|")) {
            return Optional.empty();
        }
        String[] haC = haTexto.trim().split("\\|", -1);
        for (int haI = 0; haI < haC.length; haI++) {
            haC[haI] = haC[haI].trim();
        }
        if (haC.length < 7) {
            return Optional.empty();
        }
        String haRuc = haC[0].replaceAll("\\D", "");
        String haTipo = haC[1].replaceAll("\\D", "");
        String haSerie = haC[2].toUpperCase();
        String haNumero = haC[3].replaceAll("\\D", "");
        if (haRuc.length() != 11 || haTipo.isEmpty() || haTipo.length() > 2 || !haSerie.matches("[A-Z0-9]{1,4}") || haNumero.isEmpty()
                || haNumero.length() > 8) {
            return Optional.empty();
        }
        if (haTipo.length() == 1) {
            haTipo = "0" + haTipo;
        }
        BigDecimal haIgv = haMonto(haC[4]);
        BigDecimal haTotal = haMonto(haC[5]);
        LocalDate haFecha = haFecha(haC[6]);
        if (haIgv == null || haTotal == null || haFecha == null) {
            return Optional.empty();
        }
        String haTipoDoc = haC.length > 7 ? haC[7] : "";
        String haDoc = haC.length > 8 ? haC[8].replaceAll("\\s", "") : "";
        return Optional.of(new HADatosQr(haRuc, haTipo, haSerie, haNumero, haIgv, haTotal, haFecha, haTipoDoc, haDoc));
    }

    static BigDecimal haMonto(String haTexto) {
        String haT = haTexto.replace("S/", "").replace(" ", "");
        if (haT.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (haT.contains(",") && haT.contains(".")) {
            haT = haT.replace(",", "");
        } else if (haT.contains(",")) {
            haT = haT.replace(',', '.');
        }
        try {
            return new BigDecimal(haT).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException haE) {
            return null;
        }
    }

    static LocalDate haFecha(String haTexto) {
        for (DateTimeFormatter haF : HA_FECHAS) {
            try {
                return LocalDate.parse(haTexto, haF);
            } catch (DateTimeParseException haIgnorado) {
            }
        }
        return null;
    }
}
