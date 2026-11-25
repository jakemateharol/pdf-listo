package pe.pdflisto.controller;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.sistema.HAComprobadorComponentes;
import pe.pdflisto.sistema.HADiagnostico;
import pe.pdflisto.sistema.HAEstadoComponente;
import pe.pdflisto.sistema.HAInfoApp;
import pe.pdflisto.sistema.HAManualUsuario;
import pe.pdflisto.ui.HAExportador;
import pe.pdflisto.ui.HANotificador;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAMenuAyuda {
    private final HAComprobadorComponentes haComprobador;
    private final HAInfoApp haInfo;
    private final HAFormulariosHerramientas haPaneles;
    private final HAExportador haExportador;
    private final HANotificador haNotificador;

    public void haMontar(MenuButton haMenu, HAEditor haEditor, Runnable haBienvenida) {
        haMenu.getItems().setAll(
                haItem("Manual de usuario", Feather.BOOK_OPEN, this::haAbrirManual),
                new SeparatorMenuItem(),
                haItem("Comprobar componentes…", Feather.ACTIVITY, () -> haComprobar(haEditor)),
                haItem("Abrir carpeta de datos", Feather.FOLDER, () -> haExportador.haAbrirEnSistema(HACarpetaDatos.haRuta().toFile())),
                haItem("Exportar diagnóstico…", Feather.PACKAGE, () -> haExportarDiagnostico(haEditor)),
                new SeparatorMenuItem(),
                haItem("Acerca de PDF Listo", Feather.INFO, this::haAcercaDe));
        if (haBienvenida != null) {
            haMenu.getItems().add(0, haItem("Recorrido de bienvenida", Feather.PLAY_CIRCLE, haBienvenida));
        }
    }

    public void haAvisarSiFaltaVisualC() {
        HAEstadoComponente haVc = haComprobador.haVisualC();
        if (haVc.haNivel() != HAEstadoComponente.HANivel.OK) {
            log.warn("Visual C++: {}", haVc.haDetalle());
            haNotificador.haAdvertencia("OCR: " + haVc.haDetalle());
        }
    }

    private void haAbrirManual() {
        try {
            Optional<Path> haManual = HAManualUsuario.haPreparar();
            if (haManual.isEmpty()) {
                haNotificador.haAdvertencia("El manual no viene incluido en esta versión.");
                return;
            }
            haNotificador.haInfo("Abriendo el manual en el navegador (no necesita internet)…");
            haExportador.haAbrirEnSistema(haManual.get().toFile());
        } catch (Exception haE) {
            log.warn("No se pudo abrir el manual", haE);
            haNotificador.haError("No se pudo abrir el manual: " + haE.getMessage());
        }
    }

    private void haComprobar(HAEditor haEditor) {
        haEditor.haEnSegundoPlano("Comprobando componentes", haC -> haComprobador.haComprobar(), this::haMostrarComponentes);
    }

    private void haExportarDiagnostico(HAEditor haEditor) {
        javafx.stage.FileChooser haSelector = new javafx.stage.FileChooser();
        haSelector.setTitle("Exportar diagnóstico");
        haSelector.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Archivo comprimido (.zip)", "*.zip"));
        haSelector.setInitialFileName("PDFListo-diagnostico-"
                + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".zip");
        java.io.File haEscritorio = new java.io.File(System.getProperty("user.home"), "Desktop");
        if (haEscritorio.isDirectory()) {
            haSelector.setInitialDirectory(haEscritorio);
        }
        java.io.File haElegido = haSelector.showSaveDialog(haEditor.haVentana());
        if (haElegido == null) {
            return;
        }
        java.io.File haDestino = haElegido.getName().toLowerCase().endsWith(".zip")
                ? haElegido : new java.io.File(haElegido.getParentFile(), haElegido.getName() + ".zip");
        haEditor.haEnSegundoPlano("Preparando el diagnóstico", haC -> {
            try {
                haC.haMensaje("Comprobando componentes…");
                List<HAEstadoComponente> haEstados;
                try {
                    haEstados = haComprobador.haComprobar();
                } catch (RuntimeException haE) {
                    log.warn("Diagnóstico: no se pudieron comprobar los componentes", haE);
                    haEstados = List.of();
                }
                haC.haMensaje("Guardando el diagnóstico…");
                return new HADiagnostico().haCrear(haDestino.toPath(), haInfo.haVersion(), haEstados,
                        HACarpetaDatos.haRuta().resolve("logs"), HACarpetaDatos.haRuta());
            } catch (java.io.IOException haE) {
                throw new java.io.UncheckedIOException(haE);
            }
        }, haR -> {
            haNotificador.haExito("Diagnóstico guardado: " + haR.haArchivo().getFileName() + " ("
                    + pe.pdflisto.operaciones.HAArchivos.haTamanoLegible(haR.haBytes()) + "). No incluye tus documentos ni sus "
                    + "nombres; puedes revisarlo antes de enviarlo.");
            haExportador.haAbrirEnSistema(haR.haArchivo().getParent().toFile());
        });
    }

    private void haMostrarComponentes(List<HAEstadoComponente> haEstados) {
        VBox haLista = new VBox(4);
        for (HAEstadoComponente haE : haEstados) {
            haLista.getChildren().add(haFila(haE));
        }
        long haProblemas = haEstados.stream().filter(haE -> haE.haNivel() == HAEstadoComponente.HANivel.FALTA).count();
        String haResumen = haProblemas == 0 ? "Todo lo necesario está en orden." : haProblemas + " componente(s) con problemas.";
        haPaneles.haAbrirInformativo(Feather.ACTIVITY, "Componentes", haResumen, new VBox(14, haLista,
                HAFormulariosHerramientas.haNota("Esta comprobación se hace en tu equipo, sin internet. Si algo falla, "
                        + "el registro de errores está en la carpeta de datos (Ayuda → Abrir carpeta de datos → logs).")));
    }

    private static Node haFila(HAEstadoComponente haE) {
        FontIcon haIcono = switch (haE.haNivel()) {
            case OK -> new FontIcon(Feather.CHECK_CIRCLE);
            case AVISO -> new FontIcon(Feather.ALERT_TRIANGLE);
            case FALTA -> new FontIcon(Feather.X_CIRCLE);
        };
        haIcono.getStyleClass().add(switch (haE.haNivel()) {
            case OK -> "estado-cumple";
            case AVISO -> "estado-revisar";
            case FALTA -> "estado-falla";
        });
        Label haNombre = new Label(haE.haNombre());
        haNombre.getStyleClass().add("comprobacion-nombre");
        Label haDetalle = new Label(haE.haDetalle());
        haDetalle.setWrapText(true);
        haDetalle.getStyleClass().add("texto-tenue");
        VBox haTextos = new VBox(2, haNombre, haDetalle);
        HBox.setHgrow(haTextos, Priority.ALWAYS);
        HBox haFila = new HBox(10, haIcono, haTextos);
        haFila.setAlignment(Pos.TOP_LEFT);
        haFila.getStyleClass().add("comprobacion");
        return haFila;
    }

    private void haAcercaDe() {
        Label haLema = new Label("Editor de PDF que funciona sin internet. Muestra un plan antes de actuar, trabaja "
                + "sobre una copia y verifica que el resultado cumpla lo pedido.");
        haLema.setWrapText(true);
        Label haPrivacidad = HAFormulariosHerramientas.haNota("Tus archivos nunca salen de este equipo. "
                + "Datos de la app: " + HACarpetaDatos.haRuta());
        TextArea haLicencias = new TextArea(haInfo.haLicencias());
        haLicencias.setEditable(false);
        haLicencias.setWrapText(false);
        haLicencias.setPrefRowCount(16);
        haLicencias.getStyleClass().add("texto-licencias");
        haPaneles.haAbrirInformativo(Feather.INFO, "PDF Listo " + haInfo.haVersion(), "Java " + Runtime.version().feature()
                + " · funciona sin internet", new VBox(14, haLema, haPrivacidad,
                HAFormulariosHerramientas.haCampo("Bibliotecas de código abierto y licencias", haLicencias)));
    }

    private static MenuItem haItem(String haTexto, Feather haIcono, Runnable haAccion) {
        MenuItem haM = new MenuItem(haTexto, new FontIcon(haIcono));
        haM.setOnAction(haE -> haAccion.run());
        return haM;
    }
}
