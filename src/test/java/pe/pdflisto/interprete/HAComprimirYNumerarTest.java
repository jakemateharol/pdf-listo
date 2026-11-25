package pe.pdflisto.interprete;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HAPesoPaso;
import pe.pdflisto.verificacion.HARequisitos;
import pe.pdflisto.verificacion.HAVerificador;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAComprimirYNumerarTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();
    private final HAEjecutorPlan haEjecutor = new HAEjecutorPlan(haMotor, new HACompresor(haMotor));
    private final HAVerificador haVerificador = new HAVerificador(new pe.pdflisto.privacidad.HADetectorDatos(null));

    @TempDir
    Path haCarpeta;

    @Test
    void haComprimirSeHaceDespuesDeNumerar() {
        HAInterpretacion haPlan = new HAInterpretePedidos().haInterpretar("comprime y numera", new HAInterpretePedidos.HAContexto(12, List.of()));
        List<HAPaso> haPasos = haPlan.haPasos();
        assertInstanceOf(HAPaso.HANumerar.class, haPasos.get(0), haPasos.toString());
        assertInstanceOf(HAPaso.HAComprimir.class, haPasos.get(haPasos.size() - 1), "comprimir va al final");
    }

    @Test
    void haSiPesaMasQueElOriginalSaleRevisarConExplicacion() throws Exception {
        File haOriginal = new File("ejemplos/demo-12-paginas.pdf");
        File haCopia = haCarpeta.resolve("demo.pdf").toFile();
        Files.copy(haOriginal.toPath(), haCopia.toPath());
        HAInterpretacion haPlan = new HAInterpretePedidos().haInterpretar("comprime y numera", new HAInterpretePedidos.HAContexto(12, List.of()));
        HARequisitos haReq = haEjecutor.haRequisitos(haPlan.haPasos(), 12, 0, haCopia.length());
        assertTrue(haReq.haComprimir());

        List<HAPesoPaso> haPesos = new ArrayList<>();
        File haSalida = haCarpeta.resolve("resultado.pdf").toFile();
        haEjecutor.haEjecutar(haPlan.haPasos(), haCopia, null, haSalida, List.of(), pe.pdflisto.operaciones.HAControl.haNinguno(), haPesos);
        assertEquals(haPlan.haPasos().size(), haPesos.size(), "un peso por paso");

        HAInformeVerificacion haInforme = haVerificador.haVerificar(haSalida, null, haReq.haConPesosPorPaso(haPesos), "demo.pdf",
                "comprime y numera", List.of());
        HAComprobacion haPeso = haInforme.haComprobaciones().stream().filter(haC -> haC.haNombre().equals("Peso")).findFirst().orElseThrow();
        if (haSalida.length() > haCopia.length()) {
            assertEquals(HAComprobacion.HAEstado.REVISAR, haPeso.haEstado(), haPeso.haDetalle());
            assertFalse(haInforme.haCumple(), "el informe no puede decir «Cumple»");
            assertTrue(haPeso.haDetalle().contains("Se pidió comprimir"), haPeso.haDetalle());
            assertTrue(haPeso.haDetalle().contains("agregaron peso"), "dice qué paso agregó peso: " + haPeso.haDetalle());
        } else {
            assertNotEquals(HAComprobacion.HAEstado.REVISAR, haPeso.haEstado(), haPeso.haDetalle());
        }
    }

    @Test
    void haExplicacionDelAumento() {
        HARequisitos haReq = new HARequisitos(null, null, null, null, 5_000, false, false, true, List.of(
                new HAPesoPaso("Numerar páginas", 5_000, 18_000, false),
                new HAPesoPaso("Comprimir", 18_000, 18_000, true)));
        HAInformeVerificacion haInforme = haVerificador.haVerificar(new File("no-existe.pdf"), null, haReq, "x", "x", List.of());
        assertFalse(haInforme.haCumple());
        HAComprobacion haC = haPesoDe(18_000, haReq);
        assertEquals(HAComprobacion.HAEstado.REVISAR, haC.haEstado());
        assertTrue(haC.haDetalle().contains("ya estaba optimizado"), haC.haDetalle());
        assertTrue(haC.haDetalle().contains("agregaron peso: Numerar páginas (+13"), haC.haDetalle());
    }

    @Test
    void haSiSeReduceCumple() {
        HARequisitos haReq = new HARequisitos(null, null, null, null, 10_000, false, false, true, List.of());
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haPesoDe(6_000, haReq).haEstado());
        HARequisitos haSinComprimir = new HARequisitos(null, null, null, null, 10_000, false, false);
        assertEquals(HAComprobacion.HAEstado.INFO, haPesoDe(16_000, haSinComprimir).haEstado());
    }

    @Test
    void haConLimiteCumplidoIgualSaleRevisarSiPesaMas() {
        HARequisitos haReq = new HARequisitos(2_000_000L, null, null, null, 5_000, false, false, true, List.of(
                new HAPesoPaso("Numerar páginas", 5_000, 18_000, false), new HAPesoPaso("Comprimir", 18_000, 18_000, true)));
        HAComprobacion haC = haPesoDe(18_000, haReq);
        assertEquals(HAComprobacion.HAEstado.REVISAR, haC.haEstado(), haC.haDetalle());
        assertTrue(haC.haDetalle().contains("agregaron peso"), haC.haDetalle());
        assertTrue(haC.haDetalle().contains("por debajo del límite"), haC.haDetalle());
        assertEquals(HAComprobacion.HAEstado.CUMPLE, haPesoDe(4_000, haReq).haEstado());
    }

    private static HAComprobacion haPesoDe(long haBytes, HARequisitos haReq) {
        return pe.pdflisto.verificacion.HAVerificadorPruebas.haPeso(haBytes, haReq);
    }
}
