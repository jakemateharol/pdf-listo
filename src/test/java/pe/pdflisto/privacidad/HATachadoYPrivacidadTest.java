package pe.pdflisto.privacidad;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HATachadoYPrivacidadTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HADetectorDatos haDetector = new HADetectorDatos(null);
    private final HAVerificadorTachado haVerificador = new HAVerificadorTachado();

    @TempDir
    Path haCarpeta;

    @Test
    void haUnNombreSugeridoSeTachaYSeVerificaSiElUsuarioLoMarca() throws Exception {
        File haOriginal = haPdf("acta.pdf", "Cliente: Juan Perez Quispe, DNI 45678912, firma conforme.", "Segunda hoja.");
        HADatoSensible haNombre = haDetector.haDetectar(haOriginal, null).stream().filter(haD -> haD.haTipo() == HATipoDato.NOMBRE)
                .findFirst().orElseThrow();
        assertEquals("Juan Perez Quispe", haNombre.haTexto());
        assertFalse(haNombre.haProbable(), "sugerencia: no viene marcada");
        List<HAAreaTachado> haAreas = haNombre.haAreas().stream().map(haA -> new HAAreaTachado(haNombre.haPagina(), haA).haAmpliada(1.5)).toList();
        File haSalida = haCarpeta.resolve("acta-tachada.pdf").toFile();
        haMotor.haEjecutar(haOriginal, null, new HATachar(haAreas, true), haSalida);
        String haTexto = haTexto(haSalida);
        assertFalse(haTexto.contains("Perez"), haTexto);
        assertTrue(haTexto.contains("45678912"), "solo se tacha lo que el usuario marcó");
        assertTrue(haVerificador.haVerificar(haSalida, null, haAreas, List.of(haNombre)).haSeguro());
    }

    @Test
    void haElTachadoBorraElDatoYElRestoSigueBuscable() throws Exception {
        File haOriginal = haPdf("contrato.pdf", "Cliente: Juan Perez, DNI 45678912, firma conforme.", "Segunda hoja sin datos.");
        byte[] haAntes = Files.readAllBytes(haOriginal.toPath());
        List<HADatoSensible> haDatos = haDetector.haDetectar(haOriginal, null);
        HADatoSensible haDni = haDatos.stream().filter(haD -> haD.haTipo() == HATipoDato.DNI).findFirst().orElseThrow();
        List<HAAreaTachado> haAreas = haDni.haAreas().stream().map(haA -> new HAAreaTachado(haDni.haPagina(), haA).haAmpliada(1.5)).toList();

        File haSalida = haCarpeta.resolve("tachado.pdf").toFile();
        haMotor.haEjecutar(haOriginal, null, new HATachar(haAreas, true), haSalida);

        String haTexto = haTexto(haSalida);
        assertFalse(haTexto.contains("45678912"), "el DNI no debe poder copiarse ni buscarse");
        assertTrue(haTexto.contains("Juan"), "el resto del texto sigue buscable");
        assertTrue(haTexto.contains("Segunda"), "las demás páginas no cambian");
        assertArrayEquals(haAntes, Files.readAllBytes(haOriginal.toPath()), "el original no se toca");

        HAVerificadorTachado.HAInforme haInforme = haVerificador.haVerificar(haSalida, null, haAreas, List.of(haDni));
        assertTrue(haInforme.haSeguro(), haInforme.haProblemas().toString());
    }

    @Test
    void haSiElDatoSigueEnOtraPaginaNoEsSeguro() throws Exception {
        File haOriginal = haPdf("dos.pdf", "DNI 45678912 en la primera.", "Repite: DNI 45678912 en la segunda.");
        HADatoSensible haPrimero = haDetector.haDetectar(haOriginal, null).stream()
                .filter(haD -> haD.haPagina() == 0 && haD.haTipo() == HATipoDato.DNI).findFirst().orElseThrow();
        List<HAAreaTachado> haSoloPrimera = haPrimero.haAreas().stream().map(haA -> new HAAreaTachado(0, haA).haAmpliada(1.5)).toList();
        File haSalida = haCarpeta.resolve("incompleto.pdf").toFile();
        haMotor.haEjecutar(haOriginal, null, new HATachar(haSoloPrimera, false), haSalida);

        HAVerificadorTachado.HAInforme haInforme = haVerificador.haVerificar(haSalida, null, haSoloPrimera, List.of(haPrimero));
        assertFalse(haInforme.haSeguro(), "el mismo DNI sigue en la página 2: NO puede declararse seguro");
        assertTrue(haInforme.haProblemas().get(0).contains("página 2"));
    }

    @Test
    void haSiElDatoQuedaEnLosMetadatosNoEsSeguro() throws Exception {
        File haOriginal = haPdf("meta.pdf", "DNI 45678912");
        File haConMeta = haCarpeta.resolve("meta2.pdf").toFile();
        try (PDDocument haD = Loader.loadPDF(haOriginal)) {
            haD.getDocumentInformation().setTitle("Expediente de 45678912");
            haD.save(haConMeta);
        }
        HADatoSensible haDni = haDetector.haDetectar(haConMeta, null).get(0);
        List<HAAreaTachado> haAreas = haDni.haAreas().stream().map(haA -> new HAAreaTachado(0, haA).haAmpliada(1.5)).toList();

        File haSinLimpiar = haCarpeta.resolve("sin-limpiar.pdf").toFile();
        haMotor.haEjecutar(haConMeta, null, new HATachar(haAreas, false), haSinLimpiar);
        HAVerificadorTachado.HAInforme haMalo = haVerificador.haVerificar(haSinLimpiar, null, haAreas, List.of(haDni));
        assertFalse(haMalo.haSeguro());
        assertTrue(haMalo.haProblemas().get(0).contains("metadatos"));

        File haLimpio = haCarpeta.resolve("limpio.pdf").toFile();
        haMotor.haEjecutar(haConMeta, null, new HATachar(haAreas, true), haLimpio);
        assertTrue(haVerificador.haVerificar(haLimpio, null, haAreas, List.of(haDni)).haSeguro());
    }

    @Test
    void haAreaManualEnUnPdfSinTexto() throws Exception {
        File haOriginal = haPdf("escaneado.pdf", "");
        List<HAAreaTachado> haAreas = List.of(new HAAreaTachado(0, new HAAreaTexto(100, 100, 200, 50)));
        File haSalida = haCarpeta.resolve("manual.pdf").toFile();
        haMotor.haEjecutar(haOriginal, null, new HATachar(haAreas, false), haSalida);
        assertTrue(haVerificador.haVerificar(haSalida, null, haAreas, List.of()).haSeguro());
    }

    @Test
    void haLimpiaMetadatosComentariosYVersionesAnteriores() throws Exception {
        File haOriginal = haPdf("informe.pdf", "Contenido");
        File haConTodo = haCarpeta.resolve("con-todo.pdf").toFile();
        try (PDDocument haD = Loader.loadPDF(haOriginal)) {
            PDDocumentInformation haInfo = haD.getDocumentInformation();
            haInfo.setAuthor("Ana López");
            haInfo.setCreator("Microsoft Word");
            haInfo.setCreationDate(Calendar.getInstance());
            PDAnnotationText haNota = new PDAnnotationText();
            haNota.setContents("Nota interna: no enviar al cliente");
            haNota.setRectangle(new PDRectangle(50, 50, 20, 20));
            haD.getPage(0).getAnnotations().add(haNota);
            haD.save(haConTodo);
        }
        try (PDDocument haD = Loader.loadPDF(haConTodo)) {
            haD.getDocumentInformation().setTitle("Versión 2");
            haD.getDocumentInformation().getCOSObject().setNeedToBeUpdated(true);
            try (var haOut = new java.io.FileOutputStream(haConTodo, true)) {
                haD.saveIncremental(haOut);
            }
        } catch (IOException | IllegalStateException haE) {
        }
        HAAnalizadorPrivacidad haAnalizador = new HAAnalizadorPrivacidad(haDetector);
        HAAnalizadorPrivacidad.HAInventario haAntes = haAnalizador.haInventario(haConTodo, null);
        assertEquals("Ana López", haAntes.haInformacion().get("Autor"));
        assertEquals(1, haAntes.haComentarios());

        File haLimpio = haCarpeta.resolve("limpio.pdf").toFile();
        haMotor.haEjecutar(haConTodo, null, new HALimpiarMetadatos(HALimpiarMetadatos.HAOpciones.haTodo()), haLimpio);
        HAAnalizadorPrivacidad.HAInventario haDespues = haAnalizador.haInventario(haLimpio, null);
        assertTrue(haDespues.haInformacion().isEmpty(), haDespues.haInformacion().toString());
        assertEquals(0, haDespues.haComentarios());
        assertEquals(0, haDespues.haRevisionesAnteriores());

        HAInformeVerificacion haInforme = haAnalizador.haInforme(haLimpio, null, "limpio.pdf");
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haInforme.haComprobaciones().stream()
                .filter(haC -> haC.haNombre().equals("Metadatos")).findFirst().orElseThrow().haEstado());
    }

    @Test
    void haPonerYQuitarContrasena() throws Exception {
        File haOriginal = haPdf("abierto.pdf", "Hola");
        File haProtegido = haCarpeta.resolve("protegido.pdf").toFile();
        var haR = haMotor.haEjecutar(haOriginal, null, new HAProteger("clave123", "", true, false, true), haProtegido);
        assertEquals("clave123", haR.haContrasena());
        assertThrows(IOException.class, () -> Loader.loadPDF(haProtegido).close(), "sin contraseña no abre");
        try (PDDocument haD = Loader.loadPDF(haProtegido, "clave123")) {
            assertTrue(haD.isEncrypted());
            assertFalse(haD.getCurrentAccessPermission().canExtractContent(), "copiar texto no permitido");
            assertFalse(haD.getCurrentAccessPermission().isOwnerPermission());
        }
        File haSinClave = haCarpeta.resolve("sin-clave.pdf").toFile();
        haMotor.haEjecutar(haProtegido, "clave123", new HAQuitarContrasena(), haSinClave);
        try (PDDocument haD = Loader.loadPDF(haSinClave)) {
            assertFalse(haD.isEncrypted());
        }
        assertThrows(HAPdfListoException.class,
                () -> haMotor.haEjecutar(haOriginal, null, new HAProteger("", "", true, true, true), haCarpeta.resolve("x.pdf").toFile()));
    }

    private File haPdf(String haNombre, String... haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String haTexto : haPaginas) {
                PDPage haPagina = new PDPage(PDRectangle.A4);
                haDoc.addPage(haPagina);
                if (!haTexto.isEmpty()) {
                    try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                        haCs.beginText();
                        haCs.setFont(haFuente, 12);
                        haCs.newLineAtOffset(72, 700);
                        haCs.showText(haTexto);
                        haCs.endText();
                    }
                }
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }

    private static String haTexto(File haPdf) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            return new PDFTextStripper().getText(haD);
        }
    }

}
