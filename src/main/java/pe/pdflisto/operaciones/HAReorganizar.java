package pe.pdflisto.operaciones;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record HAReorganizar(List<Integer> haOrden, String haDescripcion) implements HAOperacionPdf {
    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) {
        PDPageTree haArbol = haDocumento.getPages();
        List<PDPage> haOriginales = new ArrayList<>();
        haArbol.forEach(haOriginales::add);
        for (int haI : haOrden) {
            if (haI < 0 || haI >= haOriginales.size()) {
                throw new IllegalArgumentException("Página fuera de rango: " + (haI + 1));
            }
        }
        for (PDPage haP : haOriginales) {
            haFijarHeredados(haP);
        }
        Set<Integer> haUsadas = new HashSet<>();
        List<PDPage> haNuevas = new ArrayList<>();
        for (int haI : haOrden) {
            PDPage haPagina = haOriginales.get(haI);
            haNuevas.add(haUsadas.add(haI) ? haPagina : haCopia(haPagina));
        }
        for (PDPage haP : haOriginales) {
            haArbol.remove(haP);
        }
        for (PDPage haP : haNuevas) {
            haArbol.add(haP);
        }
        return haDocumento;
    }

    private static void haFijarHeredados(PDPage haP) {
        COSDictionary haD = haP.getCOSObject();
        if (!haD.containsKey(COSName.MEDIA_BOX)) {
            haP.setMediaBox(haP.getMediaBox());
        }
        if (!haD.containsKey(COSName.RESOURCES) && haP.getResources() != null) {
            haP.setResources(haP.getResources());
        }
        if (!haD.containsKey(COSName.CROP_BOX)) {
            haP.setCropBox(haP.getCropBox());
        }
        if (!haD.containsKey(COSName.ROTATE)) {
            haP.setRotation(haP.getRotation());
        }
    }

    private static PDPage haCopia(PDPage haOriginal) {
        COSDictionary haDic = new COSDictionary(haOriginal.getCOSObject());
        haDic.removeItem(COSName.ANNOTS);
        haDic.removeItem(COSName.PARENT);
        return new PDPage(haDic);
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAOrden(haOrden));
    }
}
