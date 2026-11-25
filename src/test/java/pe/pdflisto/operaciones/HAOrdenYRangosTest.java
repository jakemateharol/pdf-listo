package pe.pdflisto.operaciones;

import org.junit.jupiter.api.Test;
import pe.pdflisto.exception.HAPdfListoException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAOrdenYRangosTest {
    @Test
    void haEntiendeRangos() {
        assertEquals(List.of(List.of(0, 1, 2), List.of(4), List.of(7, 8, 9)), HARangoPaginas.haGrupos("1-3, 5, 8-", 10));
        assertEquals(List.of(0, 1, 2, 4), HARangoPaginas.haSeleccion("1-3,5,2", 10));
        assertEquals(List.of(List.of(0, 1)), HARangoPaginas.haGrupos("-2", 10));
    }

    @Test
    void haExplicaLosErroresDeRango() {
        assertTrue(assertThrows(HAPdfListoException.class, () -> HARangoPaginas.haGrupos("1-15", 12))
                .getMessage().contains("15 no existe"));
        assertTrue(assertThrows(HAPdfListoException.class, () -> HARangoPaginas.haGrupos("5-2", 12))
                .getMessage().contains("al revés"));
        assertTrue(assertThrows(HAPdfListoException.class, () -> HARangoPaginas.haGrupos("a", 12))
                .getMessage().contains("no es un número"));
        assertThrows(HAPdfListoException.class, () -> HARangoPaginas.haGrupos("  ", 12));
        assertThrows(HAPdfListoException.class, () -> HARangoPaginas.haGrupos("0", 12));
    }

    @Test
    void haFormateaSeleccion() {
        assertEquals("2, 5-7", HARangoPaginas.haFormatear(List.of(1, 4, 5, 6)));
        assertEquals("1", HARangoPaginas.haFormatear(List.of(0)));
    }

    @Test
    void haMueveSubeBajaDuplicaYElimina() {
        assertEquals(List.of(1, 2, 3, 0), HAOrdenPaginas.haMover(4, List.of(0), 4));
        assertEquals(List.of(0, 3, 1, 2), HAOrdenPaginas.haMover(4, List.of(3), 1));
        assertEquals(List.of(1, 0, 2, 3), HAOrdenPaginas.haMover(4, List.of(0), 2));
        assertEquals(List.of(0, 2, 1, 3), HAOrdenPaginas.haSubir(4, List.of(2)));
        assertEquals(List.of(0, 1, 2, 3), HAOrdenPaginas.haSubir(4, List.of(0)));
        assertEquals(List.of(1, 0, 2, 3), HAOrdenPaginas.haBajar(4, List.of(0)));
        assertEquals(List.of(0, 1, 1, 2), HAOrdenPaginas.haDuplicar(3, List.of(1)));
        assertEquals(List.of(0, 2), HAOrdenPaginas.haEliminar(3, List.of(1)));
        assertThrows(HAPdfListoException.class, () -> HAOrdenPaginas.haEliminar(2, List.of(0, 1)));
        assertEquals(List.of(3), HAOrdenPaginas.haPosiciones(List.of(1, 2, 3, 0), List.of(0)));
    }

    @Test
    void haPlanesDeDivision() {
        assertEquals(3, HAPlanDivision.haCadaN(4, 10, "doc").size());
        assertEquals("doc - parte 1", HAPlanDivision.haCadaN(5, 10, "doc").get(0).haNombre());
        assertEquals(2, HAPlanDivision.haPorRangos("1-3, 4-", 10, "doc").size());
        assertThrows(HAPdfListoException.class, () -> HAPlanDivision.haCadaN(10, 10, "doc"));
        var haMarcadores = List.of(
                new pe.pdflisto.pdf.HAMarcador("Introducción", 2, List.of()),
                new pe.pdflisto.pdf.HAMarcador("Capítulo 1", 5, List.of()));
        List<HAPlanDivision.HAParte> haPartes = HAPlanDivision.haPorMarcadores(haMarcadores, 10, "tesis");
        assertEquals(3, haPartes.size());
        assertEquals(List.of(0, 1), haPartes.get(0).haPaginas());
        assertEquals(List.of(5, 6, 7, 8, 9), haPartes.get(2).haPaginas());
    }

    @Test
    void haNombresDeArchivoSeguros() {
        assertEquals("a_b_c", HAArchivos.haNombreSeguro("a/b:c"));
        assertEquals("informe", HAArchivos.haSinExtension("informe.PDF"));
    }
}
