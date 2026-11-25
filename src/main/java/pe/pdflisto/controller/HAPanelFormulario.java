package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.formularios.HACampoFormulario;
import pe.pdflisto.formularios.HALectorFormulario;
import pe.pdflisto.formularios.HARellenarFormulario;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.pdf.HADocumentoPdf;
import pe.pdflisto.ui.HANotificador;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class HAPanelFormulario {
    private final HAMotorPdf haMotor;
    private final HANotificador haNotificador;

    private HAEditor haEditor;
    private final VBox haZona = new VBox(10);
    private final CheckBox haAplanar = new CheckBox("Aplanar al guardar (los campos quedan fijos y ya no se pueden editar)");
    private final Map<String, Supplier<String>> haLectores = new LinkedHashMap<>();
    private final Map<String, String> haOriginales = new LinkedHashMap<>();
    private List<HACampoFormulario> haCampos = List.of();
    private HADocumentoPdf haCargado;

    public void haMontar(VBox haContenedor, HAEditor haEditor) {
        this.haEditor = haEditor;
        Label haTitulo = new Label("Formulario");
        haTitulo.getStyleClass().add("panel-titulo");
        haAplanar.setWrapText(true);
        haContenedor.getChildren().setAll(haTitulo, haZona);
        haMostrarMensaje("Abre un PDF para ver sus campos.");
    }

    public void haDocumentoCambio() {
        haCargar();
    }

    public void haVersionCambio() {
        haCargar();
    }

    private void haCargar() {
        HASesionEdicion haS = haEditor == null ? null : haEditor.haSesion();
        if (haS == null || haS.haVista() == null) {
            haCargado = null;
            haMostrarMensaje("Abre un PDF para ver sus campos.");
            return;
        }
        HADocumentoPdf haDoc = haS.haVista();
        if (haDoc == haCargado) {
            return;
        }
        haCargado = haDoc;
        haMostrarMensaje("Leyendo el formulario…");
        haDoc.haFormulario().whenComplete((haForm, haError) -> Platform.runLater(() -> {
            if (haDoc != haCargado) {
                return;
            }
            if (haError != null) {
                haMostrarMensaje("No se pudo leer el formulario de este PDF.");
            } else {
                haDibujar(haForm);
            }
        }));
    }

    private void haMostrarMensaje(String haTexto) {
        haLectores.clear();
        haZona.getChildren().setAll(HAFormulariosHerramientas.haNota(haTexto));
    }

    private void haDibujar(HALectorFormulario.HAFormulario haForm) {
        haLectores.clear();
        haOriginales.clear();
        haCampos = haForm.haCampos();
        if (haForm.haXfaDinamico()) {
            haMostrarMensaje("Este formulario es de tipo XFA (Adobe LiveCycle). PDF Listo no puede llenarlo de forma "
                    + "confiable: ábrelo en Adobe Acrobat Reader.");
            return;
        }
        if (haCampos.isEmpty()) {
            haMostrarMensaje("Este PDF no tiene campos de formulario. Para escribir sobre él (por ejemplo, llenar un "
                    + "formato impreso), usa la herramienta «Texto» de la pestaña Anotar.");
            return;
        }
        haZona.getChildren().clear();
        haZona.getChildren().add(HAFormulariosHerramientas.haNota(haCampos.size() + " campos. Los marcados con * son obligatorios."));
        for (HACampoFormulario haC : haCampos) {
            haZona.getChildren().add(haFila(haC));
        }
        Button haGuardar = new Button("Guardar formulario", new FontIcon(Feather.SAVE));
        haGuardar.getStyleClass().add(Styles.ACCENT);
        haGuardar.setMaxWidth(Double.MAX_VALUE);
        haGuardar.setOnAction(haE -> haGuardar());
        haZona.getChildren().addAll(haAplanar, haGuardar);
    }

    private VBox haFila(HACampoFormulario haC) {
        Label haEtiqueta = new Label(haC.haEtiqueta() + (haC.haObligatorio() ? " *" : ""));
        haEtiqueta.getStyleClass().add("campo-label");
        haEtiqueta.setWrapText(true);
        Control haControl;
        Supplier<String> haLector;
        switch (haC.haTipo()) {
            case CASILLA -> {
                CheckBox haCaja = new CheckBox("Marcado");
                haCaja.setSelected(Boolean.parseBoolean(haC.haValor()));
                haControl = haCaja;
                haLector = () -> String.valueOf(haCaja.isSelected());
            }
            case OPCION, LISTA -> {
                ComboBox<String> haCombo = new ComboBox<>(FXCollections.observableArrayList(haC.haOpciones()));
                haCombo.setValue(haC.haValor() == null || haC.haValor().isBlank() || haC.haValor().equals("Off") ? null : haC.haValor());
                haCombo.setPromptText("Elige una opción");
                haControl = haCombo;
                haLector = () -> haCombo.getValue() == null ? "" : haCombo.getValue();
            }
            case FIRMA_DIGITAL -> {
                Label haAviso = new Label("Campo de firma digital (con certificado): no se puede firmar con PDF Listo. "
                        + "Para una firma visual usa la herramienta Firma de la pestaña Anotar.");
                haAviso.setWrapText(true);
                haAviso.getStyleClass().add("texto-tenue");
                haControl = haAviso;
                haLector = null;
            }
            default -> {
                TextField haTexto = new TextField(haC.haValor());
                haControl = haTexto;
                haLector = haTexto::getText;
            }
        }
        haControl.setMaxWidth(Double.MAX_VALUE);
        haControl.setDisable(haC.haSoloLectura());
        if (haLector != null && !haC.haSoloLectura()) {
            haLectores.put(haC.haNombre(), haLector);
            haOriginales.put(haC.haNombre(), haLector.get());
        }
        HBox.setHgrow(haControl, Priority.ALWAYS);
        HBox haLinea = new HBox(6, haControl);
        haLinea.setAlignment(Pos.CENTER_LEFT);
        if (haC.haPagina() >= 0) {
            Button haVer = new Button(null, new FontIcon(Feather.EYE));
            haVer.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            haVer.setTooltip(new Tooltip("Ver en la página " + (haC.haPagina() + 1)));
            haVer.setOnAction(haE -> haEditor.haMostrarPagina(haC.haPagina()));
            haLinea.getChildren().add(haVer);
        }
        VBox haV = new VBox(4, haEtiqueta, haLinea);
        haV.getStyleClass().add("campo");
        return haV;
    }

    private void haGuardar() {
        Map<String, String> haCambios = new LinkedHashMap<>();
        haLectores.forEach((haNombre, haLector) -> {
            String haValor = haLector.get();
            if (!haValor.equals(haOriginales.get(haNombre))) {
                haCambios.put(haNombre, haValor);
            }
        });
        if (haCambios.isEmpty() && !haAplanar.isSelected()) {
            haNotificador.haInfo("No cambiaste ningún campo.");
            return;
        }
        List<String> haFaltan = haCampos.stream().filter(HACampoFormulario::haObligatorio)
                .filter(haC -> haLectores.containsKey(haC.haNombre()))
                .filter(haC -> {
                    String haV = haLectores.get(haC.haNombre()).get();
                    return haV.isBlank() || haV.equals("false");
                })
                .map(HACampoFormulario::haEtiqueta).toList();
        Runnable haEjecutar = () -> {
            HARellenarFormulario haOp = new HARellenarFormulario(haCambios, haAplanar.isSelected());
            haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                    haR -> haNotificador.haExito(haAplanar.isSelected() ? "Formulario guardado y aplanado." : "Formulario guardado."));
        };
        if (haFaltan.isEmpty()) {
            haEjecutar.run();
        } else {
            haNotificador.haPreguntar("Faltan campos obligatorios", "Están vacíos: " + String.join(", ", haFaltan)
                    + ". ¿Guardar de todos modos?", "Guardar igual", haEjecutar);
        }
    }
}
