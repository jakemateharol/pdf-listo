package pe.pdflisto.controller;

import org.junit.jupiter.api.Test;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HAConfirmacionMetadatosTest {
    private final HAAnalizadorPrivacidad.HAInventario haInv = new HAAnalizadorPrivacidad.HAInventario(
            Map.of("Autor", "Ana", "Programa", "Word"), true, 3, List.of("contrato.docx"),
            0, 0, true, 0, 2, false);

    @Test
    void haLaListaNombraSoloLoMarcadoYLasVersionesAnteriores() {
        List<String> haTodo = HAFormulariosPrivacidad.haQueSeBorra(haInv, true, true, true, true, true);
        assertEquals(6, haTodo.size(), haTodo::toString);
        String haTexto = String.join("\n", haTodo);
        assertTrue(haTexto.contains("Autor"));
        assertTrue(haTexto.contains("XMP"));
        assertTrue(haTexto.contains("3 comentarios"));
        assertTrue(haTexto.contains("contrato.docx"));
        assertTrue(haTexto.contains("JavaScript"));
        assertTrue(haTexto.contains("2 versión(es) anterior(es)"));

        List<String> haSoloComentarios = HAFormulariosPrivacidad.haQueSeBorra(haInv, false, false, true, false, false);
        assertEquals(2, haSoloComentarios.size(), "comentarios + versiones anteriores (siempre se eliminan)");
        assertFalse(String.join("\n", haSoloComentarios).contains("Autor"));
    }

    @Test
    void haSinNadaMarcadoLoDiceClaramente() {
        HAAnalizadorPrivacidad.HAInventario haLimpio = new HAAnalizadorPrivacidad.HAInventario(
                Map.of(), false, 0, List.of(), 0, 0, false, 0, 0, false);
        List<String> haLista = HAFormulariosPrivacidad.haQueSeBorra(haLimpio, false, false, false, false, false);
        assertEquals(1, haLista.size());
        assertTrue(haLista.get(0).startsWith("Nada marcado"));
    }
}
