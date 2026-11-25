package pe.pdflisto.facturas;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HADatosQr(String haRucEmisor, String haTipo, String haSerie, String haNumero, BigDecimal haIgv, BigDecimal haTotal,
                      LocalDate haFecha, String haTipoDocCliente, String haDocCliente) {
    public String haNombreTipo() {
        return switch (haTipo) {
            case "01" -> "Factura";
            case "03" -> "Boleta";
            case "07" -> "Nota de crédito";
            case "08" -> "Nota de débito";
            default -> "Tipo " + haTipo;
        };
    }

    public String haNombreDocCliente() {
        if (haDocCliente == null || haDocCliente.isBlank() || "-".equals(haDocCliente)) {
            return "";
        }
        String haT = switch (haTipoDocCliente == null ? "" : haTipoDocCliente) {
            case "6" -> "RUC";
            case "1" -> "DNI";
            case "4" -> "CE";
            case "7" -> "Pasaporte";
            default -> "Doc.";
        };
        return haT + " " + haDocCliente;
    }

    public String haClave() {
        String haN = haNumero.replaceFirst("^0+(?=\\d)", "");
        return haRucEmisor + "|" + haTipo + "|" + haSerie.toUpperCase() + "|" + haN;
    }

    public String haSerieNumero() {
        return haSerie + "-" + haNumero;
    }
}
