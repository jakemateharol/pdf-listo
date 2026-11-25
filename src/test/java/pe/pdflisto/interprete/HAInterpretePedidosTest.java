package pe.pdflisto.interprete;

import org.junit.jupiter.api.Test;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HAPosicion;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAInterpretePedidosTest {
    private final HAInterpretePedidos haInterprete = new HAInterpretePedidos();
    private final HAInterpretePedidos.HAContexto haSolo = new HAInterpretePedidos.HAContexto(12, List.of());
    private final HAInterpretePedidos.HAContexto haConOtros = new HAInterpretePedidos.HAContexto(12, List.of("anexo.pdf"));

    @Test
    void haElEjemploPrincipal() {
        HAInterpretacion haR = haInterprete.haInterpretar("Une, comprime a menos de 2 MB y numera", haConOtros);
        assertTrue(haR.haPreguntas().isEmpty());
        assertEquals(List.of(HAAccion.UNIR, HAAccion.NUMERAR, HAAccion.COMPRIMIR),
                haR.haPasos().stream().map(HAPaso::haAccion).toList(), "comprimir siempre al final");
        HAPaso.HAComprimir haC = (HAPaso.HAComprimir) haR.haPasos().get(2);
        assertEquals(2L * 1024 * 1024, haC.haObjetivoBytes());
    }

    @Test
    void haUnirSinOtrosPdfPregunta() {
        HAInterpretacion haR = haInterprete.haInterpretar("une y numera", haSolo);
        assertEquals(1, haR.haPreguntas().size());
        assertEquals(List.of(HAAccion.NUMERAR), haR.haPasos().stream().map(HAPaso::haAccion).toList());
    }

    @Test
    void haNumeracionConFormatoPosicionYDesde() {
        HAPaso.HANumerar haN = (HAPaso.HANumerar) haInterprete.haInterpretar(
                "numera arriba a la derecha con formato 1 de N desde la página 3", haSolo).haPasos().get(0);
        assertEquals("Página {n} de {total}", haN.haFormato());
        assertEquals(HAPosicion.ARRIBA_DERECHA, haN.haPosicion());
        assertEquals(3, haN.haDesde());

        HAPaso.HANumerar haSinCaratula = (HAPaso.HANumerar) haInterprete.haInterpretar("numera sin la carátula", haSolo).haPasos().get(0);
        assertEquals(2, haSinCaratula.haDesde());
        assertEquals("{n}", haSinCaratula.haFormato());
    }

    @Test
    void haNiveles() {
        assertEquals(HANivelCompresion.FUERTE,
                ((HAPaso.HAComprimir) haInterprete.haInterpretar("comprímelo al máximo", haSolo).haPasos().get(0)).haNivel());
        assertEquals(HANivelCompresion.SUAVE,
                ((HAPaso.HAComprimir) haInterprete.haInterpretar("comprime un poco", haSolo).haPasos().get(0)).haNivel());
        assertEquals(500L * 1024,
                ((HAPaso.HAComprimir) haInterprete.haInterpretar("que pese menos de 500 KB", haSolo).haPasos().get(0)).haObjetivoBytes());
    }

    @Test
    void haRotarYEliminarConPaginas() {
        HAInterpretacion haR = haInterprete.haInterpretar("rota las páginas 2 a 4 a la izquierda y elimina la primera y la última", haSolo);
        HAPaso.HARotar haRot = (HAPaso.HARotar) haR.haPasos().get(0);
        assertEquals("2-4", haRot.haPaginas());
        assertEquals(270, haRot.haGrados());
        HAPaso.HAEliminar haEl = (HAPaso.HAEliminar) haR.haPasos().get(1);
        assertEquals("1,u", haEl.haPaginas());
        assertEquals(List.of(0, 11), HAPaginasPedido.haResolver(haEl.haPaginas(), 12));
    }

    @Test
    void haRota90NoEsLaPagina90() {
        HAPaso.HARotar haR = (HAPaso.HARotar) haInterprete.haInterpretar("rota 90 grados", haSolo).haPasos().get(0);
        assertNull(haR.haPaginas(), "todas las páginas");
        assertEquals(90, haR.haGrados());
    }

    @Test
    void haPreguntaCuandoFaltaUnDato() {
        HAInterpretacion haR = haInterprete.haInterpretar("rota la página 3", haSolo);
        assertTrue(haR.haPasos().isEmpty());
        assertEquals(1, haR.haPreguntas().size());
        String haCompletado = haR.haPreguntas().get(0).haOpciones().get(0).haAplicarA("rota la página 3");
        HAInterpretacion haR2 = haInterprete.haInterpretar(haCompletado, haSolo);
        assertEquals(90, ((HAPaso.HARotar) haR2.haPasos().get(0)).haGrados());

        assertEquals(1, haInterprete.haInterpretar("elimina", haSolo).haPreguntas().size());
        assertEquals(1, haInterprete.haInterpretar("pon marca de agua", haSolo).haPreguntas().size());
    }

    @Test
    void haMarcaDeAguaTomaElTextoDelUsuario() {
        assertEquals("Solo uso interno", ((HAPaso.HAMarcaAgua) haInterprete.haInterpretar(
                "marca de agua \"Solo uso interno\"", haSolo).haPasos().get(0)).haTexto());
        assertEquals("BORRADOR", ((HAPaso.HAMarcaAgua) haInterprete.haInterpretar(
                "ponle marca de agua que diga borrador y numera", haSolo).haPasos().get(0)).haTexto());
        assertEquals("CONFIDENCIAL", ((HAPaso.HAMarcaAgua) haInterprete.haInterpretar(
                "marca de agua confidencial", haSolo).haPasos().get(0)).haTexto());
    }

    @Test
    void haAvisaLoQueNoEstaDisponibleSinInventar() {
        HAInterpretacion haR = haInterprete.haInterpretar("quita los metadatos y tacha los DNI", haSolo);
        assertEquals(List.of(HAAccion.LIMPIAR_METADATOS), haR.haPasos().stream().map(HAPaso::haAccion).toList(),
                "'quita los metadatos' es limpiar, no eliminar páginas");
        assertTrue(haR.haPreguntas().isEmpty());
        assertTrue(haR.haAvisos().stream().anyMatch(haA -> haA.contains("pestaña Tachado")), "tachar siempre con confirmación individual");

        HAInterpretacion haR2 = haInterprete.haInterpretar("quita la marca de agua", haSolo);
        assertTrue(haR2.haPasos().isEmpty());
        assertTrue(haR2.haAvisos().get(0).contains("no es posible"));

        HAInterpretacion haR3 = haInterprete.haInterpretar("hazme un café", haSolo);
        assertTrue(haR3.haPasos().isEmpty());
        assertTrue(haR3.haAvisos().get(0).startsWith("No entendí"));
    }

    @Test
    void haNumerarDesdeUnaPaginaQueNoExiste() {
        HAInterpretacion haR = haInterprete.haInterpretar("numera desde la página 30", haSolo);
        assertTrue(haR.haPasos().isEmpty());
        assertTrue(haR.haAvisos().get(0).contains("tiene 12 páginas"));
    }
}
