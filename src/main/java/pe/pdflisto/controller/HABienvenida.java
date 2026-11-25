package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.ui.HANotificador;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HABienvenida {
    record HAPantalla(Feather haIcono, String haTitulo, String haTexto) {
    }

    static final List<HAPantalla> HA_PANTALLAS = List.of(
            new HAPantalla(null, "Bienvenido a PDF Listo",
                    "Un editor de PDF que funciona sin internet: tus archivos nunca salen de este equipo.\n\n"
                            + "Abre un PDF con «Abrir PDF» o arrástralo a la ventana."),
            new HAPantalla(Feather.MESSAGE_SQUARE, "Pide lo que necesitas con palabras",
                    "En la pestaña «Pedido» escribe, por ejemplo:\n«une, comprime a menos de 2 MB y numera».\n\n"
                            + "PDF Listo te muestra el plan ANTES de hacer nada, y al terminar verifica que el "
                            + "resultado cumpla lo pedido."),
            new HAPantalla(Feather.SHIELD, "Tu original está a salvo",
                    "Todo se hace sobre una copia: el archivo original nunca se modifica.\n\n"
                            + "Deshaz con Ctrl+Z, rehaz con Ctrl+Y y, cuando esté listo, pulsa «Guardar copia». "
                            + "Si la app se cierra de golpe, al volver te ofrece recuperar tu trabajo."),
            new HAPantalla(Feather.EYE_OFF, "Privacidad de verdad",
                    "En «Tachar» busca DNI, RUC, celulares, correos y cuentas. El tachado BORRA el dato del archivo "
                            + "(no solo lo tapa) y se comprueba releyendo el resultado.\n\n"
                            + "También puedes quitar los datos ocultos (metadatos) y poner contraseña."),
            new HAPantalla(Feather.GRID, "Y mucho más",
                    "Anotar y firmar · llenar formularios · OCR para escaneados · facturas con QR a Excel · "
                            + "perfiles de entrega (tesis, trámites) · recetas · convertir imágenes y texto.\n\n"
                            + "Vuelve a ver este recorrido y el manual en el botón de Ayuda (?) arriba a la derecha."));

    private final HANotificador haNotificador;
    private final HAConfiguracion haConfiguracion;

    public void haMostrarSiCorresponde() {
        if (haConfiguracion.haMostrarBienvenida()) {
            haMostrar();
        }
    }

    public void haMostrar() {
        new HARecorrido().haIr(0);
    }

    private final class HARecorrido {
        private final StackPane haCabecera = new StackPane();
        private final Label haTitulo = new Label();
        private final Label haTexto = new Label();
        private final HBox haPuntos = new HBox(8);
        private final Button haAtras = new Button("Atrás");
        private final Button haSiguiente = new Button("Siguiente");
        private final CheckBox haNoMostrar = new CheckBox("No volver a mostrar al iniciar");
        private int haActual;

        HARecorrido() {
            haTitulo.getStyleClass().add("bienvenida-titulo");
            haTitulo.setWrapText(true);
            haTexto.getStyleClass().add("dialogo-mensaje");
            haTexto.setWrapText(true);
            haTexto.setMinHeight(120);
            haTexto.setAlignment(Pos.TOP_LEFT);
            haPuntos.setAlignment(Pos.CENTER);
            haNoMostrar.setSelected(true);
            haAtras.getStyleClass().add(Styles.BUTTON_OUTLINED);
            haAtras.setOnAction(haE -> haIr(haActual - 1));
            haSiguiente.getStyleClass().add(Styles.ACCENT);
            haSiguiente.setDefaultButton(true);
            haSiguiente.setOnAction(haE -> {
                if (haActual == HA_PANTALLAS.size() - 1) {
                    haCerrar();
                } else {
                    haIr(haActual + 1);
                }
            });
            Button haSaltar = new Button("Saltar");
            haSaltar.getStyleClass().add(Styles.FLAT);
            haSaltar.setOnAction(haE -> haCerrar());

            Region haEspacio = new Region();
            HBox.setHgrow(haEspacio, Priority.ALWAYS);
            HBox haAcciones = new HBox(10, haSaltar, haEspacio, haAtras, haSiguiente);
            haAcciones.setAlignment(Pos.CENTER_LEFT);
            haAcciones.getStyleClass().add("dialogo-acciones");

            VBox haCaja = new VBox(16, haCabecera, haTitulo, haTexto, haPuntos, haNoMostrar, haAcciones);
            haCaja.getStyleClass().addAll("dialogo", "bienvenida");
            haCaja.setMaxSize(520, Region.USE_PREF_SIZE);
            haCaja.setPrefWidth(520);
            haCaja.setOnKeyPressed(haE -> {
                if (haE.getCode() == KeyCode.RIGHT && haActual < HA_PANTALLAS.size() - 1) {
                    haIr(haActual + 1);
                } else if (haE.getCode() == KeyCode.LEFT && haActual > 0) {
                    haIr(haActual - 1);
                } else if (haE.getCode() == KeyCode.ESCAPE) {
                    haCerrar();
                }
            });
            haNotificador.haMostrarModal(haCaja);
        }

        void haIr(int haI) {
            haActual = Math.max(0, Math.min(HA_PANTALLAS.size() - 1, haI));
            HAPantalla haP = HA_PANTALLAS.get(haActual);
            haCabecera.getChildren().setAll(haP.haIcono() == null ? haLogo() : haCirculo(haP.haIcono()));
            haCabecera.setAlignment(Pos.CENTER_LEFT);
            haTitulo.setText(haP.haTitulo());
            haTexto.setText(haP.haTexto());
            haPuntos.getChildren().clear();
            for (int haJ = 0; haJ < HA_PANTALLAS.size(); haJ++) {
                Circle haC = new Circle(4);
                haC.getStyleClass().add(haJ == haActual ? "punto-activo" : "punto");
                haPuntos.getChildren().add(haC);
            }
            haAtras.setDisable(haActual == 0);
            boolean haUltima = haActual == HA_PANTALLAS.size() - 1;
            haSiguiente.setText(haUltima ? "Empezar" : "Siguiente");
            haSiguiente.requestFocus();
        }

        void haCerrar() {
            haConfiguracion.setMostrarBienvenida(!haNoMostrar.isSelected());
            haNotificador.haCerrarModal();
        }
    }

    private static StackPane haCirculo(Feather haIcono) {
        StackPane haS = new StackPane(new FontIcon(haIcono));
        haS.getStyleClass().addAll("dialogo-icono", "bienvenida-icono");
        return haS;
    }

    private static StackPane haLogo() {
        try (InputStream haIn = HABienvenida.class.getResourceAsStream("/icono/pdflisto-64.png")) {
            if (haIn != null) {
                ImageView haV = new ImageView(new Image(haIn));
                haV.setFitWidth(56);
                haV.setFitHeight(56);
                return new StackPane(haV);
            }
        } catch (Exception haIgnorado) {
        }
        return haCirculo(Feather.FILE_TEXT);
    }
}
