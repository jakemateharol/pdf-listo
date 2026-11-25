package pe.pdflisto.controller;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.entrega.HARevisionEnvio;
import pe.pdflisto.operaciones.HACompresor;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.operaciones.HAProcesoVersion;
import pe.pdflisto.privacidad.HAAreaTachado;
import pe.pdflisto.privacidad.HALimpiarMetadatos;
import pe.pdflisto.privacidad.HATachar;
import pe.pdflisto.ui.HANotificador;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HAPanelListoParaEnviar {
    private final HARevisionEnvio haRevision;
    private final HAFormulariosHerramientas haPaneles;
    private final HAMotorPdf haMotor;
    private final HACompresor haCompresor;
    private final HANotificador haNotificador;

    public void haRevisar(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        var haArchivo = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        haEditor.haEnSegundoPlano("Revisando si está listo para enviar", haC -> haRevision.haRevisar(haArchivo, haClave),
                haR -> haMostrar(haEditor, haR, false));
    }

    private void haMostrar(HAEditor haEditor, HARevisionEnvio.HARevision haR, boolean haDespuesDeArreglar) {
        VBox haCuerpo = new VBox(10);
        Label haVeredicto = new Label(switch (haR.haNivel()) {
            case ROJO -> "No está listo: tiene datos sensibles.";
            case AMARILLO -> "Casi listo: revisa los puntos en amarillo.";
            case VERDE -> "Listo para enviar.";
        });
        haVeredicto.getStyleClass().add("comprobacion-nombre");
        haVeredicto.setWrapText(true);
        haCuerpo.getChildren().add(new HBox(10, haLuz(haR.haNivel(), 22), haVeredicto));
        for (HARevisionEnvio.HAPunto haP : haR.haPuntos()) {
            haCuerpo.getChildren().add(haFila(haP));
        }
        String haSubtitulo = haDespuesDeArreglar ? "Revisión del resultado después de arreglarlo." : "Revisión sin modificar el documento.";
        if (haR.haPlan().isEmpty()) {
            haCuerpo.getChildren().add(HAFormulariosHerramientas.haNota(haR.haNivel() == HARevisionEnvio.HANivel.VERDE
                    ? "No hay nada que arreglar."
                    : "Lo que queda en amarillo no se arregla solo (por ejemplo, el OCR o la contraseña): hazlo desde Herramientas o la pestaña Tachado."));
            haPaneles.haAbrirInformativo(Feather.SEND, "Listo para enviar", haSubtitulo, haCuerpo);
            return;
        }
        Label haTituloPlan = new Label("Plan para arreglarlo");
        haTituloPlan.getStyleClass().add("panel-titulo");
        haCuerpo.getChildren().add(haTituloPlan);
        int haN = 1;
        for (String haPaso : haR.haDescripcionPlan()) {
            Label haNumero = new Label(String.valueOf(haN++));
            haNumero.getStyleClass().add("paso-numero");
            Label haTexto = new Label(haPaso);
            haTexto.setWrapText(true);
            HBox.setHgrow(haTexto, Priority.ALWAYS);
            HBox haFila = new HBox(10, haNumero, haTexto);
            haFila.setAlignment(Pos.CENTER_LEFT);
            haFila.getStyleClass().add("paso-plan");
            haCuerpo.getChildren().add(haFila);
        }
        haCuerpo.getChildren().add(HAFormulariosHerramientas.haNota("Se hace sobre una copia, en este orden, y cada paso se "
                + "verifica. El original no cambia y puedes deshacer con Ctrl+Z."));
        haPaneles.haAbrir(Feather.SEND, "Listo para enviar", haSubtitulo, haCuerpo, "Aprobar y arreglar",
                () -> haEjecutar(haEditor, haPasos(haR), 0));
    }

    List<Map.Entry<String, HAProcesoVersion>> haPasos(HARevisionEnvio.HARevision haR) {
        List<Map.Entry<String, HAProcesoVersion>> haPasos = new ArrayList<>();
        for (HARevisionEnvio.HAPasoArreglo haP : haR.haPlan()) {
            switch (haP) {
                case TACHAR -> {
                    List<HAAreaTachado> haAreas = haR.haDatos().stream()
                            .flatMap(haD -> haD.haAreas().stream().map(haA -> new HAAreaTachado(haD.haPagina(), haA).haAmpliada(1.5)))
                            .toList();
                    haPasos.add(Map.entry("Tachar datos sensibles", (haE, haClave, haSalida, haC) ->
                            haMotor.haEjecutar(haE, haClave, new HATachar(haAreas, true), haSalida, haC)));
                }
                case LIMPIAR_METADATOS -> haPasos.add(Map.entry("Limpiar metadatos", (haE, haClave, haSalida, haC) ->
                        haMotor.haEjecutar(haE, haClave, new HALimpiarMetadatos(HALimpiarMetadatos.HAOpciones.haTodo()), haSalida, haC)));
                case COMPRIMIR -> haPasos.add(Map.entry("Comprimir", (haE, haClave, haSalida, haC) ->
                        haCompresor.haHastaObjetivo(haE, haClave, HARevisionEnvio.HA_PESO_MAXIMO, haSalida, haC)));
            }
        }
        return haPasos;
    }

    private void haEjecutar(HAEditor haEditor, List<Map.Entry<String, HAProcesoVersion>> haPasos, int haI) {
        if (haI >= haPasos.size()) {
            HASesionEdicion haS = haEditor.haSesion();
            if (haS != null) {
                var haArchivo = haS.haArchivoActual();
                String haClave = haS.haContrasenaActual();
                haEditor.haEnSegundoPlano("Revisando el resultado", haC -> haRevision.haRevisar(haArchivo, haClave),
                        haR -> haMostrar(haEditor, haR, true));
            }
            return;
        }
        var haPaso = haPasos.get(haI);
        haEditor.haAplicar(haPaso.getKey(), haPaso.getValue(), haR -> haEjecutar(haEditor, haPasos, haI + 1));
    }

    private static Node haFila(HARevisionEnvio.HAPunto haP) {
        Label haTitulo = new Label(haP.haTitulo());
        haTitulo.getStyleClass().add("comprobacion-nombre");
        haTitulo.setWrapText(true);
        Label haDetalle = new Label(haP.haDetalle());
        haDetalle.setWrapText(true);
        haDetalle.getStyleClass().add("texto-tenue");
        VBox haTextos = new VBox(2, haTitulo, haDetalle);
        HBox.setHgrow(haTextos, Priority.ALWAYS);
        HBox haFila = new HBox(10, haLuz(haP.haNivel(), 16), haTextos);
        haFila.setAlignment(Pos.TOP_LEFT);
        haFila.getStyleClass().add("comprobacion");
        return haFila;
    }

    private static Node haLuz(HARevisionEnvio.HANivel haNivel, int haTam) {
        FontIcon haIcono = new FontIcon(switch (haNivel) {
            case ROJO -> Feather.X_CIRCLE;
            case AMARILLO -> Feather.ALERT_TRIANGLE;
            case VERDE -> Feather.CHECK_CIRCLE;
        });
        haIcono.setIconSize(haTam);
        haIcono.getStyleClass().add(switch (haNivel) {
            case ROJO -> "estado-falla";
            case AMARILLO -> "estado-revisar";
            case VERDE -> "estado-cumple";
        });
        return haIcono;
    }
}
