package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import pe.pdflisto.exception.HAPdfListoException;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record HAEncabezadoPie(String[] haArriba, String[] haAbajo, int haDesde, float haTamano, String haArchivo)
        implements HAOperacionPdf {
    static final float HA_MARGEN = 28f;

    @Override
    public String haDescripcion() {
        boolean haHayArriba = haAlgo(haArriba);
        boolean haHayAbajo = haAlgo(haAbajo);
        return haHayArriba && haHayAbajo ? "Encabezado y pie de página" : haHayArriba ? "Encabezado" : "Pie de página";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDoc, HARecursos haRecursos) throws IOException {
        if (!haAlgo(haArriba) && !haAlgo(haAbajo)) {
            throw new HAPdfListoException("Escribe algún texto para el encabezado o el pie.");
        }
        int haTotal = haDoc.getNumberOfPages();
        if (haDesde < 1 || haDesde > haTotal) {
            throw new HAPdfListoException("La página inicial debe estar entre 1 y " + haTotal + ".");
        }
        PDFont haFuente = HAFuentes.haCargar(haDoc, false);
        String haFecha = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (int haI = haDesde - 1; haI < haTotal; haI++) {
            haRecursos.haControl().haAvance(haI, haTotal, "Encabezado y pie: página " + (haI + 1) + " de " + haTotal);
            try (HALienzo haLienzo = new HALienzo(haDoc, haDoc.getPage(haI))) {
                haEscribir(haLienzo, haArriba, true, haI + 1, haTotal, haFecha, haFuente);
                haEscribir(haLienzo, haAbajo, false, haI + 1, haTotal, haFecha, haFuente);
            }
        }
        return haDoc;
    }

    private void haEscribir(HALienzo haLienzo, String[] haTextos, boolean haEnArriba, int haPagina, int haTotal, String haFecha,
                          PDFont haFuente) throws IOException {
        if (haTextos == null) {
            return;
        }
        float haY = haEnArriba ? haLienzo.haAlto() - HA_MARGEN - haTamano : HA_MARGEN;
        for (int haK = 0; haK < Math.min(3, haTextos.length); haK++) {
            if (haTextos[haK] == null || haTextos[haK].isBlank()) {
                continue;
            }
            String haT = haTextos[haK].replace("{pagina}", String.valueOf(haPagina)).replace("{total}", String.valueOf(haTotal))
                    .replace("{fecha}", haFecha).replace("{archivo}", haArchivo == null ? "" : haArchivo);
            float haW = HAFuentes.haAncho(haFuente, haT, haTamano);
            float haX = switch (haK) {
                case 0 -> HA_MARGEN;
                case 1 -> (haLienzo.haAncho() - haW) / 2;
                default -> haLienzo.haAncho() - HA_MARGEN - haW;
            };
            haLienzo.haTexto(haT, haFuente, haTamano, haX, haY, new Color(60, 60, 60), 1f, 0);
        }
    }

    private static boolean haAlgo(String[] haT) {
        if (haT == null) {
            return false;
        }
        for (String haS : haT) {
            if (haS != null && !haS.isBlank()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        java.util.List<String> haTextos = new java.util.ArrayList<>();
        for (String[] haGrupo : new String[][]{haArriba, haAbajo}) {
            if (haGrupo != null) {
                java.util.Arrays.stream(haGrupo).filter(haT -> haT != null && !haT.isBlank()).forEach(haTextos::add);
            }
        }
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAEncabezadoPie(haTextos, haDesde, haArchivo));
    }
}
