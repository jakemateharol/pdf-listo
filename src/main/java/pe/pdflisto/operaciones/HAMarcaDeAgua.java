package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import pe.pdflisto.exception.HAPdfListoException;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.List;

public record HAMarcaDeAgua(String haTexto, File haImagen, Color haColor, float haOpacidad, boolean haDiagonal, List<Integer> haPaginas)
        implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        return haTexto != null ? "Marca de agua «" + haTexto + "»" : "Marca de agua (imagen)";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException {
        if ((haTexto == null || haTexto.isBlank()) && haImagen == null) {
            throw new HAPdfListoException("Escribe el texto de la marca de agua o elige una imagen.");
        }
        List<Integer> haDonde = haPaginas != null ? haPaginas
                : java.util.stream.IntStream.range(0, haDocumento.getNumberOfPages()).boxed().toList();
        PDFont haFuente = haTexto != null ? HAFuentes.haCargar(haDocumento, true) : null;
        PDImageXObject haLogo = null;
        if (haImagen != null) {
            try {
                haLogo = PDImageXObject.createFromFileByContent(haImagen, haDocumento);
            } catch (IOException | IllegalArgumentException haE) {
                throw new HAPdfListoException("No se pudo leer la imagen «" + haImagen.getName() + "». Usa un PNG o JPG.");
            }
        }
        int haHechas = 0;
        for (int haI : haDonde) {
            haRecursos.haControl().haAvance(haHechas++, haDonde.size(), "Marca de agua: página " + haHechas + " de " + haDonde.size());
            try (HALienzo haLienzo = new HALienzo(haDocumento, haDocumento.getPage(haI))) {
                if (haLogo != null) {
                    haDibujarImagen(haLienzo, haLogo);
                } else {
                    haDibujarTexto(haLienzo, haFuente);
                }
            }
        }
        return haDocumento;
    }

    private void haDibujarTexto(HALienzo haLienzo, PDFont haFuente) throws IOException {
        String haT = haTexto.trim();
        double haAngulo = haDiagonal ? Math.toDegrees(Math.atan2(haLienzo.haAlto(), haLienzo.haAncho())) : 0;
        double haLargoDisponible = haDiagonal ? Math.hypot(haLienzo.haAncho(), haLienzo.haAlto()) * 0.65 : haLienzo.haAncho() * 0.75;
        float haAnchoA1 = HAFuentes.haAncho(haFuente, haT, 1f);
        float haTamano = (float) Math.max(10, Math.min(150, haLargoDisponible / haAnchoA1));
        float haAnchoTexto = haAnchoA1 * haTamano;
        double haRad = Math.toRadians(haAngulo);
        double haCx = haLienzo.haAncho() / 2.0;
        double haCy = haLienzo.haAlto() / 2.0;
        double haMedioAlto = haTamano * 0.35;
        float haX = (float) (haCx - Math.cos(haRad) * haAnchoTexto / 2 + Math.sin(haRad) * haMedioAlto);
        float haY = (float) (haCy - Math.sin(haRad) * haAnchoTexto / 2 - Math.cos(haRad) * haMedioAlto);
        haLienzo.haTexto(haT, haFuente, haTamano, haX, haY, haColor, haOpacidad, haAngulo);
    }

    private void haDibujarImagen(HALienzo haLienzo, PDImageXObject haLogo) throws IOException {
        float haMaxAncho = haLienzo.haAncho() * 0.5f;
        float haMaxAlto = haLienzo.haAlto() * 0.5f;
        float haEscala = Math.min(haMaxAncho / haLogo.getWidth(), haMaxAlto / haLogo.getHeight());
        float haW = haLogo.getWidth() * haEscala;
        float haH = haLogo.getHeight() * haEscala;
        haLienzo.haImagen(haLogo, (haLienzo.haAncho() - haW) / 2, (haLienzo.haAlto() - haH) / 2, haW, haH, haOpacidad);
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        pe.pdflisto.verificacion.HAEsperado haMarca = haTexto != null && !haTexto.isBlank()
                ? new pe.pdflisto.verificacion.HAEsperado.HATexto("Marca de agua", java.util.List.of(haTexto), haPaginas)
                : new pe.pdflisto.verificacion.HAEsperado.HAContenidoCambiado("Marca de agua", haPaginas);
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), haMarca);
    }
}
