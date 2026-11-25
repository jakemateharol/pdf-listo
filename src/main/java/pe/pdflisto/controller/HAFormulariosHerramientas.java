package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HADivisor;
import pe.pdflisto.operaciones.HAMarcaDeAgua;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HANivelCompresion;
import pe.pdflisto.operaciones.HANumerar;
import pe.pdflisto.operaciones.HAPlanDivision;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HARangoPaginas;
import pe.pdflisto.operaciones.HAUnir;
import pe.pdflisto.pdf.HAMarcador;
import pe.pdflisto.ui.HANotificador;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class HAFormulariosHerramientas {
    private final HANotificador haNotificador;
    private final HAConfiguracion haConfiguracion;
    private final HAMotorPdf haMotor;
    private final HACompresor haCompresor;
    private final HADivisor haDivisor;
    private final pe.pdflisto.facturas.HALectorFacturas haLectorFacturas;
    private final pe.pdflisto.facturas.HAExportadorFacturas haExportadorFacturas;
    private final pe.pdflisto.ui.HAExportador haExportador;
    private final pe.pdflisto.facturas.HAVerificadorExcelFacturas haVerificadorExcel;

    public void haUnir(HAEditor haEditor) {
        HASesionEdicion haActual = haEditor.haSesion();
        ObservableList<HAUnir.HAFuente> haFuentes = FXCollections.observableArrayList();
        haFuentes.add(new HAUnir.HAFuente(haActual.haNombre() + "  (actual)", null, null, true));
        for (HASesionEdicion haOtra : haEditor.haSesiones()) {
            if (haOtra != haActual) {
                haFuentes.add(new HAUnir.HAFuente(haOtra.haNombre(), haOtra.haArchivoActual(), haOtra.haContrasenaActual(), false));
            }
        }
        ListView<HAUnir.HAFuente> haLista = new ListView<>(haFuentes);
        haLista.setPrefHeight(260);
        haLista.setCellFactory(haL -> new ListCell<>() {
            @Override
            protected void updateItem(HAUnir.HAFuente haF, boolean haVacia) {
                super.updateItem(haF, haVacia);
                setText(haVacia || haF == null ? null : (getIndex() + 1) + ".  " + haF.haNombre());
            }
        });

        Button haAgregar = haBoton("Agregar PDF…", Feather.PLUS, () -> {
            FileChooser haSelector = new FileChooser();
            haSelector.setTitle("Elegir PDF para unir");
            haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf", "*.PDF"));
            haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
            List<File> haElegidos = haSelector.showOpenMultipleDialog(haEditor.haVentana());
            if (haElegidos != null) {
                haElegidos.forEach(haF -> haFuentes.add(new HAUnir.HAFuente(haF.getName(), haF, null, false)));
                haConfiguracion.setUltimaCarpeta(haElegidos.get(0).getParentFile());
            }
        });
        Button haSubir = haBoton(null, Feather.ARROW_UP, () -> haMoverEnLista(haLista, -1));
        Button haBajar = haBoton(null, Feather.ARROW_DOWN, () -> haMoverEnLista(haLista, 1));
        Button haQuitar = haBoton(null, Feather.TRASH_2, () -> {
            HAUnir.HAFuente haF = haLista.getSelectionModel().getSelectedItem();
            if (haF == null) {
                return;
            }
            if (haF.haEsActual()) {
                haNotificador.haAdvertencia("El documento actual siempre forma parte de la unión.");
            } else {
                haFuentes.remove(haF);
            }
        });
        HBox haBotones = new HBox(6, haAgregar, haEspacio(), haSubir, haBajar, haQuitar);

        VBox haCuerpo = new VBox(14,
                haCampo("Orden de los documentos", haLista),
                haBotones,
                haNota("El resultado reemplaza la vista del documento actual (puedes deshacerlo). "
                        + "Los otros archivos no se modifican."));

        haAbrir(Feather.GIT_MERGE, "Unir PDF", "Junta varios PDF en uno solo, en el orden de la lista.",
                haCuerpo, "Unir", () -> {
                    if (haFuentes.size() < 2) {
                        throw new HAPdfListoException("Agrega al menos otro PDF para unir.");
                    }
                    List<HAUnir.HAFuente> haOrden = List.copyOf(haFuentes);
                    HAUnir haOp = new HAUnir(haOrden);
                    haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                            haR -> haNotificador.haExito("Unión lista: " + haR.haPaginas() + " páginas en total."));
                });
    }

    public void haDividir(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        int haTotal = haS.haVista().haPaginas();
        String haBase = HAArchivos.haSinExtension(haS.haNombre());
        List<HAMarcador> haMarcadores = new ArrayList<>();

        ToggleGroup haModo = new ToggleGroup();
        RadioButton haPorRangos = haRadio("Por rangos", haModo, true);
        TextField haRangos = new TextField(haSugerirRangos(haTotal));
        haRangos.setPromptText("Ej.: 1-3, 4-6, 7-");
        RadioButton haCadaN = haRadio("Cada cierto número de páginas", haModo, false);
        Spinner<Integer> haN = new Spinner<>(1, Math.max(1, haTotal), 1);
        haN.setEditable(true);
        RadioButton haPorMarcadores = haRadio("Por marcadores (un archivo por capítulo)", haModo, false);
        haPorMarcadores.setDisable(true);
        Label haVista = new Label();
        haVista.getStyleClass().add("vista-previa");
        haVista.setWrapText(true);

        Supplier<List<HAPlanDivision.HAParte>> haPlan = () -> {
            if (haPorRangos.isSelected()) {
                return HAPlanDivision.haPorRangos(haRangos.getText(), haTotal, haBase);
            }
            if (haCadaN.isSelected()) {
                return HAPlanDivision.haCadaN(haValor(haN), haTotal, haBase);
            }
            return HAPlanDivision.haPorMarcadores(haMarcadores, haTotal, haBase);
        };
        Runnable haActualizar = () -> {
            try {
                List<HAPlanDivision.HAParte> haPartes = haPlan.get();
                haVista.setText("Se crearán " + haPartes.size() + " archivos:\n" + haResumen(haPartes));
                haVista.pseudoClassStateChanged(Styles.STATE_DANGER, false);
            } catch (HAPdfListoException haE) {
                haVista.setText(haE.getMessage());
                haVista.pseudoClassStateChanged(Styles.STATE_DANGER, true);
            }
        };
        haRangos.textProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haN.valueProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haN.getEditor().textProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haModo.selectedToggleProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haActualizar.run();

        haS.haVista().haMarcadores().thenAccept(haLista -> Platform.runLater(() -> {
            haMarcadores.addAll(haLista);
            haPorMarcadores.setDisable(haLista.stream().noneMatch(haM -> haM.haPagina() >= 0));
            if (haPorMarcadores.isDisable()) {
                haPorMarcadores.setText("Por marcadores (este PDF no tiene)");
            }
        }));

        VBox haCuerpo = new VBox(12, haPorRangos, haRangos, haCadaN, haN, haPorMarcadores, haVista,
                haNota("Cada parte se guarda como archivo nuevo en la carpeta que elijas. El documento actual no cambia."));

        haAbrir(Feather.SCISSORS, "Dividir PDF", haTotal + " páginas · elige cómo separarlo.",
                haCuerpo, "Elegir carpeta y dividir", () -> {
                    List<HAPlanDivision.HAParte> haPartes = haPlan.get();
                    File haCarpeta = haElegirCarpeta(haEditor, haS);
                    if (haCarpeta == null) {
                        throw new HACancelado();
                    }
                    File haEntrada = haS.haArchivoActual();
                    String haPw = haS.haContrasenaActual();
                    haEditor.haExportar("Dividir " + haS.haNombre(), haC -> haDivisor.haDividir(haEntrada, haPw, haPartes, haCarpeta, haC),
                            new pe.pdflisto.verificacion.HAEsperadoArchivos.HAPartes(haEntrada, haPw, haPartes.stream().map(HAPlanDivision.HAParte::haPaginas).toList()));
                });
    }

    public void haExtraer(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        int haTotal = haS.haVista().haPaginas();
        TextField haPaginas = new TextField(HARangoPaginas.haFormatear(haEditor.haPaginasSeleccionadas()));
        haPaginas.setPromptText("Ej.: 2, 5-7");
        VBox haCuerpo = new VBox(14,
                haCampo("Páginas a extraer", haPaginas),
                haNota("Se guardan en un PDF nuevo. El documento actual no cambia. "
                        + "Consejo: selecciona varias miniaturas con Ctrl o Mayús antes de abrir esta herramienta."));
        haAbrir(Feather.EXTERNAL_LINK, "Extraer páginas", haTotal + " páginas en el documento.",
                haCuerpo, "Guardar como…", () -> {
                    List<Integer> haElegidas = HARangoPaginas.haSeleccion(haPaginas.getText(), haTotal);
                    FileChooser haSelector = new FileChooser();
                    haSelector.setTitle("Guardar páginas extraídas");
                    haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF", "*.pdf"));
                    haSelector.setInitialDirectory(haCarpetaDe(haS));
                    haSelector.setInitialFileName(HAArchivos.haNombreSeguro(HAArchivos.haSinExtension(haS.haNombre())
                            + " - págs " + HARangoPaginas.haFormatear(haElegidas).replace(", ", "_")) + ".pdf");
                    File haElegido = haSelector.showSaveDialog(haEditor.haVentana());
                    if (haElegido == null) {
                        throw new HACancelado();
                    }
                    File haDestino = haConPdf(haElegido);
                    if (haMismoArchivo(haDestino, haS.haOriginal())) {
                        throw new HAPdfListoException("Por seguridad el original nunca se sobrescribe. Elige otro nombre.");
                    }
                    File haEntrada = haS.haArchivoActual();
                    String haPw = haS.haContrasenaActual();
                    haEditor.haExportar("Extraer páginas " + HARangoPaginas.haFormatear(haElegidas),
                            haC -> List.of(haDivisor.haExtraer(haEntrada, haPw, haElegidas, haDestino, haC)),
                            new pe.pdflisto.verificacion.HAEsperadoArchivos.HAPartes(haEntrada, haPw, List.of(haElegidas)));
                });
    }

    public void haComprimir(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        long haPeso = haS.haArchivoActual().length();
        ToggleGroup haGrupo = new ToggleGroup();
        VBox haOpciones = new VBox(10);
        for (HANivelCompresion haNivel : HANivelCompresion.values()) {
            RadioButton haR = haRadio(haNivel.toString(), haGrupo, haNivel == HANivelCompresion.MEDIA);
            haR.setUserData(haNivel);
            haOpciones.getChildren().addAll(haR, haNota(haNivel.haExplicacion()));
        }
        RadioButton haObjetivo = haRadio("Que pese menos de…", haGrupo, false);
        TextField haMegas = new TextField("2");
        haMegas.setPrefColumnCount(5);
        HBox haFilaObjetivo = new HBox(8, haMegas, new Label("MB"));
        haFilaObjetivo.setAlignment(Pos.CENTER_LEFT);
        haFilaObjetivo.disableProperty().bind(haObjetivo.selectedProperty().not());
        haOpciones.getChildren().addAll(haObjetivo, haFilaObjetivo,
                haNota("Prueba los niveles de suave a fuerte y se queda con el primero que cumple."));

        VBox haCuerpo = new VBox(14,
                new Label("Peso actual: " + HAArchivos.haTamanoLegible(haPeso)),
                haOpciones,
                haNota("El texto nunca pierde calidad: solo se reducen las imágenes y fotos."));

        haAbrir(Feather.MINIMIZE_2, "Comprimir", "Reduce el peso del PDF para enviarlo o subirlo.",
                haCuerpo, "Comprimir", () -> {
                    if (haObjetivo.isSelected()) {
                        double haMb = haNumeroDecimal(haMegas.getText(), "el peso objetivo");
                        long haBytes = (long) (haMb * 1024 * 1024);
                        String haDesc = "Comprimir a menos de " + haMegas.getText().trim() + " MB";
                        haEditor.haAplicarRepetible(haDesc, pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HAComprimir(HANivelCompresion.MEDIA, haBytes)),
                                (haIn, haPw, haOut, haC) -> haCompresor.haHastaObjetivo(haIn, haPw, haBytes, haOut, haC), this::haInformarCompresion);
                    } else {
                        HANivelCompresion haNivel = (HANivelCompresion) haGrupo.getSelectedToggle().getUserData();
                        haEditor.haAplicarRepetible("Compresión " + haNivel.toString().toLowerCase(),
                                pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HAComprimir(haNivel, null)),
                                (haIn, haPw, haOut, haC) -> haCompresor.haComprimir(haIn, haPw, haNivel, haOut, haC), this::haInformarCompresion);
                    }
                });
    }

    private void haInformarCompresion(pe.pdflisto.operaciones.HAResultado haR) {
        if (haR.haCumple()) {
            haNotificador.haExito(haR.haDetalle());
        } else {
            haNotificador.haAdvertencia(haR.haDetalle());
        }
    }

    private record HAFormato(String haPlantilla, String haEjemplo) {
        @Override
        public String toString() {
            return haEjemplo;
        }
    }

    public void haNumerar(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        int haTotal = haS.haVista().haPaginas();
        ComboBox<HAFormato> haFormato = new ComboBox<>(FXCollections.observableArrayList(
                new HAFormato("{n}", "1, 2, 3…"),
                new HAFormato("Página {n}", "Página 1"),
                new HAFormato("Página {n} de {total}", "Página 1 de " + haTotal),
                new HAFormato("{n} / {total}", "1 / " + haTotal),
                new HAFormato("- {n} -", "- 1 -")));
        haFormato.getSelectionModel().select(0);
        haFormato.setMaxWidth(Double.MAX_VALUE);
        ComboBox<HAPosicion> haPosicion = new ComboBox<>(FXCollections.observableArrayList(HAPosicion.values()));
        haPosicion.getSelectionModel().select(HAPosicion.ABAJO_CENTRO);
        haPosicion.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> haDesde = new Spinner<>(1, haTotal, 1);
        haDesde.setEditable(true);
        Spinner<Integer> haPrimero = new Spinner<>(0, 99999, 1);
        haPrimero.setEditable(true);
        Spinner<Integer> haTamano = new Spinner<>(6, 36, 11);
        haTamano.setEditable(true);

        VBox haCuerpo = new VBox(14,
                haCampo("Formato", haFormato),
                haCampo("Posición", haPosicion),
                haCampo("Empezar a numerar en la página", haDesde),
                haCampo("Esa página lleva el número", haPrimero),
                haCampo("Tamaño de letra", haTamano),
                haNota("Útil para tesis: si la carátula y el índice no llevan número, empieza en la página donde inicia "
                        + "el contenido y pon el número que corresponda."));

        haAbrir(Feather.HASH, "Numerar páginas", haTotal + " páginas.", haCuerpo, "Numerar", () -> {
            HANumerar haOp = new HANumerar(haFormato.getValue().haPlantilla(), haPosicion.getValue(),
                    haValor(haDesde), haValor(haPrimero), haValor(haTamano));
            String haCodigo = haOp.haPrimerNumero() == 1
                    ? pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HANumerar(haOp.haFormato(), haOp.haPosicion(), haOp.haDesdePagina())) : null;
            haEditor.haAplicarRepetible(haOp.haDescripcion(), haCodigo, (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                    haR -> haNotificador.haExito("Páginas numeradas."));
        });
    }

    private record HAColorNombrado(String haNombre, Color haColor) {
        @Override
        public String toString() {
            return haNombre;
        }
    }

    public void haMarcaDeAgua(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        int haTotal = haS.haVista().haPaginas();
        ToggleGroup haTipo = new ToggleGroup();
        RadioButton haDeTexto = haRadio("Texto", haTipo, true);
        RadioButton haDeImagen = haRadio("Imagen (logo PNG o JPG)", haTipo, false);

        TextField haTexto = new TextField("CONFIDENCIAL");
        ComboBox<HAColorNombrado> haColor = new ComboBox<>(FXCollections.observableArrayList(
                new HAColorNombrado("Gris", new Color(110, 110, 110)),
                new HAColorNombrado("Rojo", new Color(200, 30, 30)),
                new HAColorNombrado("Azul", new Color(30, 80, 200))));
        haColor.getSelectionModel().select(0);
        CheckBox haDiagonal = new CheckBox("En diagonal");
        haDiagonal.setSelected(true);
        VBox haOpcionesTexto = new VBox(10, haCampo("Texto", haTexto), haCampo("Color", haColor), haDiagonal);
        haOpcionesTexto.disableProperty().bind(haDeTexto.selectedProperty().not());

        File[] haImagen = new File[1];
        Label haNombreImagen = new Label("Ninguna imagen elegida");
        haNombreImagen.getStyleClass().add("texto-tenue");
        Button haElegirImagen = haBoton("Elegir imagen…", Feather.IMAGE, () -> {
            FileChooser haSelector = new FileChooser();
            haSelector.setTitle("Imagen para la marca de agua");
            haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
            haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
            File haF = haSelector.showOpenDialog(haEditor.haVentana());
            if (haF != null) {
                haImagen[0] = haF;
                haNombreImagen.setText(haF.getName());
            }
        });
        VBox haOpcionesImagen = new VBox(8, haElegirImagen, haNombreImagen);
        haOpcionesImagen.disableProperty().bind(haDeImagen.selectedProperty().not());

        Slider haOpacidad = new Slider(5, 100, 25);
        Label haValorOpacidad = new Label();
        haValorOpacidad.textProperty().bind(haOpacidad.valueProperty().asString("%.0f %%"));
        HBox haFilaOpacidad = new HBox(10, haOpacidad, haValorOpacidad);
        HBox.setHgrow(haOpacidad, Priority.ALWAYS);

        TextField haPaginas = new TextField();
        haPaginas.setPromptText("Vacío = todas las páginas");

        VBox haCuerpo = new VBox(14, haDeTexto, haOpcionesTexto, haDeImagen, haOpcionesImagen,
                haCampo("Opacidad", haFilaOpacidad), haCampo("Páginas", haPaginas),
                haNota("La marca se agrega encima del contenido. Sirve para identificar el documento, "
                        + "pero alguien con un editor de PDF podría quitarla."));

        haAbrir(Feather.DROPLET, "Marca de agua", "Texto o logo semitransparente sobre las páginas.",
                haCuerpo, "Aplicar", () -> {
                    List<Integer> haDonde = haPaginas.getText().isBlank() ? null : HARangoPaginas.haSeleccion(haPaginas.getText(), haTotal);
                    HAMarcaDeAgua haOp;
                    if (haDeTexto.isSelected()) {
                        if (haTexto.getText().isBlank()) {
                            throw new HAPdfListoException("Escribe el texto de la marca de agua.");
                        }
                        haOp = new HAMarcaDeAgua(haTexto.getText().trim(), null, haColor.getValue().haColor(),
                                (float) haOpacidad.getValue() / 100f, haDiagonal.isSelected(), haDonde);
                    } else {
                        if (haImagen[0] == null) {
                            throw new HAPdfListoException("Elige la imagen de la marca de agua.");
                        }
                        haOp = new HAMarcaDeAgua(null, haImagen[0], null, (float) haOpacidad.getValue() / 100f, false, haDonde);
                    }
                    HAMarcaDeAgua haOperacion = haOp;
                    String haCodigo = haOp.haTexto() != null && haDonde == null
                            ? pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HAMarcaAgua(haOp.haTexto(), haOp.haDiagonal())) : null;
                    haEditor.haAplicarRepetible(haOp.haDescripcion(), haCodigo, (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOperacion, haOut, haC),
                            haR -> haNotificador.haExito("Marca de agua aplicada."));
                });
    }

    public void haOcr(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        Label haAnalisis = new Label("Revisando qué páginas no tienen texto…");
        haAnalisis.getStyleClass().add("vista-previa");
        haAnalisis.setWrapText(true);
        haAnalisis.setMaxWidth(Double.MAX_VALUE);
        haS.haVista().haPaginasSinTexto().thenAccept(haSinTexto -> Platform.runLater(() -> haAnalisis.setText(haSinTexto.isEmpty()
                ? "Todas las páginas ya tienen texto: no hace falta el OCR."
                : haSinTexto.size() + " de " + haS.haVista().haPaginas() + " páginas no tienen texto y se reconocerán. "
                  + "Las que ya tienen texto no se tocan.")));

        ComboBox<pe.pdflisto.ocr.HADatosOcr.HAIdioma> haIdioma =
                new ComboBox<>(FXCollections.observableArrayList(pe.pdflisto.ocr.HADatosOcr.HAIdioma.values()));
        haIdioma.getSelectionModel().select(0);
        haIdioma.setMaxWidth(Double.MAX_VALUE);
        CheckBox haGirar = new CheckBox("Poner derechas las páginas giradas (de costado o de cabeza)");
        haGirar.setSelected(true);
        CheckBox haEnderezar = new CheckBox("Enderezar páginas torcidas");
        CheckBox haBordes = new CheckBox("Limpiar bordes oscuros del escáner");
        CheckBox haContraste = new CheckBox("Mejorar el contraste (texto más negro, fondo más blanco)");

        VBox haCuerpo = new VBox(14, haAnalisis,
                haCampo("Idioma del documento", haIdioma),
                haGirar,
                haCampo("Mejorar el escaneo (opcional)", new VBox(8, haEnderezar, haBordes, haContraste)),
                haNota("Las mejoras reemplazan la imagen escaneada por una versión en escala de grises: los colores "
                        + "(sellos, logos) se pierden. Sin mejoras, la imagen queda exactamente igual."),
                haNota("El texto reconocido se agrega invisible encima de cada palabra: el PDF se ve igual, pero se puede "
                        + "buscar, copiar y revisar sus datos sensibles. Tarda unos segundos por página."),
                haNota("El OCR puede confundir letras en escaneos borrosos o con letra a mano: revisa lo importante."));
        haAbrir(Feather.FILE_TEXT, "Reconocer texto (OCR)", "Hace buscables los PDF escaneados, sin internet.",
                haCuerpo, "Reconocer", () -> {
                    pe.pdflisto.ocr.HAReconocerTexto haOp = new pe.pdflisto.ocr.HAReconocerTexto(new pe.pdflisto.ocr.HAReconocerTexto.HAOpciones(
                            haIdioma.getValue(), haEnderezar.isSelected(), haBordes.isSelected(), haContraste.isSelected(),
                            haGirar.isSelected()));
                    haEditor.haAplicarRepetible(haOp.haDescripcion(), pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HAOcr()),
                            (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                            haR -> haNotificador.haExito("Listo: ahora puedes buscar (Ctrl+F) y copiar el texto. "
                                    + (haR.haDetalle() != null ? haR.haDetalle() + " " : "")
                                    + "Revisa lo importante: el OCR puede confundir algunas letras."));
                });
    }

    public void haFacturas(HAEditor haEditor) {
        ObservableList<pe.pdflisto.facturas.HALectorFacturas.HAFuente> haFuentes = FXCollections.observableArrayList();
        HASesionEdicion haS = haEditor.haSesion();
        if (haS != null) {
            haFuentes.add(new pe.pdflisto.facturas.HALectorFacturas.HAFuente(haS.haArchivoActual(), haS.haNombre()));
        }
        ListView<pe.pdflisto.facturas.HALectorFacturas.HAFuente> haLista = new ListView<>(haFuentes);
        haLista.setPrefHeight(220);
        haLista.setCellFactory(haL -> new ListCell<>() {
            @Override
            protected void updateItem(pe.pdflisto.facturas.HALectorFacturas.HAFuente haF, boolean haVacia) {
                super.updateItem(haF, haVacia);
                setText(haVacia || haF == null ? null : haF.haNombre());
            }
        });
        haLista.setPlaceholder(new Label("Agrega PDF o una carpeta."));
        Button haAgregar = haBoton("Agregar PDF…", Feather.PLUS, () -> {
            FileChooser haSelector = new FileChooser();
            haSelector.setTitle("Facturas en PDF");
            haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF", "*.pdf", "*.PDF"));
            haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
            List<File> haElegidos = haSelector.showOpenMultipleDialog(haEditor.haVentana());
            if (haElegidos != null && !haElegidos.isEmpty()) {
                haElegidos.forEach(haF -> haFuentes.add(new pe.pdflisto.facturas.HALectorFacturas.HAFuente(haF, haF.getName())));
                haConfiguracion.setUltimaCarpeta(haElegidos.get(0).getParentFile());
            }
        });
        Button haCarpetaBoton = haBoton("Agregar carpeta…", Feather.FOLDER, () -> {
            DirectoryChooser haSelector = new DirectoryChooser();
            haSelector.setTitle("Carpeta con facturas en PDF");
            haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
            File haCarpeta = haSelector.showDialog(haEditor.haVentana());
            if (haCarpeta != null) {
                File[] haPdfs = haCarpeta.listFiles((haD, haN) -> haN.toLowerCase().endsWith(".pdf"));
                if (haPdfs == null || haPdfs.length == 0) {
                    haNotificador.haAdvertencia("Esa carpeta no tiene archivos PDF.");
                    return;
                }
                java.util.Arrays.sort(haPdfs, java.util.Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
                for (File haF : haPdfs) {
                    haFuentes.add(new pe.pdflisto.facturas.HALectorFacturas.HAFuente(haF, haF.getName()));
                }
                haConfiguracion.setUltimaCarpeta(haCarpeta);
            }
        });
        Button haQuitar = haBoton(null, Feather.TRASH_2, () -> haFuentes.remove(haLista.getSelectionModel().getSelectedItem()));
        VBox haCuerpo = new VBox(12, haCampo("PDF a leer", haLista), new HBox(6, haAgregar, haCarpetaBoton, haEspacio(), haQuitar),
                haNota("Se lee el código QR de cada comprobante electrónico (factura, boleta, notas): RUC del emisor, tipo, "
                        + "serie, número, IGV, total, fecha y documento del cliente. Funciona con PDF digitales y escaneados."),
                haNota("Se valida que el IGV cuadre (18 %, o 10 % del régimen MYPE), los RUC, la fecha, que el total esté "
                        + "impreso y que no haya comprobantes repetidos. Luego puedes exportar a Excel."),
                haNota("Comprobantes sin código QR, el nombre del proveedor y los ítems llegan en la versión 1.1."));
        haAbrir(Feather.FILE_TEXT, "Leer facturas (QR)", "Comprobantes electrónicos de SUNAT, sin internet.",
                haCuerpo, "Leer", () -> {
                    if (haFuentes.isEmpty()) {
                        throw new HAPdfListoException("Agrega al menos un PDF.");
                    }
                    List<pe.pdflisto.facturas.HALectorFacturas.HAFuente> haCopia = List.copyOf(haFuentes);
                    haEditor.haEnSegundoPlano("Leyendo comprobantes", haC -> haLectorFacturas.haLeerFuentes(haCopia, haC),
                            haResultado -> HAVentanaFacturas.haMostrar(haEditor.haVentana(), haResultado, haExportadorFacturas, haExportador, haVerificadorExcel));
                });
    }

    public void haTamano(HAEditor haEditor) {
        ComboBox<pe.pdflisto.operaciones.HATamanoPapel> haPapel =
                new ComboBox<>(FXCollections.observableArrayList(pe.pdflisto.operaciones.HATamanoPapel.values()));
        haPapel.getSelectionModel().select(0);
        haPapel.setMaxWidth(Double.MAX_VALUE);
        ComboBox<pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion> haOrientacion =
                new ComboBox<>(FXCollections.observableArrayList(pe.pdflisto.operaciones.HACambiarTamano.HAOrientacion.values()));
        haOrientacion.getSelectionModel().select(0);
        haOrientacion.setMaxWidth(Double.MAX_VALUE);
        VBox haCuerpo = new VBox(14, haCampo("Tamaño", haPapel), haCampo("Orientación", haOrientacion),
                haNota("El contenido se agranda o achica para caber completo, sin deformarse, y se centra. Si la proporción "
                        + "es distinta quedan márgenes blancos. Los textos siguen siendo texto (no se convierten en imagen)."),
                haNota("A4 = 21 × 29,7 cm (el más usado en Perú). Carta = 21,6 × 27,9 cm."));
        haAbrir(Feather.MAXIMIZE_2, "Tamaño de página", "Pasa todas las páginas a A4 o Carta.", haCuerpo, "Cambiar", () -> {
            pe.pdflisto.operaciones.HACambiarTamano haOp = new pe.pdflisto.operaciones.HACambiarTamano(haPapel.getValue(), haOrientacion.getValue());
            haEditor.haAplicarRepetible(haOp.haDescripcion(), pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HATamano(haPapel.getValue(), haOrientacion.getValue())),
                    (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC), haR -> haNotificador.haExito("Listo: páginas en " + haPapel.getValue() + "."));
        });
    }

    public void haEncabezadoPie(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        int haTotal = haS.haVista().haPaginas();
        TextField[] haArriba = {new TextField(), new TextField(), new TextField()};
        TextField[] haAbajo = {new TextField(), new TextField(), new TextField("Página {pagina} de {total}")};
        String[] haLados = {"Izquierda", "Centro", "Derecha"};
        javafx.scene.layout.GridPane haRejilla = new javafx.scene.layout.GridPane();
        haRejilla.setHgap(6);
        haRejilla.setVgap(6);
        haRejilla.add(new Label(""), 0, 0);
        haRejilla.add(new Label("Arriba"), 1, 0);
        haRejilla.add(new Label("Abajo"), 2, 0);
        for (int haI = 0; haI < 3; haI++) {
            haRejilla.add(new Label(haLados[haI]), 0, haI + 1);
            haRejilla.add(haArriba[haI], 1, haI + 1);
            haRejilla.add(haAbajo[haI], 2, haI + 1);
        }
        Spinner<Integer> haDesde = new Spinner<>(1, haTotal, 1);
        haDesde.setEditable(true);
        Spinner<Integer> haTamanoLetra = new Spinner<>(6, 24, 9);
        haTamanoLetra.setEditable(true);
        VBox haCuerpo = new VBox(14, haRejilla,
                haNota("Variables: {pagina} = número de página, {total} = total de páginas, {fecha} = fecha de hoy, "
                        + "{archivo} = nombre del documento."),
                haCampo("Desde la página", haDesde), haCampo("Tamaño de letra", haTamanoLetra),
                haNota("Si el documento ya tiene numeración, no repitas {pagina} aquí."));
        haAbrir(Feather.ALIGN_CENTER, "Encabezado y pie", haTotal + " páginas.", haCuerpo, "Aplicar", () -> {
            String[] haA = {haArriba[0].getText(), haArriba[1].getText(), haArriba[2].getText()};
            String[] haB = {haAbajo[0].getText(), haAbajo[1].getText(), haAbajo[2].getText()};
            pe.pdflisto.operaciones.HAEncabezadoPie haOp = new pe.pdflisto.operaciones.HAEncabezadoPie(haA, haB, haValor(haDesde),
                    haValor(haTamanoLetra), HAArchivos.haSinExtension(haS.haNombre()));
            haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                    haR -> haNotificador.haExito(haOp.haDescripcion() + " aplicado."));
        });
    }

    static final class HACancelado extends RuntimeException {
        HACancelado() {
            super(null, null, false, false);
        }
    }

    void haAbrir(Ikon haIcono, String haTitulo, String haSubtitulo, Node haCuerpo, String haTextoAccion, Runnable haAccion) {
        haAbrir(haIcono, haTitulo, haSubtitulo, haCuerpo, haTextoAccion, haAccion, true);
    }

    private void haAbrir(Ikon haIcono, String haTitulo, String haSubtitulo, Node haCuerpo, String haTextoAccion, Runnable haAccion,
                       boolean haConCancelar) {
        FontIcon haFi = new FontIcon(haIcono);
        StackPane haCaja = new StackPane(haFi);
        haCaja.getStyleClass().add("drawer-icono");
        Label haT = new Label(haTitulo);
        haT.getStyleClass().add("drawer-titulo");
        Label haSt = new Label(haSubtitulo);
        haSt.getStyleClass().add("drawer-subtitulo");
        haSt.setWrapText(true);
        HBox haCabecera = new HBox(haCaja, new VBox(2, haT, haSt));
        haCabecera.getStyleClass().add("drawer-header");

        VBox haContenido = new VBox(haCuerpo);
        haContenido.getStyleClass().add("drawer-body");
        ScrollPane haScroll = new ScrollPane(haContenido);
        haScroll.setFitToWidth(true);
        haScroll.getStyleClass().add("drawer-scroll");
        VBox.setVgrow(haScroll, Priority.ALWAYS);

        Button haCancelar = new Button("Cancelar");
        haCancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        haCancelar.setCancelButton(true);
        haCancelar.setOnAction(haE -> haNotificador.haCerrarPanel());
        Button haAceptar = new Button(haTextoAccion);
        haAceptar.getStyleClass().add(Styles.ACCENT);
        haAceptar.setDefaultButton(true);
        haAceptar.setOnAction(haE -> {
            try {
                haAccion.run();
                haNotificador.haCerrarPanel();
            } catch (HACancelado haC) {
            } catch (HAPdfListoException haEx) {
                haNotificador.haError(haEx.getMessage());
            }
        });
        HBox haPie = haConCancelar ? new HBox(haEspacio(), haCancelar, haAceptar) : new HBox(haEspacio(), haAceptar);
        haPie.getStyleClass().add("drawer-footer");
        haMostrarPanel(haCabecera, haScroll, haPie);
    }

    void haAbrirInformativo(Ikon haIcono, String haTitulo, String haSubtitulo, Node haCuerpo) {
        haAbrir(haIcono, haTitulo, haSubtitulo, haCuerpo, "Cerrar", () -> { }, false);
    }

    private void haMostrarPanel(HBox haCabecera, ScrollPane haScroll, HBox haPie) {
        VBox haPanel = new VBox(haCabecera, haScroll, haPie);
        haPanel.getStyleClass().add("drawer");
        haPanel.setPrefWidth(420);
        haPanel.setMaxWidth(420);
        haNotificador.haAbrirPanel(haPanel);
    }

    static VBox haCampo(String haEtiqueta, Node haControl) {
        Label haL = new Label(haEtiqueta);
        haL.getStyleClass().add("campo-label");
        if (haControl instanceof Region haR) {
            haR.setMaxWidth(Double.MAX_VALUE);
        }
        VBox haV = new VBox(haL, haControl);
        haV.getStyleClass().add("campo");
        return haV;
    }

    static Label haNota(String haTexto) {
        Label haL = new Label(haTexto);
        haL.getStyleClass().add("texto-tenue");
        haL.setWrapText(true);
        return haL;
    }

    private static RadioButton haRadio(String haTexto, ToggleGroup haGrupo, boolean haElegido) {
        RadioButton haR = new RadioButton(haTexto);
        haR.setToggleGroup(haGrupo);
        haR.setSelected(haElegido);
        return haR;
    }

    private static Button haBoton(String haTexto, Ikon haIcono, Runnable haAccion) {
        Button haB = new Button(haTexto, new FontIcon(haIcono));
        if (haTexto == null) {
            haB.getStyleClass().add(Styles.BUTTON_ICON);
        }
        haB.setOnAction(haE -> haAccion.run());
        return haB;
    }

    private static Region haEspacio() {
        Region haR = new Region();
        HBox.setHgrow(haR, Priority.ALWAYS);
        return haR;
    }

    private static <T> void haMoverEnLista(ListView<T> haLista, int haPaso) {
        int haI = haLista.getSelectionModel().getSelectedIndex();
        int haJ = haI + haPaso;
        if (haI < 0 || haJ < 0 || haJ >= haLista.getItems().size()) {
            return;
        }
        T haItem = haLista.getItems().remove(haI);
        haLista.getItems().add(haJ, haItem);
        haLista.getSelectionModel().clearAndSelect(haJ);
    }

    private static int haValor(Spinner<Integer> haSpinner) {
        String haTexto = haSpinner.getEditor().getText().trim();
        try {
            int haV = Integer.parseInt(haTexto);
            haSpinner.getValueFactory().setValue(haV);
            return haSpinner.getValue();
        } catch (NumberFormatException haE) {
            throw new HAPdfListoException("«" + haTexto + "» no es un número válido.");
        }
    }

    private static double haNumeroDecimal(String haTexto, String haQue) {
        try {
            double haV = Double.parseDouble(haTexto.trim().replace(',', '.'));
            if (haV <= 0) {
                throw new NumberFormatException();
            }
            return haV;
        } catch (NumberFormatException haE) {
            throw new HAPdfListoException("Escribe un número válido para " + haQue + " (por ejemplo 2 o 1.5).");
        }
    }

    private static String haSugerirRangos(int haTotal) {
        if (haTotal < 2) {
            return "1";
        }
        int haMitad = haTotal / 2;
        return "1-" + haMitad + ", " + (haMitad + 1) + "-" + haTotal;
    }

    private static String haResumen(List<HAPlanDivision.HAParte> haPartes) {
        StringBuilder haSb = new StringBuilder();
        int haMostrar = Math.min(6, haPartes.size());
        for (int haI = 0; haI < haMostrar; haI++) {
            HAPlanDivision.HAParte haP = haPartes.get(haI);
            haSb.append("• ").append(haP.haNombre()).append(".pdf (").append(haP.haPaginas().size()).append(" pág.)\n");
        }
        if (haPartes.size() > haMostrar) {
            haSb.append("… y ").append(haPartes.size() - haMostrar).append(" más");
        }
        return haSb.toString().trim();
    }

    private File haElegirCarpeta(HAEditor haEditor, HASesionEdicion haS) {
        DirectoryChooser haSelector = new DirectoryChooser();
        haSelector.setTitle("¿Dónde guardo las partes?");
        haSelector.setInitialDirectory(haCarpetaDe(haS));
        File haCarpeta = haSelector.showDialog(haEditor.haVentana());
        if (haCarpeta != null) {
            haConfiguracion.setUltimaCarpeta(haCarpeta);
        }
        return haCarpeta;
    }

    private File haCarpetaDe(HASesionEdicion haS) {
        File haCarpeta = haS.haOriginal().getParentFile();
        return haCarpeta != null && haCarpeta.isDirectory() ? haCarpeta : haConfiguracion.haUltimaCarpeta();
    }

    private static File haConPdf(File haF) {
        return haF.getName().toLowerCase().endsWith(".pdf") ? haF : new File(haF.getParentFile(), haF.getName() + ".pdf");
    }

    static boolean haMismoArchivo(File haA, File haB) {
        try {
            return haA.getCanonicalFile().equals(haB.getCanonicalFile());
        } catch (IOException haE) {
            return true;
        }
    }
}
