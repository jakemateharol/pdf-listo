package pe.pdflisto.sistema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.conversiones.HAConversorWord;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HAComprobadorComponentesTest {
    private final HAComprobadorComponentes haComprobador = new HAComprobadorComponentes(new HAConversorWord());

    @TempDir
    Path haCarpeta;

    @Test
    void haCompruebaLasCincoPiezasYJavaFunciona() {
        List<HAEstadoComponente> haEstados = haComprobador.haComprobar();
        assertEquals(5, haEstados.size());
        assertTrue(haEstados.stream().allMatch(haE -> haE.haDetalle() != null && !haE.haDetalle().isBlank()));
        assertNotEquals(HAEstadoComponente.HANivel.FALTA, haEstados.get(0).haNivel(), "Java siempre esta presente");
        assertEquals(HAEstadoComponente.HANivel.OK, haComprobador.haCarpetaDatos().haNivel());
    }

    @Test
    void haElOcrFuncionaEnEsteEquipo() {
        HAEstadoComponente haOcr = haComprobador.haOcr();
        assertEquals(HAEstadoComponente.HANivel.OK, haOcr.haNivel(), haOcr.haDetalle());
        assertTrue(haOcr.haDetalle().contains("5."), haOcr.haDetalle());
    }

    @Test
    void haBuscaLasDllEnLasCarpetasIndicadas() throws Exception {
        Files.writeString(haCarpeta.resolve("vcruntime140.dll"), "x");
        assertTrue(HAComprobadorComponentes.haBuscarDll("vcruntime140.dll", List.of(haCarpeta)).isPresent());
        assertTrue(HAComprobadorComponentes.haBuscarDll("msvcp140.dll", List.of(haCarpeta)).isEmpty());
    }

    @Test
    void haElManualCopiaSoloLasImagenesQueNombra() {
        String haHtml = "<img src=\"capturas/inicio.png\"><img src=\"capturas/inicio.png\">"
                + "<img src=\"https://x.com/a.png\"><img src=\"../secreto.png\"><img src=\"diagramas/arq.svg\">";
        assertEquals(List.of("capturas/inicio.png", "diagramas/arq.svg"), List.copyOf(HAManualUsuario.haImagenes(haHtml)));
    }

    @Test
    void haElManualVieneDentroDeLaAppConTodasSusImagenes() throws Exception {
        Path haHtml = HAManualUsuario.haPreparar(haCarpeta.resolve("manual")).orElseThrow();
        String haTexto = Files.readString(haHtml);
        assertTrue(haTexto.contains("Manual de usuario"));
        var haImagenes = HAManualUsuario.haImagenes(haTexto);
        assertTrue(haImagenes.size() >= 10, "el manual tiene capturas");
        for (String haImg : haImagenes) {
            assertTrue(Files.size(haCarpeta.resolve("manual").resolve(haImg)) > 1000, "falta la captura " + haImg);
        }
    }

    @Test
    void haLaVersionNoMuestraElMarcadorSinReemplazar() {
        assertEquals("desarrollo", new HAInfoApp("@project.version@").haVersion());
        assertEquals("1.0.0", new HAInfoApp("1.0.0").haVersion());
        assertTrue(new HAInfoApp("1.0.0").haLicencias().contains("PDFBox"));
    }
}
