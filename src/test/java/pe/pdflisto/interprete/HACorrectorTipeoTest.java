package pe.pdflisto.interprete;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HACorrectorTipeoTest {
    private final HAInterpretePedidos haInterprete = new HAInterpretePedidos();
    private final HAInterpretePedidos.HAContexto haDoce = new HAInterpretePedidos.HAContexto(12, List.of());

    @Test
    void haCorrigeErroresClarosYLoAvisa() {
        HAInterpretacion haR = haInterprete.haInterpretar("comprme a menos de 2 MB y nmuera", haDoce);
        assertEquals("comprime a menos de 2 MB y numera", haR.haTextoCorregido());
        assertEquals(List.of(HAAccion.NUMERAR, HAAccion.COMPRIMIR), haR.haPasos().stream().map(HAPaso::haAccion).toList());
        assertTrue(haR.haAvisos().get(0).contains("«comprme» → «comprime»"), haR.haAvisos().toString());
        assertTrue(haR.haPreguntas().isEmpty());
    }

    @Test
    void haLetrasCambiadasDeLugarCuentanComoUnSoloError() {
        HAInterpretacion haR = haInterprete.haInterpretar("Comrpime y elimnia la última página", haDoce);
        assertEquals("Comprime y elimina la última página", haR.haTextoCorregido(), "respeta la mayúscula y las tildes");
        assertEquals(List.of(HAAccion.ELIMINAR, HAAccion.COMPRIMIR), haR.haPasos().stream().map(HAPaso::haAccion).toList());
    }

    @Test
    void haEnLosDudososPreguntaQuisisteDecir() {
        HAInterpretacion haR = haInterprete.haInterpretar("rtoa la página 3 a la derecha", haDoce);
        assertNull(haR.haTextoCorregido());
        assertTrue(haR.haPasos().isEmpty());
        assertEquals(1, haR.haPreguntas().size());
        HAPregunta haP = haR.haPreguntas().get(0);
        assertTrue(haP.haTexto().contains("¿Quisiste decir «rota»?"), haP.haTexto());
        String haAceptado = haP.haOpciones().get(0).haAplicarA("rtoa la página 3 a la derecha");
        assertEquals("rota la página 3 a la derecha", haAceptado);
        HAInterpretacion haDespues = haInterprete.haInterpretar(haAceptado, haDoce);
        assertEquals(List.of(HAAccion.ROTAR), haDespues.haPasos().stream().map(HAPaso::haAccion).toList());

        HAInterpretacion haDos = haInterprete.haInterpretar("cmoprmir el pdf", haDoce);
        assertNull(haDos.haTextoCorregido());
        assertTrue(haDos.haPreguntas().stream().anyMatch(haQ -> haQ.haTexto().contains("comprimir")), haDos.haPreguntas().toString());
    }

    @Test
    void haLaPreguntaSigueEnSuLugarDespuesDeUnaCorreccion() {
        HAInterpretacion haR = haInterprete.haInterpretar("comprme y rtoa la página 2 a la izquierda", haDoce);
        assertEquals("comprime y rtoa la página 2 a la izquierda", haR.haTextoCorregido());
        String haAceptado = haR.haPreguntas().get(0).haOpciones().get(0).haAplicarA(haR.haTextoCorregido());
        assertEquals("comprime y rota la página 2 a la izquierda", haAceptado);
    }

    @Test
    void haNoTocaPalabrasComunesNiTextoEntreComillasNiLoQueYaEntiende() {
        assertTrue(HACorrectorTipeo.haCorregir("pon una nota en todas las páginas").haCorregidos().isEmpty());
        assertTrue(HACorrectorTipeo.haCorregir("pon una nota en todas las páginas").haDudosos().isEmpty(), "«nota» no es «rota»");
        HACorrectorTipeo.HAResultado haComillas = HACorrectorTipeo.haCorregir("marca de agua \"BORRDOR\"");
        assertTrue(haComillas.haCorregidos().isEmpty() && haComillas.haDudosos().isEmpty(), "el texto de la marca es del usuario");
        for (String haFrase : List.of("une, comprime a menos de 2 MB y numera", "numera las páginas desde la 3",
                "elimina la última página", "marca de agua CONFIDENCIAL", "rota la página 5 a la derecha",
                "quita la contraseña", "limpia los metadatos", "cambia el tamaño a A4", "reconoce el texto",
                "numera sin la carátula con el formato página 1 de 12")) {
            HACorrectorTipeo.HAResultado haR = HACorrectorTipeo.haCorregir(haFrase);
            assertEquals(haFrase, haR.haTexto(), haFrase);
            assertTrue(haR.haDudosos().isEmpty(), haFrase + " → " + haR.haDudosos());
        }
    }

    @Test
    void haPalabrasRealesParecidasNoSeCambianSolas() {
        for (String haPalabra : List.of("corta", "fuente", "contrato", "imprime", "cambiar", "ajustar", "aplicar",
                "anterior", "centrar", "invertir", "adjuntar", "partida", "girarlas", "copiar", "medio", "portadas",
                "caratulas", "esquinas", "escaneadas", "escaneados", "centrada", "factura")) {
            HACorrectorTipeo.HAResultado haR = HACorrectorTipeo.haCorregir(haPalabra);
            assertTrue(haR.haCorregidos().isEmpty() && haR.haDudosos().isEmpty(), haPalabra + " → " + haR.haDudosos());
        }
        assertTrue(HACorrectorTipeo.haSoloCambiaLaTerminacion("portadas", "portada"));
        assertFalse(HACorrectorTipeo.haSoloCambiaLaTerminacion("numerq", "numera"), "la q no es una terminación");
        assertEquals("numera todo", haInterprete.haInterpretar("numerq todo", haDoce).haTextoCorregido(),
                "un error al final que no es una terminación sí se corrige");
    }

    @Test
    void haElVocabularioYLasPalabrasComunesNuncaSeCorrigen() {
        for (String haPalabra : HACorrectorTipeo.HA_VOCABULARIO) {
            HACorrectorTipeo.HAResultado haR = HACorrectorTipeo.haCorregir(haPalabra);
            assertTrue(haR.haCorregidos().isEmpty() && haR.haDudosos().isEmpty(), haPalabra);
        }
    }

    @Test
    void haDistanciaDeEdicion() {
        assertEquals(0, HACorrectorTipeo.haDistancia("numera", "numera", 2));
        assertEquals(1, HACorrectorTipeo.haDistancia("nmuera", "numera", 2), "intercambio");
        assertEquals(1, HACorrectorTipeo.haDistancia("comprme", "comprime", 2), "falta una letra");
        assertEquals(1, HACorrectorTipeo.haDistancia("girarr", "girar", 2), "sobra una letra");
        assertEquals(3, HACorrectorTipeo.haDistancia("hola", "comprime", 2), "corta al superar el máximo");
    }

    @Test
    void haSinNadaReconocibleSigueDiciendoQueNoEntendio() {
        HAInterpretacion haR = haInterprete.haInterpretar("hazme un café", haDoce);
        assertTrue(haR.haPasos().isEmpty() && haR.haPreguntas().isEmpty());
        assertTrue(haR.haAvisos().get(0).startsWith("No entendí"));
    }
}
