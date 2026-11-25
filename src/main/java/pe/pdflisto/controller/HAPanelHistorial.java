package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.model.HARegistroHistorial;
import pe.pdflisto.service.HAHistorialService;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class HAPanelHistorial {
    private static final DateTimeFormatter HA_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final HAHistorialService haHistorial;
    private final ListView<HARegistroHistorial> haLista = new ListView<>();

    public void haMontar(VBox haContenedor, Consumer<String> haAlRepetir) {
        Label haTitulo = new Label("Operaciones recientes");
        haTitulo.getStyleClass().add("panel-titulo");
        Button haActualizar = new Button(null, new FontIcon(Feather.REFRESH_CW));
        haActualizar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        haActualizar.setTooltip(new Tooltip("Actualizar"));
        haActualizar.setOnAction(haE -> haRefrescar());
        HBox haCabecera = new HBox(8, haTitulo, haEspacio(), haActualizar);
        haCabecera.setAlignment(Pos.CENTER_LEFT);

        haLista.getStyleClass().add("lista-historial");
        haLista.setPlaceholder(new Label("Aún no hay operaciones."));
        haLista.setCellFactory(haL -> new HACelda(haAlRepetir));
        VBox.setVgrow(haLista, Priority.ALWAYS);
        haContenedor.getChildren().setAll(haCabecera, haLista);
        haRefrescar();
    }

    public void haRefrescar() {
        haLista.getItems().setAll(haHistorial.haRecientes());
    }

    private static HBox haEspacio() {
        HBox haH = new HBox();
        HBox.setHgrow(haH, Priority.ALWAYS);
        return haH;
    }

    private static final class HACelda extends ListCell<HARegistroHistorial> {
        private final Consumer<String> haAlRepetir;

        HACelda(Consumer<String> haAlRepetir) {
            this.haAlRepetir = haAlRepetir;
            getStyleClass().add("celda-historial");
        }

        @Override
        protected void updateItem(HARegistroHistorial haR, boolean haVacia) {
            super.updateItem(haR, haVacia);
            if (haVacia || haR == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label haDescripcion = new Label(haR.getDescripcion());
            haDescripcion.getStyleClass().add("comprobacion-nombre");
            haDescripcion.setWrapText(true);
            String haArchivos = haNombre(haR.getOrigen()) + (haR.getDestino() != null ? " → " + haNombre(haR.getDestino()) : "");
            Label haLinea = new Label(haR.getFecha().format(HA_FECHA) + " · " + haArchivos);
            haLinea.getStyleClass().add("texto-tenue");
            haLinea.setWrapText(true);
            VBox haTextos = new VBox(2, haDescripcion, haLinea);
            if (haR.getPasos() != null) {
                Label haPasos = new Label("Pasos: " + haR.getPasos());
                haPasos.getStyleClass().add("texto-tenue");
                haPasos.setWrapText(true);
                haTextos.getChildren().add(haPasos);
            }
            if (haR.getVerificacion() != null) {
                Label haV = new Label("Verificación: " + haR.getVerificacion());
                haV.getStyleClass().add("Cumple".equals(haR.getVerificacion()) ? "texto-cumple" : "texto-revisar");
                haTextos.getChildren().add(haV);
            }
            haTextos.setMaxWidth(240);
            HBox.setHgrow(haTextos, Priority.ALWAYS);
            HBox haFila = new HBox(6, haTextos);
            if (haR.getPedido() != null) {
                Button haRepetir = new Button(null, new FontIcon(Feather.REPEAT));
                haRepetir.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.SMALL);
                haRepetir.setTooltip(new Tooltip("Repetir en el documento abierto (verás el plan antes)"));
                haRepetir.setOnAction(haE -> haAlRepetir.accept(haR.getPedido()));
                haFila.getChildren().add(haRepetir);
            }
            setText(null);
            setGraphic(haFila);
        }

        private static String haNombre(String haRuta) {
            return haRuta == null ? "—" : new File(haRuta).getName();
        }
    }
}
