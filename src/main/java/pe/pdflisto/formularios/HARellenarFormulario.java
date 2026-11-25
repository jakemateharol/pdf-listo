package pe.pdflisto.formularios;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record HARellenarFormulario(Map<String, String> haValores, boolean haAplanar) implements HAOperacionPdf {
    @Override
    public String haDescripcion() {
        return haAplanar ? "Rellenar y aplanar formulario" : "Rellenar formulario";
    }

    @Override
    public PDDocument haAplicar(PDDocument haDoc, HARecursos haRecursos) throws IOException {
        PDAcroForm haForm = haDoc.getDocumentCatalog().getAcroForm(null);
        if (haForm == null) {
            throw new HAPdfListoException("Este PDF no tiene formulario.");
        }
        if (haForm.xfaIsDynamic()) {
            throw new HAPdfListoException("Este formulario es de tipo XFA (Adobe LiveCycle): PDF Listo no puede llenarlo. "
                    + "Ábrelo en Adobe Acrobat Reader.");
        }
        haAsegurarFuente(haForm);
        List<String> haErrores = new ArrayList<>();
        int haI = 0;
        for (Map.Entry<String, String> haE : haValores.entrySet()) {
            haRecursos.haControl().haAvance(haI++, haValores.size(), "Escribiendo campo " + haI + " de " + haValores.size());
            PDField haCampo = haForm.getField(haE.getKey());
            if (haCampo == null || haCampo.isReadOnly()) {
                continue;
            }
            try {
                if (haCampo instanceof PDCheckBox haCasilla) {
                    if (Boolean.parseBoolean(haE.getValue())) {
                        haCasilla.check();
                    } else {
                        haCasilla.unCheck();
                    }
                } else {
                    haCampo.setValue(haE.getValue());
                }
            } catch (IOException | IllegalArgumentException | IllegalStateException haEx) {
                haErrores.add("«" + haCampo.getPartialName() + "» (" + haEx.getMessage() + ")");
            }
        }
        if (!haErrores.isEmpty()) {
            throw new HAPdfListoException("No se pudo escribir en: " + String.join(", ", haErrores)
                    + ". Revisa que el valor sea válido para ese campo.");
        }
        if (haAplanar) {
            haRecursos.haControl().haMensaje("Aplanando el formulario…");
            haForm.flatten();
        }
        return haDoc;
    }

    private static void haAsegurarFuente(PDAcroForm haForm) {
        PDResources haDr = haForm.getDefaultResources();
        if (haDr == null) {
            haDr = new PDResources();
            haForm.setDefaultResources(haDr);
        }
        if (haDr.getCOSObject().getCOSDictionary(COSName.FONT) == null
                || !haDr.getCOSObject().getCOSDictionary(COSName.FONT).containsKey(COSName.getPDFName("Helv"))) {
            haDr.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        }
        if (haForm.getDefaultAppearance() == null || haForm.getDefaultAppearance().isBlank()) {
            haForm.setDefaultAppearance("/Helv 0 Tf 0 g");
        }
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAFormulario(haValores, haAplanar));
    }
}
