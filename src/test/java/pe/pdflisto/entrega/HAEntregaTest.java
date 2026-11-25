package pe.pdflisto.entrega;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.interprete.HAAccion;
import pe.pdflisto.interprete.HAEjecutorPlan;
import pe.pdflisto.interprete.HAInterpretePedidos;
import pe.pdflisto.interprete.HAPaso;
import pe.pdflisto.interprete.HASerializadorPasos;
import pe.pdflisto.model.HAPerfilEntrega;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAEncabezadoPie;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HAVerificador;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAEntregaTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HAEjecutorPlan haEjecutor = new HAEjecutorPlan(haMotor, new HACompresor(haMotor));
    private final HAVerificadorPerfil haVerificadorPerfil = new HAVerificadorPerfil(new HAVerificador(new HADetectorDatos(null)));

    @TempDir
    Path haCarpeta;

    @Test
    void haCartaAA4SinPerderNadaYMoviendoLosEnlaces() throws Exception {
        File haCarta = haPdf("carta.pdf", PDRectangle.LETTER, null, "Contenido en hoja carta");
        try (PDDocument haD = Loader.loadPDF(haCarta)) {
            PDAnnotationLink haEnlace = new PDAnnotationLink();
            haEnlace.setRectangle(new PDRectangle(72, 690, 100, 20));
            haD.getPage(0).getAnnotations().add(haEnlace);
            haD.save(haCarta);
        }
        File haA4 = haCarpeta.resolve("a4.pdf").toFile();
        haMotor.haEjecutar(haCarta, null, new HACambiarTamano(HATamanoPapel.A4, HACambiarTamano.HAOrientacion.MANTENER), haA4);
        try (PDDocument haD = Loader.loadPDF(haA4)) {
            PDRectangle haCaja = haD.getPage(0).getMediaBox();
            assertEquals(595.28, haCaja.getWidth(), 0.5);
            assertEquals(841.89, haCaja.getHeight(), 0.5);
            assertTrue(new PDFTextStripper().getText(haD).contains("Contenido en hoja carta"), "el texto sigue siendo texto");
            HALectorTexto.HAPaginaTexto haP = HALectorTexto.haLeerPagina(haD, 0);
            var haArea = haP.haAreas(0, 9).get(0);
            assertTrue(haArea.haX() > 60 && haArea.haX() < 80, "x=" + haArea.haX());
            PDRectangle haR = haD.getPage(0).getAnnotations().get(0).getRectangle();
            assertTrue(haR.getWidth() < 100 && haR.getWidth() > 90, "el enlace se escaló con la página");
        }
    }

    @Test
    void haHorizontalSeMantieneHorizontal() throws Exception {
        File haApaisado = haPdf("apaisado.pdf", new PDRectangle(792, 612), null, "Hoja horizontal");
        File haSalida = haCarpeta.resolve("apaisado-a4.pdf").toFile();
        haMotor.haEjecutar(haApaisado, null, new HACambiarTamano(HATamanoPapel.A4, HACambiarTamano.HAOrientacion.MANTENER), haSalida);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            assertEquals(841.89, haD.getPage(0).getMediaBox().getWidth(), 0.5);
            assertEquals(595.28, haD.getPage(0).getMediaBox().getHeight(), 0.5);
        }
    }

    @Test
    void haEncabezadoYPieConVariables() throws Exception {
        File haBase = haPdf("base.pdf", PDRectangle.A4, null, "Uno", "Dos", "Tres");
        File haSalida = haCarpeta.resolve("encabezado.pdf").toFile();
        haMotor.haEjecutar(haBase, null, new HAEncabezadoPie(new String[]{"{archivo}", "", ""},
                new String[]{"", "", "Página {pagina} de {total}"}, 2, 9, "Informe anual"), haSalida);
        try (PDDocument haD = Loader.loadPDF(haSalida)) {
            PDFTextStripper haT = new PDFTextStripper();
            haT.setStartPage(1);
            haT.setEndPage(1);
            assertFalse(haT.getText(haD).contains("Informe anual"), "desde la página 2");
            haT.setStartPage(3);
            haT.setEndPage(3);
            String haP3 = haT.getText(haD);
            assertTrue(haP3.contains("Informe anual") && haP3.contains("Página 3 de 3"), haP3);
        }
        assertThrows(HAPdfListoException.class, () -> haMotor.haEjecutar(haBase, null,
                new HAEncabezadoPie(new String[]{"", "", ""}, new String[]{"", "", ""}, 1, 9, "x"), haCarpeta.resolve("x.pdf").toFile()));
    }

    @Test
    void haLasRecetasSeGuardanComoTextoYVuelvenIguales() {
        List<HAPaso> haPasos = List.of(
                new HAPaso.HARotar("1,u", 90), new HAPaso.HAEliminar("2-3"),
                new HAPaso.HATamano(HATamanoPapel.CARTA, HACambiarTamano.HAOrientacion.VERTICAL), new HAPaso.HAOcr(),
                new HAPaso.HAMarcaAgua("USO INTERNO, NO DIFUNDIR", true), new HAPaso.HANumerar("Folio {n}", HAPosicion.ARRIBA_DERECHA, 2),
                new HAPaso.HALimpiarMetadatos(), new HAPaso.HAQuitarClave(), new HAPaso.HAComprimir(HANivelCompresion.MEDIA, 5L * 1024 * 1024),
                new HAPaso.HAComprimir(HANivelCompresion.FUERTE, null));
        String haCodigo = HASerializadorPasos.haCodificar(haPasos);
        assertEquals(haPasos, HASerializadorPasos.haDecodificar(haCodigo, List.of()), haCodigo);
        assertEquals(List.of(new HAPaso.HAUnir(List.of("anexo.pdf"))), HASerializadorPasos.haDecodificar("UNIR", List.of("anexo.pdf")));
        assertThrows(HAPdfListoException.class, () -> HASerializadorPasos.haDecodificar("VOLAR:alto=10", List.of()));
        assertEquals("Rotar + Eliminar páginas", HASerializadorPasos.haResumen(haPasos.subList(0, 2)));
    }

    @Test
    void haElInterpreteEntiendeTamanoYQuitarContrasena() {
        HAInterpretePedidos haI = new HAInterpretePedidos();
        var haCtx = new HAInterpretePedidos.HAContexto(5, List.of());
        var haR = haI.haInterpretar("pásalo a tamaño A4 horizontal y quita la contraseña", haCtx);
        assertEquals(List.of(HAAccion.CAMBIAR_TAMANO, HAAccion.QUITAR_CONTRASENA), haR.haPasos().stream().map(HAPaso::haAccion).toList());
        assertEquals(new HAPaso.HATamano(HATamanoPapel.A4, HACambiarTamano.HAOrientacion.HORIZONTAL), haR.haPasos().get(0));
        assertEquals(1, haI.haInterpretar("cambia el tamaño de la hoja", haCtx).haPreguntas().size(), "¿A4 o carta?");
        assertTrue(haI.haInterpretar("escribe una carta formal", haCtx).haPasos().isEmpty(), "«carta» suelta no es un tamaño");
    }

    @Test
    void haPerfilDetectaCorrigeYLuegoCumple() throws Exception {
        File haOriginal = haPdf("propuesta.pdf", PDRectangle.LETTER, "clave", "Propuesta tecnica", "Anexos");
        HAPerfilEntrega haPerfil = new HAPerfilEntrega();
        haPerfil.setNombre("Licitación de prueba");
        haPerfil.setPesoMaximoMb(10.0);
        haPerfil.setTamano(HATamanoPapel.A4);
        haPerfil.setNumerar(true);
        haPerfil.setFormatoNumero("Folio {n}");
        haPerfil.setPosicionNumero(HAPosicion.ARRIBA_DERECHA);
        haPerfil.setSinMetadatos(true);
        haPerfil.setSinContrasena(true);
        haPerfil.setTextoBuscable(true);
        haPerfil.setMaxPaginas(50);

        HAInformeVerificacion haAntes = haVerificadorPerfil.haValidar(haOriginal, "clave", haPerfil, "propuesta.pdf");
        assertFalse(haAntes.haCumple());
        for (String haFalla : List.of("Tamaño de página", "Numeración", "Metadatos", "Contraseña", "Perfil de entrega")) {
            assertEquals(HAComprobacion.HAEstado.REVISAR, haEstado(haAntes, haFalla), haFalla);
        }
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haEstado(haAntes, "Máximo de páginas"));

        HAVerificadorPerfil.HACorreccion haC = haVerificadorPerfil.haCorrecciones(haAntes, haPerfil);
        assertEquals(List.of(HAAccion.CAMBIAR_TAMANO, HAAccion.NUMERAR, HAAccion.LIMPIAR_METADATOS,
                HAAccion.QUITAR_CONTRASENA, HAAccion.COMPRIMIR), haC.haPasos().stream().map(HAPaso::haAccion).toList());

        File haCorregido = haCarpeta.resolve("propuesta-ok.pdf").toFile();
        haEjecutor.haEjecutar(haC.haPasos(), haOriginal, "clave", haCorregido, List.of());
        HAInformeVerificacion haDespues = haVerificadorPerfil.haValidar(haCorregido, null, haPerfil, "propuesta.pdf");
        assertTrue(haDespues.haCumple(), haDespues.haComprobaciones().toString());
    }

    @Test
    void haReglaDelAprendizaje() {
        String haCombinacion = "NUMERAR:formato=%7Bn%7D,posicion=ABAJO_CENTRO,desde=1;COMPRIMIR:nivel=MEDIA,objetivo=-";
        assertTrue(HARecetasService.haDebeOfrecer(haCombinacion, 3, false, false));
        assertFalse(HARecetasService.haDebeOfrecer(haCombinacion, 2, false, false), "solo desde la 3a vez");
        assertFalse(HARecetasService.haDebeOfrecer(haCombinacion, 5, true, false), "ya es receta");
        assertFalse(HARecetasService.haDebeOfrecer(haCombinacion, 5, false, true), "el usuario dijo que no");
        assertFalse(HARecetasService.haDebeOfrecer("OCR", 9, false, false), "un solo paso no es una combinación");
        assertFalse(HARecetasService.haDebeOfrecer(null, 9, false, false));
    }

    @Test
    void haPatronDeNombre() {
        LocalDateTime haT = LocalDateTime.of(2026, 11, 14, 15, 30);
        assertEquals("Tesis - Garcia final.pdf", HAPatronNombre.haAplicar("Tesis - {nombre}", "Garcia final.pdf", null, 80, haT));
        assertEquals("informe - 2026-11-14.pdf", HAPatronNombre.haAplicar("{nombre} - {fecha}", "informe.PDF", null, 3, haT));
        assertEquals("a_b (editado).pdf", HAPatronNombre.haAplicar("", "a/b.pdf", null, 1, haT));
        assertEquals("doc - Licitación - 12 págs.pdf",
                HAPatronNombre.haAplicar("{nombre} - {perfil} - {paginas} págs", "doc.pdf", "Licitación", 12, haT));
    }

    private static HAComprobacion.HAEstado haEstado(HAInformeVerificacion haI, String haNombre) {
        return haI.haComprobaciones().stream().filter(haC -> haC.haNombre().equals(haNombre)).findFirst().orElseThrow().haEstado();
    }

    private File haPdf(String haNombre, PDRectangle haTamano, String haClave, String... haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String haTexto : haPaginas) {
                PDPage haP = new PDPage(haTamano);
                haDoc.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, 14);
                    haCs.newLineAtOffset(72, haTamano.getHeight() - 100);
                    haCs.showText(haTexto + " con suficiente texto para ser buscable");
                    haCs.endText();
                }
            }
            haDoc.getDocumentInformation().setAuthor("Empresa Ficticia SAC");
            if (haClave != null) {
                StandardProtectionPolicy haPol = new StandardProtectionPolicy("dueno-" + haClave, haClave, new AccessPermission());
                haPol.setEncryptionKeyLength(128);
                haDoc.protect(haPol);
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
