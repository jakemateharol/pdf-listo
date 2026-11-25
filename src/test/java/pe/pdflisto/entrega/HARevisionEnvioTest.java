package pe.pdflisto.entrega;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.controller.HAPanelListoParaEnviarPruebas;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAProcesoVersion;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HATipoDato;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HARevisionEnvioTest {
    @TempDir
    Path haCarpeta;

    private final HADetectorDatos haDetector = new HADetectorDatos(null);
    private final HARevisionEnvio haRevision = new HARevisionEnvio(haDetector, new HAAnalizadorPrivacidad(haDetector));
    private final HAMotorPdf haMotor = new HAMotorPdf();

    @Test
    void haConDatosSensiblesSaleRojoConTiposYPaginasYNoModificaElArchivo() throws Exception {
        File haPdf = new File("ejemplos/demo-datos-sensibles.pdf");
        byte[] haAntes = Files.readAllBytes(haPdf.toPath());
        HARevisionEnvio.HARevision haR = haRevision.haRevisar(haPdf, null);

        assertEquals(HARevisionEnvio.HANivel.ROJO, haR.haNivel());
        assertEquals(java.util.Set.of(HATipoDato.DNI, HATipoDato.RUC, HATipoDato.CELULAR, HATipoDato.CORREO, HATipoDato.CUENTA,
                HATipoDato.CCI), haR.haDatos().stream().map(pe.pdflisto.privacidad.HADatoSensible::haTipo).collect(
                java.util.stream.Collectors.toSet()), "solo los tipos rojos, sin direcciones ni nombres sugeridos");
        String haRojo = haR.haPuntos().get(0).haDetalle();
        assertTrue(haRojo.contains("DNI (págs. 1, 2)") && haRojo.contains("Cuenta bancaria (pág. 2)"), haRojo);
        assertTrue(haR.haPuntos().stream().anyMatch(haP -> haP.haNivel() == HARevisionEnvio.HANivel.VERDE
                && haP.haTitulo().equals("Abre correctamente")));
        assertEquals(List.of(HARevisionEnvio.HAPasoArreglo.TACHAR), haR.haPlan(), "el tachado ya limpia los metadatos");
        assertTrue(haR.haDescripcionPlan().get(0).startsWith("Tachar "), haR.haDescripcionPlan().toString());
        assertArrayEquals(haAntes, Files.readAllBytes(haPdf.toPath()), "revisar nunca modifica el archivo");
    }

    @Test
    void haUnPdfLimpioSaleVerdeYSinPlan() throws Exception {
        File haPdf = haPdfConTexto("limpio.pdf", "Informe de avance del proyecto, sin datos personales.");
        HARevisionEnvio.HARevision haR = haRevision.haRevisar(haPdf, null);
        assertEquals(HARevisionEnvio.HANivel.VERDE, haR.haNivel(), haR.haPuntos().toString());
        assertTrue(haR.haPlan().isEmpty());
        assertTrue(haR.haPuntos().stream().map(HARevisionEnvio.HAPunto::haTitulo).toList()
                .containsAll(List.of("Abre correctamente", "Sin datos sensibles", "Peso adecuado", "Sin metadatos",
                        "Texto buscable", "Sin contraseña")));
    }

    @Test
    void haMetadatosEscaneoYPesoSalenAmarilloYElPlanComprimeAlFinal() throws Exception {
        File haPdf = haCarpeta.resolve("pesado.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            PDPage haP = new PDPage();
            haD.addPage(haP);
            BufferedImage haImg = new BufferedImage(2400, 2000, BufferedImage.TYPE_INT_RGB);
            Random haAzar = new Random(7);
            for (int haY = 0; haY < haImg.getHeight(); haY++) {
                for (int haX = 0; haX < haImg.getWidth(); haX++) {
                    haImg.setRGB(haX, haY, haAzar.nextInt(0xFFFFFF));
                }
            }
            try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                haCs.drawImage(LosslessFactory.createFromImage(haD, haImg), 0, 0, 612, 792);
            }
            haD.getDocumentInformation().setAuthor("Ana Torres");
            haD.getDocumentInformation().setProducer("Programa de prueba");
            haD.save(haPdf);
        }
        assertTrue(haPdf.length() > HARevisionEnvio.HA_PESO_MAXIMO, "la prueba necesita un PDF de más de 10 MB");
        HARevisionEnvio.HARevision haR = haRevision.haRevisar(haPdf, null);
        assertEquals(HARevisionEnvio.HANivel.AMARILLO, haR.haNivel());
        List<String> haTitulos = haR.haPuntos().stream().map(HARevisionEnvio.HAPunto::haTitulo).toList();
        assertTrue(haTitulos.contains("Tiene metadatos") && haTitulos.contains("1 página sin texto buscable")
                && haTitulos.stream().anyMatch(haT -> haT.startsWith("Pesa ")), haTitulos.toString());
        assertEquals(List.of(HARevisionEnvio.HAPasoArreglo.LIMPIAR_METADATOS, HARevisionEnvio.HAPasoArreglo.COMPRIMIR), haR.haPlan());
    }

    @Test
    void haArreglarDejaElDocumentoEnVerdeYVerificado() throws Exception {
        File haOriginal = new File("ejemplos/demo-datos-sensibles.pdf");
        byte[] haAntes = Files.readAllBytes(haOriginal.toPath());
        HARevisionEnvio.HARevision haR = haRevision.haRevisar(haOriginal, null);
        List<Map.Entry<String, HAProcesoVersion>> haPasos = HAPanelListoParaEnviarPruebas.haPasos(haMotor,
                new HACompresor(haMotor), haR);
        assertEquals(1, haPasos.size());

        File haActual = haOriginal;
        int haN = 0;
        for (var haPaso : haPasos) {
            File haSalida = haCarpeta.resolve("paso-" + (++haN) + ".pdf").toFile();
            var haRes = haPaso.getValue().haEjecutar(haActual, null, haSalida, HAControl.haNinguno());
            assertFalse(haRes.haSinCambios());
            haActual = haRes.haArchivo();
        }
        HARevisionEnvio.HARevision haDespues = haRevision.haRevisar(haActual, null);
        assertEquals(HARevisionEnvio.HANivel.VERDE, haDespues.haNivel(), haDespues.haPuntos().toString());
        assertTrue(haDespues.haPlan().isEmpty());
        assertArrayEquals(haAntes, Files.readAllBytes(haOriginal.toPath()), "el original no se toca");
    }

    private File haPdfConTexto(String haNombre, String haTexto) throws Exception {
        File haF = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haD = new PDDocument()) {
            PDPage haP = new PDPage();
            haD.addPage(haP);
            try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                haCs.beginText();
                haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                haCs.newLineAtOffset(60, 700);
                haCs.showText(haTexto);
                haCs.endText();
            }
            haD.save(haF);
        }
        return haF;
    }
}
