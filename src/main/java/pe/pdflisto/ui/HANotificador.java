package pe.pdflisto.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

@Component
public class HANotificador {
    private static final Duration HA_DURACION_TOAST = Duration.seconds(3.5);

    private ModalPane haModalPanel;
    private ModalPane haModalDialogo;
    private VBox haZonaToasts;

    public void haRegistrar(ModalPane haModalPanel, ModalPane haModalDialogo, VBox haZonaToasts) {
        this.haModalPanel = haModalPanel;
        this.haModalDialogo = haModalDialogo;
        this.haZonaToasts = haZonaToasts;
    }

    public void haExito(String haMensaje) {
        haToast(haMensaje, Feather.CHECK_CIRCLE, Styles.SUCCESS);
    }

    public void haAdvertencia(String haMensaje) {
        haToast(haMensaje, Feather.ALERT_TRIANGLE, Styles.WARNING);
    }

    public void haError(String haMensaje) {
        haToast(haMensaje, Feather.X_CIRCLE, Styles.DANGER);
    }

    public void haInfo(String haMensaje) {
        haToast(haMensaje, Feather.INFO, Styles.ACCENT);
    }

    private void haToast(String haMensaje, Ikon haIcono, String haEstilo) {
        Notification haN = new Notification(haMensaje, new FontIcon(haIcono));
        haN.getStyleClass().addAll(haEstilo, Styles.ELEVATED_2, "toast");
        haN.setPrefWidth(360);
        haN.setMaxWidth(360);
        haN.setOnClose(haE -> haCerrarToast(haN));

        haZonaToasts.getChildren().add(haN);
        Animations.fadeInUp(haN, Duration.millis(280)).play();

        PauseTransition haEspera = new PauseTransition(HA_DURACION_TOAST);
        haEspera.setOnFinished(haE -> haCerrarToast(haN));
        haEspera.play();
    }

    public Runnable haTarjeta(Node haContenido, Duration haDuracion) {
        Button haCerrar = new Button(null, new FontIcon(Feather.X));
        haCerrar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        StackPane.setAlignment(haCerrar, Pos.TOP_RIGHT);
        StackPane haCaja = new StackPane(haContenido, haCerrar);
        haCaja.getStyleClass().addAll("tarjeta-flotante", Styles.ELEVATED_2);
        haCaja.setMaxWidth(360);
        haCaja.setPrefWidth(360);
        haZonaToasts.getChildren().add(haCaja);
        Animations.fadeInUp(haCaja, Duration.millis(280)).play();
        Runnable haCierre = () -> haCerrarToast(haCaja);
        haCerrar.setOnAction(haE -> haCierre.run());
        PauseTransition haEspera = new PauseTransition(haDuracion);
        haEspera.setOnFinished(haE -> haCierre.run());
        haCaja.setOnMouseEntered(haE -> haEspera.pause());
        haCaja.setOnMouseExited(haE -> haEspera.playFromStart());
        haEspera.play();
        return haCierre;
    }

    public void haElegir(String haTitulo, String haMensaje, String haTextoNo, Runnable haAlNo, String haTextoSi, Runnable haAlSi) {
        haDialogo(Feather.ALERT_TRIANGLE, "aviso", haTitulo, haMensaje, haTextoNo, haAlNo, haTextoSi, Styles.ACCENT, haAlSi);
    }

    private void haCerrarToast(Node haN) {
        if (!haZonaToasts.getChildren().contains(haN) || haN.getProperties().containsKey("cerrando")) {
            return;
        }
        haN.getProperties().put("cerrando", true);
        Timeline haSalida = Animations.fadeOutRight(haN, Duration.millis(250));
        haSalida.setOnFinished(haE -> haZonaToasts.getChildren().remove(haN));
        haSalida.play();
    }

    public void haConfirmar(String haTitulo, String haMensaje, String haTextoAccion, Runnable haAlConfirmar) {
        haDialogo(Feather.TRASH_2, "peligro", haTitulo, haMensaje,
                "Cancelar", null, haTextoAccion, Styles.DANGER, haAlConfirmar);
    }

    public void haPreguntar(String haTitulo, String haMensaje, String haTextoAccion, Runnable haAlConfirmar) {
        haDialogo(Feather.ALERT_TRIANGLE, "aviso", haTitulo, haMensaje,
                "Cancelar", null, haTextoAccion, Styles.ACCENT, haAlConfirmar);
    }

    public void haExitoConAcciones(String haTitulo, String haMensaje,
                                 String haTextoSecundario, Runnable haSecundaria,
                                 String haTextoPrincipal, Runnable haPrincipal) {
        haDialogo(Feather.CHECK_CIRCLE, "exito", haTitulo, haMensaje,
                haTextoSecundario, haSecundaria, haTextoPrincipal, Styles.ACCENT, haPrincipal);
    }

    private void haDialogo(Ikon haIconoDialogo, String haClaseIcono, String haTitulo, String haMensaje,
                         String haTextoSecundario, Runnable haSecundaria,
                         String haTextoPrincipal, String haEstiloPrincipal, Runnable haPrincipal) {
        FontIcon haIcono = new FontIcon(haIconoDialogo);
        StackPane haCirculo = new StackPane(haIcono);
        haCirculo.getStyleClass().addAll("dialogo-icono", haClaseIcono);

        Label haLblTitulo = new Label(haTitulo);
        haLblTitulo.getStyleClass().add("dialogo-titulo");
        Label haLblMensaje = new Label(haMensaje);
        haLblMensaje.getStyleClass().add("dialogo-mensaje");
        haLblMensaje.setWrapText(true);

        Button haCancelar = new Button(haTextoSecundario);
        haCancelar.getStyleClass().add(Styles.BUTTON_OUTLINED);
        haCancelar.setCancelButton(true);
        haCancelar.setOnAction(haE -> {
            haModalDialogo.hide();
            if (haSecundaria != null) {
                haSecundaria.run();
            }
        });

        Button haAceptar = new Button(haTextoPrincipal);
        haAceptar.getStyleClass().add(haEstiloPrincipal);
        haAceptar.setOnAction(haE -> {
            haModalDialogo.hide();
            haPrincipal.run();
        });

        Region haEspacio = new Region();
        HBox.setHgrow(haEspacio, javafx.scene.layout.Priority.ALWAYS);
        HBox haAcciones = new HBox(10, haEspacio, haCancelar, haAceptar);
        haAcciones.setAlignment(Pos.CENTER_RIGHT);
        haAcciones.getStyleClass().add("dialogo-acciones");

        VBox haDialogo = new VBox(14, haCirculo, haLblTitulo, haLblMensaje, haAcciones);
        haDialogo.getStyleClass().add("dialogo");
        haDialogo.setMaxSize(440, Region.USE_PREF_SIZE);

        haModalDialogo.usePredefinedTransitionFactories(null);
        haModalDialogo.show(haDialogo);
        haAceptar.requestFocus();
    }

    public void haMostrarModal(Node haContenido) {
        haModalDialogo.usePredefinedTransitionFactories(null);
        haModalDialogo.show(haContenido);
    }

    public void haCerrarModal() {
        haModalDialogo.hide();
    }

    public void haAbrirPanel(Node haContenido) {
        haModalPanel.usePredefinedTransitionFactories(Side.RIGHT,
                Duration.millis(320), Duration.millis(220));
        haModalPanel.show(haContenido);
    }

    public void haCerrarPanel() {
        haModalPanel.hide();
    }
}
