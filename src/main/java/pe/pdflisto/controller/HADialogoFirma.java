package pe.pdflisto.controller;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import pe.pdflisto.anotaciones.HABibliotecaFirmas;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.ui.HAImagenes;
import pe.pdflisto.ui.HANotificador;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class HADialogoFirma {
    private static final List<String> HA_LETRAS_MANUSCRITAS = List.of(
            "Segoe Script", "Lucida Handwriting", "Ink Free", "Brush Script MT", "Mistral", "Freestyle Script", "Kristen ITC");
    private static final Color HA_TINTA = Color.rgb(20, 40, 120);

    private final HABibliotecaFirmas haBiblioteca;
    private final HANotificador haNotificador;
    private final Window haVentana;
    private final HAConfiguracion haConfiguracion;

    HADialogoFirma(HABibliotecaFirmas haBiblioteca, HANotificador haNotificador, Window haVentana, HAConfiguracion haConfiguracion) {
        this.haBiblioteca = haBiblioteca;
        this.haNotificador = haNotificador;
        this.haVentana = haVentana;
        this.haConfiguracion = haConfiguracion;
    }

    Optional<HABibliotecaFirmas.HAFirma> haMostrar() {
        Dialog<ButtonType> haDialogo = new Dialog<>();
        haDialogo.initOwner(haVentana);
        haDialogo.setTitle("Nueva firma");
        haDialogo.setHeaderText("Crea tu firma. Se guarda solo en esta computadora.");

        Canvas haLienzo = new Canvas(440, 170);
        GraphicsContext haG = haLienzo.getGraphicsContext2D();
        haG.setStroke(HA_TINTA);
        haG.setLineWidth(2.8);
        haG.setLineCap(StrokeLineCap.ROUND);
        double[] haAnterior = new double[2];
        haLienzo.setOnMousePressed(haE -> {
            haAnterior[0] = haE.getX();
            haAnterior[1] = haE.getY();
        });
        haLienzo.setOnMouseDragged(haE -> {
            haG.strokeLine(haAnterior[0], haAnterior[1], haE.getX(), haE.getY());
            haAnterior[0] = haE.getX();
            haAnterior[1] = haE.getY();
        });
        StackPane haMarco = new StackPane(haLienzo);
        haMarco.getStyleClass().add("lienzo-firma");
        Button haLimpiar = new Button("Borrar y empezar de nuevo");
        haLimpiar.setOnAction(haE -> haG.clearRect(0, 0, haLienzo.getWidth(), haLienzo.getHeight()));
        Tab haDibujar = new Tab("Dibujar", new VBox(8, new Label("Firma con el mouse dentro del recuadro:"), haMarco, haLimpiar));

        TextField haNombreEscrito = new TextField();
        haNombreEscrito.setPromptText("Tu nombre");
        List<String> haDisponibles = haLetrasDisponibles();
        ComboBox<String> haLetra = new ComboBox<>(javafx.collections.FXCollections.observableArrayList(haDisponibles));
        haLetra.getSelectionModel().select(0);
        Label haMuestra = new Label("Tu firma");
        haMuestra.setTextFill(HA_TINTA);
        haMuestra.setMinHeight(80);
        Runnable haActualizar = () -> {
            haMuestra.setText(haNombreEscrito.getText().isBlank() ? "Tu firma" : haNombreEscrito.getText());
            haMuestra.setFont(Font.font(haLetra.getValue(), 40));
        };
        haNombreEscrito.textProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haLetra.valueProperty().addListener((haO, haA, haB) -> haActualizar.run());
        haActualizar.run();
        Tab haEscribir = new Tab("Escribir", new VBox(8, new Label("Nombre:"), haNombreEscrito, new Label("Tipo de letra:"), haLetra, haMuestra));

        File[] haArchivo = new File[1];
        ImageView haVista = new ImageView();
        haVista.setFitWidth(420);
        haVista.setFitHeight(150);
        haVista.setPreserveRatio(true);
        CheckBox haQuitarFondo = new CheckBox("Quitar el fondo blanco (recomendado para fotos o escaneos)");
        haQuitarFondo.setSelected(true);
        Button haElegir = new Button("Elegir imagen…");
        haElegir.setOnAction(haE -> {
            FileChooser haSelector = new FileChooser();
            haSelector.setTitle("Imagen de tu firma");
            haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg"));
            haSelector.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
            File haF = haSelector.showOpenDialog(haVentana);
            if (haF != null) {
                haArchivo[0] = haF;
                haVista.setImage(new javafx.scene.image.Image(haF.toURI().toString()));
            }
        });
        Tab haImagen = new Tab("Desde imagen", new VBox(8, haElegir, haVista, haQuitarFondo));

        TabPane haPestanas = new TabPane(haDibujar, haEscribir, haImagen);
        haPestanas.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        TextField haNombre = new TextField();
        haNombre.setPromptText("Nombre para guardarla (p. ej. «Firma completa» o «Visto bueno»)");
        haDialogo.getDialogPane().setContent(new VBox(10, haPestanas, new Label("Nombre de la firma:"), haNombre));
        haDialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        while (true) {
            Optional<ButtonType> haR = pe.pdflisto.ui.HADialogos.haMostrar(haDialogo);
            if (haR.isEmpty() || haR.get() != ButtonType.OK) {
                return Optional.empty();
            }
            try {
                BufferedImage haImg;
                Tab haElegida = haPestanas.getSelectionModel().getSelectedItem();
                if (haElegida == haDibujar) {
                    SnapshotParameters haP = new SnapshotParameters();
                    haP.setFill(Color.TRANSPARENT);
                    haImg = HAImagenes.haABuffered(haLienzo.snapshot(haP, null));
                } else if (haElegida == haEscribir) {
                    if (haNombreEscrito.getText().isBlank()) {
                        throw new HAPdfListoException("Escribe tu nombre para generar la firma.");
                    }
                    haImg = haEscrita(haNombreEscrito.getText().trim(), haLetra.getValue());
                } else {
                    if (haArchivo[0] == null) {
                        throw new HAPdfListoException("Elige la imagen de tu firma.");
                    }
                    BufferedImage haLeida = ImageIO.read(haArchivo[0]);
                    if (haLeida == null) {
                        throw new HAPdfListoException("No se pudo leer la imagen. Usa PNG o JPG.");
                    }
                    haImg = haQuitarFondo.isSelected() ? HABibliotecaFirmas.haQuitarFondoBlanco(haLeida) : haConTransparencia(haLeida);
                }
                String haNombreFinal = haNombre.getText().isBlank()
                        ? (haNombreEscrito.getText().isBlank() ? "Mi firma" : haNombreEscrito.getText().trim())
                        : haNombre.getText().trim();
                HABibliotecaFirmas.HAFirma haF = haBiblioteca.haGuardar(haNombreFinal, haImg);
                haNotificador.haExito("Firma «" + haF.haNombre() + "» guardada.");
                return Optional.of(haF);
            } catch (HAPdfListoException haE) {
                haNotificador.haError(haE.getMessage());
            } catch (IOException haE) {
                haNotificador.haError("No se pudo leer la imagen: " + haE.getMessage());
            }
        }
    }

    private static List<String> haLetrasDisponibles() {
        Set<String> haInstaladas = Set.copyOf(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        List<String> haLista = HA_LETRAS_MANUSCRITAS.stream().filter(haInstaladas::contains).toList();
        return haLista.isEmpty() ? List.of("Serif") : haLista;
    }

    private static BufferedImage haEscrita(String haTexto, String haFamilia) {
        BufferedImage haImg = new BufferedImage(1600, 360, BufferedImage.TYPE_INT_ARGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        haG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        haG.setColor(new java.awt.Color(20, 40, 120));
        java.awt.Font haFuente = new java.awt.Font(haFamilia, java.awt.Font.PLAIN, 130);
        haG.setFont(haFuente);
        int haAncho = haG.getFontMetrics().stringWidth(haTexto);
        if (haAncho > 1500) {
            haG.setFont(haFuente.deriveFont(130f * 1500 / haAncho));
        }
        haG.drawString(haTexto, 40, 230);
        haG.dispose();
        return haImg;
    }

    private static BufferedImage haConTransparencia(BufferedImage haImg) {
        BufferedImage haSalida = new BufferedImage(haImg.getWidth(), haImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        haSalida.getGraphics().drawImage(haImg, 0, 0, null);
        return haSalida;
    }
}
