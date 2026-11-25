package pe.pdflisto.formularios;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAMotorPdf;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HAFormularioTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();

    @TempDir
    Path haCarpeta;

    @Test
    void haLeeLlenaYAplana() throws Exception {
        File haFormulario = haCrearFormulario();
        try (PDDocument haD = Loader.loadPDF(haFormulario)) {
            List<HACampoFormulario> haCampos = HALectorFormulario.haLeer(haD).haCampos();
            assertEquals(3, haCampos.size());
            HACampoFormulario haNombre = haCampos.stream().filter(haC -> haC.haNombre().equals("nombre")).findFirst().orElseThrow();
            assertEquals(HACampoFormulario.HATipo.TEXTO, haNombre.haTipo());
            assertEquals("Nombre completo", haNombre.haEtiqueta());
            assertEquals(0, haNombre.haPagina());
            HACampoFormulario haDistrito = haCampos.stream().filter(haC -> haC.haNombre().equals("distrito")).findFirst().orElseThrow();
            assertEquals(List.of("Lince", "Miraflores", "Surco"), haDistrito.haOpciones());
        }

        File haLleno = haCarpeta.resolve("lleno.pdf").toFile();
        haMotor.haEjecutar(haFormulario, null, new HARellenarFormulario(Map.of(
                "nombre", "María Quispe", "acepto", "true", "distrito", "Miraflores"), false), haLleno);
        try (PDDocument haD = Loader.loadPDF(haLleno)) {
            PDAcroForm haF = haD.getDocumentCatalog().getAcroForm(null);
            assertEquals("María Quispe", haF.getField("nombre").getValueAsString());
            assertTrue(((PDCheckBox) haF.getField("acepto")).isChecked());
            assertEquals(List.of("Miraflores"), ((PDComboBox) haF.getField("distrito")).getValue());
        }

        File haAplanado = haCarpeta.resolve("aplanado.pdf").toFile();
        haMotor.haEjecutar(haFormulario, null, new HARellenarFormulario(Map.of("nombre", "Juan Pérez"), true), haAplanado);
        try (PDDocument haD = Loader.loadPDF(haAplanado)) {
            PDAcroForm haF = haD.getDocumentCatalog().getAcroForm(null);
            assertTrue(haF == null || haF.getFields().isEmpty(), "ya no quedan campos editables");
            assertTrue(new PDFTextStripper().getText(haD).contains("Juan Pérez"), "el valor quedó como texto fijo");
        }
    }

    @Test
    void haSinFormularioDaUnMensajeClaro() throws Exception {
        File haVacio = haCarpeta.resolve("vacio.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            haD.addPage(new PDPage());
            haD.save(haVacio);
        }
        assertThrows(HAPdfListoException.class, () -> haMotor.haEjecutar(haVacio, null,
                new HARellenarFormulario(Map.of("x", "y"), false), haCarpeta.resolve("x.pdf").toFile()));
    }

    private File haCrearFormulario() throws Exception {
        File haArchivo = haCarpeta.resolve("formulario.pdf").toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haPagina = new PDPage(PDRectangle.A4);
            haDoc.addPage(haPagina);
            PDAcroForm haForm = new PDAcroForm(haDoc);
            haDoc.getDocumentCatalog().setAcroForm(haForm);
            PDResources haDr = new PDResources();
            haDr.put(COSName.getPDFName("Helv"), new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            haForm.setDefaultResources(haDr);
            haForm.setDefaultAppearance("/Helv 0 Tf 0 g");

            PDTextField haNombre = new PDTextField(haForm);
            haNombre.setPartialName("nombre");
            haNombre.setAlternateFieldName("Nombre completo");
            haWidget(haNombre.getWidgets().get(0), haPagina, 100, 700, 300, 20);
            haForm.getFields().add(haNombre);

            PDCheckBox haAcepto = new PDCheckBox(haForm);
            haAcepto.setPartialName("acepto");
            haWidget(haAcepto.getWidgets().get(0), haPagina, 100, 650, 15, 15);
            haForm.getFields().add(haAcepto);

            PDComboBox haDistrito = new PDComboBox(haForm);
            haDistrito.setPartialName("distrito");
            haDistrito.setOptions(List.of("Lince", "Miraflores", "Surco"));
            haWidget(haDistrito.getWidgets().get(0), haPagina, 100, 600, 200, 20);
            haForm.getFields().add(haDistrito);
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private static void haWidget(PDAnnotationWidget haW, PDPage haPagina, float haX, float haY, float haAncho, float haAlto) throws Exception {
        haW.setRectangle(new PDRectangle(haX, haY, haAncho, haAlto));
        haW.setPage(haPagina);
        haW.setPrinted(true);
        haPagina.getAnnotations().add(haW);
    }
}
