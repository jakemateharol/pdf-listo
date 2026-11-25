package pe.pdflisto.privacidad;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;

import java.util.ArrayList;
import java.util.List;

public record HALimpiarMetadatos(HAOpciones haOpciones) implements HAOperacionPdf {
    public record HAOpciones(boolean haInformacion, boolean haXmp, boolean haComentarios, boolean haAdjuntos, boolean haJavascript) {
        public static HAOpciones haTodo() {
            return new HAOpciones(true, true, true, true, true);
        }
    }

    @Override
    public String haDescripcion() {
        return "Limpiar metadatos";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) {
        PDDocumentCatalog haCatalogo = haDocumento.getDocumentCatalog();
        COSDictionary haCat = haCatalogo.getCOSObject();
        if (haOpciones.haInformacion()) {
            haDocumento.setDocumentInformation(new PDDocumentInformation());
        }
        if (haOpciones.haXmp()) {
            haCatalogo.setMetadata(null);
            haCat.removeItem(COSName.getPDFName("PieceInfo"));
            for (PDPage haP : haDocumento.getPages()) {
                haP.setMetadata(null);
                haP.getCOSObject().removeItem(COSName.getPDFName("PieceInfo"));
            }
        }
        if (haOpciones.haComentarios() || haOpciones.haAdjuntos()) {
            for (PDPage haP : haDocumento.getPages()) {
                try {
                    List<PDAnnotation> haQuedan = new ArrayList<>();
                    for (PDAnnotation haA : haP.getAnnotations()) {
                        boolean haConservar = haA instanceof PDAnnotationLink || haA instanceof PDAnnotationWidget;
                        boolean haEsAdjunto = "FileAttachment".equals(haA.getSubtype());
                        if (haConservar || (!haOpciones.haComentarios() && !haEsAdjunto)) {
                            haQuedan.add(haA);
                        }
                    }
                    haP.setAnnotations(haQuedan);
                } catch (java.io.IOException haE) {
                    haP.getCOSObject().removeItem(COSName.ANNOTS);
                }
            }
        }
        PDDocumentNameDictionary haNombres = haCatalogo.getNames();
        if (haOpciones.haAdjuntos()) {
            if (haNombres != null) {
                haNombres.setEmbeddedFiles(null);
            }
            haCat.removeItem(COSName.getPDFName("AF"));
        }
        if (haOpciones.haJavascript()) {
            if (haNombres != null) {
                haNombres.setJavascript(null);
            }
            haCat.removeItem(COSName.AA);
            if (haCat.getDictionaryObject(COSName.OPEN_ACTION) instanceof COSDictionary haAccion
                    && COSName.JAVA_SCRIPT.equals(haAccion.getCOSName(COSName.S))) {
                haCat.removeItem(COSName.OPEN_ACTION);
            }
        }
        return haDocumento;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HASinMetadatos(haOpciones.haInformacion(), haOpciones.haXmp(),
                haOpciones.haComentarios(), haOpciones.haAdjuntos(), haOpciones.haJavascript()));
    }
}
