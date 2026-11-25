package pe.pdflisto.verificacion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.anotaciones.HAAnotacion;
import pe.pdflisto.anotaciones.HAAnotar;
import pe.pdflisto.formularios.HACampoFormulario;
import pe.pdflisto.formularios.HALectorFormulario;
import pe.pdflisto.formularios.HARellenarFormulario;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAEncabezadoPie;
import pe.pdflisto.operaciones.HAMarcaDeAgua;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HANumerar;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HAReorganizar;
import pe.pdflisto.operaciones.HAResultado;
import pe.pdflisto.operaciones.HARotar;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HALimpiarMetadatos;
import pe.pdflisto.privacidad.HAProteger;
import pe.pdflisto.privacidad.HAQuitarContrasena;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class HAVerificadorOperacionTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HAVerificadorOperacion haVerificador =
            new HAVerificadorOperacion(new HAAnalizadorPrivacidad(new HADetectorDatos(null)));

    @TempDir
    Path haCarpeta;

    private int haContador;

    @Test
    void haCadaOperacionDePaginasQuedaVerificada() throws Exception {
        File haPdf = haPdfConTexto("base.pdf", "Uno primera pagina de prueba", "Dos segunda pagina de prueba",
                "Tres tercera pagina de prueba", "Cuatro cuarta pagina de prueba");
        File haOtro = haPdfConTexto("otro.pdf", "Anexo de prueba con texto");

        haCumple(haPdf, null, new HARotar(List.of(1, 3), 90));
        haCumple(haPdf, null, new HAReorganizar(List.of(3, 0, 0, 1), "mover, duplicar y eliminar"));
        haCumple(haPdf, null, new HAUnir(List.of(new HAUnir.HAFuente("actual", null, null, true),
                new HAUnir.HAFuente("otro", haOtro, null, false))));
        haCumple(haPdf, null, new HANumerar("Página {n} de {total}", HAPosicion.ABAJO_DERECHA, 2, 1, 11));
        haCumple(haPdf, null, new HAMarcaDeAgua("BORRADOR", null, Color.GRAY, 0.3f, true, List.of(0, 2)));
        haCumple(haPdf, null, new HACambiarTamano(HATamanoPapel.CARTA, HACambiarTamano.HAOrientacion.HORIZONTAL));
        haCumple(haPdf, null, new HAEncabezadoPie(new String[]{"Tesis {archivo}", "", "{fecha}"},
                new String[]{"", "Hoja {pagina} de {total}", ""}, 2, 9, "informe"));
    }

    @Test
    void haCompresionContrasenaYMetadatosQuedanVerificados() throws Exception {
        File haPesado = haPdfConFoto("foto.pdf");
        HACompresor haCompresor = new HACompresor(haMotor);
        HAResultado haR = haCompresor.haComprimir(haPesado, null, HANivelCompresion.FUERTE, haSalida());
        assertNotNull(haR.haEsperado(), "la compresión debe declarar qué se espera");
        assertTrue(haInforme(haPesado, null, haR).haCumple(), () -> haTexto(haInforme(haPesado, null, haR)));

        File haPdf = haPdfConMetadatos("metadatos.pdf");
        haCumple(haPdf, null, new HALimpiarMetadatos(HALimpiarMetadatos.HAOpciones.haTodo()));

        HAResultado haProtegido = haMotor.haEjecutar(haPdf, null, new HAProteger("1234", "dueno", true, true, true), haSalida());
        HAInformeVerificacion haIProteger = haInforme(haPdf, null, haProtegido);
        assertTrue(haIProteger.haCumple(), () -> haTexto(haIProteger));
        assertEquals(HAEstado.CUMPLE, haFila(haIProteger, "Contraseña").haEstado());

        HAResultado haSinClave = haMotor.haEjecutar(haProtegido.haArchivo(), "1234", new HAQuitarContrasena(), haSalida());
        HAInformeVerificacion haIQuitar = haInforme(haProtegido.haArchivo(), "1234", haSinClave);
        assertTrue(haIQuitar.haCumple(), () -> haTexto(haIQuitar));
    }

    @Test
    void haAnotacionesTextoYSelloQuedanVerificados() throws Exception {
        File haPdf = haPdfConTexto("anotar.pdf", "Contrato de alquiler de prueba", "Segunda hoja del contrato");
        List<HAAnotacion> haLista = List.of(
                new HAAnotacion.HAMarcado(HAAnotacion.HATipoMarcado.RESALTADO, 0, List.of(new HAAreaTexto(60, 90, 120, 14)),
                        Color.YELLOW, null),
                new HAAnotacion.HANota(0, 300, 300, "Revisar la cláusula", Color.ORANGE),
                new HAAnotacion.HATexto(1, 80, 200, "Visto bueno", 12, Color.BLUE),
                new HAAnotacion.HASello(1, new HAAreaTexto(300, 400, 150, 60), "Aprobado", Color.RED, "24/11/2026"));
        haCumple(haPdf, null, new HAAnotar(haLista, "Prueba"));
    }

    @Test
    void haFormularioLlenoYAplanadoQuedanVerificados() throws Exception {
        File haFormulario = new File("ejemplos/demo-formulario.pdf");
        assertTrue(haFormulario.exists(), "falta el PDF de ejemplo con formulario");
        Map<String, String> haValores = new LinkedHashMap<>();
        try (PDDocument haD = Loader.loadPDF(haFormulario)) {
            for (HACampoFormulario haC : HALectorFormulario.haLeer(haD).haCampos()) {
                if (haC.haSoloLectura()) {
                    continue;
                }
                if (haC.haTipo() == HACampoFormulario.HATipo.TEXTO) {
                    haValores.put(haC.haNombre(), "Valor " + haC.haNombre().length());
                } else if (haC.haTipo() == HACampoFormulario.HATipo.CASILLA) {
                    haValores.put(haC.haNombre(), "true");
                }
            }
        }
        assertFalse(haValores.isEmpty());
        haCumple(haFormulario, null, new HARellenarFormulario(haValores, false));
        haCumple(haFormulario, null, new HARellenarFormulario(haValores, true));
    }

    @Test
    void haLasPaginasEnBlancoNoCuentanComoTextoQueFalta() throws Exception {
        File haPdf = haCarpeta.resolve("con-blanca.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            haAgregarPaginaConTexto(haD, "Esta pagina tiene texto suficiente para buscar");
            haD.addPage(new PDPage(PDRectangle.A4));
            haD.save(haPdf);
        }
        HAInformeVerificacion haI = haVerificador.haVerificar("prueba", haPdf, null, haPdf, null,
                List.of(new HAEsperado.HATextoBuscable()), "doc");
        assertEquals(HAEstado.CUMPLE, haFila(haI, "Texto buscable").haEstado(), () -> haTexto(haI));
    }

    @Test
    void haDiceRevisarCuandoElResultadoNoHizoLoPrometido() throws Exception {
        File haPdf = haPdfConTexto("base.pdf", "Uno primera pagina", "Dos segunda pagina", "Tres tercera pagina");
        File haRotado = haMotor.haEjecutar(haPdf, null, new HARotar(List.of(0), 90), haSalida()).haArchivo();

        haAssertRevisar(haPdf, haRotado, new HAEsperado.HARotacion(List.of(1), 90), "Rotación");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HAOrden(List.of(2, 1, 0)), "Orden de páginas");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HANumeracion(new HARequisitos.HANumeracion("{n}", 1, 1, false)), "Numeración");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HATexto("Marca de agua", List.of("CONFIDENCIAL"), null), "Marca de agua");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HAPeso(1024L), "Peso");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HAContrasena(true), "Contraseña");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HATamano(HATamanoPapel.CARTA, HACambiarTamano.HAOrientacion.VERTICAL),
                "Tamaño de página");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HAAnotaciones(Map.of(0, 1)), "Anotaciones");
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HAContenidoCambiado("Firma", List.of(1)), "Firma");
        File haSinUna = haMotor.haEjecutar(haPdf, null, new HAReorganizar(List.of(0, 1), "eliminar"), haSalida()).haArchivo();
        haAssertRevisar(haPdf, haSinUna, new HAEsperado.HAMismasPaginas(), "Número de páginas");
    }

    @Test
    void haMetadatosQueSiguenAhiDanRevisar() throws Exception {
        File haPdf = haPdfConMetadatos("meta.pdf");
        File haRotado = haMotor.haEjecutar(haPdf, null, new HARotar(List.of(0), 180), haSalida()).haArchivo();
        haAssertRevisar(haPdf, haRotado, new HAEsperado.HASinMetadatos(true, true, true, true, true), "Metadatos");
    }

    @Test
    void haUnEscaneoSinTextoDaRevisar() throws Exception {
        File haPdf = haPdfConFoto("escaneo.pdf");
        HAInformeVerificacion haI = haVerificador.haVerificar("prueba", haPdf, null, haPdf, null,
                List.of(new HAEsperado.HATextoBuscable()), "doc");
        assertEquals(HAEstado.REVISAR, haFila(haI, "Texto buscable").haEstado(), () -> haTexto(haI));
    }

    @Test
    void haUnResultadoQueNoAbreDaRevisarSinLanzarError() throws Exception {
        File haPdf = haPdfConTexto("bien.pdf", "Hola mundo de prueba");
        File haRoto = haCarpeta.resolve("roto.pdf").toFile();
        Files.writeString(haRoto.toPath(), "esto no es un PDF");
        HAInformeVerificacion haI = haVerificador.haVerificar("prueba", haPdf, null, haRoto, null,
                List.of(new HAEsperado.HAMismasPaginas()), "doc");
        assertFalse(haI.haCumple());
        assertEquals(HAEstado.REVISAR, haFila(haI, "Integridad").haEstado());
    }

    @Test
    void haElMotorAdjuntaLoEsperadoPeroUnPlanNo() throws Exception {
        File haPdf = haPdfConTexto("motor.pdf", "Texto de prueba");
        HAResultado haR = haMotor.haEjecutar(haPdf, null, new HARotar(List.of(0), 90), haSalida());
        assertEquals(List.of(new HAEsperado.HARotacion(List.of(0), 90)), haR.haEsperado());
        assertNull(HAResultado.haDe(haPdf, 1).haEsperado());
    }

    private void haCumple(File haEntrada, String haClave, HAOperacionPdf haOp) {
        HAResultado haR = haMotor.haEjecutar(haEntrada, haClave, haOp, haSalida());
        HAInformeVerificacion haI = haInforme(haEntrada, haClave, haR);
        assertTrue(haI.haCumple(), () -> haOp.haDescripcion() + " debería cumplir:\n" + haTexto(haI));
        assertTrue(haI.haComprobaciones().stream().anyMatch(haC -> haC.haEstado() == HAEstado.CUMPLE
                        && !haC.haNombre().equals("Integridad")),
                () -> haOp.haDescripcion() + ": además de la integridad, algo propio de la operación debe quedar comprobado:\n" + haTexto(haI));
    }

    private HAInformeVerificacion haInforme(File haEntrada, String haClave, HAResultado haR) {
        return haVerificador.haVerificar("prueba", haEntrada, haClave, haR.haArchivo(), haR.haContrasena(), haR.haEsperado(), "doc");
    }

    private void haAssertRevisar(File haAntes, File haDespues, HAEsperado haEsperado, String haNombre) {
        HAInformeVerificacion haI = haVerificador.haVerificar("prueba", haAntes, null, haDespues, null, List.of(haEsperado), "doc");
        assertFalse(haI.haCumple(), () -> haNombre + " debería dar Revisar:\n" + haTexto(haI));
        assertEquals(HAEstado.REVISAR, haFila(haI, haNombre).haEstado(), () -> haTexto(haI));
    }

    private static HAComprobacion haFila(HAInformeVerificacion haI, String haNombre) {
        return haI.haComprobaciones().stream().filter(haC -> haC.haNombre().equals(haNombre)).findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila «" + haNombre + "»:\n" + haTexto(haI)));
    }

    private static String haTexto(HAInformeVerificacion haI) {
        StringBuilder haSb = new StringBuilder();
        haI.haComprobaciones().forEach(haC -> haSb.append(haC.haNombre()).append(" · ").append(haC.haEstado()).append(": ")
                .append(haC.haDetalle()).append('\n'));
        return haSb.toString();
    }

    private File haSalida() {
        return haCarpeta.resolve("salida-" + (++haContador) + ".pdf").toFile();
    }

    private File haPdfConTexto(String haNombre, String... haPaginas) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haD = new PDDocument()) {
            for (String haT : haPaginas) {
                haAgregarPaginaConTexto(haD, haT);
            }
            haD.save(haF);
        }
        return haF;
    }

    private static void haAgregarPaginaConTexto(PDDocument haD, String haTexto) throws IOException {
        PDPage haP = new PDPage(PDRectangle.A4);
        haD.addPage(haP);
        try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
            haCs.beginText();
            haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
            haCs.newLineAtOffset(60, 740);
            haCs.showText(haTexto);
            haCs.endText();
        }
    }

    private File haPdfConMetadatos(String haNombre) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haD = new PDDocument()) {
            haAgregarPaginaConTexto(haD, "Documento con autor y comentarios");
            PDDocumentInformation haInfo = new PDDocumentInformation();
            haInfo.setAuthor("Autor de prueba");
            haInfo.setCreator("Programa de prueba");
            haD.setDocumentInformation(haInfo);
            PDAnnotationText haNota = new PDAnnotationText();
            haNota.setRectangle(new PDRectangle(100, 600, 20, 20));
            haNota.setContents("Comentario privado");
            haD.getPage(0).getAnnotations().add(haNota);
            haD.save(haF);
        }
        return haF;
    }

    private File haPdfConFoto(String haNombre) throws IOException {
        File haF = haCarpeta.resolve(haNombre).toFile();
        BufferedImage haImg = new BufferedImage(900, 1200, BufferedImage.TYPE_INT_RGB);
        Random haR = new Random(7);
        for (int haY = 0; haY < haImg.getHeight(); haY++) {
            for (int haX = 0; haX < haImg.getWidth(); haX++) {
                int haG = 60 + haR.nextInt(120);
                haImg.setRGB(haX, haY, (haG << 16) | (haG << 8) | haG);
            }
        }
        try (PDDocument haD = new PDDocument()) {
            PDPage haP = new PDPage(PDRectangle.A4);
            haD.addPage(haP);
            try (PDPageContentStream haCs = new PDPageContentStream(haD, haP)) {
                haCs.drawImage(LosslessFactory.createFromImage(haD, haImg), 0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            haD.save(haF);
        }
        return haF;
    }
}
