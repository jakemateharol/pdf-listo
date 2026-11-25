package pe.pdflisto.interprete;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAResultado;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAInformePdf;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HARequisitos;
import pe.pdflisto.verificacion.HAVerificador;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAPlanYVerificacionTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HAEjecutorPlan haEjecutor = new HAEjecutorPlan(haMotor, new HACompresor(haMotor));
    private final HAInterpretePedidos haInterprete = new HAInterpretePedidos();
    private final HAVerificador haVerificador = new HAVerificador(new pe.pdflisto.privacidad.HADetectorDatos(null));

    @TempDir
    Path haCarpeta;

    @Test
    void haUnirEliminarNumerarYVerificar() throws Exception {
        File haPrincipal = haPdf("principal.pdf", 4);
        File haAnexo = haPdf("anexo.pdf", 2);
        byte[] haAntes = Files.readAllBytes(haPrincipal.toPath());

        HAInterpretacion haPlan = haInterprete.haInterpretar("une, elimina la página 2 y numera con formato 1 de N",
                new HAInterpretePedidos.HAContexto(4, List.of("anexo.pdf")));
        assertTrue(haPlan.haPreguntas().isEmpty(), haPlan.toString());
        List<HAUnir.HAFuente> haOtros = List.of(new HAUnir.HAFuente("anexo.pdf", haAnexo, null, false));

        HARequisitos haReq = haEjecutor.haRequisitos(haPlan.haPasos(), 4, 2, haPrincipal.length());
        assertEquals(5, haReq.haPaginasEsperadas());

        File haSalida = haCarpeta.resolve("resultado.pdf").toFile();
        HAResultado haR = haEjecutor.haEjecutar(haPlan.haPasos(), haPrincipal, null, haSalida, haOtros);
        assertEquals(5, haR.haPaginas());
        assertArrayEquals(haAntes, Files.readAllBytes(haPrincipal.toPath()), "el original no cambia");
        try (var haArchivos = Files.list(haCarpeta)) {
            assertTrue(haArchivos.noneMatch(haP -> haP.getFileName().toString().startsWith("paso")), "no quedan intermedios");
        }

        HAInformeVerificacion haInforme = haVerificador.haVerificar(haSalida, null, haReq, "principal.pdf", "une...",
                haPlan.haPasos().stream().map(HAPaso::haDescripcion).toList());
        assertTrue(haInforme.haCumple(), haInforme.haComprobaciones().toString());
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haEstado(haInforme, "Numeración"));
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haEstado(haInforme, "Número de páginas"));

        File haPdfInforme = haCarpeta.resolve("informe.pdf").toFile();
        new HAInformePdf().haGenerar(haInforme, haPdfInforme);
        try (PDDocument haD = org.apache.pdfbox.Loader.loadPDF(haPdfInforme)) {
            assertTrue(haD.getNumberOfPages() >= 1);
        }
    }

    @Test
    void haElVerificadorDetectaLoQueNoCumple() throws Exception {
        File haSinNumeros = haPdf("sin-numeros.pdf", 3);
        HARequisitos haExigente = new HARequisitos(100L, 5, new HARequisitos.HANumeracion("{n}", 1, 1, false), "SECRETO", 0, false, false);
        HAInformeVerificacion haInforme = haVerificador.haVerificar(haSinNumeros, null, haExigente, "x", null, List.of());
        assertFalse(haInforme.haCumple());
        assertEquals(HAComprobacion.HAEstado.REVISAR, haEstado(haInforme, "Peso"));
        assertEquals(HAComprobacion.HAEstado.REVISAR, haEstado(haInforme, "Número de páginas"));
        assertEquals(HAComprobacion.HAEstado.REVISAR, haEstado(haInforme, "Numeración"));
        assertEquals(HAComprobacion.HAEstado.REVISAR, haEstado(haInforme, "Marca de agua"));
    }

    @Test
    void haNumeracionVerificadaEnElMargenAunqueLaPaginaEsteGirada() throws Exception {
        File haBase = haPdf("girada.pdf", 3);
        File haGirado = haCarpeta.resolve("girado.pdf").toFile();
        haMotor.haEjecutar(haBase, null, new pe.pdflisto.operaciones.HARotar(List.of(1), 90), haGirado);
        File haNumerado = haCarpeta.resolve("numerado.pdf").toFile();
        haMotor.haEjecutar(haGirado, null, new pe.pdflisto.operaciones.HANumerar("{n}",
                pe.pdflisto.operaciones.HAPosicion.ABAJO_DERECHA, 1, 1, 11), haNumerado);

        HARequisitos haAbajo = new HARequisitos(null, 3, new HARequisitos.HANumeracion("{n}", 1, 1, false), null, 0, false, false);
        assertEquals(HAComprobacion.HAEstado.CUMPLE,
                haEstado(haVerificador.haVerificar(haNumerado, null, haAbajo, "x", null, List.of()), "Numeración"));

        HARequisitos haArriba = new HARequisitos(null, 3, new HARequisitos.HANumeracion("{n}", 1, 1, true), null, 0, false, false);
        assertEquals(HAComprobacion.HAEstado.REVISAR,
                haEstado(haVerificador.haVerificar(haNumerado, null, haArriba, "x", null, List.of()), "Numeración"),
                "los números están abajo, no arriba");
    }

    @Test
    void haNumeracionVerificadaAunqueElTextoLlegueHastaElMargen() throws Exception {
        File haEjemplo = new File("ejemplos/demo-12-paginas.pdf");
        File haNumerado = haCarpeta.resolve("ejemplo-numerado.pdf").toFile();
        HAInterpretacion haPlan = haInterprete.haInterpretar("numera", new HAInterpretePedidos.HAContexto(12, List.of()));
        haEjecutor.haEjecutar(haPlan.haPasos(), haEjemplo, null, haNumerado, List.of());
        HARequisitos haReq = haEjecutor.haRequisitos(haPlan.haPasos(), 12, 0, haEjemplo.length());
        HAInformeVerificacion haInforme = haVerificador.haVerificar(haNumerado, null, haReq, "x", null, List.of());
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haEstado(haInforme, "Numeración"), haInforme.haComprobaciones().toString());
        assertTrue(haInforme.haCumple(), haInforme.haComprobaciones().toString());
    }

    @Test
    void haUnArchivoDanadoNoSeDeclaraCorrecto() throws Exception {
        File haRoto = haCarpeta.resolve("roto.pdf").toFile();
        Files.writeString(haRoto.toPath(), "%PDF-1.4 basura");
        HAInformeVerificacion haInforme = haVerificador.haVerificar(haRoto, null, HARequisitos.haNinguno(0), "roto", null, List.of());
        assertFalse(haInforme.haCumple());
    }

    private static HAComprobacion.HAEstado haEstado(HAInformeVerificacion haI, String haNombre) {
        return haI.haComprobaciones().stream().filter(haC -> haC.haNombre().equals(haNombre)).findFirst().orElseThrow().haEstado();
    }

    private File haPdf(String haNombre, int haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int haI = 1; haI <= haPaginas; haI++) {
                PDPage haPagina = new PDPage(PDRectangle.A4);
                haDoc.addPage(haPagina);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, 12);
                    haCs.newLineAtOffset(72, 700);
                    haCs.showText(haNombre + " contenido de la hoja " + haI + " con texto suficiente");
                    haCs.endText();
                }
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
