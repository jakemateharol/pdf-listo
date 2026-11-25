package pe.pdflisto.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HAPanelTachadoTest {
    @Test
    void haResumenConPlurales() {
        assertEquals("9 datos encontrados en 2 páginas · 7 marcados para tachar", HAPanelTachado.haTextoResumen(9, 2, 0, 7));
        assertEquals("1 dato encontrado en 1 página · 1 marcado para tachar", HAPanelTachado.haTextoResumen(1, 1, 0, 1));
        assertEquals("3 datos encontrados en 1 página + 2 áreas a mano · 5 marcados para tachar",
                HAPanelTachado.haTextoResumen(3, 1, 2, 5));
        assertEquals("0 datos encontrados + 1 área a mano · 0 marcados para tachar", HAPanelTachado.haTextoResumen(0, 0, 1, 0));
    }
}
