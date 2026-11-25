package pe.pdflisto.anotaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationCircle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationHighlight;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationInk;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationStrikeout;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationUnderline;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAFuentes;
import pe.pdflisto.operaciones.HALienzo;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;
import pe.pdflisto.pdf.HAAreaTexto;

import java.awt.Color;
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

public record HAAnotar(List<HAAnotacion> haAnotaciones, String haAutor) implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        boolean haFirma = haAnotaciones.stream().anyMatch(haA -> haA instanceof HAAnotacion.HAFirma);
        int haN = haAnotaciones.size();
        return (haFirma ? "Firma y anotaciones" : "Anotaciones") + " (" + haN + ")";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDoc, HARecursos haRecursos) throws IOException {
        if (haAnotaciones.isEmpty()) {
            throw new HAPdfListoException("No hay anotaciones para aplicar.");
        }
        PDFont haFuente = null;
        PDFont haNegrita = null;
        for (int haI = 0; haI < haAnotaciones.size(); haI++) {
            haRecursos.haControl().haAvance(haI, haAnotaciones.size(), "Aplicando anotación " + (haI + 1) + " de " + haAnotaciones.size());
            HAAnotacion haA = haAnotaciones.get(haI);
            if (haA.haPagina() < 0 || haA.haPagina() >= haDoc.getNumberOfPages()) {
                throw new HAPdfListoException("Una anotación está en una página que ya no existe.");
            }
            PDPage haPagina = haDoc.getPage(haA.haPagina());
            HAEspacioPagina haEsp = HAEspacioPagina.haDe(haPagina);
            if (haA instanceof HAAnotacion.HAMarcado haM) {
                haAgregar(haDoc, haPagina, haMarcado(haM, haEsp));
            } else if (haA instanceof HAAnotacion.HANota haN) {
                haAgregar(haDoc, haPagina, haNota(haN, haEsp));
            } else if (haA instanceof HAAnotacion.HAForma haF) {
                haAgregar(haDoc, haPagina, haForma(haF, haEsp));
            } else if (haA instanceof HAAnotacion.HATrazo haT) {
                haAgregar(haDoc, haPagina, haTrazo(haT, haEsp));
            } else if (haA instanceof HAAnotacion.HATexto haT) {
                haFuente = haFuente != null ? haFuente : HAFuentes.haCargar(haDoc, false);
                haTexto(haDoc, haPagina, haT, haFuente);
            } else if (haA instanceof HAAnotacion.HAImagen haIm) {
                haImagen(haDoc, haPagina, haIm.haArea(), PDImageXObject.createFromFileByContent(haIm.haArchivo(), haDoc));
            } else if (haA instanceof HAAnotacion.HAFirma haFi) {
                haImagen(haDoc, haPagina, haFi.haArea(), PDImageXObject.createFromFileByContent(haFi.haImagen(), haDoc));
            } else if (haA instanceof HAAnotacion.HASello haS) {
                haNegrita = haNegrita != null ? haNegrita : HAFuentes.haCargar(haDoc, true);
                haSello(haDoc, haPagina, haS, haNegrita);
            }
        }
        return haDoc;
    }

    private PDAnnotation haMarcado(HAAnotacion.HAMarcado haM, HAEspacioPagina haEsp) {
        PDAnnotationTextMarkup haA = switch (haM.haTipo()) {
            case RESALTADO -> new PDAnnotationHighlight();
            case SUBRAYADO -> new PDAnnotationUnderline();
            case TACHADO -> new PDAnnotationStrikeout();
        };
        float[] haCuadros = new float[haM.haAreas().size() * 8];
        PDRectangle haTotal = null;
        for (int haI = 0; haI < haM.haAreas().size(); haI++) {
            HAAreaTexto haArea = haM.haAreas().get(haI);
            System.arraycopy(haEsp.haCuadrilatero(haArea), 0, haCuadros, haI * 8, 8);
            haTotal = haUnion(haTotal, haEsp.haRectangulo(haArea, 1));
        }
        haA.setQuadPoints(haCuadros);
        haA.setRectangle(haTotal);
        haComunes(haA, haM.haColor(), haM.haTexto());
        return haA;
    }

    private PDAnnotation haNota(HAAnotacion.HANota haN, HAEspacioPagina haEsp) {
        PDAnnotationText haA = new PDAnnotationText();
        haA.setRectangle(haEsp.haRectangulo(new HAAreaTexto(haN.haX(), haN.haY(), 20, 20), 0));
        haA.setName(PDAnnotationText.NAME_COMMENT);
        haComunes(haA, haN.haColor(), haN.haTexto());
        return haA;
    }

    private PDAnnotation haForma(HAAnotacion.HAForma haF, HAEspacioPagina haEsp) {
        PDAnnotationMarkup haA;
        if (haF.haTipo() == HAAnotacion.HATipoForma.RECTANGULO || haF.haTipo() == HAAnotacion.HATipoForma.ELIPSE) {
            PDAnnotationSquareCircle haSc = haF.haTipo() == HAAnotacion.HATipoForma.RECTANGULO
                    ? new PDAnnotationSquare() : new PDAnnotationCircle();
            haSc.setRectangle(haEsp.haRectangulo(haF.haArea(), haF.haGrosor()));
            haA = haSc;
        } else {
            PDAnnotationLine haLinea = new PDAnnotationLine();
            float[] haP1 = haEsp.haAUsuario(haF.haX1(), haF.haY1());
            float[] haP2 = haEsp.haAUsuario(haF.haX2(), haF.haY2());
            haLinea.setLine(new float[]{haP1[0], haP1[1], haP2[0], haP2[1]});
            if (haF.haTipo() == HAAnotacion.HATipoForma.FLECHA) {
                haLinea.setEndPointEndingStyle(PDAnnotationLine.LE_OPEN_ARROW);
            }
            haLinea.setRectangle(haEsp.haRectangulo(haF.haArea(), 10 + haF.haGrosor() * 2));
            haA = haLinea;
        }
        haBorde(haA, haF.haGrosor());
        haComunes(haA, haF.haColor(), null);
        return haA;
    }

    private PDAnnotation haTrazo(HAAnotacion.HATrazo haT, HAEspacioPagina haEsp) {
        PDAnnotationInk haA = new PDAnnotationInk();
        float[] haCoords = new float[haT.haPuntos().size() * 2];
        double haMinX = Double.MAX_VALUE, haMinY = Double.MAX_VALUE, haMaxX = -Double.MAX_VALUE, haMaxY = -Double.MAX_VALUE;
        for (int haI = 0; haI < haT.haPuntos().size(); haI++) {
            double[] haP = haT.haPuntos().get(haI);
            float[] haU = haEsp.haAUsuario(haP[0], haP[1]);
            haCoords[haI * 2] = haU[0];
            haCoords[haI * 2 + 1] = haU[1];
            haMinX = Math.min(haMinX, haP[0]);
            haMinY = Math.min(haMinY, haP[1]);
            haMaxX = Math.max(haMaxX, haP[0]);
            haMaxY = Math.max(haMaxY, haP[1]);
        }
        haA.setInkList(new float[][]{haCoords});
        haA.setRectangle(haEsp.haRectangulo(new HAAreaTexto(haMinX, haMinY, haMaxX - haMinX, haMaxY - haMinY), haT.haGrosor() + 2));
        haBorde(haA, haT.haGrosor());
        haComunes(haA, haT.haColor(), null);
        return haA;
    }

    private void haComunes(PDAnnotationMarkup haA, Color haColor, String haContenido) {
        haA.setColor(new PDColor(new float[]{haColor.getRed() / 255f, haColor.getGreen() / 255f, haColor.getBlue() / 255f},
                PDDeviceRGB.INSTANCE));
        if (haContenido != null && !haContenido.isBlank()) {
            haA.setContents(haContenido);
        }
        if (haAutor != null && !haAutor.isBlank()) {
            haA.setTitlePopup(haAutor);
        }
        haA.setCreationDate(Calendar.getInstance());
        haA.setModifiedDate(Calendar.getInstance());
        haA.setPrinted(true);
    }

    private static void haBorde(PDAnnotationMarkup haA, float haGrosor) {
        PDBorderStyleDictionary haBorde = new PDBorderStyleDictionary();
        haBorde.setWidth(haGrosor);
        haA.setBorderStyle(haBorde);
    }

    private static void haAgregar(PDDocument haDoc, PDPage haPagina, PDAnnotation haA) throws IOException {
        haA.setPage(haPagina);
        haA.constructAppearances(haDoc);
        haPagina.getAnnotations().add(haA);
    }

    private static PDRectangle haUnion(PDRectangle haA, PDRectangle haB) {
        if (haA == null) {
            return haB;
        }
        float haX1 = Math.min(haA.getLowerLeftX(), haB.getLowerLeftX());
        float haY1 = Math.min(haA.getLowerLeftY(), haB.getLowerLeftY());
        float haX2 = Math.max(haA.getUpperRightX(), haB.getUpperRightX());
        float haY2 = Math.max(haA.getUpperRightY(), haB.getUpperRightY());
        return new PDRectangle(haX1, haY1, haX2 - haX1, haY2 - haY1);
    }

    private static void haTexto(PDDocument haDoc, PDPage haPagina, HAAnotacion.HATexto haT, PDFont haFuente) throws IOException {
        String[] haLineas = haT.haTexto().split("\\R");
        try (HALienzo haLienzo = new HALienzo(haDoc, haPagina)) {
            for (int haI = 0; haI < haLineas.length; haI++) {
                if (haLineas[haI].isEmpty()) {
                    continue;
                }
                HAFuentes.haAncho(haFuente, haLineas[haI], haT.haTamano());
                float haY = (float) (haLienzo.haAlto() - haT.haY() - haT.haTamano() * (0.85 + haI * 1.25));
                haLienzo.haTexto(haLineas[haI], haFuente, haT.haTamano(), (float) haT.haX(), haY, haT.haColor(), 1f, 0);
            }
        }
    }

    private static void haImagen(PDDocument haDoc, PDPage haPagina, HAAreaTexto haA, PDImageXObject haImg) throws IOException {
        try (HALienzo haLienzo = new HALienzo(haDoc, haPagina)) {
            haLienzo.haImagen(haImg, (float) haA.haX(), (float) (haLienzo.haAlto() - haA.haY() - haA.haAlto()),
                    (float) haA.haAncho(), (float) haA.haAlto(), 1f);
        }
    }

    private static void haSello(PDDocument haDoc, PDPage haPagina, HAAnotacion.HASello haS, PDFont haFuente) throws IOException {
        HAAreaTexto haA = haS.haArea();
        String haTexto = haS.haTexto().trim().toUpperCase();
        try (HALienzo haLienzo = new HALienzo(haDoc, haPagina)) {
            float haBase = (float) (haLienzo.haAlto() - haA.haY() - haA.haAlto());
            float haGrosor = (float) Math.max(1.5, Math.min(haA.haAncho(), haA.haAlto()) * 0.04);
            haLienzo.haRectangulo((float) haA.haX(), haBase, (float) haA.haAncho(), (float) haA.haAlto(), haS.haColor(), haGrosor);
            float haAnchoA1 = HAFuentes.haAncho(haFuente, haTexto, 1f);
            float haAltoTexto = (float) (haA.haAlto() * (haS.haFecha() != null ? 0.40 : 0.55));
            float haTam = (float) Math.max(5, Math.min(haAltoTexto, haA.haAncho() * 0.85 / Math.max(haAnchoA1, 0.01)));
            float haY = haS.haFecha() != null ? haBase + (float) (haA.haAlto() * 0.48) : haBase + (float) (haA.haAlto() - haTam * 0.72) / 2;
            haLienzo.haTexto(haTexto, haFuente, haTam, (float) (haA.haX() + (haA.haAncho() - haAnchoA1 * haTam) / 2), haY, haS.haColor(), 1f, 0);
            if (haS.haFecha() != null) {
                float haTamFecha = haTam * 0.55f;
                float haAnchoFecha = HAFuentes.haAncho(haFuente, haS.haFecha(), haTamFecha);
                haLienzo.haTexto(haS.haFecha(), haFuente, haTamFecha, (float) (haA.haX() + (haA.haAncho() - haAnchoFecha) / 2),
                        haBase + (float) (haA.haAlto() * 0.16), haS.haColor(), 1f, 0);
            }
        }
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        java.util.Map<Integer, Integer> haPorPagina = new java.util.TreeMap<>();
        java.util.Set<Integer> haPaginasTexto = new java.util.TreeSet<>();
        java.util.Set<Integer> haPaginasImagen = new java.util.TreeSet<>();
        for (HAAnotacion haA : haAnotaciones) {
            if (haA instanceof HAAnotacion.HAMarcado || haA instanceof HAAnotacion.HANota || haA instanceof HAAnotacion.HAForma
                    || haA instanceof HAAnotacion.HATrazo) {
                haPorPagina.merge(haA.haPagina(), 1, Integer::sum);
            } else if (haA instanceof HAAnotacion.HATexto haT && !haT.haTexto().isBlank()) {
                haPaginasTexto.add(haA.haPagina());
            } else if (haA instanceof HAAnotacion.HASello haS && haS.haTexto() != null && !haS.haTexto().isBlank()) {
                haPaginasTexto.add(haA.haPagina());
            } else {
                haPaginasImagen.add(haA.haPagina());
            }
        }
        java.util.List<pe.pdflisto.verificacion.HAEsperado> haLista = new java.util.ArrayList<>();
        haLista.add(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas());
        if (!haPorPagina.isEmpty()) {
            haLista.add(new pe.pdflisto.verificacion.HAEsperado.HAAnotaciones(haPorPagina));
        }
        if (!haPaginasTexto.isEmpty()) {
            for (HAAnotacion haA : haAnotaciones) {
                if (haA instanceof HAAnotacion.HATexto haT && !haT.haTexto().isBlank()) {
                    haLista.add(new pe.pdflisto.verificacion.HAEsperado.HATexto("Texto agregado", haT.haTexto().lines().filter(haL -> !haL.isBlank()).toList(), java.util.List.of(haA.haPagina())));
                } else if (haA instanceof HAAnotacion.HASello haS && haS.haTexto() != null && !haS.haTexto().isBlank()) {
                    haLista.add(new pe.pdflisto.verificacion.HAEsperado.HATexto("Sello", java.util.List.of(haS.haTexto().trim().toUpperCase()), java.util.List.of(haA.haPagina())));
                }
            }
        }
        if (!haPaginasImagen.isEmpty()) {
            boolean haFirma = haAnotaciones.stream().anyMatch(haA -> haA instanceof HAAnotacion.HAFirma);
            haLista.add(new pe.pdflisto.verificacion.HAEsperado.HAContenidoCambiado(haFirma ? "Firma e imágenes" : "Imágenes", new java.util.ArrayList<>(haPaginasImagen)));
        }
        return haLista;
    }
}
