package pe.pdflisto;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import pe.pdflisto.ocr.HAReconocerTexto;
import pe.pdflisto.operaciones.HACambiarTamano;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HADivisor;
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
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HAAreaTachado;
import pe.pdflisto.privacidad.HADatoSensible;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HALimpiarMetadatos;
import pe.pdflisto.privacidad.HATachar;
import pe.pdflisto.privacidad.HAVerificadorTachado;
import pe.pdflisto.verificacion.HAEsperadoArchivos;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HAVerificadorArchivos;
import pe.pdflisto.verificacion.HAVerificadorOperacion;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HACorpusTest {
    static final Path HA_CORPUS = Path.of("corpus-pdf");
    static final Path HA_INFORME = Path.of("target", "corpus-informe.txt");

    private static final HAMotorPdf HA_MOTOR = new HAMotorPdf();
    private static final HADetectorDatos HA_DETECTOR = new HADetectorDatos(null);
    private static final HAVerificadorOperacion HA_VERIFICADOR = new HAVerificadorOperacion(new HAAnalizadorPrivacidad(HA_DETECTOR));
    private static final List<String> HA_LINEAS = Collections.synchronizedList(new ArrayList<>());

    @TestFactory
    Stream<DynamicNode> haCadaPdfDelCorpusCumple() throws IOException {
        List<Path> haPdfs = haBuscarPdfs();
        assumeTrue(!haPdfs.isEmpty(), "corpus-pdf/ no tiene PDF: pon ahí tus PDF reales para probarlos");
        Path haTrabajo = Files.createDirectories(Path.of("target", "corpus-trabajo"));
        return haPdfs.stream().map(haPdf -> {
            String haNombre = HA_CORPUS.relativize(haPdf).toString();
            File haOriginal = haPdf.toFile();
            int haPaginas;
            try (PDDocument haD = Loader.loadPDF(haOriginal)) {
                haPaginas = haD.getNumberOfPages();
            } catch (InvalidPasswordException haE) {
                HA_LINEAS.add(haNombre + " | (todas) | NO PROBADO | tiene contraseña para abrir");
                return DynamicContainer.dynamicContainer(haNombre, Stream.of(
                        DynamicTest.dynamicTest("tiene contraseña: no probado", () -> assumeTrue(false, "PDF con contraseña"))));
            } catch (IOException haE) {
                HA_LINEAS.add(haNombre + " | abrir | REVISAR | no se pudo abrir: " + haE.getMessage());
                return DynamicContainer.dynamicContainer(haNombre, Stream.of(DynamicTest.dynamicTest("abrir",
                        () -> fail("PDF Listo no pudo abrir " + haNombre + ": " + haE.getMessage()))));
            }
            Path haCarpeta = haTrabajo.resolve(haNombre.replaceAll("[\\\\/:]", "_"));
            String haHuella = haHuella(haPdf);
            DynamicTest haIntacto = DynamicTest.dynamicTest("El original no se modificó", () -> {
                boolean haIgual = haHuella.equals(haHuella(haPdf));
                HA_LINEAS.add(haNombre + " | El original no se modificó | " + (haIgual ? "CUMPLE" : "REVISAR") + " | ");
                assertTrue(haIgual, haNombre + ": ¡el original cambió!");
            });
            return DynamicContainer.dynamicContainer(haNombre + " (" + haPaginas + " págs.)",
                    Stream.concat(haOperaciones(haNombre, haOriginal, haPaginas, haCarpeta), Stream.of(haIntacto)));
        }).map(haN -> (DynamicNode) haN);
    }

    private Stream<DynamicNode> haOperaciones(String haNombre, File haPdf, int haN, Path haCarpeta) {
        List<Integer> haTodasAlReves = new ArrayList<>();
        for (int haI = haN - 1; haI >= 0; haI--) {
            haTodasAlReves.add(haI);
        }
        haTodasAlReves.add(haN - 1);
        List<DynamicNode> haPruebas = new ArrayList<>();
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HARotar(List.of(0), 90)));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HAReorganizar(haTodasAlReves, "invertir el orden y duplicar")));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HANumerar("Página {n} de {total}", HAPosicion.ABAJO_DERECHA, 1, 1, 11)));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HAMarcaDeAgua("CONFIDENCIAL", null, Color.GRAY, 0.3f, true, null)));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HACambiarTamano(HATamanoPapel.A4, HACambiarTamano.HAOrientacion.MANTENER)));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HAEncabezadoPie(new String[]{"{archivo}", "", "{fecha}"},
                new String[]{"", "Hoja {pagina} de {total}", ""}, 1, 9, "corpus")));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HALimpiarMetadatos(HALimpiarMetadatos.HAOpciones.haTodo())));
        haPruebas.add(haOperacion(haNombre, haPdf, haCarpeta, new HAUnir(List.of(new HAUnir.HAFuente("actual", null, null, true),
                new HAUnir.HAFuente("copia", haPdf, null, false)))));
        haPruebas.add(DynamicTest.dynamicTest("Comprimir", () -> {
            HAResultado haR = new HACompresor(HA_MOTOR).haComprimir(haPdf, null, HANivelCompresion.MEDIA, haSalida(haCarpeta, "comprimir"));
            haExigir(haNombre, "Comprimir", haPdf, haR);
        }));
        haPruebas.add(DynamicTest.dynamicTest("Extraer la primera página", () -> {
            File haDestino = haSalida(haCarpeta, "extraer");
            new HADivisor(HA_MOTOR).haExtraer(haPdf, null, List.of(0), haDestino);
            HAInformeVerificacion haI = new HAVerificadorArchivos().haVerificar("Extraer", List.of(haDestino),
                    new HAEsperadoArchivos.HAPartes(haPdf, null, List.of(List.of(0))));
            haAnotarYExigir(haNombre, "Extraer la primera página", haI);
        }));
        haPruebas.add(DynamicTest.dynamicTest("Reconocer texto (OCR)", () -> {
            HAResultado haR;
            try {
                haR = HA_MOTOR.haEjecutar(haPdf, null, new HAReconocerTexto(HAReconocerTexto.HAOpciones.haBasicas()), haSalida(haCarpeta, "ocr"));
            } catch (pe.pdflisto.exception.HAPdfListoException haE) {
                if (haE.getMessage() != null && haE.getMessage().contains("ya tienen texto")) {
                    HA_LINEAS.add(haNombre + " | Reconocer texto (OCR) | NO APLICA | todas las páginas ya tienen texto");
                    return;
                }
                throw haE;
            }
            haExigir(haNombre, "Reconocer texto (OCR)", haPdf, haR);
        }));
        haPruebas.add(DynamicTest.dynamicTest("Tachar datos sensibles", () -> haTachar(haNombre, haPdf, haCarpeta)));
        return haPruebas.stream();
    }

    private DynamicTest haOperacion(String haNombre, File haPdf, Path haCarpeta, HAOperacionPdf haOp) {
        return DynamicTest.dynamicTest(haOp.haDescripcion(), () -> {
            HAResultado haR = HA_MOTOR.haEjecutar(haPdf, null, haOp, haSalida(haCarpeta, haOp.getClass().getSimpleName()));
            haExigir(haNombre, haOp.haDescripcion(), haPdf, haR);
        });
    }

    private void haTachar(String haNombre, File haPdf, Path haCarpeta) {
        List<HADatoSensible> haDatos = HA_DETECTOR.haDetectar(haPdf, null).stream().filter(HADatoSensible::haProbable).toList();
        if (haDatos.isEmpty()) {
            HA_LINEAS.add(haNombre + " | Tachar datos sensibles | NO APLICA | no se encontraron datos sensibles");
            return;
        }
        List<HAAreaTachado> haAreas = haDatos.stream()
                .flatMap(haD -> haD.haAreas().stream().map(haA -> new HAAreaTachado(haD.haPagina(), haA).haAmpliada(1.5))).toList();
        File haSalida = haSalida(haCarpeta, "tachar");
        HA_MOTOR.haEjecutar(haPdf, null, new HATachar(haAreas, true), haSalida);
        HAVerificadorTachado.HAInforme haInforme = new HAVerificadorTachado().haVerificar(haSalida, null, haAreas, haDatos);
        String haDetalle = haDatos.size() + " datos tachados" + (haInforme.haSeguro() ? "" : ": " + haInforme.haProblemas());
        HA_LINEAS.add(haNombre + " | Tachar datos sensibles | " + (haInforme.haSeguro() ? "CUMPLE" : "REVISAR") + " | " + haDetalle);
        assertTrue(haInforme.haSeguro(), haNombre + ": el tachado no quedó seguro: " + haInforme.haProblemas());
    }

    private static void haExigir(String haNombre, String haOperacion, File haPdf, HAResultado haR) {
        if (haR.haSinCambios()) {
            HA_LINEAS.add(haNombre + " | " + haOperacion + " | SIN CAMBIOS | " + haR.haDetalle());
            return;
        }
        HAInformeVerificacion haI = HA_VERIFICADOR.haVerificar(haOperacion, haPdf, null, haR.haArchivo(), haR.haContrasena(), haR.haEsperado(), haNombre);
        haAnotarYExigir(haNombre, haOperacion, haI);
    }

    private static void haAnotarYExigir(String haNombre, String haOperacion, HAInformeVerificacion haI) {
        String haProblemas = haI.haComprobaciones().stream()
                .filter(haC -> haC.haEstado() == pe.pdflisto.verificacion.HAComprobacion.HAEstado.REVISAR)
                .map(haC -> haC.haNombre() + ": " + haC.haDetalle()).reduce((haA, haB) -> haA + "; " + haB).orElse("");
        HA_LINEAS.add(haNombre + " | " + haOperacion + " | " + (haI.haCumple() ? "CUMPLE" : "REVISAR") + " | " + haProblemas);
        assertTrue(haI.haCumple(), haNombre + " → «" + haOperacion + "» no cumple: " + haProblemas);
    }

    private static File haSalida(Path haCarpeta, String haNombre) {
        try {
            Files.createDirectories(haCarpeta);
        } catch (IOException haE) {
            throw new java.io.UncheckedIOException(haE);
        }
        return haCarpeta.resolve(haNombre + ".pdf").toFile();
    }

    private static String haHuella(Path haArchivo) {
        try {
            byte[] haH = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(haArchivo));
            return java.util.HexFormat.of().formatHex(haH);
        } catch (IOException | java.security.NoSuchAlgorithmException haE) {
            throw new IllegalStateException(haE);
        }
    }

    static List<Path> haBuscarPdfs() throws IOException {
        if (!Files.isDirectory(HA_CORPUS)) {
            return List.of();
        }
        try (Stream<Path> haTodo = Files.walk(HA_CORPUS)) {
            return haTodo.filter(Files::isRegularFile)
                    .filter(haP -> haP.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted().toList();
        }
    }

    @AfterAll
    static void haEscribirInforme() throws IOException {
        if (HA_LINEAS.isEmpty()) {
            return;
        }
        List<String> haLineas = new ArrayList<>(HA_LINEAS);
        long haRevisar = haLineas.stream().filter(haL -> haL.contains("| REVISAR |")).count();
        long haCumple = haLineas.stream().filter(haL -> haL.contains("| CUMPLE |")).count();
        StringBuilder haSb = new StringBuilder("Prueba con corpus-pdf/ — "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) + "\n");
        haSb.append("Cumple: ").append(haCumple).append(" · Revisar: ").append(haRevisar).append(" · Otros (sin cambios / no aplica / no probado): ")
                .append(haLineas.size() - haCumple - haRevisar).append("\n\nPDF | operación | resultado | detalle\n");
        haLineas.forEach(haL -> haSb.append(haL).append('\n'));
        Files.createDirectories(HA_INFORME.getParent());
        Files.writeString(HA_INFORME, haSb, StandardCharsets.UTF_8);
    }
}
