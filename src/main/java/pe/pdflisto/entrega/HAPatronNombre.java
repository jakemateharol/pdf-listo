package pe.pdflisto.entrega;

import pe.pdflisto.operaciones.HAArchivos;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class HAPatronNombre {
    public static final String HA_PREDETERMINADO = "{nombre} (editado)";

    private HAPatronNombre() {
    }

    public static String haAplicar(String haPatron, String haNombreOriginal, String haPerfil, int haPaginas, LocalDateTime haCuando) {
        String haBase = HAArchivos.haSinExtension(haNombreOriginal);
        String haP = haPatron == null || haPatron.isBlank() ? HA_PREDETERMINADO : haPatron;
        String haResultado = haP.replace("{nombre}", haBase)
                .replace("{fecha}", haCuando.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .replace("{hora}", haCuando.format(DateTimeFormatter.ofPattern("HH'h'mm")))
                .replace("{perfil}", haPerfil == null ? "" : haPerfil)
                .replace("{paginas}", String.valueOf(haPaginas))
                .replaceAll("\\s+", " ").replaceAll("\\s*-\\s*$", "").trim();
        return HAArchivos.haNombreSeguro(haResultado.isBlank() ? haBase : haResultado) + ".pdf";
    }
}
