package pe.pdflisto.formularios;

import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class HALectorFormulario {
    public record HAFormulario(List<HACampoFormulario> haCampos, boolean haXfaDinamico) {
        public static HAFormulario haVacio() {
            return new HAFormulario(List.of(), false);
        }
    }

    private HALectorFormulario() {
    }

    public static HAFormulario haLeer(PDDocument haPdf) {
        PDAcroForm haForm = haPdf.getDocumentCatalog().getAcroForm(null);
        if (haForm == null) {
            return HAFormulario.haVacio();
        }
        if (haForm.xfaIsDynamic()) {
            return new HAFormulario(List.of(), true);
        }
        Map<COSBase, Integer> haPaginaDeWidget = new IdentityHashMap<>();
        int haI = 0;
        for (PDPage haP : haPdf.getPages()) {
            try {
                for (PDAnnotation haA : haP.getAnnotations()) {
                    haPaginaDeWidget.put(haA.getCOSObject(), haI);
                }
            } catch (IOException haIgnorado) {
            }
            haI++;
        }
        List<HACampoFormulario> haCampos = new ArrayList<>();
        for (PDField haF : haForm.getFieldTree()) {
            if (!(haF instanceof PDTerminalField haT) || haF instanceof PDPushButton) {
                continue;
            }
            int haPagina = -1;
            for (PDAnnotationWidget haW : haT.getWidgets()) {
                Integer haP = haPaginaDeWidget.get(haW.getCOSObject());
                if (haP != null) {
                    haPagina = haP;
                    break;
                }
            }
            String haEtiqueta = haF.getAlternateFieldName() != null && !haF.getAlternateFieldName().isBlank()
                    ? haF.getAlternateFieldName() : haF.getPartialName();
            if (haEtiqueta == null || haEtiqueta.isBlank()) {
                haEtiqueta = haF.getFullyQualifiedName();
            }
            HACampoFormulario.HATipo haTipo;
            String haValor = haF.getValueAsString();
            List<String> haOpciones = List.of();
            if (haF instanceof PDCheckBox haC) {
                haTipo = HACampoFormulario.HATipo.CASILLA;
                haValor = String.valueOf(haC.isChecked());
            } else if (haF instanceof PDRadioButton haR) {
                haTipo = HACampoFormulario.HATipo.OPCION;
                haOpciones = new ArrayList<>(haR.getOnValues());
            } else if (haF instanceof PDChoice haC) {
                haTipo = HACampoFormulario.HATipo.LISTA;
                haOpciones = haC.getOptionsDisplayValues().isEmpty() ? haC.getOptions() : haC.getOptionsDisplayValues();
                haValor = haC.getValue().isEmpty() ? "" : haC.getValue().get(0);
            } else if (haF instanceof PDSignatureField) {
                haTipo = HACampoFormulario.HATipo.FIRMA_DIGITAL;
            } else if (haF instanceof PDTextField) {
                haTipo = HACampoFormulario.HATipo.TEXTO;
            } else {
                continue;
            }
            haCampos.add(new HACampoFormulario(haF.getFullyQualifiedName(), haEtiqueta, haTipo, haValor == null ? "" : haValor,
                    haOpciones, haF.isReadOnly(), haF.isRequired(), haPagina));
        }
        return new HAFormulario(haCampos, false);
    }
}
