package pe.pdflisto.operaciones;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public record HAComprimir(HANivelCompresion haNivel) implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        return "Compresión " + haNivel.toString().toLowerCase();
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) {
        Map<COSBase, Optional<PDImageXObject>> haHechas = new IdentityHashMap<>();
        int haTotal = haDocumento.getNumberOfPages();
        int haI = 0;
        for (PDPage haPagina : haDocumento.getPages()) {
            haRecursos.haControl().haAvance(haI++, haTotal, "Comprimiendo imágenes: página " + haI + " de " + haTotal);
            haProcesar(haPagina.getResources(), haDocumento, haHechas, 0);
        }
        return haDocumento;
    }

    private void haProcesar(PDResources haRecursos, PDDocument haDoc, Map<COSBase, Optional<PDImageXObject>> haHechas, int haNivelAnidado) {
        if (haRecursos == null || haNivelAnidado > 6) {
            return;
        }
        for (COSName haNombre : haRecursos.getXObjectNames()) {
            try {
                PDXObject haObjeto = haRecursos.getXObject(haNombre);
                if (haObjeto instanceof PDImageXObject haImagen) {
                    Optional<PDImageXObject> haNueva = haHechas.get(haImagen.getCOSObject());
                    if (haNueva == null) {
                        haNueva = Optional.ofNullable(haRecodificar(haDoc, haImagen));
                        haHechas.put(haImagen.getCOSObject(), haNueva);
                    }
                    haNueva.ifPresent(haN -> haRecursos.put(haNombre, haN));
                } else if (haObjeto instanceof PDFormXObject haFormulario) {
                    haProcesar(haFormulario.getResources(), haDoc, haHechas, haNivelAnidado + 1);
                }
            } catch (IOException | RuntimeException haE) {
            }
        }
    }

    private PDImageXObject haRecodificar(PDDocument haDoc, PDImageXObject haOriginal) throws IOException {
        if (haOriginal.isStencil() || haOriginal.getSoftMask() != null || haOriginal.getBitsPerComponent() == 1
                || haOriginal.getCOSObject().containsKey(COSName.MASK)) {
            return null;
        }
        int haW = haOriginal.getWidth();
        int haH = haOriginal.getHeight();
        if ((long) haW * haH < 64 * 64) {
            return null;
        }
        BufferedImage haImagen = haOriginal.getImage();
        boolean haGris = haImagen.getType() == BufferedImage.TYPE_BYTE_GRAY;
        double haEscala = Math.min(1.0, (double) haNivel.haLadoMaximo() / Math.max(haW, haH));
        int haNw = Math.max(1, (int) Math.round(haW * haEscala));
        int haNh = Math.max(1, (int) Math.round(haH * haEscala));
        BufferedImage haDestino = new BufferedImage(haNw, haNh, haGris ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
        Graphics2D haG = haDestino.createGraphics();
        haG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        haG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        haG.drawImage(haImagen, 0, 0, haNw, haNh, null);
        haG.dispose();

        PDImageXObject haJpeg = JPEGFactory.createFromImage(haDoc, haDestino, haNivel.haCalidadJpeg());
        long haAntes = haOriginal.getCOSObject().getLength();
        long haDespues = haJpeg.getCOSObject().getLength();
        return haDespues < haAntes * 0.9 ? haJpeg : null;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAPeso(null));
    }
}
