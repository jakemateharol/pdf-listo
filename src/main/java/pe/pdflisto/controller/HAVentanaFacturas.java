package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import pe.pdflisto.facturas.HAComprobante;
import pe.pdflisto.facturas.HADatosQr;
import pe.pdflisto.facturas.HAExportadorFacturas;
import pe.pdflisto.ui.HAExportador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class HAVentanaFacturas {
    private HAVentanaFacturas() {
    }

    static void haMostrar(Window haDueno, List<HAComprobante> haLista, HAExportadorFacturas haExportadorFacturas, HAExportador haExportador,
                        pe.pdflisto.facturas.HAVerificadorExcelFacturas haVerificadorExcel) {
        haCrear(haDueno, haLista, haExportadorFacturas, haExportador, haVerificadorExcel).show();
    }

    static Stage haCrear(Window haDueno, List<HAComprobante> haLista, HAExportadorFacturas haExportadorFacturas, HAExportador haExportador,
                       pe.pdflisto.facturas.HAVerificadorExcelFacturas haVerificadorExcel) {
        TableView<HAComprobante> haTabla = new TableView<>(FXCollections.observableArrayList(haLista));
        haTabla.getColumns().add(haColumna("Archivo", 170, haC -> haC.haArchivo()));
        haTabla.getColumns().add(haColumna("Pág.", 45, haC -> haC.haPagina() > 0 ? String.valueOf(haC.haPagina()) : ""));
        haTabla.getColumns().add(haColumna("Tipo", 95, haC -> haDato(haC, HADatosQr::haNombreTipo)));
        haTabla.getColumns().add(haColumna("Serie-Número", 115, haC -> haDato(haC, HADatosQr::haSerieNumero)));
        haTabla.getColumns().add(haColumna("Fecha", 85, haC -> haDato(haC, haD -> haD.haFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))));
        haTabla.getColumns().add(haColumna("RUC emisor", 100, haC -> haDato(haC, HADatosQr::haRucEmisor)));
        haTabla.getColumns().add(haColumna("Cliente", 130, haC -> haDato(haC, HADatosQr::haNombreDocCliente)));
        haTabla.getColumns().add(haColumna("IGV", 75, haC -> haDato(haC, haD -> "S/ " + haD.haIgv())));
        haTabla.getColumns().add(haColumna("Total", 90, haC -> haDato(haC, haD -> "S/ " + haD.haTotal())));
        haTabla.getColumns().add(haColumna("Estado", 175, haC -> haC.haEstado().toString()));
        haTabla.getColumns().add(haColumna("Observaciones", 420, HAComprobante::haObservacionesTexto));
        haTabla.setRowFactory(haT -> new TableRow<>() {
            @Override
            protected void updateItem(HAComprobante haC, boolean haVacia) {
                super.updateItem(haC, haVacia);
                getStyleClass().removeAll("fila-correcto", "fila-no-comprobable", "fila-revisar", "fila-duplicado");
                setTooltip(null);
                if (!haVacia && haC != null) {
                    getStyleClass().add(switch (haC.haEstado()) {
                        case CUMPLE -> "fila-correcto";
                        case NO_COMPROBABLE -> "fila-no-comprobable";
                        case REVISAR -> "fila-revisar";
                        default -> "fila-duplicado";
                    });
                    if (!haC.haObservaciones().isEmpty()) {
                        setTooltip(new Tooltip(haC.haObservacionesTexto()));
                    }
                }
            }
        });
        haTabla.setPlaceholder(new Label("No se leyó ningún comprobante."));

        long haCorrectos = haLista.stream().filter(haC -> haC.haEstado() == HAComprobante.HAEstado.CUMPLE).count();
        long haNoComprobables = haLista.stream().filter(haC -> haC.haEstado() == HAComprobante.HAEstado.NO_COMPROBABLE).count();
        long haRevisar = haLista.stream().filter(haC -> haC.haEstado() == HAComprobante.HAEstado.REVISAR).count();
        long haDuplicados = haLista.stream().filter(haC -> haC.haEstado() == HAComprobante.HAEstado.DUPLICADO).count();
        long haSinQr = haLista.stream().filter(haC -> haC.haEstado() == HAComprobante.HAEstado.SIN_QR).count();
        BigDecimal haTotal = haLista.stream()
                .filter(haC -> haC.haDatos() != null && haC.haEstado() != HAComprobante.HAEstado.DUPLICADO)
                .map(haC -> haC.haDatos().haTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Label haResumen = new Label((haLista.size() - haSinQr) + " comprobantes · " + haCorrectos + " cumplen · " + haRevisar
                + " para revisar · " + haNoComprobables + " no se pueden comprobar · " + haDuplicados + " duplicados" + (haSinQr > 0 ? " · " + haSinQr + " sin QR" : "")
                + "   |   Total (sin duplicados): S/ " + String.format("%,.2f", haTotal));
        haResumen.getStyleClass().add("comprobacion-nombre");
        Label haNota = new Label("Datos leídos del código QR de SUNAT (no incluye el nombre del proveedor ni los ítems). "
                + "Pasa el mouse sobre una fila para ver sus observaciones. Amarillo = error seguro (Revisar); azul = el QR no basta para comprobarlo (mira el impreso).");
        haNota.getStyleClass().add("texto-tenue");
        haNota.setWrapText(true);

        Stage haVentana = new Stage();
        haVentana.initOwner(haDueno);
        haVentana.setTitle("Facturas leídas del código QR");
        Button haExportar = new Button("Exportar a Excel…", new FontIcon(Feather.DOWNLOAD));
        haExportar.getStyleClass().add(Styles.ACCENT);
        haExportar.setDisable(haLista.stream().allMatch(haC -> haC.haDatos() == null));
        VBox haZonaVerificacion = new VBox(6);
        haExportar.setOnAction(haE -> haExportador.haGuardarVerificado(haVentana, "Guardar Excel de comprobantes",
                "Comprobantes " + LocalDate.now(), HAExportador.HAFormato.EXCEL,
                haDestino -> haExportadorFacturas.haExportar(haLista, haDestino),
                haDestino -> haVerificadorExcel.haVerificar(haDestino, haLista),
                haInforme -> haMostrarVerificacion(haZonaVerificacion, haInforme)));
        Button haCerrar = new Button("Cerrar");
        haCerrar.setOnAction(haE -> haVentana.close());
        Region haEspacio = new Region();
        HBox.setHgrow(haEspacio, Priority.ALWAYS);
        haCerrar.setMinWidth(Region.USE_PREF_SIZE);
        haExportar.setMinWidth(Region.USE_PREF_SIZE);
        HBox haPie = new HBox(10, haNota, haEspacio, haCerrar, haExportar);
        haPie.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(haNota, Priority.ALWAYS);

        BorderPane haRaiz = new BorderPane(haTabla);
        haRaiz.setTop(new VBox(6, haResumen, haZonaVerificacion));
        haRaiz.setBottom(haPie);
        BorderPane.setMargin(haTabla, new Insets(10, 0, 10, 0));
        haRaiz.setPadding(new Insets(16));
        haRaiz.getStyleClass().add("app");
        Scene haEscena = new Scene(haRaiz, 1260, 620);
        haEscena.getStylesheets().add(Objects.requireNonNull(HAVentanaFacturas.class.getResource("/css/app.css")).toExternalForm());
        haVentana.setScene(haEscena);
        return haVentana;
    }

    private static void haMostrarVerificacion(VBox haZona, pe.pdflisto.verificacion.HAInformeVerificacion haInforme) {
        boolean haOk = haInforme.haCumple();
        Label haVeredicto = pe.pdflisto.ui.HAVistaComprobaciones.haVeredicto(haOk
                ? "Excel verificado: cumple (" + haInforme.haDocumento() + ")"
                : "Revisar el Excel: no cumple todo lo esperado (" + haInforme.haDocumento() + ")", haOk, false);
        VBox haFilas = pe.pdflisto.ui.HAVistaComprobaciones.haFilas(haInforme.haComprobaciones());
        haFilas.setVisible(!haOk);
        haFilas.setManaged(!haOk);
        Button haDetalle = new Button(haOk ? "Ver qué se comprobó" : "Ocultar detalle");
        haDetalle.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        haDetalle.setOnAction(haE -> {
            boolean haVer = !haFilas.isVisible();
            haFilas.setVisible(haVer);
            haFilas.setManaged(haVer);
            haDetalle.setText(haVer ? "Ocultar detalle" : "Ver qué se comprobó");
        });
        haZona.getChildren().setAll(haVeredicto, haDetalle, haFilas);
    }

    private static TableColumn<HAComprobante, String> haColumna(String haTitulo, double haAncho, Function<HAComprobante, String> haValor) {
        TableColumn<HAComprobante, String> haC = new TableColumn<>(haTitulo);
        haC.setPrefWidth(haAncho);
        haC.setCellValueFactory(haF -> new SimpleStringProperty(haValor.apply(haF.getValue())));
        return haC;
    }

    private static String haDato(HAComprobante haC, Function<HADatosQr, String> haF) {
        return haC.haDatos() == null ? "" : haF.apply(haC.haDatos());
    }
}
