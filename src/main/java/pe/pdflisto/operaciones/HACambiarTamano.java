package pe.pdflisto.operaciones;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.util.Matrix;

import java.io.IOException;

public record HACambiarTamano(HATamanoPapel haPapel, HAOrientacion haOrientacion) implements HAOperacionPdf {
    public enum HAOrientacion {
        MANTENER("La de cada página"), VERTICAL("Vertical"), HORIZONTAL("Horizontal");

        private final String haTexto;

        HAOrientacion(String haTexto) {
            this.haTexto = haTexto;
        }

        @Override
        public String toString() {
            return haTexto;
        }
    }

    @Override
    public String haDescripcion() {
        return "Cambiar el tamaño a " + haPapel + (haOrientacion == HAOrientacion.MANTENER ? "" : " " + haOrientacion.toString().toLowerCase());
    }

    @Override
    public PDDocument haAplicar(PDDocument haDoc, HARecursos haRecursos) throws IOException {
        int haTotal = haDoc.getNumberOfPages();
        for (int haI = 0; haI < haTotal; haI++) {
            haRecursos.haControl().haAvance(haI, haTotal, "Cambiando tamaño: página " + (haI + 1) + " de " + haTotal);
            haCambiar(haDoc, haDoc.getPage(haI));
        }
        return haDoc;
    }

    private void haCambiar(PDDocument haDoc, PDPage haPagina) throws IOException {
        PDRectangle haCaja = haPagina.getCropBox();
        int haGiro = ((haPagina.getRotation() % 360) + 360) % 360;
        boolean haGirada = haGiro == 90 || haGiro == 270;
        float haAnchoVisto = haGirada ? haCaja.getHeight() : haCaja.getWidth();
        float haAltoVisto = haGirada ? haCaja.getWidth() : haCaja.getHeight();
        boolean haHorizontal = switch (haOrientacion) {
            case VERTICAL -> false;
            case HORIZONTAL -> true;
            case MANTENER -> haAnchoVisto > haAltoVisto;
        };
        float haDestinoVistoAncho = haHorizontal ? haPapel.haAlto() : haPapel.haAncho();
        float haDestinoVistoAlto = haHorizontal ? haPapel.haAncho() : haPapel.haAlto();
        if (Math.abs(haAnchoVisto - haDestinoVistoAncho) < 1 && Math.abs(haAltoVisto - haDestinoVistoAlto) < 1) {
            return;
        }
        float haW = haGirada ? haDestinoVistoAlto : haDestinoVistoAncho;
        float haH = haGirada ? haDestinoVistoAncho : haDestinoVistoAlto;
        float haEscala = Math.min(haW / haCaja.getWidth(), haH / haCaja.getHeight());
        float haDx = (haW - haCaja.getWidth() * haEscala) / 2 - haCaja.getLowerLeftX() * haEscala;
        float haDy = (haH - haCaja.getHeight() * haEscala) / 2 - haCaja.getLowerLeftY() * haEscala;
        Matrix haM = new Matrix(haEscala, 0, 0, haEscala, haDx, haDy);

        try (PDPageContentStream haAntes = new PDPageContentStream(haDoc, haPagina, PDPageContentStream.AppendMode.PREPEND, true)) {
            haAntes.saveGraphicsState();
            haAntes.transform(haM);
        }
        try (PDPageContentStream haDespues = new PDPageContentStream(haDoc, haPagina, PDPageContentStream.AppendMode.APPEND, true)) {
            haDespues.restoreGraphicsState();
        }
        COSDictionary haD = haPagina.getCOSObject();
        for (String haClave : new String[]{"BleedBox", "TrimBox", "ArtBox"}) {
            haD.removeItem(COSName.getPDFName(haClave));
        }
        PDRectangle haNueva = new PDRectangle(0, 0, haW, haH);
        haPagina.setMediaBox(haNueva);
        haPagina.setCropBox(haNueva);
        for (PDAnnotation haA : haPagina.getAnnotations()) {
            PDRectangle haR = haA.getRectangle();
            if (haR != null) {
                haA.setRectangle(new PDRectangle(haR.getLowerLeftX() * haEscala + haDx, haR.getLowerLeftY() * haEscala + haDy,
                        haR.getWidth() * haEscala, haR.getHeight() * haEscala));
            }
        }
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HATamano(haPapel, haOrientacion));
    }
}
