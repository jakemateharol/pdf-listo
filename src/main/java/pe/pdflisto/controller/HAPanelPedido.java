package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.interprete.HAEjecutorPlan;
import pe.pdflisto.interprete.HAInterpretePedidos;
import pe.pdflisto.interprete.HAInterpretacion;
import pe.pdflisto.interprete.HAPaso;
import pe.pdflisto.interprete.HAPregunta;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.service.HAHistorialService;
import pe.pdflisto.ui.HAExportador;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.verificacion.HAInformePdf;
import pe.pdflisto.verificacion.HAInformeVerificacion;
import pe.pdflisto.verificacion.HARequisitos;
import pe.pdflisto.verificacion.HAVerificador;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAPanelPedido {
    private static final List<String> HA_EJEMPLOS = List.of(
            "Comprime a menos de 2 MB",
            "Numera las páginas desde la 3",
            "Une, comprime a menos de 2 MB y numera",
            "Marca de agua CONFIDENCIAL",
            "Elimina la última página");

    private final HAInterpretePedidos haInterprete;
    private final HAEjecutorPlan haEjecutor;
    private final HAVerificador haVerificador;
    private final HAInformePdf haInformePdf;
    private final HAExportador haExportador;
    private final HANotificador haNotificador;
    private final HAHistorialService haHistorial;
    private final pe.pdflisto.entrega.HAVerificadorPerfil haVerificadorPerfil;
    private final pe.pdflisto.entrega.HARecetasService haRecetas;
    private final HAPanelListoParaEnviar haListoParaEnviar;

    private HAEditor haEditor;
    private final TextArea haTxtPedido = new TextArea();
    private final VBox haZonaAvisos = new VBox(8);
    private final VBox haZonaPlan = new VBox(8);
    private final VBox haZonaInforme = new VBox(8);

    private String haPedidoInterpretado;
    private final List<HAPaso> haPlan = new ArrayList<>();
    private final List<HAPregunta> haPreguntas = new ArrayList<>();
    private HAInformeVerificacion haUltimoInforme;
    private pe.pdflisto.model.HAPerfilEntrega haPerfilActivo;

    public void haMontar(VBox haContenedor, HAEditor haEditor) {
        this.haEditor = haEditor;

        Label haTitulo = new Label("¿Qué quieres hacer con este PDF?");
        haTitulo.getStyleClass().add("panel-titulo");
        haTitulo.setWrapText(true);

        haTxtPedido.setPromptText("Ej.: une, comprime a menos de 2 MB y numera");
        haTxtPedido.setWrapText(true);
        haTxtPedido.setPrefRowCount(3);
        haTxtPedido.setOnKeyPressed(haE -> {
            if (haE.getCode() == KeyCode.ENTER && !haE.isShiftDown()) {
                haE.consume();
                haInterpretar();
            }
        });

        FlowPane haEjemplos = new FlowPane(6, 6);
        for (String haEjemplo : HA_EJEMPLOS) {
            Button haB = new Button(haEjemplo);
            haB.getStyleClass().addAll(Styles.SMALL, "chip-ejemplo");
            haB.setOnAction(haE -> {
                haTxtPedido.setText(haEjemplo);
                haInterpretar();
            });
            haEjemplos.getChildren().add(haB);
        }

        Button haVerPlan = new Button("Ver plan", new FontIcon(Feather.LIST));
        haVerPlan.getStyleClass().add(Styles.ACCENT);
        haVerPlan.setOnAction(haE -> haInterpretar());
        Label haAyuda = new Label("Enter = ver plan");
        haAyuda.getStyleClass().add("texto-tenue");
        HBox haAcciones = new HBox(10, haVerPlan, haAyuda);
        haAcciones.setAlignment(Pos.CENTER_LEFT);

        Button haVerificarActual = new Button("Verificar documento actual", new FontIcon(Feather.CHECK_SQUARE));
        haVerificarActual.getStyleClass().add(Styles.FLAT);
        haVerificarActual.setOnAction(haE -> haVerificarDocumentoActual());
        Button haListo = new Button("Listo para enviar", new FontIcon(Feather.SEND));
        haListo.getStyleClass().add(Styles.FLAT);
        haListo.setTooltip(new javafx.scene.control.Tooltip("Revisa el PDF sin modificarlo: datos sensibles, peso, metadatos y texto"));
        haListo.setOnAction(haE -> haListoParaEnviar.haRevisar(haEditor));

        haContenedor.getChildren().setAll(haTitulo, haTxtPedido, haEjemplos, haAcciones,
                haZonaAvisos, haZonaPlan, haZonaInforme, haVerificarActual, haListo);
        haLimpiar();
    }

    public void haDocumentoCambio() {
        haPlan.clear();
        haPreguntas.clear();
        haPedidoInterpretado = null;
        haZonaAvisos.getChildren().clear();
        haDibujarPlan();
    }

    public void haRepetir(String haPedido) {
        haTxtPedido.setText(haPedido);
        haInterpretar();
    }

    private void haInterpretar() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        String haPedido = haTxtPedido.getText();
        List<String> haOtros = haEditor.haSesiones().stream().filter(haO -> haO != haS).map(HASesionEdicion::haNombre).toList();
        HAInterpretacion haR = haInterprete.haInterpretar(haPedido, new HAInterpretePedidos.HAContexto(haS.haVista().haPaginas(), haOtros));
        if (haR.haTextoCorregido() != null) {
            haPedido = haR.haTextoCorregido();
            haTxtPedido.setText(haPedido);
            haTxtPedido.positionCaret(haPedido.length());
        }
        haPedidoInterpretado = haPedido == null ? "" : haPedido.trim();
        haPerfilActivo = null;
        haPlan.clear();
        haPlan.addAll(haR.haPasos());
        haPreguntas.clear();
        haPreguntas.addAll(haR.haPreguntas());
        haZonaInforme.getChildren().clear();
        haDibujarAvisos(haR.haAvisos());
        haDibujarPlan();
    }

    private void haDibujarAvisos(List<String> haAvisos) {
        haZonaAvisos.getChildren().clear();
        for (String haAviso : haAvisos) {
            haZonaAvisos.getChildren().add(haTarjeta(Feather.INFO, haAviso, "tarjeta-aviso"));
        }
        for (HAPregunta haP : haPreguntas) {
            Label haTexto = new Label(haP.haTexto());
            haTexto.setWrapText(true);
            FlowPane haOpciones = new FlowPane(6, 6);
            for (HAPregunta.HAOpcion haO : haP.haOpciones()) {
                Button haB = new Button(haO.haEtiqueta());
                haB.getStyleClass().add(Styles.SMALL);
                haB.setOnAction(haE -> {
                    haTxtPedido.setText(haO.haAplicarA(haTxtPedido.getText()));
                    haInterpretar();
                });
                haOpciones.getChildren().add(haB);
            }
            Button haIgnorar = new Button("Ignorar esta parte");
            haIgnorar.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
            haOpciones.getChildren().add(haIgnorar);
            HBox haCabecera = new HBox(8, haIcono(Feather.HELP_CIRCLE), haTexto);
            VBox haTarjeta = new VBox(8, haCabecera, haOpciones);
            haTarjeta.getStyleClass().add("tarjeta-pregunta");
            haIgnorar.setOnAction(haE -> {
                haPreguntas.remove(haP);
                haZonaAvisos.getChildren().remove(haTarjeta);
                haDibujarPlan();
            });
            haZonaAvisos.getChildren().add(haTarjeta);
        }
    }

    private void haDibujarPlan() {
        haZonaPlan.getChildren().clear();
        if (haPlan.isEmpty()) {
            return;
        }
        Label haTitulo = new Label("Plan propuesto");
        haTitulo.getStyleClass().add("panel-titulo");
        Label haNota = new Label("Se ejecuta sobre una copia, en este orden. El original no cambia "
                + "y puedes deshacer todo con Ctrl+Z.");
        haNota.getStyleClass().add("texto-tenue");
        haNota.setWrapText(true);
        haZonaPlan.getChildren().addAll(haTitulo, haNota);
        Label haEsperado = haResultadoEsperado();
        if (haEsperado != null) {
            haZonaPlan.getChildren().add(haEsperado);
        }

        int haN = 1;
        for (HAPaso haPaso : List.copyOf(haPlan)) {
            Label haNumero = new Label(String.valueOf(haN++));
            haNumero.getStyleClass().add("paso-numero");
            Label haTexto = new Label(haPaso.haDescripcion());
            haTexto.setWrapText(true);
            HBox.setHgrow(haTexto, Priority.ALWAYS);
            haTexto.setMaxWidth(Double.MAX_VALUE);
            Button haQuitar = new Button(null, new FontIcon(Feather.X));
            haQuitar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            haQuitar.setOnAction(haE -> {
                haPlan.remove(haPaso);
                haDibujarPlan();
            });
            HBox haFila = new HBox(10, haNumero, haTexto, haQuitar);
            haFila.setAlignment(Pos.CENTER_LEFT);
            haFila.getStyleClass().add("paso-plan");
            haZonaPlan.getChildren().add(haFila);
        }

        Button haAprobar = new Button("Aprobar y ejecutar", new FontIcon(Feather.PLAY));
        haAprobar.getStyleClass().add(Styles.ACCENT);
        haAprobar.setMaxWidth(Double.MAX_VALUE);
        haAprobar.setDisable(!haPreguntas.isEmpty());
        haAprobar.setOnAction(haE -> haEjecutar());
        Button haReceta = new Button("Guardar como receta", new FontIcon(Feather.BOOKMARK));
        haReceta.getStyleClass().add(Styles.FLAT);
        haReceta.setMaxWidth(Double.MAX_VALUE);
        haReceta.setDisable(!haPreguntas.isEmpty());
        haReceta.setOnAction(haE -> haGuardarComoReceta());
        haZonaPlan.getChildren().addAll(haAprobar, haReceta);
        if (!haPreguntas.isEmpty()) {
            Label haFalta = new Label("Responde o ignora las preguntas de arriba para poder ejecutar.");
            haFalta.getStyleClass().add("texto-tenue");
            haFalta.setWrapText(true);
            haZonaPlan.getChildren().add(haFalta);
        }
    }

    private Label haResultadoEsperado() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null || haPlan.isEmpty()) {
            return null;
        }
        int haPaginasOtros = haEditor.haSesiones().stream().filter(haO -> haO != haS).mapToInt(haO -> haO.haVista().haPaginas()).sum();
        long haPeso = haS.haArchivoActual().length();
        HARequisitos haReq = haEjecutor.haRequisitos(List.copyOf(haPlan), haS.haVista().haPaginas(), haPaginasOtros, haPeso);
        String haAhora = haS.haVista().haPaginas() + " págs · " + HAArchivos.haTamanoLegible(haPeso);
        String haDespues = (haReq.haPaginasEsperadas() != null ? haReq.haPaginasEsperadas() + " págs" : "páginas según el plan")
                + (haReq.haPesoMaximo() != null ? " · menos de " + HAArchivos.haTamanoLegible(haReq.haPesoMaximo()) : "");
        Label haL = new Label("Ahora: " + haAhora + "\nEsperado: " + haDespues);
        haL.getStyleClass().add("vista-previa");
        haL.setMaxWidth(Double.MAX_VALUE);
        haL.setWrapText(true);
        return haL;
    }

    private void haEjecutar() {
        if (haEditor.haSesion() == null || haPlan.isEmpty()) {
            return;
        }
        haEjecutarPasos(List.copyOf(haPlan), haPedidoInterpretado, haPerfilActivo);
        haPlan.clear();
        haPreguntas.clear();
        haZonaAvisos.getChildren().clear();
        haDibujarPlan();
    }

    private void haEjecutarPasos(List<HAPaso> haPasos, String haPedido, pe.pdflisto.model.HAPerfilEntrega haPerfil) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null || haPasos.isEmpty()) {
            return;
        }
        List<String> haDescripciones = haPasos.stream().map(HAPaso::haDescripcion).toList();
        List<HASesionEdicion> haOtras = haEditor.haSesiones().stream().filter(haO -> haO != haS).toList();
        List<HAUnir.HAFuente> haFuentes = haOtras.stream()
                .map(haO -> new HAUnir.HAFuente(haO.haNombre(), haO.haArchivoActual(), haO.haContrasenaActual(), false)).toList();
        int haPaginasOtros = haOtras.stream().mapToInt(haO -> haO.haVista().haPaginas()).sum();
        HARequisitos haRequisitos = haEjecutor.haRequisitos(haPasos, haS.haVista().haPaginas(), haPaginasOtros, haS.haArchivoActual().length());
        String haDescripcion = "Pedido: «" + haRecortar(haPedido == null || haPedido.isBlank() ? "plan" : haPedido, 60) + "»";
        String haCodigo = haPasos.stream().anyMatch(haX -> haX instanceof HAPaso.HAUnir) ? null
                : pe.pdflisto.interprete.HASerializadorPasos.haCodificar(haPasos);

        List<pe.pdflisto.verificacion.HAPesoPaso> haPesos = new java.util.concurrent.CopyOnWriteArrayList<>();
        haEditor.haAplicar(haDescripcion, haPedido, haDescripciones, haCodigo,
                (haIn, haPw, haOut, haC) -> {
                    haPesos.clear();
                    return haEjecutor.haEjecutar(haPasos, haIn, haPw, haOut, haFuentes, haC, haPesos);
                },
                (haResultado, haIdHistorial) -> {
                    if (haResultado.haDetalle() != null) {
                        haNotificador.haInfo(haResultado.haDetalle());
                    }
                    if (haPerfil != null) {
                        haVerificarPerfil(haResultado.haArchivo(), haResultado.haContrasena(), haPerfil, haS.haNombre(), haIdHistorial);
                    } else {
                        haVerificar(haResultado.haArchivo(), haResultado.haContrasena(), haRequisitos.haConPesosPorPaso(haPesos),
                                haS.haNombre(), haPedido, haDescripciones, haIdHistorial);
                    }
                });
    }

    public void haProponerPlan(String haTitulo, List<HAPaso> haPasos, List<String> haAvisos, pe.pdflisto.model.HAPerfilEntrega haPerfil) {
        haTxtPedido.setText("");
        haPedidoInterpretado = haTitulo;
        haPerfilActivo = haPerfil;
        haPlan.clear();
        haPlan.addAll(haPasos);
        haPreguntas.clear();
        haZonaInforme.getChildren().clear();
        haDibujarAvisos(haAvisos);
        haDibujarPlan();
        haEditor.haMostrarPestanaPedido();
    }

    public void haEjecutarReceta(String haNombre, List<HAPaso> haPasos) {
        haEditor.haMostrarPestanaPedido();
        haZonaInforme.getChildren().clear();
        haEjecutarPasos(haPasos, "Receta «" + haNombre + "»", null);
    }

    private void haGuardarComoReceta() {
        if (haPlan.isEmpty()) {
            return;
        }
        if (haPlan.stream().anyMatch(haX -> haX instanceof HAPaso.HAUnir)) {
            haNotificador.haAdvertencia("Un plan que une otros PDF depende de los documentos abiertos: quita el paso «Unir» "
                    + "para guardarlo como receta.");
            return;
        }
        javafx.scene.control.TextInputDialog haD = new javafx.scene.control.TextInputDialog(
                pe.pdflisto.interprete.HASerializadorPasos.haResumen(haPlan));
        haD.initOwner(haEditor.haVentana());
        haD.setTitle("Guardar como receta");
        haD.setHeaderText("Ponle un nombre para encontrarla en la pestaña Entrega.");
        pe.pdflisto.ui.HADialogos.haMostrar(haD).filter(haN -> !haN.isBlank()).ifPresent(haN -> {
            try {
                haRecetas.haGuardar(haN, pe.pdflisto.interprete.HASerializadorPasos.haCodificar(haPlan),
                        pe.pdflisto.interprete.HASerializadorPasos.haResumen(haPlan));
                haNotificador.haExito("Receta «" + haN.trim() + "» guardada. La encuentras en la pestaña Entrega.");
            } catch (pe.pdflisto.exception.HAPdfListoException haE) {
                haNotificador.haError(haE.getMessage());
            }
        });
    }

    private void haVerificarPerfil(File haArchivo, String haContrasena, pe.pdflisto.model.HAPerfilEntrega haPerfil, String haNombre,
                                 Long haIdHistorial) {
        haMostrarCargando("Verificando contra el perfil «" + haPerfil.getNombre() + "»…");
        CompletableFuture.supplyAsync(() -> haVerificadorPerfil.haValidar(haArchivo, haContrasena, haPerfil, haNombre))
                .whenComplete((haInforme, haError) -> Platform.runLater(() -> {
                    if (haError != null) {
                        log.error("Fallo la verificacion del perfil", haError);
                        haZonaInforme.getChildren().setAll(haTarjeta(Feather.ALERT_TRIANGLE,
                                "No se pudo verificar el resultado. El detalle quedó en el registro.", "tarjeta-aviso"));
                        return;
                    }
                    haHistorial.haAnotarVerificacion(haIdHistorial, haInforme.haCumple());
                    haMostrarInforme(haInforme);
                }));
    }

    private void haVerificarDocumentoActual() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        haVerificar(haS.haArchivoActual(), haS.haContrasenaActual(), HARequisitos.haNinguno(haS.haArchivoActual().length()),
                haS.haNombre(), null, List.of(), null);
    }

    private void haVerificar(File haArchivo, String haContrasena, HARequisitos haReq, String haNombre,
                           String haPedido, List<String> haPasos, Long haIdHistorial) {
        haMostrarCargando("Verificando el resultado…");
        CompletableFuture.supplyAsync(() -> haVerificador.haVerificar(haArchivo, haContrasena, haReq, haNombre, haPedido, haPasos))
                .whenComplete((haInforme, haError) -> Platform.runLater(() -> {
                    if (haError != null) {
                        log.error("Fallo la verificacion", haError);
                        haZonaInforme.getChildren().setAll(haTarjeta(Feather.ALERT_TRIANGLE,
                                "No se pudo verificar el resultado. El detalle quedó en el registro.", "tarjeta-aviso"));
                        return;
                    }
                    haHistorial.haAnotarVerificacion(haIdHistorial, haInforme.haCumple());
                    haMostrarInforme(haInforme);
                }));
    }

    private void haMostrarCargando(String haTexto) {
        haZonaInforme.getChildren().setAll(haTarjeta(Feather.LOADER, haTexto, "tarjeta-aviso"));
    }

    private void haMostrarInforme(HAInformeVerificacion haInforme) {
        haUltimoInforme = haInforme;
        boolean haOk = haInforme.haCumple();
        Label haTitulo = new Label("Informe de verificación");
        haTitulo.getStyleClass().add("panel-titulo");
        Label haVeredicto = new Label(haOk ? "Cumple" : "Revisar", new FontIcon(haOk ? Feather.CHECK_CIRCLE : Feather.ALERT_TRIANGLE));
        haVeredicto.getStyleClass().addAll("veredicto", haOk ? "cumple" : "revisar");
        haVeredicto.setMaxWidth(Double.MAX_VALUE);
        Label haExplicacion = new Label(haOk
                ? "El resultado se volvió a leer y cumple todo lo pedido."
                : "Hay puntos que no cumplen lo pedido; revísalos abajo antes de entregar el archivo.");
        haExplicacion.getStyleClass().add("texto-tenue");
        haExplicacion.setWrapText(true);

        VBox haFilas = pe.pdflisto.ui.HAVistaComprobaciones.haFilas(haInforme.haComprobaciones());
        Button haExportar = new Button("Exportar informe PDF", new FontIcon(Feather.DOWNLOAD));
        haExportar.setMaxWidth(Double.MAX_VALUE);
        haExportar.setOnAction(haE -> haExportarInforme());
        haZonaInforme.getChildren().setAll(haTitulo, haVeredicto, haExplicacion, haFilas, haExportar);
    }

    private void haExportarInforme() {
        HAInformeVerificacion haInforme = haUltimoInforme;
        if (haInforme == null) {
            return;
        }
        String haBase = HAArchivos.haNombreSeguro("Informe de verificación - " + HAArchivos.haSinExtension(haInforme.haDocumento()));
        haExportador.haGuardar(haEditor.haVentana(), "Guardar informe de verificación", haBase,
                HAExportador.HAFormato.PDF, haDestino -> haInformePdf.haGenerar(haInforme, haDestino));
    }

    private void haLimpiar() {
        haZonaAvisos.getChildren().clear();
        haZonaPlan.getChildren().clear();
        haZonaInforme.getChildren().clear();
    }

    private static HBox haTarjeta(Ikon haIkon, String haTexto, String haEstilo) {
        Label haL = new Label(haTexto);
        haL.setWrapText(true);
        HBox.setHgrow(haL, Priority.ALWAYS);
        HBox haH = new HBox(8, haIcono(haIkon), haL);
        haH.getStyleClass().add(haEstilo);
        return haH;
    }

    private static StackPane haIcono(Ikon haIkon) {
        StackPane haCaja = new StackPane(new FontIcon(haIkon));
        haCaja.setMinWidth(Region.USE_PREF_SIZE);
        return haCaja;
    }

    private static String haRecortar(String haTexto, int haMax) {
        return haTexto.length() <= haMax ? haTexto : haTexto.substring(0, haMax - 1) + "…";
    }
}
