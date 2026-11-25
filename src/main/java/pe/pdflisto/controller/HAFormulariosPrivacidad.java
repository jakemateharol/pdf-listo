package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.model.HAReglaDeteccion;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HALimpiarMetadatos;
import pe.pdflisto.privacidad.HAProteger;
import pe.pdflisto.service.HAReglasService;
import pe.pdflisto.ui.HANotificador;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class HAFormulariosPrivacidad {
    private final HAFormulariosHerramientas haPaneles;
    private final HAReglasService haReglasService;
    private final HAMotorPdf haMotor;
    private final HANotificador haNotificador;

    private enum HAModoUsuario {
        PALABRA("Palabra o frase exacta", HAReglaDeteccion.HAModo.PALABRA),
        PATRON("Patrón avanzado (expresión regular)", HAReglaDeteccion.HAModo.PATRON);

        final String haTexto;
        final HAReglaDeteccion.HAModo haModo;

        HAModoUsuario(String haTexto, HAReglaDeteccion.HAModo haModo) {
            this.haTexto = haTexto;
            this.haModo = haModo;
        }

        @Override
        public String toString() {
            return haTexto;
        }
    }

    public void haReglas(Runnable haAlGuardar) {
        List<HAReglaDeteccion> haReglas = new ArrayList<>(haReglasService.haTodas());
        List<HAReglaDeteccion> haBorradas = new ArrayList<>();
        VBox haLista = new VBox(8);
        Runnable[] haRedibujar = new Runnable[1];
        haRedibujar[0] = () -> {
            haLista.getChildren().clear();
            for (HAReglaDeteccion haR : haReglas) {
                CheckBox haActiva = new CheckBox(haR.getNombre());
                haActiva.setSelected(haR.isActiva());
                haActiva.setWrapText(true);
                haActiva.selectedProperty().addListener((haO, haA, haB) -> haR.setActiva(haB));
                HBox haFila = new HBox(6, haActiva);
                haFila.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(haActiva, Priority.ALWAYS);
                if (haR.getModo() != HAReglaDeteccion.HAModo.INCORPORADA) {
                    Label haValor = new Label("«" + haR.getValor() + "»");
                    haValor.getStyleClass().add("texto-tenue");
                    Button haBorrar = new Button(null, new FontIcon(Feather.TRASH_2));
                    haBorrar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
                    haBorrar.setOnAction(haE -> {
                        haReglas.remove(haR);
                        haBorradas.add(haR);
                        haRedibujar[0].run();
                    });
                    haFila.getChildren().addAll(haValor, haBorrar);
                }
                haLista.getChildren().add(haFila);
            }
        };
        haRedibujar[0].run();

        TextField haNombre = new TextField();
        haNombre.setPromptText("Ej.: Nombre del cliente");
        ComboBox<HAModoUsuario> haModo = new ComboBox<>(FXCollections.observableArrayList(HAModoUsuario.values()));
        haModo.getSelectionModel().select(0);
        TextField haValor = new TextField();
        haValor.setPromptText("Ej.: Juan Pérez Quispe");
        Button haAgregar = new Button("Agregar regla", new FontIcon(Feather.PLUS));
        haAgregar.setOnAction(haE -> {
            try {
                HAReglaDeteccion haNueva = HAReglaDeteccion.haDelUsuario(haNombre.getText().trim(), haModo.getValue().haModo, haValor.getText().trim());
                HAReglasService.haValidar(haNueva);
                haReglas.add(haNueva);
                haNombre.clear();
                haValor.clear();
                haRedibujar[0].run();
            } catch (HAPdfListoException haEx) {
                haNotificador.haError(haEx.getMessage());
            }
        });

        VBox haCuerpo = new VBox(14,
                HAFormulariosHerramientas.haCampo("Qué buscar", haLista),
                new Label("Agregar una regla propia"),
                HAFormulariosHerramientas.haCampo("Nombre", haNombre),
                HAFormulariosHerramientas.haCampo("Tipo", haModo),
                HAFormulariosHerramientas.haCampo("Palabra o patrón", haValor),
                haAgregar,
                HAFormulariosHerramientas.haNota("Las palabras se buscan sin importar mayúsculas ni tildes. "
                        + "El patrón avanzado es para usuarios que conocen expresiones regulares."));
        haPaneles.haAbrir(Feather.SLIDERS, "Reglas de detección", "Qué datos se buscan al pulsar «Buscar datos sensibles».",
                haCuerpo, "Guardar", () -> {
                    haBorradas.forEach(haReglasService::haEliminar);
                    haReglasService.haGuardar(haReglas);
                    haNotificador.haExito("Reglas guardadas.");
                    haAlGuardar.run();
                });
    }

    public void haLimpiarMetadatos(HAEditor haEditor, HAAnalizadorPrivacidad.HAInventario haInv, Consumer<String> haAlTerminar) {
        CheckBox haInfo = haOpcion("Autor, título, programa y fechas", !haInv.haInformacion().isEmpty(),
                haInv.haInformacion().isEmpty() ? "No tiene." : String.join("; ",
                        haInv.haInformacion().entrySet().stream().map(haE -> haE.getKey() + ": " + haE.getValue()).toList()));
        CheckBox haXmp = haOpcion("Metadatos XMP y datos privados de programas", haInv.haXmp(), haInv.haXmp() ? "Sí tiene." : "No tiene.");
        CheckBox haComentarios = haOpcion("Comentarios y anotaciones", haInv.haComentarios() > 0,
                haInv.haComentarios() + " (los enlaces y los campos de formulario se conservan).");
        CheckBox haAdjuntos = haOpcion("Archivos adjuntos", !haInv.haAdjuntos().isEmpty(),
                haInv.haAdjuntos().isEmpty() ? "No tiene." : String.join(", ", haInv.haAdjuntos()));
        CheckBox haJs = haOpcion("JavaScript y acciones automáticas", haInv.haJavascript(), haInv.haJavascript() ? "Sí tiene." : "No tiene.");

        VBox haCuerpo = new VBox(12,
                HAFormulariosHerramientas.haNota("Esto es lo que se encontró. Marca lo que quieres quitar:"),
                haEnvolver(haInfo), haEnvolver(haXmp), haEnvolver(haComentarios), haEnvolver(haAdjuntos), haEnvolver(haJs),
                HAFormulariosHerramientas.haNota(haInv.haRevisionesAnteriores() > 0
                        ? "Además, el archivo guarda " + haInv.haRevisionesAnteriores()
                          + " versión(es) anterior(es): se eliminan siempre al limpiar, porque el resultado se escribe de nuevo."
                        : "No guarda versiones anteriores."),
                HAFormulariosHerramientas.haNota(haInv.haCapasOcultas() > 0
                        ? "Atención: tiene " + haInv.haCapasOcultas() + " capa(s) oculta(s). Su contenido sigue en el archivo; "
                          + "para eliminarlo de verdad, tacha esas páginas."
                        : ""));
        haPaneles.haAbrir(Feather.FILE_MINUS, "Limpiar metadatos", "Información que viaja con el archivo aunque no se vea.",
                haCuerpo, "Limpiar", () -> {
                    List<String> haSeBorra = haQueSeBorra(haInv, haInfo.isSelected(), haXmp.isSelected(), haComentarios.isSelected(),
                            haAdjuntos.isSelected(), haJs.isSelected());
                    haNotificador.haConfirmar("Borrar metadatos", "Se va a borrar del documento:\n• "
                                    + String.join("\n• ", haSeBorra)
                                    + "\n\nSe crea una versión nueva (puedes deshacerla); el archivo original no cambia.",
                            "Borrar", () -> {
                                haNotificador.haCerrarPanel();
                                haEjecutarLimpieza(haEditor, haInv, haInfo.isSelected(), haXmp.isSelected(), haComentarios.isSelected(),
                                        haAdjuntos.isSelected(), haJs.isSelected(), haAlTerminar);
                            });
                    throw new HAFormulariosHerramientas.HACancelado();
                });
    }

    static List<String> haQueSeBorra(HAAnalizadorPrivacidad.HAInventario haInv, boolean haInfo, boolean haXmp, boolean haComentarios,
                                   boolean haAdjuntos, boolean haJs) {
        List<String> haLista = new ArrayList<>();
        if (haInfo) {
            haLista.add(haInv.haInformacion().isEmpty() ? "Autor, título, programa y fechas (no tiene ninguno)"
                    : "Autor, título, programa y fechas: " + String.join(", ", haInv.haInformacion().keySet()));
        }
        if (haXmp) {
            haLista.add("Metadatos XMP y datos privados de programas");
        }
        if (haComentarios) {
            haLista.add(haInv.haComentarios() + (haInv.haComentarios() == 1 ? " comentario o anotación" : " comentarios y anotaciones")
                    + " (los enlaces y campos de formulario se conservan)");
        }
        if (haAdjuntos) {
            haLista.add(haInv.haAdjuntos().isEmpty() ? "Archivos adjuntos (no tiene)"
                    : haInv.haAdjuntos().size() + " archivo(s) adjunto(s): " + String.join(", ", haInv.haAdjuntos()));
        }
        if (haJs) {
            haLista.add("JavaScript y acciones automáticas");
        }
        if (haInv.haRevisionesAnteriores() > 0) {
            haLista.add(haInv.haRevisionesAnteriores() + " versión(es) anterior(es) guardada(s) dentro del archivo");
        }
        if (haLista.isEmpty()) {
            haLista.add("Nada marcado: solo se reescribe el archivo limpio");
        }
        return haLista;
    }

    private void haEjecutarLimpieza(HAEditor haEditor, HAAnalizadorPrivacidad.HAInventario haInv, boolean haInfo, boolean haXmp,
                                  boolean haComentarios, boolean haAdjuntos, boolean haJs, Consumer<String> haAlTerminar) {
        HALimpiarMetadatos haOp = new HALimpiarMetadatos(new HALimpiarMetadatos.HAOpciones(haInfo, haXmp, haComentarios, haAdjuntos, haJs));
        List<String> haQuitado = new ArrayList<>();
        if (haInfo) haQuitado.add("autor/programa/fechas");
        if (haXmp) haQuitado.add("XMP");
        if (haComentarios) haQuitado.add(haInv.haComentarios() + " comentario(s)");
        if (haAdjuntos) haQuitado.add(haInv.haAdjuntos().size() + " adjunto(s)");
        if (haJs) haQuitado.add("JavaScript");
        String haResumen = "Se quitó: " + (haQuitado.isEmpty() ? "solo las versiones anteriores" : String.join(", ", haQuitado)) + ".";
        String haCodigo = haOp.haOpciones().equals(HALimpiarMetadatos.HAOpciones.haTodo())
                ? pe.pdflisto.interprete.HASerializadorPasos.haCodificar(new pe.pdflisto.interprete.HAPaso.HALimpiarMetadatos()) : null;
        haEditor.haAplicarRepetible(haOp.haDescripcion(), haCodigo, (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                haR -> haAlTerminar.accept(haResumen));
    }

    public void haProteger(HAEditor haEditor) {
        PasswordField haAbrir = new PasswordField();
        haAbrir.setPromptText("Contraseña para abrir");
        PasswordField haRepetir = new PasswordField();
        haRepetir.setPromptText("Repite la contraseña");
        CheckBox haImprimir = new CheckBox("Permitir imprimir");
        haImprimir.setSelected(true);
        CheckBox haCopiar = new CheckBox("Permitir copiar texto");
        haCopiar.setSelected(true);
        CheckBox haModificar = new CheckBox("Permitir modificar");
        haModificar.setSelected(true);
        PasswordField haPropietario = new PasswordField();
        haPropietario.setPromptText("Opcional");

        VBox haCuerpo = new VBox(14,
                HAFormulariosHerramientas.haCampo("Contraseña para abrir", haAbrir),
                HAFormulariosHerramientas.haCampo("Repetir", haRepetir),
                HAFormulariosHerramientas.haCampo("Permisos", new VBox(8, haImprimir, haCopiar, haModificar)),
                HAFormulariosHerramientas.haCampo("Contraseña de propietario (para cambiar permisos)", haPropietario),
                HAFormulariosHerramientas.haNota("Se usa cifrado AES de 256 bits. Si quitas permisos y no pones contraseña de "
                        + "propietario, se genera una al azar: los permisos quedarán fijos. Algunos programas ignoran los "
                        + "permisos; lo que sí protege de verdad es la contraseña para abrir."),
                HAFormulariosHerramientas.haNota("Guarda la contraseña en un lugar seguro: si la olvidas, no hay forma de recuperar el PDF."));
        haPaneles.haAbrir(Feather.LOCK, "Poner contraseña", "El resultado será una versión nueva; el original no cambia.",
                haCuerpo, "Proteger", () -> {
                    if (!haAbrir.getText().equals(haRepetir.getText())) {
                        throw new HAPdfListoException("Las contraseñas no coinciden.");
                    }
                    if (!haAbrir.getText().isEmpty() && haAbrir.getText().length() < 4) {
                        throw new HAPdfListoException("Usa una contraseña de al menos 4 caracteres.");
                    }
                    HAProteger haOp = new HAProteger(haAbrir.getText(), haPropietario.getText(),
                            haImprimir.isSelected(), haCopiar.isSelected(), haModificar.isSelected());
                    if (haAbrir.getText().isEmpty() && haImprimir.isSelected() && haCopiar.isSelected() && haModificar.isSelected()) {
                        throw new HAPdfListoException("Escribe una contraseña para abrir o quita algún permiso.");
                    }
                    haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                            haR -> haNotificador.haExito("Listo: la nueva versión está protegida. Guárdala con «Guardar copia»."));
                });
    }

    private static CheckBox haOpcion(String haTexto, boolean haMarcado, String haDetalle) {
        CheckBox haC = new CheckBox(haTexto);
        haC.setSelected(haMarcado);
        haC.setUserData(haDetalle);
        haC.setWrapText(true);
        return haC;
    }

    private static VBox haEnvolver(CheckBox haC) {
        Label haDetalle = new Label(String.valueOf(haC.getUserData()));
        haDetalle.getStyleClass().add("texto-tenue");
        haDetalle.setWrapText(true);
        haDetalle.setPadding(new javafx.geometry.Insets(0, 0, 0, 26));
        return new VBox(2, haC, haDetalle);
    }
}
