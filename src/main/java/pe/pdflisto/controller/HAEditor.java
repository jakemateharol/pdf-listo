package pe.pdflisto.controller;

import javafx.stage.Window;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HAProcesoVersion;
import pe.pdflisto.operaciones.HAResultado;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public interface HAEditor {
    HASesionEdicion haSesion();

    List<HASesionEdicion> haSesiones();

    List<Integer> haPaginasSeleccionadas();

    Window haVentana();

    void haAplicar(String haDescripcion, String haPedido, List<String> haPasos, String haCodigo, HAProcesoVersion haProceso,
                 BiConsumer<HAResultado, Long> haAlTerminar);

    default void haAplicar(String haDescripcion, String haPedido, List<String> haPasos, HAProcesoVersion haProceso,
                         BiConsumer<HAResultado, Long> haAlTerminar) {
        haAplicar(haDescripcion, haPedido, haPasos, null, haProceso, haAlTerminar);
    }

    default void haAplicarRepetible(String haDescripcion, String haCodigo, HAProcesoVersion haProceso, Consumer<HAResultado> haAlTerminar) {
        haAplicar(haDescripcion, null, null, haCodigo, haProceso, (haR, haId) -> {
            if (haAlTerminar != null) {
                haAlTerminar.accept(haR);
            }
        });
    }

    void haMostrarPestanaPedido();

    default void haAplicar(String haDescripcion, HAProcesoVersion haProceso, Consumer<HAResultado> haAlTerminar) {
        haAplicar(haDescripcion, null, null, haProceso, (haR, haId) -> {
            if (haAlTerminar != null) {
                haAlTerminar.accept(haR);
            }
        });
    }

    @FunctionalInterface
    interface HATrabajoArchivos {
        List<File> haEjecutar(HAControl haControl) throws Exception;
    }

    default void haExportar(String haDescripcion, HATrabajoArchivos haTrabajo, pe.pdflisto.verificacion.HAEsperadoArchivos haEsperado) {
        haExportar(haDescripcion, haTrabajo, haEsperado, null);
    }

    void haExportar(String haDescripcion, HATrabajoArchivos haTrabajo, pe.pdflisto.verificacion.HAEsperadoArchivos haEsperado,
                  Consumer<List<File>> haAlTerminar);

    void haAbrirPdf(List<File> haArchivos);

    <T> void haEnSegundoPlano(String haDescripcion, java.util.function.Function<HAControl, T> haTrabajo, Consumer<T> haAlTerminar);

    void haMostrarPagina(int haPagina);

    void haMarcarParaTachar(Map<Integer, List<HAAreaTexto>> haMarcas);

    void haModoDibujo(BiConsumer<Integer, HAAreaTexto> haAlDibujar);

    int haPaginaVisible();

    void haHerramientaEnVisor(pe.pdflisto.ui.HAVisorPagina.HAHerramienta haHerramienta);

    void haPintorEnVisor(pe.pdflisto.ui.HAVisorPagina.HAPintor haPintor);

    void haRepintarVisor();
}
