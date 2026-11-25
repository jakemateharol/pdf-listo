package pe.pdflisto.ui;

import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import pe.pdflisto.verificacion.HAComprobacion;

import java.util.List;

public final class HAVistaComprobaciones {
    private HAVistaComprobaciones() {
    }

    public static VBox haFilas(List<HAComprobacion> haComprobaciones) {
        VBox haV = new VBox(6);
        haComprobaciones.stream().filter(haC -> haC.haEstado() != HAComprobacion.HAEstado.NO_APLICA)
                .forEach(haC -> haV.getChildren().add(haFila(haC)));
        return haV;
    }

    public static HBox haFila(HAComprobacion haC) {
        Ikon haIkon = switch (haC.haEstado()) {
            case CUMPLE -> Feather.CHECK_CIRCLE;
            case REVISAR -> Feather.ALERT_TRIANGLE;
            case INFO -> Feather.INFO;
            case NO_APLICA -> Feather.MINUS_CIRCLE;
        };
        FontIcon haIcono = new FontIcon(haIkon);
        haIcono.getStyleClass().add("estado-" + haC.haEstado().name().toLowerCase());
        Label haNombre = new Label(haC.haNombre() + " · " + haC.haEstado());
        haNombre.getStyleClass().add("comprobacion-nombre");
        Label haDetalle = new Label(haC.haDetalle());
        haDetalle.getStyleClass().add("texto-tenue");
        haDetalle.setWrapText(true);
        VBox haTextos = new VBox(1, haNombre, haDetalle);
        HBox.setHgrow(haTextos, Priority.ALWAYS);
        HBox haFila = new HBox(8, haIcono, haTextos);
        haFila.getStyleClass().add("comprobacion");
        return haFila;
    }

    public static Label haVeredicto(String haTexto, boolean haBien, boolean haGrave) {
        Label haL = new Label(haTexto, new FontIcon(haBien ? Feather.CHECK_CIRCLE : haGrave ? Feather.X_OCTAGON : Feather.ALERT_TRIANGLE));
        haL.getStyleClass().addAll("veredicto", haBien ? "cumple" : haGrave ? "peligro" : "revisar");
        haL.setMaxWidth(Double.MAX_VALUE);
        haL.setWrapText(true);
        return haL;
    }
}
