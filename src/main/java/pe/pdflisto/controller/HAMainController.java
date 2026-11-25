package pe.pdflisto.controller;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.edicion.HARecuperacion;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.exception.HAContrasenaRequeridaException;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAArchivoReciente;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HACanceladoException;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HAOrdenPaginas;
import pe.pdflisto.operaciones.HAProcesoVersion;
import pe.pdflisto.operaciones.HARangoPaginas;
import pe.pdflisto.operaciones.HAReorganizar;
import pe.pdflisto.operaciones.HAResultado;
import pe.pdflisto.operaciones.HARotar;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.pdf.HACoincidencia;
import pe.pdflisto.pdf.HADocumentoPdf;
import pe.pdflisto.pdf.HAMarcador;
import pe.pdflisto.pdf.HAPdfService;
import pe.pdflisto.service.HAArchivosRecientesService;
import pe.pdflisto.service.HAHistorialService;
import pe.pdflisto.ui.HACeldaMiniatura;
import pe.pdflisto.ui.HAExportador;
import pe.pdflisto.ui.HAMiniaturas;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.ui.HAVisorPagina;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAMainController implements HAEditor {
    private static final double[] HA_NIVELES_ZOOM = {0.25, 0.33, 0.5, 0.67, 0.75, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4};
    private static final double HA_MARGEN_VISOR = 48 + 16;

    private final HAPdfService haPdfService;
    private final HAMotorPdf haMotor;
    private final HAArchivosRecientesService haRecientesService;
    private final HAConfiguracion haConfiguracion;
    private final HANotificador haNotificador;
    private final HAExportador haExportador;
    private final HAFormulariosHerramientas haFormularios;
    private final HAPanelPedido haPanelPedido;
    private final HAPanelHistorial haPanelHistorial;
    private final HAPanelTachado haPanelTachado;
    private final HAPanelAnotar haPanelAnotar;
    private final HAPanelFormulario haPanelFormulario;
    private final HAPanelEntrega haPanelEntrega;
    private final HAHistorialService haHistorialService;
    private final HAFormulariosConversion haFormulariosConversion;
    private final HAMenuAyuda haMenuAyudaApp;
    private final HABienvenida haBienvenida;
    private final pe.pdflisto.verificacion.HAVerificadorOperacion haVerificadorOperacion;
    private final pe.pdflisto.verificacion.HAVerificadorArchivos haVerificadorArchivos;

    @FXML private StackPane haRaiz;
    @FXML private Label haLblArchivo;
    @FXML private Label haLblMarca;
    @FXML private javafx.scene.control.Separator haSepMarca;
    @FXML private HBox haBarraSuperior;
    @FXML private HBox haZonaArchivo;
    @FXML private Button haBtnAbrir;
    @FXML private Label haLblDetalle;
    @FXML private HBox haIndicadorTrabajo;
    @FXML private Label haLblTrabajo;
    @FXML private javafx.scene.control.ProgressBar haBarraTrabajo;
    @FXML private Button haBtnCancelarTrabajo;
    @FXML private Button haBtnDeshacer;
    @FXML private Button haBtnRehacer;
    @FXML private Tooltip haTtDeshacer;
    @FXML private Tooltip haTtRehacer;
    @FXML private MenuButton haMenuHerramientas;
    @FXML private MenuButton haMenuConvertir;
    @FXML private MenuButton haMenuAyuda;
    @FXML private ComboBox<HASesionEdicion> haCmbDocumentos;
    @FXML private Button haBtnCerrarDocumento;
    @FXML private MenuButton haBtnRecientes;
    @FXML private Button haBtnGuardarCopia;
    @FXML private Button haBtnTema;

    @FXML private VBox haPanelIzquierdo;
    @FXML private HBox haHerramientasPaginas;
    @FXML private ListView<Integer> haListaMiniaturas;
    @FXML private TreeView<HAMarcador> haArbolMarcadores;
    @FXML private Label haLblSinMarcadores;

    @FXML private VBox haVisorContenedor;
    @FXML private ScrollPane haScrollVisor;
    @FXML private TextField haTxtPagina;
    @FXML private Label haLblTotalPaginas;
    @FXML private Label haLblZoom;
    @FXML private ToggleButton haBtnAjustarAncho;
    @FXML private Button haBtnPantallaCompleta;
    @FXML private TextField haTxtBuscar;
    @FXML private Label haLblResultados;
    @FXML private Label haLblPie;

    @FXML private VBox haEstadoVacio;
    @FXML private Label haLblTituloRecientes;
    @FXML private VBox haListaRecientesInicio;
    @FXML private StackPane haCapaArrastre;
    @FXML private TabPane haPanelDerecho;
    @FXML private VBox haContenedorPedido;
    @FXML private VBox haContenedorHistorial;
    @FXML private VBox haContenedorTachado;
    @FXML private VBox haContenedorAnotar;
    @FXML private VBox haContenedorFormulario;
    @FXML private VBox haContenedorEntrega;

    @FXML private ModalPane haModalPanel;
    @FXML private ModalPane haModalDialogo;
    @FXML private VBox haZonaNotificaciones;

    private final HAVisorPagina haVisor = new HAVisorPagina();
    private final HAMiniaturas haMiniaturas = new HAMiniaturas();
    private final ObservableList<HASesionEdicion> haAbiertos = FXCollections.observableArrayList();
    private Stage haVentana;
    private HASesionEdicion haActual;
    private int haPaginaActual = -1;
    private boolean haAjustarAncho = true;
    private boolean haCambiandoDocumento;
    private boolean haOcupado;
    private HAControl haTrabajoActual;
    private static final double HA_ANCHO_TARJETA = 90;
    private long haUltimoCambioPorRueda;

    private List<HACoincidencia> haCoincidencias = List.of();
    private int haIndiceCoincidencia = -1;
    private String haUltimaBusqueda = "";

    private record HAHecho(HAResultado haResultado, HADocumentoPdf haDocumento, pe.pdflisto.verificacion.HAInformeVerificacion haInforme) {
    }

    @FXML
    public void initialize() {
        haNotificador.haRegistrar(haModalPanel, haModalDialogo, haZonaNotificaciones);
        haModalPanel.setAlignment(Pos.TOP_RIGHT);
        haModalDialogo.setAlignment(Pos.CENTER);

        if (haTxtPagina.getParent() instanceof javafx.scene.layout.Pane haBarraVisor) {
            haBarraVisor.getChildren().stream()
                    .filter(haN -> haN instanceof javafx.scene.layout.Region && haN != haTxtBuscar)
                    .forEach(haN -> ((javafx.scene.layout.Region) haN).setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE));
        }

        haScrollVisor.setContent(haVisor);
        haVisor.minWidthProperty().bind(Bindings.createDoubleBinding(
                () -> haScrollVisor.getViewportBounds().getWidth(), haScrollVisor.viewportBoundsProperty()));
        haVisor.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> haScrollVisor.getViewportBounds().getHeight(), haScrollVisor.viewportBoundsProperty()));
        haVisor.setAlFallar(this::haErrorAlDibujar);
        haScrollVisor.viewportBoundsProperty().addListener((haO, haA, haB) -> {
            if (haAjustarAncho && haActual != null && Math.abs(haA.getWidth() - haB.getWidth()) > 0.5) {
                haAplicarAjusteAncho();
            }
        });
        haScrollVisor.addEventFilter(ScrollEvent.SCROLL, this::haAlGirarRueda);

        haListaMiniaturas.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        haListaMiniaturas.setCellFactory(haL -> new HACeldaMiniatura(this::haDocumento, haMiniaturas, this::haMoverPaginas));
        haListaMiniaturas.getSelectionModel().selectedItemProperty().addListener((haO, haA, haPagina) -> {
            if (haPagina != null && haPagina != haPaginaActual && !haCambiandoDocumento) {
                haIrAPagina(haPagina);
            }
        });
        haListaMiniaturas.setOnKeyPressed(haE -> {
            if (haE.getCode() == KeyCode.DELETE) {
                haOnEliminarPaginas();
                haE.consume();
            }
        });

        haArbolMarcadores.setRoot(new TreeItem<>());
        haArbolMarcadores.setCellFactory(haT -> new TreeCell<>() {
            @Override
            protected void updateItem(HAMarcador haM, boolean haVacio) {
                super.updateItem(haM, haVacio);
                setText(haVacio || haM == null ? null : haM.haTitulo());
            }
        });
        haArbolMarcadores.getSelectionModel().selectedItemProperty().addListener((haO, haA, haItem) -> {
            if (haItem != null && haItem.getValue() != null && haItem.getValue().haPagina() >= 0) {
                haIrAPagina(haItem.getValue().haPagina());
            }
        });

        haCmbDocumentos.setItems(haAbiertos);
        haCmbDocumentos.setConverter(new StringConverter<>() {
            @Override public String toString(HASesionEdicion haS) { return haS == null ? "" : haS.haNombre(); }
            @Override public HASesionEdicion fromString(String haS) { return null; }
        });
        haCmbDocumentos.visibleProperty().bind(Bindings.size(haAbiertos).greaterThan(1));
        haCmbDocumentos.managedProperty().bind(haCmbDocumentos.visibleProperty());
        haCmbDocumentos.setOnAction(haE -> {
            HASesionEdicion haElegida = haCmbDocumentos.getValue();
            if (haElegida != null && haElegida != haActual && !haCambiandoDocumento) {
                haActivar(haElegida);
            }
        });

        haBtnRecientes.setOnShowing(haE -> haLlenarMenuRecientes());
        haInstalarBarraAdaptable();
        for (MenuButton haM : List.of(haMenuHerramientas, haMenuConvertir, haBtnRecientes, haMenuAyuda)) {
            haSinTooltipMientrasEstaAbierto(haM);
        }
        haPanelDerecho.getTabs().forEach(haT -> haT.setTooltip(new Tooltip(haT.getText())));

        haTxtBuscar.addEventFilter(KeyEvent.KEY_PRESSED, haE -> {
            if (haE.getCode() == KeyCode.ENTER && haE.isShiftDown()) {
                haOnResultadoAnterior();
                haE.consume();
            } else if (haE.getCode() == KeyCode.ESCAPE) {
                haTxtBuscar.clear();
                haLimpiarBusqueda();
                haScrollVisor.requestFocus();
            }
        });
        haTxtBuscar.textProperty().addListener((haO, haA, haB) -> {
            if (!haB.trim().equals(haUltimaBusqueda)) {
                haLimpiarBusqueda();
            }
        });

        haPanelPedido.haMontar(haContenedorPedido, this);
        haPanelTachado.haMontar(haContenedorTachado, this);
        haPanelAnotar.haMontar(haContenedorAnotar, this);
        haPanelFormulario.haMontar(haContenedorFormulario, this);
        haPanelEntrega.haMontar(haContenedorEntrega, this);
        haFormulariosConversion.haMontar(haMenuConvertir, this);
        haMenuAyudaApp.haMontar(haMenuAyuda, this, haBienvenida::haMostrar);
        haPanelHistorial.haMontar(haContenedorHistorial, haPedido -> {
            if (haActual == null) {
                haNotificador.haAdvertencia("Abre el PDF sobre el que quieres repetir la operación.");
                return;
            }
            haPanelDerecho.getSelectionModel().select(0);
            haPanelPedido.haRepetir(haPedido);
        });
        haMostrarSinDocumento();
    }

    private static void haSinTooltipMientrasEstaAbierto(MenuButton haMenu) {
        haMenu.showingProperty().addListener((haO, haAntes, haAbierto) -> {
            if (haAbierto) {
                Tooltip haT = haMenu.getTooltip();
                if (haT != null) {
                    haT.hide();
                    haMenu.getProperties().put("tooltipGuardado", haT);
                    haMenu.setTooltip(null);
                }
            } else if (haMenu.getProperties().get("tooltipGuardado") instanceof Tooltip haT) {
                haMenu.setTooltip(haT);
                haMenu.getProperties().remove("tooltipGuardado");
            }
        });
    }

    private void haInstalarBarraAdaptable() {
        new pe.pdflisto.ui.HABarraAdaptable(haBarraSuperior, haZonaArchivo)
                .haSoloIconoDesde(haMenuHerramientas, pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haMenuConvertir, pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haBtnRecientes, pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haBtnGuardarCopia, pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPACTO)
                .haTextoCorto(haBtnAbrir, "Abrir")
                .haOcultarDesde(haLblMarca, pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPACTO)
                .haOcultarDesde(haSepMarca, pe.pdflisto.ui.HABarraAdaptable.HANivel.MINIMO)
                .haAlCambiarNivel(haN -> {
                    boolean haMinimo = haN == pe.pdflisto.ui.HABarraAdaptable.HANivel.MINIMO;
                    haCmbDocumentos.setPrefWidth(haN == pe.pdflisto.ui.HABarraAdaptable.HANivel.COMPLETO ? 220 : haMinimo ? 140 : 170);
                    haLblTrabajo.setPrefWidth(haMinimo ? 140 : 260);
                    haLblTrabajo.setMaxWidth(haMinimo ? 140 : 260);
                    haBarraTrabajo.setPrefWidth(haMinimo ? 130 : 220);
                })
                .haInstalar();
    }

    public void haAlMostrarVentana(Stage haStage) {
        this.haVentana = haStage;
        haAplicarTema(haConfiguracion.haTemaOscuro());
        haInstalarAtajos(haStage.getScene());
        haInstalarArrastrarYSoltar(haStage.getScene());
        haStage.fullScreenProperty().addListener((haO, haA, haCompleta) -> {
            haPanelIzquierdo.setVisible(!haCompleta);
            haPanelIzquierdo.setManaged(!haCompleta);
            haPanelDerecho.setVisible(!haCompleta);
            haPanelDerecho.setManaged(!haCompleta);
            ((FontIcon) haBtnPantallaCompleta.getGraphic()).setIconCode(haCompleta ? Feather.MINIMIZE : Feather.MAXIMIZE);
        });
        haStage.setOnCloseRequest(haE -> {
            List<String> haPendientes = new ArrayList<>(haAbiertos.stream().filter(HASesionEdicion::haHayCambios).map(HASesionEdicion::haNombre).toList());
            haPanelAnotar.haDocumentosConPendientes().stream().filter(haN -> !haPendientes.contains(haN)).forEach(haPendientes::add);
            if (!haPendientes.isEmpty()) {
                haE.consume();
                haNotificador.haConfirmar("Salir sin guardar",
                        "Hay cambios sin guardar en: " + String.join(", ", haPendientes)
                                + ". Si sales se perderán (los originales no se modifican).",
                        "Salir sin guardar", haStage::close);
            }
        });
        Platform.runLater(this::haOfrecerRecuperacion);
        Platform.runLater(haMenuAyudaApp::haAvisarSiFaltaVisualC);
        Platform.runLater(this::haAvisarSiQuedaPocoEspacio);
    }

    private void haAvisarSiQuedaPocoEspacio() {
        try {
            pe.pdflisto.sistema.HAEspacioDisco.haAvisoInicio(pe.pdflisto.edicion.HACarpetaTrabajo.haRuta())
                    .ifPresent(haNotificador::haAdvertencia);
        } catch (RuntimeException haE) {
            log.debug("No se pudo medir el espacio libre", haE);
        }
    }

    private void haOfrecerRecuperacion() {
        List<HARecuperacion.HAAbandonada> haLista;
        try {
            haLista = HARecuperacion.haBuscar();
        } catch (RuntimeException haE) {
            log.warn("No se pudo revisar el trabajo sin guardar", haE);
            haLista = List.of();
        }
        if (haLista.isEmpty()) {
            haBienvenida.haMostrarSiCorresponde();
            return;
        }
        haPreguntarRecuperar(haLista, 0);
    }

    private void haPreguntarRecuperar(List<HARecuperacion.HAAbandonada> haLista, int haI) {
        if (haI >= haLista.size()) {
            return;
        }
        HARecuperacion.HAAbandonada haA = haLista.get(haI);
        String haCuando = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")
                .format(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(haA.haDatos().haFecha()),
                        java.time.ZoneId.systemDefault()));
        haNotificador.haElegir("Recuperar trabajo sin guardar",
                "PDF Listo se cerró antes de que guardaras los cambios de «" + haA.haDatos().haNombre() + "» ("
                        + haA.haDatos().haCambios() + (haA.haDatos().haCambios() == 1 ? " cambio" : " cambios") + ", " + haCuando
                        + "). ¿Quieres recuperarlos? Podrás seguir deshaciendo como antes. El original está intacto.",
                "Descartar", () -> {
                    HARecuperacion.haDescartar(haA);
                    haPreguntarRecuperar(haLista, haI + 1);
                },
                "Recuperar", () -> {
                    haRecuperar(haA);
                    haPreguntarRecuperar(haLista, haI + 1);
                });
    }

    private void haRecuperar(HARecuperacion.HAAbandonada haA) {
        HASesionEdicion haS;
        try {
            haS = HASesionEdicion.haRecuperar(haA.haCarpeta(), haA.haDatos());
        } catch (RuntimeException haE) {
            log.error("No se pudo recuperar {}", haA.haCarpeta(), haE);
            haNotificador.haError("No se pudo recuperar el trabajo de «" + haA.haDatos().haNombre() + "».");
            return;
        }
        haAbrirVersionActual(haS, haDoc -> {
            haS.haCambiarVista(haDoc);
            haAbiertos.add(haS);
            haActivar(haS);
            haRegistrarHistorial("Recuperó el trabajo sin guardar de " + haS.haNombre());
            haNotificador.haExito("Se recuperó «" + haS.haNombre() + "». Recuerda guardarlo con «Guardar copia».");
        }, haS::haLiberarSinBorrar);
    }

    private void haAbrirVersionActual(HASesionEdicion haS, java.util.function.Consumer<HADocumentoPdf> haAlAbrir, Runnable haAlFallar) {
        File haArchivo = haS.haArchivoActual();
        CompletableFuture.supplyAsync(() -> haPdfService.haAbrir(haArchivo, haS.haContrasenaActual()))
                .whenComplete((haDoc, haError) -> Platform.runLater(() -> {
                    if (haError == null) {
                        haAlAbrir.accept(haDoc);
                        return;
                    }
                    Throwable haC = haCausa(haError);
                    if (haC instanceof HAContrasenaRequeridaException haCr) {
                        Optional<String> haClave = haPedirContrasena(haS.haOriginal(), haCr.isIncorrecta());
                        if (haClave.isPresent()) {
                            haS.haPonerContrasenaActual(haClave.get());
                            haAbrirVersionActual(haS, haAlAbrir, haAlFallar);
                            return;
                        }
                    } else {
                        haMostrarError("Abrir " + haS.haNombre(), haError);
                    }
                    haAlFallar.run();
                }));
    }

    private void haInstalarAtajos(Scene haScene) {
        var haAtajos = haScene.getAccelerators();
        haAtajos.put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::haOnAbrir);
        haAtajos.put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::haOnGuardarCopia);
        haAtajos.put(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN), this::haOnCerrarDocumento);
        haAtajos.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::haOnDeshacer);
        haAtajos.put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::haOnRehacer);
        haAtajos.put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::haOnRehacer);
        haAtajos.put(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN), this::haOnRotarDerecha);
        haAtajos.put(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::haOnRotarIzquierda);
        haAtajos.put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::haOnDuplicarPaginas);
        haAtajos.put(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN), () -> {
            if (haActual != null) {
                haTxtBuscar.requestFocus();
                haTxtBuscar.selectAll();
            }
        });
        for (KeyCode haK : List.of(KeyCode.PLUS, KeyCode.EQUALS, KeyCode.ADD)) {
            haAtajos.put(new KeyCodeCombination(haK, KeyCombination.SHORTCUT_DOWN), this::haOnAcercar);
        }
        for (KeyCode haK : List.of(KeyCode.MINUS, KeyCode.SUBTRACT)) {
            haAtajos.put(new KeyCodeCombination(haK, KeyCombination.SHORTCUT_DOWN), this::haOnAlejar);
        }
        for (KeyCode haK : List.of(KeyCode.DIGIT0, KeyCode.NUMPAD0)) {
            haAtajos.put(new KeyCodeCombination(haK, KeyCombination.SHORTCUT_DOWN), this::haOnAjustarAncho);
        }
        haAtajos.put(new KeyCodeCombination(KeyCode.DIGIT1, KeyCombination.SHORTCUT_DOWN), () -> setZoom(1.0, false));
        haAtajos.put(new KeyCodeCombination(KeyCode.F11), this::haOnPantallaCompleta);
        haAtajos.put(new KeyCodeCombination(KeyCode.PAGE_DOWN), this::haOnPaginaSiguiente);
        haAtajos.put(new KeyCodeCombination(KeyCode.PAGE_UP), this::haOnPaginaAnterior);
        haAtajos.put(new KeyCodeCombination(KeyCode.HOME, KeyCombination.SHORTCUT_DOWN), () -> haIrAPagina(0));
        haAtajos.put(new KeyCodeCombination(KeyCode.END, KeyCombination.SHORTCUT_DOWN),
                () -> { if (haActual != null) haIrAPagina(haDocumento().haPaginas() - 1); });
    }

    private void haInstalarArrastrarYSoltar(Scene haScene) {
        haScene.setOnDragOver(haE -> {
            if (!haPdfsArrastrados(haE.getDragboard()).isEmpty()) {
                haE.acceptTransferModes(TransferMode.COPY);
                haCapaArrastre.setVisible(true);
            }
            haE.consume();
        });
        haScene.setOnDragExited(haE -> haCapaArrastre.setVisible(false));
        haScene.setOnDragDropped(haE -> {
            List<File> haPdfs = haPdfsArrastrados(haE.getDragboard());
            haCapaArrastre.setVisible(false);
            if (haPdfs.isEmpty()) {
                return;
            }
            haE.setDropCompleted(true);
            haE.consume();
            Platform.runLater(() -> haAbrirArchivos(haPdfs));
        });
    }

    private static List<File> haPdfsArrastrados(Dragboard haDb) {
        if (!haDb.hasFiles()) {
            return List.of();
        }
        return haDb.getFiles().stream()
                .filter(haF -> haF.isFile() && haF.getName().toLowerCase().endsWith(".pdf"))
                .toList();
    }

    @Override public HASesionEdicion haSesion() { return haActual; }
    @Override public List<HASesionEdicion> haSesiones() { return List.copyOf(haAbiertos); }
    @Override public Window haVentana() { return haVentana; }

    @Override
    public List<Integer> haPaginasSeleccionadas() {
        List<Integer> haSel = new ArrayList<>(haListaMiniaturas.getSelectionModel().getSelectedItems());
        haSel.removeIf(haI -> haI == null);
        haSel.sort(Integer::compare);
        if (haSel.isEmpty() && haPaginaActual >= 0) {
            haSel.add(haPaginaActual);
        }
        return haSel;
    }

    @Override
    public void haMostrarPagina(int haPagina) {
        haIrAPagina(haPagina);
    }

    @Override
    public void haMarcarParaTachar(java.util.Map<Integer, List<HAAreaTexto>> haMarcas) {
        haVisor.setParaTachar(haMarcas);
    }

    @Override
    public void haModoDibujo(BiConsumer<Integer, HAAreaTexto> haAlDibujar) {
        haVisor.setModoDibujo(haAlDibujar);
    }

    @Override
    public int haPaginaVisible() {
        return Math.max(0, haPaginaActual);
    }

    @Override
    public void haHerramientaEnVisor(HAVisorPagina.HAHerramienta haHerramienta) {
        haVisor.setHerramienta(haHerramienta);
    }

    @Override
    public void haPintorEnVisor(HAVisorPagina.HAPintor haPintor) {
        haVisor.setPintor(haPintor);
    }

    @Override
    public void haRepintarVisor() {
        haVisor.haRepintarExtra();
    }

    private void haPedirPropietario(HASesionEdicion haS, Runnable haContinuar) {
        Dialog<String> haDialogo = new Dialog<>();
        haDialogo.initOwner(haVentana);
        haDialogo.setTitle("PDF con restricciones");
        haDialogo.setHeaderText("«" + haS.haNombre() + "» tiene restricciones del autor (por ejemplo, no copiar o no modificar).");
        PasswordField haCampo = new PasswordField();
        haCampo.setPromptText("Contraseña de propietario");
        Label haExplicacion = new Label("Para editarlo hace falta la contraseña de PROPIETARIO. Si no la tienes, "
                + "puedes seguir viéndolo, buscando y verificándolo, pero no modificarlo.");
        haExplicacion.setWrapText(true);
        haExplicacion.setMaxWidth(380);
        haDialogo.getDialogPane().setContent(new VBox(10, haExplicacion, haCampo));
        haDialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        haDialogo.setResultConverter(haB -> haB == ButtonType.OK ? haCampo.getText() : null);
        Platform.runLater(haCampo::requestFocus);
        pe.pdflisto.ui.HADialogos.haMostrar(haDialogo).ifPresent(haClave -> {
            if (haPdfService.haEsPropietario(haS.haOriginal(), haClave)) {
                haS.haDesbloquear(haClave);
                haContinuar.run();
            } else {
                haNotificador.haError("Esa no es la contraseña de propietario. El documento no se modificó.");
            }
        });
    }

    private HADocumentoPdf haDocumento() {
        return haActual == null ? null : haActual.haVista();
    }

    @FXML
    private void haOnAbrir() {
        FileChooser haSelector = new FileChooser();
        haSelector.setTitle("Abrir PDF");
        haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf", "*.PDF"));
        haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
        List<File> haElegidos = haSelector.showOpenMultipleDialog(haVentana);
        if (haElegidos != null && !haElegidos.isEmpty()) {
            haConfiguracion.setUltimaCarpeta(haElegidos.get(0).getParentFile());
            haAbrirArchivos(haElegidos);
        }
    }

    public void haAbrirArchivos(List<File> haArchivos) {
        for (File haArchivo : haArchivos) {
            Optional<HASesionEdicion> haYaAbierto = haAbiertos.stream().filter(haS -> haMismoArchivo(haS.haOriginal(), haArchivo)).findFirst();
            if (haYaAbierto.isPresent()) {
                haActivar(haYaAbierto.get());
            } else {
                haAbrir(haArchivo, null);
            }
        }
    }

    private void haAbrir(File haArchivo, String haContrasena) {
        haLblDetalle.setText("Abriendo " + haArchivo.getName() + "…");
        CompletableFuture.supplyAsync(() -> haPdfService.haAbrir(haArchivo, haContrasena))
                .whenComplete((haDocumento, haError) -> Platform.runLater(() -> {
                    if (haError == null) {
                        haAgregar(haDocumento);
                        return;
                    }
                    Throwable haCausa = haCausa(haError);
                    haActualizarBarraSuperior();
                    if (haCausa instanceof HAContrasenaRequeridaException haCr) {
                        haPedirContrasena(haArchivo, haCr.isIncorrecta()).ifPresent(haC -> haAbrir(haArchivo, haC));
                    } else if (haCausa instanceof HAPdfListoException) {
                        haNotificador.haError(haCausa.getMessage());
                    } else {
                        log.error("Error al abrir {}", haArchivo, haCausa);
                        haNotificador.haError("No se pudo abrir «" + haArchivo.getName() + "». El detalle quedó en el registro.");
                    }
                }));
    }

    private Optional<String> haPedirContrasena(File haArchivo, boolean haIncorrecta) {
        Dialog<String> haDialogo = new Dialog<>();
        haDialogo.initOwner(haVentana);
        haDialogo.setTitle("PDF protegido");
        haDialogo.setHeaderText(haIncorrecta
                ? "La contraseña no es correcta. Inténtalo de nuevo."
                : "«" + haArchivo.getName() + "» está protegido con contraseña.");
        PasswordField haCampo = new PasswordField();
        haCampo.setPromptText("Contraseña");
        haDialogo.getDialogPane().setContent(new VBox(8, new Label("Escribe la contraseña para abrirlo:"), haCampo));
        haDialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        haDialogo.setResultConverter(haB -> haB == ButtonType.OK ? haCampo.getText() : null);
        Platform.runLater(haCampo::requestFocus);
        return pe.pdflisto.ui.HADialogos.haMostrar(haDialogo);
    }

    private void haAgregar(HADocumentoPdf haDocumento) {
        HASesionEdicion haSesion = new HASesionEdicion(haDocumento);
        haAbiertos.add(haSesion);
        haActivar(haSesion);
        haRegistrarHistorial("Abrió " + haDocumento.haNombre() + " (" + haDocumento.haPaginas() + " pág.)");
        try {
            haRecientesService.haRegistrar(haDocumento.haArchivo(), haDocumento.haPaginas());
        } catch (RuntimeException haE) {
            log.warn("No se pudo actualizar la lista de recientes", haE);
        }
        if (pe.pdflisto.pdf.HACargaPdf.haEsPesado(haDocumento.haBytes(), haDocumento.haPaginas())) {
            haNotificador.haInfo("«" + haDocumento.haNombre() + "» es grande (" + HAArchivos.haTamanoLegible(haDocumento.haBytes()) + ", "
                    + haDocumento.haPaginas() + " páginas). Algunas operaciones pueden tardar: verás el avance arriba y podrás cancelar.");
        }
        haDocumento.haPaginasSinTexto().thenAccept(haSinTexto -> Platform.runLater(() -> {
            if (!haSinTexto.isEmpty() && haAbiertos.contains(haSesion)) {
                haOfrecerOcr(haSesion, haSinTexto.size(), haDocumento.haPaginas());
            }
        }));
    }

    private void haOfrecerOcr(HASesionEdicion haSesion, int haSinTexto, int haTotal) {
        Label haTitulo = new Label(haSinTexto == haTotal ? "Parece un PDF escaneado" : "Algunas páginas son escaneos");
        haTitulo.getStyleClass().add("comprobacion-nombre");
        Label haDetalle = new Label((haSinTexto == haTotal ? "Ninguna página" : haSinTexto + " de " + haTotal + " páginas")
                + (haSinTexto == haTotal ? " tiene" : " no tienen") + " texto: no se pueden buscar, copiar ni revisar sus datos. "
                + "El OCR reconoce el texto sin cambiar cómo se ve.");
        haDetalle.setWrapText(true);
        haDetalle.getStyleClass().add("texto-tenue");
        Button haReconocer = new Button("Reconocer texto (OCR)", new FontIcon(Feather.FILE_TEXT));
        haReconocer.getStyleClass().add(atlantafx.base.theme.Styles.ACCENT);
        Runnable haCerrar = haNotificador.haTarjeta(new VBox(8, haTitulo, haDetalle, haReconocer), javafx.util.Duration.seconds(15));
        haReconocer.setOnAction(haE -> {
            haCerrar.run();
            if (!haAbiertos.contains(haSesion)) {
                return;
            }
            if (haActual != haSesion) {
                haActivar(haSesion);
            }
            haOnOcr();
        });
    }

    private void haActivar(HASesionEdicion haSesion) {
        if (haActual != haSesion) {
            haPanelPedido.haDocumentoCambio();
            haPanelEntrega.haDocumentoCambio();
            haPanelTachado.haDocumentoCambio();
        }
        haActual = haSesion;
        haCambiandoDocumento = true;
        try {
            haCmbDocumentos.setValue(haSesion);
        } finally {
            haCambiandoDocumento = false;
        }
        haAjustarAncho = true;
        haBtnAjustarAncho.setSelected(true);
        haMostrarConDocumento();
        haRefrescarDocumento(0);
        haPanelAnotar.haDocumentoCambio();
        haPanelFormulario.haDocumentoCambio();
    }

    private void haRefrescarDocumento(int haPagina) {
        HADocumentoPdf haDoc = haDocumento();
        haCambiandoDocumento = true;
        try {
            haTxtBuscar.clear();
            haLimpiarBusqueda();
            haPaginaActual = -1;
            haListaMiniaturas.getSelectionModel().clearSelection();
            haListaMiniaturas.setItems(FXCollections.observableArrayList(
                    IntStream.range(0, haDoc.haPaginas()).boxed().toList()));
            haListaMiniaturas.refresh();
        } finally {
            haCambiandoDocumento = false;
        }
        haCargarMarcadores(haDoc);
        haActualizarBarraSuperior();
        haIrAPagina(Math.min(haPagina, haDoc.haPaginas() - 1));
        if (haAjustarAncho) {
            Platform.runLater(this::haAplicarAjusteAncho);
        }
    }

    private void haCargarMarcadores(HADocumentoPdf haDocumento) {
        haArbolMarcadores.getRoot().getChildren().clear();
        haLblSinMarcadores.setVisible(true);
        haDocumento.haMarcadores().whenComplete((haLista, haError) -> Platform.runLater(() -> {
            if (haDocumento != haDocumento()) {
                return;
            }
            if (haError != null) {
                log.warn("No se pudieron leer los marcadores de {}", haDocumento.haNombre(), haError);
                return;
            }
            for (HAMarcador haM : haLista) {
                haArbolMarcadores.getRoot().getChildren().add(haNodo(haM));
            }
            haLblSinMarcadores.setVisible(haLista.isEmpty());
        }));
    }

    private static TreeItem<HAMarcador> haNodo(HAMarcador haM) {
        TreeItem<HAMarcador> haItem = new TreeItem<>(haM);
        haM.haHijos().forEach(haH -> haItem.getChildren().add(haNodo(haH)));
        return haItem;
    }

    @Override
    public void haAplicar(String haDescripcion, String haPedido, List<String> haPasos, String haCodigo, HAProcesoVersion haProceso,
                        BiConsumer<HAResultado, Long> haAlTerminar) {
        HASesionEdicion haS = haActual;
        if (haS == null || !haLibre() || haBorradoresPendientes()) {
            return;
        }
        if (haS.haRequierePropietario()) {
            haPedirPropietario(haS, () -> haAplicar(haDescripcion, haPedido, haPasos, haCodigo, haProceso, haAlTerminar));
            return;
        }
        if (haS.haContrasenaActual() != null && !haS.haAvisoContrasenaDado()) {
            haNotificador.haPreguntar("Editar un PDF protegido",
                    "Para editarlo, las copias de trabajo se guardan sin contraseña. El original sigue protegido "
                            + "y puedes volver a ponerle contraseña al resultado en la pestaña Tachado → Contraseña.",
                    "Continuar", () -> {
                        haS.haMarcarAvisoContrasena();
                        haAplicar(haDescripcion, haPedido, haPasos, haCodigo, haProceso, haAlTerminar);
                    });
            return;
        }
        File haEntrada = haS.haArchivoActual();
        java.util.Optional<String> haSinEspacio = pe.pdflisto.sistema.HAEspacioDisco.haFaltaPara(haS.haCarpeta(), haEntrada.length());
        if (haSinEspacio.isPresent()) {
            haNotificador.haError(haSinEspacio.get());
            return;
        }
        String haContrasena = haS.haContrasenaActual();
        File haSalida = haS.haNuevoArchivo();
        int haPagina = haPaginaActual;
        HADocumentoPdf haAntes = haS.haVista();
        HAAntes haFoto = new HAAntes(haAntes.haPaginas(), haEntrada.length(),
                haMiniaturas.haObtener(haAntes, Math.max(0, haPagina), HA_ANCHO_TARJETA * 2));
        HAControl haControl = haOcupar(haDescripcion + "…", true);
        CompletableFuture.supplyAsync(() -> {
            HAResultado haR = haProceso.haEjecutar(haEntrada, haContrasena, haSalida, haControl);
            pe.pdflisto.verificacion.HAInformeVerificacion haInforme = null;
            if (!haR.haSinCambios() && haR.haEsperado() != null) {
                haControl.haMensaje("Verificando el resultado…");
                haInforme = haVerificadorOperacion.haVerificar(haDescripcion, haEntrada, haContrasena, haR.haArchivo(), haR.haContrasena(),
                        haR.haEsperado(), haS.haNombre());
            }
            haControl.haMensaje("Abriendo el resultado…");
            HADocumentoPdf haNuevo = haR.haSinCambios() ? null : haPdfService.haAbrir(haR.haArchivo(), haR.haContrasena());
            return new HAHecho(haR, haNuevo, haInforme);
        }).whenComplete((haHecho, haError) -> Platform.runLater(() -> {
            haLiberar();
            if (haError != null) {
                haBorrarSilencioso(haSalida);
                haMostrarError(haDescripcion, haError);
                return;
            }
            HAResultado haR = haHecho.haResultado();
            if (haR.haSinCambios()) {
                haBorrarSilencioso(haSalida);
                haNotificador.haInfo(haR.haDetalle() != null ? haR.haDetalle() : "No hubo cambios.");
                return;
            }
            if (!haAbiertos.contains(haS)) {
                haHecho.haDocumento().close();
                return;
            }
            haS.haAgregar(new HASesionEdicion.HAVersion(haR.haArchivo(), haDescripcion, haR.haContrasena(), haR.haContrasena() != null, haCodigo));
            haCambiarVista(haS, haHecho.haDocumento(), haPagina);
            Long haId = haRegistrarHistorial(haDescripcion, haPasos == null ? null : String.join(" | ", haPasos), null, haPedido);
            haMostrarAntesDespues(haDescripcion, haS, haFoto, haR, haHecho.haInforme());
            if (haAlTerminar != null) {
                haAlTerminar.accept(haR, haId);
            }
        }));
    }

    private record HAAntes(int haPaginas, long haBytes, CompletableFuture<javafx.scene.image.Image> haMiniatura) {
        javafx.scene.image.Image haImagen() {
            return haMiniatura.isDone() && !haMiniatura.isCompletedExceptionally() ? haMiniatura.join() : null;
        }
    }

    private void haMostrarAntesDespues(String haDescripcion, HASesionEdicion haS, HAAntes haAntes, HAResultado haR,
                                     pe.pdflisto.verificacion.HAInformeVerificacion haInforme) {
        HADocumentoPdf haNuevo = haS.haVista();
        int haP = Math.min(Math.max(0, haPaginaActual), haNuevo.haPaginas() - 1);
        javafx.scene.image.ImageView haVAntes = haMiniaturaTarjeta(haAntes.haImagen());
        javafx.scene.image.ImageView haVDespues = haMiniaturaTarjeta(null);
        haMiniaturas.haObtener(haNuevo, haP, HA_ANCHO_TARJETA * 2).thenAccept(haImg -> Platform.runLater(() -> haVDespues.setImage(haImg)));

        Label haTitulo = new Label(haDescripcion);
        haTitulo.getStyleClass().add("comprobacion-nombre");
        haTitulo.setWrapText(true);
        Label haLAntes = new Label("Antes");
        Label haLDespues = new Label("Después");
        haLAntes.getStyleClass().add("texto-tenue");
        haLDespues.getStyleClass().add("texto-tenue");
        HBox haFotos = new HBox(10, new VBox(4, haLAntes, haVAntes), new FontIcon(Feather.ARROW_RIGHT), new VBox(4, haLDespues, haVDespues));
        haFotos.setAlignment(Pos.CENTER_LEFT);
        String haCambioPeso = HAArchivos.haTamanoLegible(haAntes.haBytes()) + " → " + HAArchivos.haTamanoLegible(haR.haBytes());
        if (haAntes.haBytes() > 0 && haR.haBytes() != haAntes.haBytes()) {
            long haDif = Math.round(100.0 * (haR.haBytes() - haAntes.haBytes()) / haAntes.haBytes());
            haCambioPeso += " (" + (haDif > 0 ? "+" : "−") + Math.abs(haDif) + " %)";
        }
        Label haDatos = new Label("Páginas: " + haAntes.haPaginas() + " → " + haNuevo.haPaginas() + "\nPeso: " + haCambioPeso);
        haDatos.getStyleClass().add("texto-tenue");
        Button haDeshacer = new Button("Deshacer", new FontIcon(Feather.CORNER_UP_LEFT));
        haDeshacer.getStyleClass().add(atlantafx.base.theme.Styles.SMALL);
        VBox haCaja = new VBox(8, haTitulo, haFotos, haDatos);
        boolean haRevisar = haInforme != null && !haInforme.haCumple();
        if (haInforme != null) {
            haCaja.getChildren().add(haVerificacionTarjeta(haInforme));
        }
        haCaja.getChildren().add(haDeshacer);
        Runnable haCerrar = haNotificador.haTarjeta(haCaja, javafx.util.Duration.seconds(haRevisar ? 60 : 9));
        haDeshacer.setOnAction(haE -> {
            haCerrar.run();
            if (haActual == haS && haS.haPuedeDeshacer()) {
                haOnDeshacer();
            }
        });
    }

    private static VBox haVerificacionTarjeta(pe.pdflisto.verificacion.HAInformeVerificacion haInforme) {
        boolean haOk = haInforme.haCumple();
        Label haVeredicto = pe.pdflisto.ui.HAVistaComprobaciones.haVeredicto(haOk
                ? "Verificado: cumple" : "Revisar: el resultado no cumple todo lo esperado", haOk, false);
        List<pe.pdflisto.verificacion.HAComprobacion> haRelevantes = haInforme.haComprobaciones().stream()
                .filter(haC -> haC.haEstado() != pe.pdflisto.verificacion.HAComprobacion.HAEstado.NO_APLICA).toList();
        VBox haFilas = pe.pdflisto.ui.HAVistaComprobaciones.haFilas(haRelevantes);
        haFilas.setVisible(!haOk);
        haFilas.setManaged(!haOk);
        Button haDetalle = new Button(haOk ? "Ver qué se comprobó" : "Ocultar detalle");
        haDetalle.getStyleClass().addAll(atlantafx.base.theme.Styles.SMALL, atlantafx.base.theme.Styles.FLAT);
        haDetalle.setOnAction(haE -> {
            boolean haVer = !haFilas.isVisible();
            haFilas.setVisible(haVer);
            haFilas.setManaged(haVer);
            haDetalle.setText(haVer ? "Ocultar detalle" : "Ver qué se comprobó");
        });
        return new VBox(6, haVeredicto, haDetalle, haFilas);
    }

    private static javafx.scene.image.ImageView haMiniaturaTarjeta(javafx.scene.image.Image haImg) {
        javafx.scene.image.ImageView haV = new javafx.scene.image.ImageView(haImg);
        haV.setFitWidth(HA_ANCHO_TARJETA);
        haV.setFitHeight(HA_ANCHO_TARJETA * 1.414);
        haV.setPreserveRatio(true);
        haV.getStyleClass().add("miniatura-tarjeta");
        return haV;
    }

    @Override
    public void haAbrirPdf(List<File> haArchivos) {
        haAbrirArchivos(haArchivos);
    }

    @Override
    public void haExportar(String haDescripcion, HATrabajoArchivos haTrabajo, pe.pdflisto.verificacion.HAEsperadoArchivos haEsperado,
                         java.util.function.Consumer<List<File>> haAlTerminar) {
        if (!haLibre() || haBorradoresPendientes()) {
            return;
        }
        HAControl haControl = haOcupar(haDescripcion + "…", true);
        CompletableFuture.supplyAsync(() -> {
            List<File> haArchivos;
            try {
                haArchivos = haTrabajo.haEjecutar(haControl);
            } catch (RuntimeException haE) {
                throw haE;
            } catch (Exception haE) {
                throw new CompletionException(haE);
            }
            pe.pdflisto.verificacion.HAInformeVerificacion haInforme = null;
            if (haEsperado != null) {
                haControl.haMensaje("Verificando lo creado…");
                haInforme = haVerificadorArchivos.haVerificar(haDescripcion, haArchivos, haEsperado);
            }
            return new HAExportado(haArchivos, haInforme);
        }).whenComplete((haHecho, haError) -> Platform.runLater(() -> {
            haLiberar();
            if (haError != null) {
                haMostrarError(haDescripcion, haError);
                return;
            }
            List<File> haArchivos = haHecho.haArchivos();
            pe.pdflisto.verificacion.HAInformeVerificacion haInforme = haHecho.haInforme();
            File haCarpeta = haArchivos.get(0).getAbsoluteFile().getParentFile();
            haRegistrarHistorial(haDescripcion + " → " + haArchivos.size() + (haArchivos.size() == 1 ? " archivo" : " archivos"));
            boolean haRevisar = haInforme != null && !haInforme.haCumple();
            if (haInforme != null) {
                Label haTitulo = new Label(haDescripcion);
                haTitulo.getStyleClass().add("comprobacion-nombre");
                haTitulo.setWrapText(true);
                haNotificador.haTarjeta(new VBox(8, haTitulo, haVerificacionTarjeta(haInforme)),
                        javafx.util.Duration.seconds(haRevisar ? 60 : 9));
            }
            if (haAlTerminar != null) {
                haAlTerminar.accept(haArchivos);
                return;
            }
            String haMensaje = haArchivos.size() == 1
                    ? "Se creó «" + haArchivos.get(0).getName() + "» en " + haCarpeta.getAbsolutePath() + "."
                    : "Se crearon " + haArchivos.size() + " archivos en " + haCarpeta.getAbsolutePath() + ".";
            if (haRevisar) {
                haNotificador.haElegir("Revisar el resultado", haMensaje + " La verificación encontró algo que no cumple: "
                                + "mira el detalle en la tarjeta de la derecha.",
                        "Cerrar", null, "Abrir carpeta", () -> haExportador.haAbrirEnSistema(haCarpeta));
            } else {
                haNotificador.haExitoConAcciones("Listo", haMensaje + (haInforme != null
                                ? " Se volvió a leer lo creado y cumple lo esperado." : " Cada uno fue verificado."),
                        "Cerrar", null, "Abrir carpeta", () -> haExportador.haAbrirEnSistema(haCarpeta));
            }
        }));
    }

    private record HAExportado(List<File> haArchivos, pe.pdflisto.verificacion.HAInformeVerificacion haInforme) {
    }

    @Override
    public <T> void haEnSegundoPlano(String haDescripcion, java.util.function.Function<HAControl, T> haTrabajo,
                                   java.util.function.Consumer<T> haAlTerminar) {
        if (!haLibre()) {
            return;
        }
        HAControl haControl = haOcupar(haDescripcion + "…", true);
        CompletableFuture.supplyAsync(() -> haTrabajo.apply(haControl))
                .whenComplete((haResultado, haError) -> Platform.runLater(() -> {
                    haLiberar();
                    if (haError != null) {
                        haMostrarError(haDescripcion, haError);
                        return;
                    }
                    haAlTerminar.accept(haResultado);
                }));
    }

    private void haAplicarOperacion(HAOperacionPdf haOp, java.util.function.Consumer<HAResultado> haAlTerminar) {
        haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC), haAlTerminar);
    }

    private void haCambiarVista(HASesionEdicion haS, HADocumentoPdf haNuevo, int haPagina) {
        HADocumentoPdf haViejo = haS.haCambiarVista(haNuevo);
        if (haViejo != null) {
            haMiniaturas.haOlvidar(haViejo);
            haViejo.close();
        }
        if (haS == haActual) {
            haPanelTachado.haVersionCambio();
            haRefrescarDocumento(Math.max(0, haPagina));
            haPanelAnotar.haVersionCambio();
            haPanelFormulario.haVersionCambio();
        }
    }

    private boolean haBorradoresPendientes() {
        if (haPanelAnotar.haBloqueaOtrasOperaciones()) {
            int haN = haPanelAnotar.haCantidadPendientes();
            haNotificador.haAdvertencia("Tienes " + haN + (haN == 1 ? " anotación" : " anotaciones")
                    + " sin aplicar: pulsa «Aplicar» o «Descartar» en la pestaña Anotar.");
            return true;
        }
        return false;
    }

    private boolean haLibre() {
        if (haOcupado) {
            haNotificador.haAdvertencia("Espera a que termine: " + haLblTrabajo.getText());
            return false;
        }
        return true;
    }

    private HAControl haOcupar(String haTexto, boolean haCancelable) {
        haOcupado = true;
        long[] haUltimo = {0};
        HAControl haControl = new HAControl((haFraccion, haMensaje) -> {
            long haAhora = System.nanoTime();
            if (haFraccion >= 0 && haFraccion < 0.999 && haAhora - haUltimo[0] < 40_000_000L) {
                return;
            }
            haUltimo[0] = haAhora;
            Platform.runLater(() -> {
                if (!haOcupado) {
                    return;
                }
                haBarraTrabajo.setProgress(haFraccion < 0 ? javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS : haFraccion);
                if (haMensaje != null) {
                    haLblTrabajo.setText(haMensaje);
                }
            });
        });
        haTrabajoActual = haControl;
        haLblTrabajo.setText(haTexto);
        haBarraTrabajo.setProgress(javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);
        haBtnCancelarTrabajo.setVisible(haCancelable);
        haBtnCancelarTrabajo.setManaged(haCancelable);
        haBtnCancelarTrabajo.setDisable(false);
        haIndicadorTrabajo.setVisible(true);
        haIndicadorTrabajo.setManaged(true);
        haActualizarBotones();
        return haControl;
    }

    private HAControl haOcupar(String haTexto) {
        return haOcupar(haTexto, false);
    }

    private void haLiberar() {
        haOcupado = false;
        haTrabajoActual = null;
        haIndicadorTrabajo.setVisible(false);
        haIndicadorTrabajo.setManaged(false);
        haActualizarBotones();
    }

    @FXML
    private void haOnCancelarTrabajo() {
        HAControl haC = haTrabajoActual;
        if (haC != null) {
            haC.haCancelar();
            haLblTrabajo.setText("Cancelando…");
            haBtnCancelarTrabajo.setDisable(true);
        }
    }

    private void haMostrarError(String haDescripcion, Throwable haError) {
        haMostrarError(haDescripcion, haError, pe.pdflisto.edicion.HACarpetaTrabajo.haRuta());
    }

    private void haMostrarError(String haDescripcion, Throwable haError, java.nio.file.Path haCarpeta) {
        Throwable haC = haCausa(haError);
        if (pe.pdflisto.sistema.HAEspacioDisco.haEsDiscoLleno(haError, haCarpeta)) {
            log.error("Disco lleno en «{}»", haDescripcion, haC);
            haNotificador.haError(pe.pdflisto.sistema.HAEspacioDisco.haMensajeDiscoLleno(haDescripcion, haCarpeta));
        } else if (haC instanceof HACanceladoException) {
            haNotificador.haInfo(haC.getMessage());
        } else if (haC instanceof HAPdfListoException) {
            haNotificador.haError(haC.getMessage());
        } else if (haC instanceof OutOfMemoryError) {
            log.error("Sin memoria en «{}»", haDescripcion, haC);
            haNotificador.haError("No alcanzó la memoria para «" + haDescripcion + "». Cierra otros documentos e inténtalo de nuevo.");
        } else {
            log.error("Fallo «{}»", haDescripcion, haC);
            haNotificador.haError("No se pudo completar «" + haDescripcion + "». El detalle quedó en el registro.");
        }
    }

    @FXML private void haOnRotarDerecha() { haRotar(90); }
    @FXML private void haOnRotarIzquierda() { haRotar(270); }

    private void haRotar(int haGrados) {
        if (haActual == null) {
            return;
        }
        List<Integer> haSel = haPaginasSeleccionadas();
        haAplicarOperacion(new HARotar(haSel, haGrados), haR -> haSeleccionar(haSel));
    }

    @FXML
    private void haOnSubirPaginas() {
        if (haActual == null) {
            return;
        }
        List<Integer> haSel = haPaginasSeleccionadas();
        List<Integer> haOrden = HAOrdenPaginas.haSubir(haDocumento().haPaginas(), haSel);
        haReordenar(haOrden, haSel, "Subir " + haDescribir(haSel));
    }

    @FXML
    private void haOnBajarPaginas() {
        if (haActual == null) {
            return;
        }
        List<Integer> haSel = haPaginasSeleccionadas();
        List<Integer> haOrden = HAOrdenPaginas.haBajar(haDocumento().haPaginas(), haSel);
        haReordenar(haOrden, haSel, "Bajar " + haDescribir(haSel));
    }

    private void haMoverPaginas(List<Integer> haPaginas, int haDestino) {
        if (haActual == null) {
            return;
        }
        List<Integer> haOrden = HAOrdenPaginas.haMover(haDocumento().haPaginas(), haPaginas, haDestino);
        haReordenar(haOrden, haPaginas, "Mover " + haDescribir(haPaginas));
    }

    @FXML
    private void haOnDuplicarPaginas() {
        if (haActual == null) {
            return;
        }
        List<Integer> haSel = haPaginasSeleccionadas();
        List<Integer> haOrden = HAOrdenPaginas.haDuplicar(haDocumento().haPaginas(), haSel);
        haReordenar(haOrden, haSel, "Duplicar " + haDescribir(haSel));
    }

    @FXML
    private void haOnEliminarPaginas() {
        if (haActual == null) {
            return;
        }
        List<Integer> haSel = haPaginasSeleccionadas();
        List<Integer> haOrden;
        try {
            haOrden = HAOrdenPaginas.haEliminar(haDocumento().haPaginas(), haSel);
        } catch (HAPdfListoException haE) {
            haNotificador.haError(haE.getMessage());
            return;
        }
        String haCuales = haDescribir(haSel);
        haNotificador.haConfirmar("Eliminar " + haCuales,
                "Se eliminará" + (haSel.size() == 1 ? " la " : "n las ") + haCuales + " de la copia de trabajo. "
                        + "El original no se toca y puedes deshacerlo con Ctrl+Z.",
                "Eliminar", () -> haAplicarOperacion(new HAReorganizar(haOrden, "Eliminar " + haCuales),
                        haR -> haIrAPagina(Math.min(haSel.get(0), haR.haPaginas() - 1))));
    }

    private void haReordenar(List<Integer> haOrden, List<Integer> haSeguir, String haDescripcion) {
        if (haOrden.equals(HAOrdenPaginas.haIdentidad(haDocumento().haPaginas()))) {
            return;
        }
        List<Integer> haNuevasPosiciones = HAOrdenPaginas.haPosiciones(haOrden, haSeguir);
        haAplicarOperacion(new HAReorganizar(haOrden, haDescripcion), haR -> haSeleccionar(haNuevasPosiciones));
    }

    private void haSeleccionar(List<Integer> haPaginas) {
        if (haPaginas.isEmpty() || haActual == null) {
            return;
        }
        haIrAPagina(haPaginas.get(0));
        haCambiandoDocumento = true;
        try {
            var haModelo = haListaMiniaturas.getSelectionModel();
            haModelo.clearSelection();
            haPaginas.stream().filter(haP -> haP < haDocumento().haPaginas()).forEach(haModelo::select);
        } finally {
            haCambiandoDocumento = false;
        }
    }

    private static String haDescribir(List<Integer> haPaginas) {
        return haPaginas.size() == 1 ? "página " + (haPaginas.get(0) + 1) : "páginas " + HARangoPaginas.haFormatear(haPaginas);
    }

    @FXML private void haOnUnir() { if (haActual != null && haLibre()) haFormularios.haUnir(this); }
    @FXML private void haOnDividir() { if (haActual != null && haLibre()) haFormularios.haDividir(this); }
    @FXML private void haOnExtraer() { if (haActual != null && haLibre()) haFormularios.haExtraer(this); }
    @FXML private void haOnComprimir() { if (haActual != null && haLibre()) haFormularios.haComprimir(this); }
    @FXML private void haOnNumerar() { if (haActual != null && haLibre()) haFormularios.haNumerar(this); }
    @FXML private void haOnMarcaDeAgua() { if (haActual != null && haLibre()) haFormularios.haMarcaDeAgua(this); }
    @FXML private void haOnOcr() { if (haActual != null && haLibre()) haFormularios.haOcr(this); }
    @FXML private void haOnTamano() { if (haActual != null && haLibre()) haFormularios.haTamano(this); }
    @FXML private void haOnFacturas() { if (haLibre()) haFormularios.haFacturas(this); }
    @FXML private void haOnEncabezado() { if (haActual != null && haLibre()) haFormularios.haEncabezadoPie(this); }

    @Override
    public void haMostrarPestanaPedido() {
        haPanelDerecho.getSelectionModel().select(0);
    }

    @FXML
    private void haOnDeshacer() {
        if (haPanelAnotar.haQuitarUltima()) {
            return;
        }
        HASesionEdicion haS = haActual;
        if (haS == null || !haS.haPuedeDeshacer() || !haLibre()) {
            return;
        }
        String haQue = haS.haQueDeshace();
        haS.haDeshacer();
        haRecargarVersion(haS, "Deshecho: " + haQue, haS::haRehacer);
    }

    @FXML
    private void haOnRehacer() {
        HASesionEdicion haS = haActual;
        if (haS == null || !haS.haPuedeRehacer() || !haLibre() || haBorradoresPendientes()) {
            return;
        }
        String haQue = haS.haQueRehace();
        haS.haRehacer();
        haRecargarVersion(haS, "Rehecho: " + haQue, haS::haDeshacer);
    }

    private void haRecargarVersion(HASesionEdicion haS, String haMensaje, Runnable haRevertir) {
        int haPagina = haPaginaActual;
        haOcupar("Cargando…");
        haAbrirVersionActual(haS, haDoc -> {
            haLiberar();
            haCambiarVista(haS, haDoc, haPagina);
            haRegistrarHistorial(haMensaje);
            haNotificador.haInfo(haMensaje);
        }, () -> {
            haLiberar();
            haRevertir.run();
            haActualizarBarraSuperior();
        });
    }

    @FXML
    private void haOnCerrarDocumento() {
        if (haActual == null || !haLibre()) {
            return;
        }
        HASesionEdicion haS = haActual;
        if (haS.haHayCambios()) {
            haNotificador.haConfirmar("Cerrar sin guardar",
                    "«" + haS.haNombre() + "» tiene cambios que no guardaste. Si lo cierras se perderán "
                            + "(el original no se modifica).",
                    "Cerrar sin guardar", () -> haCerrarSesion(haS));
        } else {
            haCerrarSesion(haS);
        }
    }

    private void haCerrarSesion(HASesionEdicion haS) {
        haAbiertos.remove(haS);
        if (haS.haVista() != null) {
            haMiniaturas.haOlvidar(haS.haVista());
        }
        haS.haCerrar();
        haPanelPedido.haDocumentoCambio();
        haPanelEntrega.haDocumentoCambio();
        haPanelTachado.haDocumentoCambio();
        haPanelAnotar.haOlvidar(haS);
        if (haAbiertos.isEmpty()) {
            haActual = null;
            haVisor.haLimpiar();
            haPanelFormulario.haDocumentoCambio();
            haListaMiniaturas.setItems(FXCollections.observableArrayList());
            haArbolMarcadores.getRoot().getChildren().clear();
            haMostrarSinDocumento();
        } else {
            haActivar(haAbiertos.get(0));
        }
    }

    @FXML
    private void haOnGuardarCopia() {
        if (haActual == null || !haLibre()) {
            return;
        }
        HASesionEdicion haS = haActual;
        HADocumentoPdf haDocumento = haS.haVista();
        FileChooser haSelector = new FileChooser();
        haSelector.setTitle("Guardar copia");
        haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
        File haCarpeta = haS.haOriginal().getParentFile();
        haSelector.setInitialDirectory(haCarpeta != null && haCarpeta.isDirectory() ? haCarpeta : haConfiguracion.haUltimaCarpeta());
        haSelector.setInitialFileName(haPanelEntrega.haNombreSugerido(haS));
        File haElegido = haSelector.showSaveDialog(haVentana);
        if (haElegido == null) {
            return;
        }
        File haDestino = haElegido.getName().toLowerCase().endsWith(".pdf")
                ? haElegido : new File(haElegido.getParentFile(), haElegido.getName() + ".pdf");
        if (haMismoArchivo(haDestino, haS.haOriginal())) {
            haNotificador.haError("Por seguridad el original nunca se sobrescribe. Elige otro nombre.");
            return;
        }
        long haLibreDestino = pe.pdflisto.sistema.HAEspacioDisco.haLibre(haDestino.getParentFile().toPath());
        if (haLibreDestino < haS.haArchivoActual().length() + 10 * pe.pdflisto.sistema.HAEspacioDisco.HA_MB) {
            haNotificador.haError("No hay espacio suficiente en " + haDestino.getParent() + " para guardar la copia (quedan "
                    + pe.pdflisto.operaciones.HAArchivos.haTamanoLegible(haLibreDestino) + " libres). Elige otra carpeta o libera espacio.");
            return;
        }
        haOcupar("Guardando copia…");
        CompletableFuture.runAsync(() -> haPdfService.haGuardarCopia(haDocumento, haDestino, haS.haOriginal()))
                .whenComplete((haOk, haError) -> Platform.runLater(() -> {
                    haLiberar();
                    if (haError != null) {
                        haMostrarError("Guardar copia", haError, haDestino.getParentFile().toPath());
                        return;
                    }
                    haS.haMarcarGuardado();
                    haActualizarBarraSuperior();
                    haNotificador.haExito("Copia guardada y verificada: " + haDestino.getName());
                    String haCombinacion = haS.haCodigosAplicados();
                    File haOrigen = haS.haOriginal();
                    var haRegistro = haHistorialService.haRegistrar(haOrigen, "Guardó una copia", null, haDestino, null, haCombinacion);
                    haPanelHistorial.haRefrescar();
                    if (haRegistro != null) {
                        haPanelEntrega.haDespuesDeGuardar(haCombinacion);
                    }
                }));
    }

    public void haCerrarTodo() {
        haAbiertos.forEach(HASesionEdicion::haCerrar);
        pe.pdflisto.edicion.HACarpetaTrabajo.haLimpiarSobrantes();
    }

    private void haIrAPagina(int haPagina) {
        HADocumentoPdf haDoc = haDocumento();
        if (haDoc == null) {
            return;
        }
        int haP = Math.max(0, Math.min(haPagina, haDoc.haPaginas() - 1));
        boolean haCambia = haP != haPaginaActual;
        haPaginaActual = haP;
        haVisor.haMostrar(haDoc, haP);
        haAplicarResaltados();

        haTxtPagina.setText(String.valueOf(haP + 1));
        haLblTotalPaginas.setText("/ " + haDoc.haPaginas());
        haLblPie.setText("Página " + (haP + 1) + " de " + haDoc.haPaginas());

        Integer haSeleccionada = haListaMiniaturas.getSelectionModel().getSelectedItem();
        if (haSeleccionada == null || haSeleccionada != haP) {
            haCambiandoDocumento = true;
            try {
                haListaMiniaturas.getSelectionModel().clearAndSelect(haP);
            } finally {
                haCambiandoDocumento = false;
            }
            haListaMiniaturas.scrollTo(Math.max(0, haP - 1));
        }
        if (haCambia) {
            haScrollVisor.setVvalue(0);
        }
    }

    @FXML private void haOnPaginaAnterior() { haIrAPagina(haPaginaActual - 1); }
    @FXML private void haOnPaginaSiguiente() { haIrAPagina(haPaginaActual + 1); }

    @FXML
    private void haOnIrAPagina() {
        try {
            haIrAPagina(Integer.parseInt(haTxtPagina.getText().trim()) - 1);
        } catch (NumberFormatException haE) {
            haTxtPagina.setText(String.valueOf(haPaginaActual + 1));
        }
        haScrollVisor.requestFocus();
    }

    private void haAlGirarRueda(ScrollEvent haE) {
        HADocumentoPdf haDoc = haDocumento();
        if (haDoc == null) {
            return;
        }
        if (haE.isShortcutDown()) {
            if (haE.getDeltaY() != 0) {
                setZoom(haVisor.haZoomProperty().get() * (haE.getDeltaY() > 0 ? 1.1 : 1 / 1.1), false);
            }
            haE.consume();
            return;
        }
        long haAhora = System.currentTimeMillis();
        if (haAhora - haUltimoCambioPorRueda < 350) {
            return;
        }
        boolean haCabeEntera = haVisor.getHeight() <= haScrollVisor.getViewportBounds().getHeight() + 1;
        if (haE.getDeltaY() < 0 && (haCabeEntera || haScrollVisor.getVvalue() >= 0.999) && haPaginaActual < haDoc.haPaginas() - 1) {
            haUltimoCambioPorRueda = haAhora;
            haIrAPagina(haPaginaActual + 1);
            haE.consume();
        } else if (haE.getDeltaY() > 0 && (haCabeEntera || haScrollVisor.getVvalue() <= 0.001) && haPaginaActual > 0) {
            haUltimoCambioPorRueda = haAhora;
            haIrAPagina(haPaginaActual - 1);
            Platform.runLater(() -> haScrollVisor.setVvalue(1));
            haE.consume();
        }
    }

    @FXML
    private void haOnAcercar() {
        double haZ = haVisor.haZoomProperty().get();
        for (double haNivel : HA_NIVELES_ZOOM) {
            if (haNivel > haZ + 0.01) {
                setZoom(haNivel, false);
                return;
            }
        }
    }

    @FXML
    private void haOnAlejar() {
        double haZ = haVisor.haZoomProperty().get();
        for (int haI = HA_NIVELES_ZOOM.length - 1; haI >= 0; haI--) {
            if (HA_NIVELES_ZOOM[haI] < haZ - 0.01) {
                setZoom(HA_NIVELES_ZOOM[haI], false);
                return;
            }
        }
    }

    @FXML
    private void haOnAjustarAncho() {
        haAjustarAncho = true;
        haBtnAjustarAncho.setSelected(true);
        haAplicarAjusteAncho();
    }

    private void haAplicarAjusteAncho() {
        HADocumentoPdf haDoc = haDocumento();
        if (haDoc == null || haPaginaActual < 0) {
            return;
        }
        double haDisponible = haScrollVisor.getViewportBounds().getWidth();
        if (haDisponible <= 0) {
            return;
        }
        double haAnchoPagina = haDoc.haTamano(haPaginaActual).haAncho() * HAVisorPagina.HA_PIXELES_POR_PUNTO;
        setZoom((haDisponible - HA_MARGEN_VISOR) / haAnchoPagina, true);
    }

    private void setZoom(double haZoom, boolean haPorAjuste) {
        if (haActual == null) {
            return;
        }
        if (!haPorAjuste) {
            haAjustarAncho = false;
            haBtnAjustarAncho.setSelected(false);
        }
        double haZ = Math.max(0.1, Math.min(4.0, haZoom));
        haVisor.haZoomProperty().set(haZ);
        haLblZoom.setText(Math.round(haZ * 100) + " %");
    }

    @FXML
    private void haOnPantallaCompleta() {
        if (haVentana != null) {
            haVentana.setFullScreen(!haVentana.isFullScreen());
        }
    }

    @FXML
    private void haOnBuscar() {
        String haConsulta = haTxtBuscar.getText().trim();
        HADocumentoPdf haDocumento = haDocumento();
        if (haDocumento == null || haConsulta.isEmpty()) {
            haLimpiarBusqueda();
            return;
        }
        if (haConsulta.equals(haUltimaBusqueda) && !haCoincidencias.isEmpty()) {
            haOnResultadoSiguiente();
            return;
        }
        haUltimaBusqueda = haConsulta;
        haLblResultados.setText("Buscando…");
        haDocumento.haBuscar(haConsulta).whenComplete((haLista, haError) -> Platform.runLater(() -> {
            if (haDocumento != haDocumento() || !haConsulta.equals(haUltimaBusqueda)) {
                return;
            }
            if (haError != null) {
                log.warn("Fallo la busqueda en {}", haDocumento.haNombre(), haError);
                haLblResultados.setText("Error");
                haNotificador.haError("No se pudo buscar en este PDF.");
                return;
            }
            haCoincidencias = haLista;
            if (haLista.isEmpty()) {
                haIndiceCoincidencia = -1;
                haLblResultados.setText("Sin resultados");
                haAplicarResaltados();
            } else {
                haIndiceCoincidencia = haPrimeraDesdePaginaActual();
                haMostrarCoincidencia();
            }
        }));
    }

    @FXML
    private void haOnResultadoSiguiente() {
        if (haCoincidencias.isEmpty()) {
            haOnBuscar();
            return;
        }
        haIndiceCoincidencia = (haIndiceCoincidencia + 1) % haCoincidencias.size();
        haMostrarCoincidencia();
    }

    @FXML
    private void haOnResultadoAnterior() {
        if (haCoincidencias.isEmpty()) {
            return;
        }
        haIndiceCoincidencia = (haIndiceCoincidencia - 1 + haCoincidencias.size()) % haCoincidencias.size();
        haMostrarCoincidencia();
    }

    private int haPrimeraDesdePaginaActual() {
        for (int haI = 0; haI < haCoincidencias.size(); haI++) {
            if (haCoincidencias.get(haI).haPagina() >= haPaginaActual) {
                return haI;
            }
        }
        return 0;
    }

    private void haMostrarCoincidencia() {
        HACoincidencia haC = haCoincidencias.get(haIndiceCoincidencia);
        haLblResultados.setText((haIndiceCoincidencia + 1) + " de " + haCoincidencias.size());
        if (haC.haPagina() != haPaginaActual) {
            haIrAPagina(haC.haPagina());
        } else {
            haAplicarResaltados();
        }
        Platform.runLater(() -> haDesplazarHasta(haC.haAreas().get(0)));
    }

    private void haAplicarResaltados() {
        List<HAAreaTexto> haEnPagina = new ArrayList<>();
        for (HACoincidencia haC : haCoincidencias) {
            if (haC.haPagina() == haPaginaActual) {
                haEnPagina.addAll(haC.haAreas());
            }
        }
        List<HAAreaTexto> haActiva = haIndiceCoincidencia >= 0 && haIndiceCoincidencia < haCoincidencias.size()
                && haCoincidencias.get(haIndiceCoincidencia).haPagina() == haPaginaActual
                ? haCoincidencias.get(haIndiceCoincidencia).haAreas() : List.of();
        haVisor.setResaltados(haEnPagina, haActiva);
    }

    private void haLimpiarBusqueda() {
        haCoincidencias = List.of();
        haIndiceCoincidencia = -1;
        haUltimaBusqueda = "";
        haLblResultados.setText("");
        haVisor.setResaltados(List.of(), List.of());
    }

    private void haDesplazarHasta(HAAreaTexto haArea) {
        haScrollVisor.layout();
        Bounds haB = haVisor.haUbicacion(haArea);
        double haAltoContenido = haVisor.getHeight();
        double haAltoVista = haScrollVisor.getViewportBounds().getHeight();
        if (haAltoContenido > haAltoVista) {
            haScrollVisor.setVvalue(haLimitar((haB.getMinY() - haAltoVista / 3) / (haAltoContenido - haAltoVista)));
        }
        double haAnchoContenido = haVisor.getWidth();
        double haAnchoVista = haScrollVisor.getViewportBounds().getWidth();
        if (haAnchoContenido > haAnchoVista) {
            haScrollVisor.setHvalue(haLimitar((haB.getMinX() - haAnchoVista / 3) / (haAnchoContenido - haAnchoVista)));
        }
    }

    private static double haLimitar(double haV) {
        return Math.max(0, Math.min(1, haV));
    }

    @FXML
    private void haOnCambiarTema() {
        boolean haOscuro = !haRaiz.getStyleClass().contains("dark");
        haAplicarTema(haOscuro);
        haConfiguracion.setTemaOscuro(haOscuro);
    }

    private void haAplicarTema(boolean haOscuro) {
        Application.setUserAgentStylesheet(haOscuro
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        haRaiz.getStyleClass().remove("dark");
        if (haOscuro) {
            haRaiz.getStyleClass().add("dark");
        }
        ((FontIcon) haBtnTema.getGraphic()).setIconCode(haOscuro ? Feather.SUN : Feather.MOON);
    }

    private void haMostrarConDocumento() {
        haVisorContenedor.setVisible(true);
        haEstadoVacio.setVisible(false);
        haActualizarBarraSuperior();
    }

    private void haMostrarSinDocumento() {
        haVisorContenedor.setVisible(false);
        haEstadoVacio.setVisible(true);
        haActualizarBarraSuperior();
        haLlenarRecientesInicio();
    }

    private void haActualizarBarraSuperior() {
        haActualizarBotones();
        HADocumentoPdf haDoc = haDocumento();
        if (haDoc == null) {
            haLblArchivo.setText("Ningún archivo abierto");
            haLblDetalle.setText("Abre un PDF o arrástralo a la ventana");
            if (haVentana != null) {
                haVentana.setTitle("PDF Listo");
            }
            return;
        }
        haLblArchivo.setText(haActual.haNombre() + (haActual.haHayCambios() ? "  •" : ""));
        String haDetalle = haDoc.haPaginas() + (haDoc.haPaginas() == 1 ? " página" : " páginas")
                + " · " + HAArchivos.haTamanoLegible(haDoc.haBytes());
        if (haDoc.haCifrado()) {
            haDetalle += " · protegido";
        }
        if (haActual.haHayCambios()) {
            haDetalle += " · cambios sin guardar";
        }
        haLblDetalle.setText(haDetalle);
        if (haVentana != null) {
            haVentana.setTitle((haActual.haHayCambios() ? "• " : "") + haActual.haNombre() + " — PDF Listo");
        }
    }

    private void haActualizarBotones() {
        boolean haHay = haActual != null;
        haBtnGuardarCopia.setDisable(!haHay || haOcupado);
        haBtnCerrarDocumento.setDisable(!haHay);
        haBtnCerrarDocumento.setVisible(haHay);
        haBtnCerrarDocumento.setManaged(haHay);
        haMenuHerramientas.setDisable(!haHay || haOcupado);
        haMenuConvertir.setDisable(haOcupado);
        haHerramientasPaginas.setDisable(!haHay || haOcupado);
        haBtnDeshacer.setDisable(!haHay || haOcupado || !haActual.haPuedeDeshacer());
        haBtnRehacer.setDisable(!haHay || haOcupado || !haActual.haPuedeRehacer());
        haTtDeshacer.setText(haHay && haActual.haPuedeDeshacer() ? "Deshacer: " + haActual.haQueDeshace() + " (Ctrl+Z)" : "Deshacer (Ctrl+Z)");
        haTtRehacer.setText(haHay && haActual.haPuedeRehacer() ? "Rehacer: " + haActual.haQueRehace() + " (Ctrl+Y)" : "Rehacer (Ctrl+Y)");
    }

    private void haErrorAlDibujar(Throwable haError) {
        Throwable haC = haCausa(haError);
        if (haC instanceof CancellationException) {
            return;
        }
        log.warn("No se pudo dibujar la pagina {}", haPaginaActual + 1, haC);
        haNotificador.haError("No se pudo mostrar la página " + (haPaginaActual + 1) + ". Puede estar dañada.");
    }

    private Long haRegistrarHistorial(String haTexto) {
        return haRegistrarHistorial(haTexto, null, null, null);
    }

    private Long haRegistrarHistorial(String haTexto, String haPasos, File haDestino, String haPedido) {
        File haOrigen = haActual != null ? haActual.haOriginal() : null;
        var haRegistro = haHistorialService.haRegistrar(haOrigen, haTexto, haPasos, haDestino, haPedido);
        haPanelHistorial.haRefrescar();
        return haRegistro == null ? null : haRegistro.getId();
    }

    private List<HAArchivoReciente> haLeerRecientes() {
        try {
            return haRecientesService.haRecientes();
        } catch (RuntimeException haE) {
            log.warn("No se pudo leer la lista de recientes", haE);
            return List.of();
        }
    }

    private void haLlenarMenuRecientes() {
        haBtnRecientes.getItems().clear();
        List<HAArchivoReciente> haLista = haLeerRecientes();
        if (haLista.isEmpty()) {
            MenuItem haVacio = new MenuItem("Sin archivos recientes");
            haVacio.setDisable(true);
            haBtnRecientes.getItems().add(haVacio);
            return;
        }
        for (HAArchivoReciente haR : haLista) {
            MenuItem haItem = new MenuItem(haR.getNombre() + "   (" + haCarpetaCorta(haR.getRuta()) + ")");
            haItem.setOnAction(haE -> haAbrirReciente(haR));
            haBtnRecientes.getItems().add(haItem);
        }
    }

    private void haLlenarRecientesInicio() {
        haListaRecientesInicio.getChildren().clear();
        List<HAArchivoReciente> haLista = haLeerRecientes();
        haLblTituloRecientes.setVisible(!haLista.isEmpty());
        for (HAArchivoReciente haR : haLista) {
            Label haNombre = new Label(haR.getNombre());
            haNombre.getStyleClass().add("reciente-nombre");
            Label haCarpeta = new Label(haCarpetaCorta(haR.getRuta()) + " · " + haR.getPaginas() + " pág.");
            haCarpeta.getStyleClass().add("texto-tenue");
            FontIcon haIcono = new FontIcon(Feather.FILE_TEXT);
            HBox haFila = new HBox(10, haIcono, new VBox(1, haNombre, haCarpeta));
            haFila.setAlignment(Pos.CENTER_LEFT);
            haFila.getStyleClass().add("fila-reciente");
            haFila.setOnMouseClicked(haE -> haAbrirReciente(haR));
            haListaRecientesInicio.getChildren().add(haFila);
        }
    }

    private void haAbrirReciente(HAArchivoReciente haR) {
        File haArchivo = new File(haR.getRuta());
        if (!haArchivo.isFile()) {
            haNotificador.haAdvertencia("«" + haR.getNombre() + "» ya no está en su carpeta.");
            haRecientesService.haQuitar(haR.getRuta());
            haLlenarRecientesInicio();
            return;
        }
        haAbrirArchivos(List.of(haArchivo));
    }

    private static String haCarpetaCorta(String haRuta) {
        File haPadre = new File(haRuta).getParentFile();
        if (haPadre == null) {
            return "";
        }
        String haTexto = haPadre.getAbsolutePath();
        return haTexto.length() > 42 ? "…" + haTexto.substring(haTexto.length() - 41) : haTexto;
    }

    private static boolean haMismoArchivo(File haA, File haB) {
        try {
            return haA.getCanonicalFile().equals(haB.getCanonicalFile());
        } catch (IOException haE) {
            return haA.getAbsoluteFile().equals(haB.getAbsoluteFile());
        }
    }

    private static Throwable haCausa(Throwable haError) {
        Throwable haC = haError;
        while ((haC instanceof CompletionException || haC instanceof java.util.concurrent.ExecutionException)
                && haC.getCause() != null) {
            haC = haC.getCause();
        }
        return haC;
    }

    private static void haBorrarSilencioso(File haArchivo) {
        try {
            Files.deleteIfExists(haArchivo.toPath());
        } catch (IOException haIgnorado) {
        }
    }
}
