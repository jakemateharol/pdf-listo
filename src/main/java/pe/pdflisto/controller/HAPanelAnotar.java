package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.geometry.VPos;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.anotaciones.HAAnotacion;
import pe.pdflisto.anotaciones.HAAnotar;
import pe.pdflisto.anotaciones.HABibliotecaFirmas;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.pdf.HADocumentoPdf;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.pdf.HATamanoPagina;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.ui.HAVisorPagina;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAPanelAnotar {
    private enum HAHerr {
        RESALTAR("Resaltar", Feather.EDIT, "Arrastra sobre el texto que quieres resaltar."),
        SUBRAYAR("Subrayar", Feather.UNDERLINE, "Arrastra sobre el texto que quieres subrayar."),
        TACHAR("Tachar texto", Feather.MINUS, "Arrastra sobre el texto para rayarlo. Ojo: el texto sigue ahí; "
                + "para BORRAR datos usa la pestaña Tachado."),
        NOTA("Nota", Feather.MESSAGE_CIRCLE, "Haz clic donde quieres dejar una nota (comentario)."),
        RECTANGULO("Rectángulo", Feather.SQUARE, "Arrastra para dibujar un rectángulo."),
        ELIPSE("Elipse", Feather.CIRCLE, "Arrastra para dibujar una elipse o círculo."),
        LINEA("Línea", Feather.SLASH, "Arrastra para dibujar una línea."),
        FLECHA("Flecha", Feather.ARROW_UP_RIGHT, "Arrastra desde el inicio hasta la punta de la flecha."),
        DIBUJO("Dibujo libre", Feather.PEN_TOOL, "Mantén presionado y dibuja con el mouse."),
        TEXTO("Texto", Feather.TYPE, "Haz clic donde quieres escribir (sirve para llenar PDF que no son formularios)."),
        IMAGEN("Imagen", Feather.IMAGE, "Haz clic (o arrastra para elegir el tamaño) donde va la imagen."),
        FIRMA("Firma", Feather.FEATHER, "Elige tu firma y haz clic (o arrastra) donde va. Antes de aplicar se te pedirá confirmar."),
        SELLO("Sello", Feather.AWARD, "Haz clic (o arrastra) donde va el sello."),
        FECHA("Fecha", Feather.CALENDAR, "Haz clic donde va la fecha de hoy.");

        final String haNombre;
        final Feather haIcono;
        final String haAyuda;

        HAHerr(String haNombre, Feather haIcono, String haAyuda) {
            this.haNombre = haNombre;
            this.haIcono = haIcono;
            this.haAyuda = haAyuda;
        }
    }

    private record HAColorNombrado(String haNombre, Color haColor) {
    }

    private static final List<HAColorNombrado> HA_COLORES = List.of(
            new HAColorNombrado("Amarillo", new Color(255, 213, 0)),
            new HAColorNombrado("Verde", new Color(46, 160, 67)),
            new HAColorNombrado("Rojo", new Color(207, 34, 46)),
            new HAColorNombrado("Azul", new Color(9, 105, 218)),
            new HAColorNombrado("Negro", new Color(30, 30, 30)));
    private static final DateTimeFormatter HA_HOY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HAMotorPdf haMotor;
    private final HABibliotecaFirmas haBiblioteca;
    private final HANotificador haNotificador;
    private final HAConfiguracion haConfiguracion;

    private HAEditor haEditor;
    private final Map<HASesionEdicion, List<HAAnotacion>> haPendientes = new IdentityHashMap<>();
    private HAAnotacion haBorrador;
    private boolean haAplicando;

    private final ToggleGroup haGrupoHerramientas = new ToggleGroup();
    private final ToggleGroup haGrupoColores = new ToggleGroup();
    private final Label haLblHerramienta = new Label();
    private final Label haLblAyuda = new Label();
    private final Slider haGrosor = new Slider(1, 8, 2);
    private final Spinner<Integer> haTamanoLetra = new Spinner<>(6, 72, 12);
    private final ComboBox<HABibliotecaFirmas.HAFirma> haFirmas = new ComboBox<>();
    private final TextField haTextoSello = new TextField("RECIBIDO");
    private final CheckBox haSelloConFecha = new CheckBox("Con la fecha de hoy");
    private final VBox haOpcionesFirma = new VBox(6);
    private final VBox haOpcionesSello = new VBox(6);
    private final VBox haListaPendientes = new VBox(4);
    private final Label haTituloPendientes = new Label();
    private final HBox haAccionesPendientes = new HBox(8);
    private File haImagenElegida;
    private final Map<File, Image> haImagenes = new HashMap<>();

    public void haMontar(VBox haContenedor, HAEditor haEditor) {
        this.haEditor = haEditor;
        Label haTitulo = new Label("Anotar y firmar");
        haTitulo.getStyleClass().add("panel-titulo");

        FlowPane haHerramientas = new FlowPane(4, 4);
        for (HAHerr haH : HAHerr.values()) {
            ToggleButton haB = new ToggleButton(null, new FontIcon(haH.haIcono));
            haB.getStyleClass().addAll(Styles.BUTTON_ICON, "boton-herramienta");
            haB.setUserData(haH);
            haB.setToggleGroup(haGrupoHerramientas);
            haB.setTooltip(new Tooltip(haH.haNombre));
            haHerramientas.getChildren().add(haB);
        }
        haGrupoHerramientas.selectedToggleProperty().addListener((haO, haA, haB) -> haCambiarHerramienta());
        haLblHerramienta.getStyleClass().add("comprobacion-nombre");
        haLblAyuda.getStyleClass().add("texto-tenue");
        haLblAyuda.setWrapText(true);

        HBox haColores = new HBox(6);
        for (HAColorNombrado haC : HA_COLORES) {
            ToggleButton haB = new ToggleButton();
            haB.getStyleClass().add("muestra-color");
            haB.setStyle(String.format("-fx-background-color: rgb(%d,%d,%d);", haC.haColor().getRed(), haC.haColor().getGreen(), haC.haColor().getBlue()));
            haB.setUserData(haC);
            haB.setToggleGroup(haGrupoColores);
            haB.setTooltip(new Tooltip(haC.haNombre()));
            haColores.getChildren().add(haB);
        }
        haGrupoColores.selectToggle(haGrupoColores.getToggles().get(0));
        haGrupoColores.selectedToggleProperty().addListener((haO, haA, haB) -> {
            if (haB == null) {
                haGrupoColores.selectToggle(haA);
            }
        });
        haTamanoLetra.setEditable(true);
        haTamanoLetra.setPrefWidth(80);
        haGrosor.setMajorTickUnit(1);
        haGrosor.setSnapToTicks(true);
        HBox haFilaGrosor = new HBox(8, new Label("Grosor"), haGrosor, new Label("Letra"), haTamanoLetra);
        haFilaGrosor.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(haGrosor, Priority.ALWAYS);

        haFirmas.setMaxWidth(Double.MAX_VALUE);
        haFirmas.setPromptText("Elige una firma");
        Button haNueva = new Button("Nueva firma…", new FontIcon(Feather.PLUS));
        haNueva.setOnAction(haE -> new HADialogoFirma(haBiblioteca, haNotificador, haEditor.haVentana(), haConfiguracion)
                .haMostrar().ifPresent(haF -> {
                    haRecargarFirmas();
                    haFirmas.getItems().stream().filter(haX -> haX.haNombre().equals(haF.haNombre())).findFirst()
                            .ifPresent(haX -> haFirmas.getSelectionModel().select(haX));
                }));
        Button haGuardada = new Button("Usar posición guardada", new FontIcon(Feather.MAP_PIN));
        haGuardada.setOnAction(haE -> haUsarPosicionGuardada());
        Button haBorrar = new Button(null, new FontIcon(Feather.TRASH_2));
        haBorrar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        haBorrar.setTooltip(new Tooltip("Borrar esta firma de la biblioteca"));
        haBorrar.setOnAction(haE -> haBorrarFirma());
        HBox haFilaFirma = new HBox(6, haFirmas, haBorrar);
        HBox.setHgrow(haFirmas, Priority.ALWAYS);
        haOpcionesFirma.getChildren().addAll(haFilaFirma, new HBox(6, haNueva, haGuardada));

        haSelloConFecha.setSelected(true);
        haOpcionesSello.getChildren().addAll(new Label("Texto del sello"), haTextoSello, haSelloConFecha);

        haTituloPendientes.getStyleClass().add("panel-titulo");
        Button haAplicar = new Button("Aplicar", new FontIcon(Feather.CHECK));
        haAplicar.getStyleClass().add(Styles.ACCENT);
        haAplicar.setOnAction(haE -> haAplicar());
        Button haDescartar = new Button("Descartar");
        haDescartar.getStyleClass().add(Styles.FLAT);
        haDescartar.setOnAction(haE -> haDescartarTodo());
        haAccionesPendientes.getChildren().addAll(haAplicar, haDescartar);

        haContenedor.getChildren().setAll(haTitulo, haHerramientas, haLblHerramienta, haLblAyuda, haColores, haFilaGrosor,
                haOpcionesFirma, haOpcionesSello, new javafx.scene.control.Separator(),
                haTituloPendientes, haListaPendientes, haAccionesPendientes,
                HAFormulariosHerramientas.haNota("Lo que dibujas es un borrador hasta pulsar «Aplicar». Ctrl+Z quita el último."));
        haRecargarFirmas();
        haCambiarHerramienta();
        haRefrescarLista();
    }

    public boolean haBloqueaOtrasOperaciones() {
        return !haAplicando && !haPendientesActuales().isEmpty();
    }

    public int haCantidadPendientes() {
        return haPendientesActuales().size();
    }

    public List<String> haDocumentosConPendientes() {
        return haPendientes.entrySet().stream().filter(haE -> !haE.getValue().isEmpty()).map(haE -> haE.getKey().haNombre()).toList();
    }

    public boolean haQuitarUltima() {
        List<HAAnotacion> haLista = haPendientesActuales();
        if (haLista.isEmpty()) {
            return false;
        }
        HAAnotacion haA = haLista.remove(haLista.size() - 1);
        haNotificador.haInfo("Se quitó: " + haA.haDescripcion());
        haRefrescarLista();
        return true;
    }

    public void haDocumentoCambio() {
        haBorrador = null;
        haRefrescarLista();
    }

    public void haVersionCambio() {
        haBorrador = null;
        if (haEditor != null) {
            haEditor.haRepintarVisor();
        }
    }

    public void haOlvidar(HASesionEdicion haS) {
        haPendientes.remove(haS);
        haRefrescarLista();
    }

    private List<HAAnotacion> haPendientesActuales() {
        HASesionEdicion haS = haEditor == null ? null : haEditor.haSesion();
        return haS == null ? new ArrayList<>() : haPendientes.computeIfAbsent(haS, haK -> new ArrayList<>());
    }

    private void haCambiarHerramienta() {
        HAHerr haH = haHerramienta();
        haLblHerramienta.setText(haH == null ? "Elige una herramienta" : haH.haNombre);
        haLblAyuda.setText(haH == null ? "Las marcas (resaltado, notas, formas) quedan como anotaciones que otros lectores "
                + "también muestran. Texto, imagen, firma y sello quedan fijos en la página." : haH.haAyuda);
        haOpcionesFirma.setVisible(haH == HAHerr.FIRMA);
        haOpcionesFirma.setManaged(haH == HAHerr.FIRMA);
        haOpcionesSello.setVisible(haH == HAHerr.SELLO);
        haOpcionesSello.setManaged(haH == HAHerr.SELLO);
        if (haH == HAHerr.IMAGEN) {
            haImagenElegida = null;
        }
        if (haH == HAHerr.RESALTAR && haColor().equals(new Color(30, 30, 30))) {
            haGrupoColores.selectToggle(haGrupoColores.getToggles().get(0));
        }
        if (haEditor != null) {
            haEditor.haHerramientaEnVisor(haH == null ? null : new HATrazador());
            haEditor.haPintorEnVisor(this::haPintar);
        }
    }

    private HAHerr haHerramienta() {
        var haT = haGrupoHerramientas.getSelectedToggle();
        return haT == null ? null : (HAHerr) haT.getUserData();
    }

    private Color haColor() {
        var haT = haGrupoColores.getSelectedToggle();
        return haT == null ? HA_COLORES.get(0).haColor() : ((HAColorNombrado) haT.getUserData()).haColor();
    }

    private float haGrosor() {
        return (float) haGrosor.getValue();
    }

    private float haTamanoLetra() {
        try {
            return Math.max(4, Math.min(144, Integer.parseInt(haTamanoLetra.getEditor().getText().trim())));
        } catch (NumberFormatException haE) {
            return 12;
        }
    }

    private void haAgregar(HAAnotacion haA) {
        haPendientesActuales().add(haA);
        haRefrescarLista();
    }

    private void haRefrescarLista() {
        List<HAAnotacion> haLista = haPendientesActuales();
        haTituloPendientes.setText(haLista.isEmpty() ? "Sin borradores" : "Por aplicar (" + haLista.size() + ")");
        haListaPendientes.getChildren().clear();
        for (HAAnotacion haA : List.copyOf(haLista)) {
            Label haL = new Label(haA.haDescripcion() + "  ·  pág. " + (haA.haPagina() + 1));
            haL.setWrapText(true);
            HBox.setHgrow(haL, Priority.ALWAYS);
            haL.setMaxWidth(Double.MAX_VALUE);
            Button haVer = haBoton(Feather.EYE, "Ver", () -> haEditor.haMostrarPagina(haA.haPagina()));
            Button haQuitar = haBoton(Feather.X, "Quitar", () -> {
                haPendientesActuales().remove(haA);
                haRefrescarLista();
            });
            HBox haFila = new HBox(4, haL, haVer, haQuitar);
            haFila.setAlignment(Pos.CENTER_LEFT);
            haFila.getStyleClass().add("fila-dato");
            haListaPendientes.getChildren().add(haFila);
        }
        haAccionesPendientes.setDisable(haLista.isEmpty());
        if (haEditor != null) {
            haEditor.haRepintarVisor();
        }
    }

    private void haAplicar() {
        HASesionEdicion haS = haEditor.haSesion();
        List<HAAnotacion> haLista = List.copyOf(haPendientesActuales());
        if (haS == null || haLista.isEmpty()) {
            return;
        }
        List<HAAnotacion.HAFirma> haConFirma = haLista.stream().filter(haA -> haA instanceof HAAnotacion.HAFirma)
                .map(haA -> (HAAnotacion.HAFirma) haA).toList();
        Runnable haEjecutar = () -> {
            HAAnotar haOp = new HAAnotar(haLista, null);
            haAplicando = true;
            haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC), haR -> {
                haAplicando = false;
                haPendientes.remove(haS);
                HADocumentoPdf haDoc = haS.haVista();
                for (HAAnotacion.HAFirma haF : haConFirma) {
                    if (haF.haPagina() < haDoc.haPaginas()) {
                        HATamanoPagina haT = haDoc.haTamano(haF.haPagina());
                        haBiblioteca.haRecordarPosicion(haF.haNombre(), haF.haArea(), haT.haAncho(), haT.haAlto());
                    }
                }
                haRefrescarLista();
                haRecargarFirmas();
                haNotificador.haExito("Se aplicaron " + haLista.size() + (haLista.size() == 1 ? " anotación." : " anotaciones."));
            });
        };
        if (haConFirma.isEmpty()) {
            haEjecutar.run();
        } else {
            String haPaginas = haConFirma.stream().map(haF -> String.valueOf(haF.haPagina() + 1)).distinct()
                    .reduce((haA, haB) -> haA + ", " + haB).orElse("");
            haNotificador.haPreguntar("Firmar el documento",
                    "Vas a poner la firma «" + haConFirma.get(0).haNombre() + "» en la página " + haPaginas + ". Quedará fija en "
                            + "una versión nueva (el original no cambia). Esta firma es una imagen: no es una firma digital "
                            + "con certificado.", "Firmar", haEjecutar);
        }
    }

    private void haDescartarTodo() {
        int haN = haPendientesActuales().size();
        if (haN == 0) {
            return;
        }
        haNotificador.haConfirmar("Descartar borradores", "Se perderán " + haN + (haN == 1 ? " anotación" : " anotaciones")
                + " que aún no aplicaste.", "Descartar", () -> {
                    haPendientesActuales().clear();
                    haRefrescarLista();
                });
    }

    private void haRecargarFirmas() {
        HABibliotecaFirmas.HAFirma haElegida = haFirmas.getValue();
        haFirmas.setItems(FXCollections.observableArrayList(haBiblioteca.haListar()));
        if (haElegida != null) {
            haFirmas.getItems().stream().filter(haF -> haF.haNombre().equals(haElegida.haNombre())).findFirst()
                    .ifPresent(haF -> haFirmas.getSelectionModel().select(haF));
        } else if (!haFirmas.getItems().isEmpty()) {
            haFirmas.getSelectionModel().select(0);
        }
    }

    private void haUsarPosicionGuardada() {
        HABibliotecaFirmas.HAFirma haF = haFirmas.getValue();
        if (haF == null || haEditor.haSesion() == null) {
            haNotificador.haAdvertencia("Elige una firma (o crea una nueva) y abre un PDF.");
            return;
        }
        if (haF.haPosicion() == null) {
            haNotificador.haInfo("Esta firma aún no tiene una posición guardada: colócala una vez con clic y se recordará.");
            return;
        }
        int haP = haEditor.haPaginaVisible();
        HATamanoPagina haT = haEditor.haSesion().haVista().haTamano(haP);
        haAgregar(new HAAnotacion.HAFirma(haP, haF.haPosicion().haEn(haT.haAncho(), haT.haAlto()), haF.haArchivo(), haF.haNombre()));
    }

    private void haBorrarFirma() {
        HABibliotecaFirmas.HAFirma haF = haFirmas.getValue();
        if (haF == null) {
            return;
        }
        haNotificador.haConfirmar("Borrar firma", "Se borrará «" + haF.haNombre() + "» de tu biblioteca. Los PDF ya firmados no cambian.",
                "Borrar", () -> {
                    haBiblioteca.haEliminar(haF);
                    haFirmas.setValue(null);
                    haRecargarFirmas();
                });
    }

    private final class HATrazador implements HAVisorPagina.HAHerramienta {
        private int haPag;
        private double haX0;
        private double haY0;
        private List<double[]> haPuntos;
        private HALectorTexto.HAPaginaTexto haTexto;
        private Integer haLetraInicio;

        @Override
        public void haPresionar(int haPagina, double haX, double haY) {
            if (haEditor.haSesion() == null) {
                return;
            }
            haPag = haPagina;
            haX0 = haX;
            haY0 = haY;
            haBorrador = null;
            HAHerr haH = haHerramienta();
            if (haH == HAHerr.RESALTAR || haH == HAHerr.SUBRAYAR || haH == HAHerr.TACHAR) {
                haTexto = haTextoDe(haPagina);
                haLetraInicio = haCercana(haTexto, haX, haY);
            } else if (haH == HAHerr.DIBUJO) {
                haPuntos = new ArrayList<>();
                haPuntos.add(new double[]{haX, haY});
            }
        }

        @Override
        public void haArrastrar(int haPagina, double haX, double haY) {
            if (haPagina != haPag || haEditor.haSesion() == null) {
                return;
            }
            HAHerr haH = haHerramienta();
            if (haH == null) {
                return;
            }
            switch (haH) {
                case RESALTAR, SUBRAYAR, TACHAR -> haBorrador = haMarcado(haX, haY);
                case RECTANGULO, ELIPSE, LINEA, FLECHA -> haBorrador = haForma(haH, haX, haY);
                case DIBUJO -> {
                    double[] haUltimo = haPuntos.get(haPuntos.size() - 1);
                    if (Math.hypot(haX - haUltimo[0], haY - haUltimo[1]) > 1) {
                        haPuntos.add(new double[]{haX, haY});
                    }
                    haBorrador = new HAAnotacion.HATrazo(haPag, List.copyOf(haPuntos), haColor(), haGrosor());
                }
                case IMAGEN, FIRMA, SELLO -> haBorrador = haConArea(haH, haRectangulo(haX, haY, haProporcion(haH)), false);
                default -> { }
            }
            haEditor.haRepintarVisor();
        }

        @Override
        public void haSoltar(int haPagina, double haX, double haY) {
            if (haEditor.haSesion() == null) {
                return;
            }
            HAHerr haH = haHerramienta();
            boolean haClic = Math.hypot(haX - haX0, haY - haY0) < 3;
            haBorrador = null;
            if (haH == null) {
                return;
            }
            try {
                switch (haH) {
                    case RESALTAR, SUBRAYAR, TACHAR -> {
                        HAAnotacion haA = haClic ? null : haMarcado(haX, haY);
                        if (haA != null) {
                            haAgregar(haA);
                        }
                    }
                    case NOTA -> haPedirTexto("Nota", "Escribe el comentario:", "")
                            .ifPresent(haT -> haAgregar(new HAAnotacion.HANota(haPag, haX0, haY0, haT, haColor())));
                    case TEXTO -> haPedirTexto("Texto", "Escribe el texto que irá sobre la página:", "")
                            .ifPresent(haT -> haAgregar(new HAAnotacion.HATexto(haPag, haX0, haY0, haT, haTamanoLetra(), haColor())));
                    case FECHA -> haAgregar(new HAAnotacion.HATexto(haPag, haX0, haY0, LocalDate.now().format(HA_HOY), haTamanoLetra(), haColor()));
                    case RECTANGULO, ELIPSE, LINEA, FLECHA -> {
                        if (!haClic) {
                            haAgregar(haForma(haH, haX, haY));
                        }
                    }
                    case DIBUJO -> {
                        if (haPuntos != null && haPuntos.size() > 1) {
                            haAgregar(new HAAnotacion.HATrazo(haPag, List.copyOf(haPuntos), haColor(), haGrosor()));
                        }
                    }
                    case IMAGEN, FIRMA, SELLO -> {
                        HAAnotacion haA = haConArea(haH, haClic ? haAreaPorDefecto(haH, haX, haY) : haRectangulo(haX, haY, haProporcion(haH)), true);
                        if (haA != null) {
                            haAgregar(haA);
                        }
                    }
                }
            } catch (HAPdfListoException haE) {
                haNotificador.haAdvertencia(haE.getMessage());
            }
            haEditor.haRepintarVisor();
        }

        private HAAnotacion haMarcado(double haX, double haY) {
            HAAnotacion.HATipoMarcado haTipo = switch (haHerramienta()) {
                case SUBRAYAR -> HAAnotacion.HATipoMarcado.SUBRAYADO;
                case TACHAR -> HAAnotacion.HATipoMarcado.TACHADO;
                default -> HAAnotacion.HATipoMarcado.RESALTADO;
            };
            if (haTexto != null && haLetraInicio != null) {
                Integer haFin = haCercana(haTexto, haX, haY);
                int haA = Math.min(haLetraInicio, haFin == null ? haLetraInicio : haFin);
                int haB = Math.max(haLetraInicio, haFin == null ? haLetraInicio : haFin);
                List<HAAreaTexto> haAreas = haTexto.haAreas(haA, haB + 1);
                if (!haAreas.isEmpty()) {
                    return new HAAnotacion.HAMarcado(haTipo, haPag, haAreas, haColor(), haTexto.haTexto().substring(haA, haB + 1).replace('\n', ' '));
                }
            }
            HAAreaTexto haR = new HAAreaTexto(Math.min(haX0, haX), Math.min(haY0, haY), Math.abs(haX - haX0), Math.abs(haY - haY0));
            return haR.haAncho() < 2 ? null : new HAAnotacion.HAMarcado(haTipo, haPag, List.of(haR), haColor(), null);
        }

        private HAAnotacion.HAForma haForma(HAHerr haH, double haX, double haY) {
            HAAnotacion.HATipoForma haTipo = switch (haH) {
                case ELIPSE -> HAAnotacion.HATipoForma.ELIPSE;
                case LINEA -> HAAnotacion.HATipoForma.LINEA;
                case FLECHA -> HAAnotacion.HATipoForma.FLECHA;
                default -> HAAnotacion.HATipoForma.RECTANGULO;
            };
            return new HAAnotacion.HAForma(haTipo, haPag, haX0, haY0, haX, haY, haColor(), haGrosor());
        }

        private HAAreaTexto haRectangulo(double haX, double haY, double haProporcion) {
            double haW = Math.abs(haX - haX0);
            double haH = haProporcion > 0 ? haW / haProporcion : Math.abs(haY - haY0);
            double haTop = haProporcion > 0 && haY < haY0 ? haY0 - haH : Math.min(haY0, haY);
            return new HAAreaTexto(Math.min(haX0, haX), haTop, haW, haH);
        }

        private HAAreaTexto haAreaPorDefecto(HAHerr haH, double haX, double haY) {
            double haAncho = haH == HAHerr.SELLO ? 160 : haH == HAHerr.FIRMA ? 140 : 180;
            if (haH == HAHerr.FIRMA && haFirmas.getValue() != null && haFirmas.getValue().haPosicion() != null) {
                HATamanoPagina haT = haEditor.haSesion().haVista().haTamano(haPag);
                haAncho = haFirmas.getValue().haPosicion().haAncho() * haT.haAncho();
            }
            double haP = haProporcion(haH);
            double haAlto = haH == HAHerr.SELLO ? 60 : haAncho / (haP > 0 ? haP : 1.5);
            return new HAAreaTexto(haX - haAncho / 2, haY - haAlto / 2, haAncho, haAlto);
        }

        private HAAnotacion haConArea(HAHerr haH, HAAreaTexto haArea, boolean haDefinitivo) {
            if (haArea.haAncho() < 4 && !haDefinitivo) {
                return null;
            }
            return switch (haH) {
                case IMAGEN -> {
                    File haF = haImagenElegida != null ? haImagenElegida : haDefinitivo ? haElegirImagen() : null;
                    yield haF == null ? null : new HAAnotacion.HAImagen(haPag, haArea, haF);
                }
                case FIRMA -> {
                    HABibliotecaFirmas.HAFirma haF = haFirmas.getValue();
                    if (haF == null) {
                        if (haDefinitivo) {
                            throw new HAPdfListoException("Primero elige una firma o crea una con «Nueva firma…».");
                        }
                        yield null;
                    }
                    yield new HAAnotacion.HAFirma(haPag, haArea, haF.haArchivo(), haF.haNombre());
                }
                case SELLO -> {
                    String haT = haTextoSello.getText().isBlank() ? "RECIBIDO" : haTextoSello.getText();
                    yield new HAAnotacion.HASello(haPag, haArea, haT, haColor(), haSelloConFecha.isSelected() ? LocalDate.now().format(HA_HOY) : null);
                }
                default -> null;
            };
        }
    }

    private double haProporcion(HAHerr haH) {
        File haF = haH == HAHerr.FIRMA && haFirmas.getValue() != null ? haFirmas.getValue().haArchivo()
                : haH == HAHerr.IMAGEN ? haImagenElegida : null;
        Image haImg = haF == null ? null : haImagen(haF);
        return haImg == null || haImg.getHeight() == 0 ? 0 : haImg.getWidth() / haImg.getHeight();
    }

    private File haElegirImagen() {
        FileChooser haSelector = new FileChooser();
        haSelector.setTitle("Imagen para poner en la página");
        haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
        haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
        File haF = haSelector.showOpenDialog(haEditor.haVentana());
        if (haF != null) {
            try {
                if (ImageIO.read(haF) == null) {
                    throw new HAPdfListoException("«" + haF.getName() + "» no es una imagen que se pueda leer. Usa PNG o JPG.");
                }
            } catch (java.io.IOException haE) {
                throw new HAPdfListoException("No se pudo leer la imagen «" + haF.getName() + "».");
            }
            haImagenElegida = haF;
        }
        return haF;
    }

    private HALectorTexto.HAPaginaTexto haTextoDe(int haPagina) {
        try {
            return haEditor.haSesion().haVista().haTextoPagina(haPagina).get(4, TimeUnit.SECONDS);
        } catch (Exception haE) {
            return null;
        }
    }

    private static Integer haCercana(HALectorTexto.HAPaginaTexto haT, double haX, double haY) {
        if (haT == null) {
            return null;
        }
        Integer haMejor = null;
        double haDistancia = 40;
        for (int haI = 0; haI < haT.haLetras().size(); haI++) {
            var haL = haT.haLetras().get(haI);
            if (haL == null) {
                continue;
            }
            HAAreaTexto haA = HALectorTexto.haAreaDe(haL);
            double haDx = Math.max(0, Math.max(haA.haX() - haX, haX - (haA.haX() + haA.haAncho())));
            double haDy = Math.max(0, Math.max(haA.haY() - haY, haY - (haA.haY() + haA.haAlto())));
            double haD = Math.hypot(haDx, haDy * 2);
            if (haD < haDistancia) {
                haDistancia = haD;
                haMejor = haI;
            }
        }
        return haMejor;
    }

    private Optional<String> haPedirTexto(String haTitulo, String haMensaje, String haInicial) {
        javafx.scene.control.Dialog<String> haD = new javafx.scene.control.Dialog<>();
        haD.initOwner(haEditor.haVentana());
        haD.setTitle(haTitulo);
        haD.setHeaderText(haMensaje);
        javafx.scene.control.TextArea haArea = new javafx.scene.control.TextArea(haInicial);
        haArea.setPrefRowCount(4);
        haArea.setWrapText(true);
        haD.getDialogPane().setContent(haArea);
        haD.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        haD.setResultConverter(haB -> haB == javafx.scene.control.ButtonType.OK ? haArea.getText() : null);
        javafx.application.Platform.runLater(haArea::requestFocus);
        return pe.pdflisto.ui.HADialogos.haMostrar(haD).filter(haT -> !haT.isBlank());
    }

    private List<Node> haPintar(int haPagina, double haF) {
        List<Node> haNodos = new ArrayList<>();
        List<HAAnotacion> haTodos = new ArrayList<>(haPendientesActuales());
        if (haBorrador != null) {
            haTodos.add(haBorrador);
        }
        for (HAAnotacion haA : haTodos) {
            if (haA.haPagina() == haPagina) {
                haNodos.addAll(haNodos(haA, haF));
            }
        }
        return haNodos;
    }

    private List<Node> haNodos(HAAnotacion haA, double haF) {
        List<Node> haN = new ArrayList<>();
        if (haA instanceof HAAnotacion.HAMarcado haM) {
            for (HAAreaTexto haR : haM.haAreas()) {
                switch (haM.haTipo()) {
                    case RESALTADO -> {
                        Rectangle haRect = new Rectangle(haR.haX() * haF, haR.haY() * haF, haR.haAncho() * haF, haR.haAlto() * haF);
                        haRect.setFill(haFx(haM.haColor(), 0.38));
                        haN.add(haRect);
                    }
                    case SUBRAYADO -> haN.add(haLinea(haR.haX(), haR.haY() + haR.haAlto(), haR.haX() + haR.haAncho(), haR.haY() + haR.haAlto(), haM.haColor(), 1.5, haF));
                    case TACHADO -> haN.add(haLinea(haR.haX(), haR.haY() + haR.haAlto() * 0.55, haR.haX() + haR.haAncho(), haR.haY() + haR.haAlto() * 0.55, haM.haColor(), 1.5, haF));
                }
            }
        } else if (haA instanceof HAAnotacion.HANota haNota) {
            FontIcon haIcono = new FontIcon(Feather.MESSAGE_SQUARE);
            haIcono.setIconSize((int) Math.max(12, 18 * haF));
            haIcono.setIconColor(haFx(haNota.haColor(), 1));
            haIcono.setLayoutX(haNota.haX() * haF);
            haIcono.setLayoutY(haNota.haY() * haF + 16 * haF);
            haN.add(haIcono);
        } else if (haA instanceof HAAnotacion.HAForma haFo) {
            HAAreaTexto haR = haFo.haArea();
            switch (haFo.haTipo()) {
                case RECTANGULO -> {
                    Rectangle haRect = new Rectangle(haR.haX() * haF, haR.haY() * haF, haR.haAncho() * haF, haR.haAlto() * haF);
                    haTrazo(haRect, haFo.haColor(), haFo.haGrosor(), haF);
                    haN.add(haRect);
                }
                case ELIPSE -> {
                    Ellipse haE = new Ellipse((haR.haX() + haR.haAncho() / 2) * haF, (haR.haY() + haR.haAlto() / 2) * haF, haR.haAncho() / 2 * haF, haR.haAlto() / 2 * haF);
                    haTrazo(haE, haFo.haColor(), haFo.haGrosor(), haF);
                    haN.add(haE);
                }
                case LINEA -> haN.add(haLinea(haFo.haX1(), haFo.haY1(), haFo.haX2(), haFo.haY2(), haFo.haColor(), haFo.haGrosor(), haF));
                case FLECHA -> {
                    haN.add(haLinea(haFo.haX1(), haFo.haY1(), haFo.haX2(), haFo.haY2(), haFo.haColor(), haFo.haGrosor(), haF));
                    double haAng = Math.atan2(haFo.haY2() - haFo.haY1(), haFo.haX2() - haFo.haX1());
                    double haLargo = 10 + haFo.haGrosor() * 2;
                    for (double haD : new double[]{Math.PI * 0.82, -Math.PI * 0.82}) {
                        haN.add(haLinea(haFo.haX2(), haFo.haY2(), haFo.haX2() + Math.cos(haAng + haD) * haLargo, haFo.haY2() + Math.sin(haAng + haD) * haLargo,
                                haFo.haColor(), haFo.haGrosor(), haF));
                    }
                }
            }
        } else if (haA instanceof HAAnotacion.HATrazo haT) {
            Polyline haP = new Polyline();
            haT.haPuntos().forEach(haPt -> haP.getPoints().addAll(haPt[0] * haF, haPt[1] * haF));
            haTrazo(haP, haT.haColor(), haT.haGrosor(), haF);
            haN.add(haP);
        } else if (haA instanceof HAAnotacion.HATexto haT) {
            Text haTexto = new Text(haT.haX() * haF, haT.haY() * haF, haT.haTexto());
            haTexto.setTextOrigin(VPos.TOP);
            haTexto.setFont(Font.font("Arial", haT.haTamano() * haF));
            haTexto.setLineSpacing(haT.haTamano() * 0.25 * haF);
            haTexto.setFill(haFx(haT.haColor(), 1));
            haN.add(haTexto);
        } else if (haA instanceof HAAnotacion.HAImagen haIm) {
            haN.add(haVista(haIm.haArchivo(), haIm.haArea(), haF));
        } else if (haA instanceof HAAnotacion.HAFirma haFi) {
            haN.add(haVista(haFi.haImagen(), haFi.haArea(), haF));
        } else if (haA instanceof HAAnotacion.HASello haS) {
            HAAreaTexto haR = haS.haArea();
            Rectangle haMarco = new Rectangle(haR.haX() * haF, haR.haY() * haF, haR.haAncho() * haF, haR.haAlto() * haF);
            haTrazo(haMarco, haS.haColor(), 2, haF);
            Text haTexto = new Text(haS.haTexto().toUpperCase() + (haS.haFecha() != null ? "\n" + haS.haFecha() : ""));
            haTexto.setFont(Font.font("Arial", FontWeight.BOLD, Math.max(6, haR.haAlto() * (haS.haFecha() != null ? 0.32 : 0.45) * haF)));
            haTexto.setTextAlignment(TextAlignment.CENTER);
            haTexto.setFill(haFx(haS.haColor(), 1));
            haTexto.setWrappingWidth(haR.haAncho() * haF);
            haTexto.setX(haR.haX() * haF);
            haTexto.setY((haR.haY() + haR.haAlto() / 2) * haF);
            haTexto.setTextOrigin(VPos.CENTER);
            haN.add(haMarco);
            haN.add(haTexto);
        }
        return haN;
    }

    private ImageView haVista(File haArchivo, HAAreaTexto haR, double haF) {
        ImageView haV = new ImageView(haImagen(haArchivo));
        haV.setX(haR.haX() * haF);
        haV.setY(haR.haY() * haF);
        haV.setFitWidth(haR.haAncho() * haF);
        haV.setFitHeight(haR.haAlto() * haF);
        return haV;
    }

    private Image haImagen(File haF) {
        return haImagenes.computeIfAbsent(haF, haK -> new Image(haK.toURI().toString()));
    }

    private static Line haLinea(double haX1, double haY1, double haX2, double haY2, Color haC, double haGrosor, double haF) {
        Line haL = new Line(haX1 * haF, haY1 * haF, haX2 * haF, haY2 * haF);
        haTrazo(haL, haC, haGrosor, haF);
        return haL;
    }

    private static void haTrazo(javafx.scene.shape.Shape haS, Color haC, double haGrosor, double haF) {
        haS.setFill(null);
        haS.setStroke(haFx(haC, 1));
        haS.setStrokeWidth(Math.max(1, haGrosor * haF));
        haS.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        haS.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
    }

    private static javafx.scene.paint.Color haFx(Color haC, double haOpacidad) {
        return javafx.scene.paint.Color.rgb(haC.getRed(), haC.getGreen(), haC.getBlue(), haOpacidad);
    }

    private static Button haBoton(Feather haIcono, String haAyuda, Runnable haAccion) {
        Button haB = new Button(null, new FontIcon(haIcono));
        haB.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        haB.setTooltip(new Tooltip(haAyuda));
        haB.setOnAction(haE -> haAccion.run());
        return haB;
    }
}
