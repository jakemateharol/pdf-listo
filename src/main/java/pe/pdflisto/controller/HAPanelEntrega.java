package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.entrega.HAPatronNombre;
import pe.pdflisto.entrega.HAPerfilesService;
import pe.pdflisto.entrega.HARecetasService;
import pe.pdflisto.entrega.HAVerificadorPerfil;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.interprete.HAInterpretePedidos;
import pe.pdflisto.interprete.HAInterpretacion;
import pe.pdflisto.interprete.HAPaso;
import pe.pdflisto.interprete.HASerializadorPasos;
import pe.pdflisto.model.HAPerfilEntrega;
import pe.pdflisto.model.HAReceta;
import pe.pdflisto.operaciones.HAPosicion;
import pe.pdflisto.operaciones.HATamanoPapel;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.ui.HAVistaComprobaciones;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAPanelEntrega {
    private final HAPerfilesService haPerfiles;
    private final HARecetasService haRecetas;
    private final HAVerificadorPerfil haVerificador;
    private final HAPanelPedido haPanelPedido;
    private final HAFormulariosHerramientas haPaneles;
    private final HAInterpretePedidos haInterprete;
    private final HANotificador haNotificador;
    private final HAConfiguracion haConfiguracion;

    private HAEditor haEditor;
    private final ComboBox<HAPerfilEntrega> haCombo = new ComboBox<>();
    private final Label haDescripcion = new Label();
    private final VBox haZonaInforme = new VBox(8);
    private final VBox haListaRecetas = new VBox(6);
    private final TextField haPatron = new TextField();
    private final Label haEjemploNombre = new Label();
    private HAInformeVerificacion haUltimoInforme;
    private HAPerfilEntrega haPerfilValidado;

    public void haMontar(VBox haContenedor, HAEditor haEditor) {
        this.haEditor = haEditor;

        Label haTituloPerfil = haTitulo("Perfil de entrega");
        haCombo.setMaxWidth(Double.MAX_VALUE);
        haCombo.valueProperty().addListener((haO, haA, haB) -> {
            haDescripcion.setText(haB == null ? "" : haB.getDescripcion());
            haZonaInforme.getChildren().clear();
            haActualizarEjemplo();
        });
        haDescripcion.setWrapText(true);
        haDescripcion.getStyleClass().add("texto-tenue");
        Button haValidar = new Button("Validar", new FontIcon(Feather.CHECK_SQUARE));
        haValidar.getStyleClass().add(Styles.ACCENT);
        haValidar.setOnAction(haE -> haValidar());
        Button haEditar = new Button(null, new FontIcon(Feather.EDIT_2));
        haEditar.setTooltip(new Tooltip("Editar este perfil"));
        haEditar.setOnAction(haE -> haEditarPerfil(haCombo.getValue()));
        Button haNuevo = new Button(null, new FontIcon(Feather.PLUS));
        haNuevo.setTooltip(new Tooltip("Nuevo perfil"));
        haNuevo.setOnAction(haE -> haEditarPerfil(null));
        HBox haAcciones = new HBox(6, haValidar, haEditar, haNuevo);

        Label haTituloRecetas = haTitulo("Recetas");
        Button haNuevaReceta = new Button("Nueva receta…", new FontIcon(Feather.PLUS));
        haNuevaReceta.setOnAction(haE -> haNuevaReceta());
        Label haAyudaRecetas = HAFormulariosHerramientas.haNota("Secuencias de pasos que se aplican con un clic. También puedes "
                + "guardar cualquier plan desde la pestaña Pedido. Si repites la misma combinación "
                + HARecetasService.HA_REPETICIONES + " veces, te ofreceré guardarla.");

        Label haTituloFacturas = haTitulo("Facturas electrónicas");
        Button haFacturas = new Button("Leer facturas con QR…", new FontIcon(Feather.FILE_TEXT));
        haFacturas.setMaxWidth(Double.MAX_VALUE);
        haFacturas.setOnAction(haE -> haPaneles.haFacturas(haEditor));
        Label haAyudaFacturas = HAFormulariosHerramientas.haNota("Lee el QR de muchas facturas o boletas a la vez, valida el IGV "
                + "y los duplicados, y las pasa a Excel. No hace falta tener un PDF abierto.");

        Label haTituloNombre = haTitulo("Nombre al guardar");
        haPatron.setText(haConfiguracion.haPatronNombre());
        haPatron.textProperty().addListener((haO, haA, haB) -> {
            haConfiguracion.setPatronNombre(haB);
            haActualizarEjemplo();
        });
        haEjemploNombre.getStyleClass().add("texto-tenue");
        haEjemploNombre.setWrapText(true);
        Label haVariables = HAFormulariosHerramientas.haNota("Variables: {nombre}, {fecha}, {hora}, {perfil}, {paginas}. "
                + "Si eliges un perfil con su propio patrón, se usa el del perfil.");

        haContenedor.getChildren().setAll(haTituloPerfil, haCombo, haDescripcion, haAcciones, haZonaInforme, new Separator(),
                haTituloRecetas, haListaRecetas, haNuevaReceta, haAyudaRecetas, new Separator(),
                haTituloFacturas, haFacturas, haAyudaFacturas, new Separator(),
                haTituloNombre, haPatron, haEjemploNombre, haVariables);
        haRecargarPerfiles(null);
        haRecetas.haAlCambiar(this::haRecargarRecetas);
        haRecargarRecetas();
    }

    private void haRecargarPerfiles(HAPerfilEntrega haElegir) {
        List<HAPerfilEntrega> haLista = haPerfiles.haTodos();
        haCombo.setItems(FXCollections.observableArrayList(haLista));
        if (haElegir != null) {
            haLista.stream().filter(haP -> haP.getId() != null && haP.getId().equals(haElegir.getId())).findFirst().ifPresent(haCombo::setValue);
        } else if (!haLista.isEmpty() && haCombo.getValue() == null) {
            haCombo.setValue(haLista.get(0));
        }
    }

    private void haValidar() {
        HASesionEdicion haS = haEditor.haSesion();
        HAPerfilEntrega haP = haCombo.getValue();
        if (haS == null || haP == null) {
            haNotificador.haAdvertencia("Abre un PDF y elige un perfil.");
            return;
        }
        File haArchivo = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        haEditor.haEnSegundoPlano("Validando contra «" + haP.getNombre() + "»",
                haC -> haVerificador.haValidar(haArchivo, haClave, haP, haS.haNombre()), haInforme -> haMostrarInforme(haInforme, haP));
    }

    private void haMostrarInforme(HAInformeVerificacion haInforme, HAPerfilEntrega haP) {
        haUltimoInforme = haInforme;
        haPerfilValidado = haP;
        boolean haOk = haInforme.haCumple();
        haZonaInforme.getChildren().setAll(
                HAVistaComprobaciones.haVeredicto(haOk ? "Listo para entregar" : "No cumple el perfil", haOk, false),
                HAVistaComprobaciones.haFilas(haInforme.haComprobaciones().stream()
                        .filter(haC -> haC.haEstado() != pe.pdflisto.verificacion.HAComprobacion.HAEstado.NO_APLICA).toList()));
        if (!haOk) {
            Button haCorregir = new Button("Corregir…", new FontIcon(Feather.TOOL));
            haCorregir.getStyleClass().add(Styles.ACCENT);
            haCorregir.setMaxWidth(Double.MAX_VALUE);
            haCorregir.setOnAction(haE -> haCorregir());
            haZonaInforme.getChildren().add(haCorregir);
        }
    }

    private void haCorregir() {
        if (haUltimoInforme == null || haPerfilValidado == null) {
            return;
        }
        HAVerificadorPerfil.HACorreccion haC = haVerificador.haCorrecciones(haUltimoInforme, haPerfilValidado);
        if (haC.haPasos().isEmpty()) {
            haNotificador.haAdvertencia(haC.haAvisos().isEmpty() ? "No hay arreglos automáticos para esto."
                    : String.join(" ", haC.haAvisos()));
            return;
        }
        haPanelPedido.haProponerPlan("Corregir para «" + haPerfilValidado.getNombre() + "»", haC.haPasos(), haC.haAvisos(), haPerfilValidado);
    }

    public void haDocumentoCambio() {
        haZonaInforme.getChildren().clear();
        haUltimoInforme = null;
        haActualizarEjemplo();
    }

    private void haEditarPerfil(HAPerfilEntrega haExistente) {
        HAPerfilEntrega haP = haExistente != null ? haExistente : new HAPerfilEntrega();
        TextField haNombre = new TextField(haP.getNombre());
        TextField haDesc = new TextField(haP.getDescripcion());
        TextField haPeso = new TextField(haP.getPesoMaximoMb() == null ? "" : String.valueOf(haP.getPesoMaximoMb()));
        haPeso.setPromptText("Vacío = sin límite");
        ComboBox<String> haTamano = new ComboBox<>(FXCollections.observableArrayList("Sin requisito", "A4", "Carta"));
        haTamano.setValue(haP.getTamano() == null ? "Sin requisito" : haP.getTamano().toString());
        CheckBox haNumerar = new CheckBox("Páginas numeradas");
        haNumerar.setSelected(haP.isNumerar());
        TextField haFormato = new TextField(haP.getFormatoNumero());
        ComboBox<HAPosicion> haPosicion = new ComboBox<>(FXCollections.observableArrayList(HAPosicion.values()));
        haPosicion.setValue(haP.getPosicionNumero());
        Spinner<Integer> haDesde = new Spinner<>(1, 999, Math.max(1, haP.getDesdeNumero()));
        haDesde.setEditable(true);
        VBox haOpcionesNumero = new VBox(8, HAFormulariosHerramientas.haCampo("Formato (con {n})", haFormato),
                HAFormulariosHerramientas.haCampo("Posición", haPosicion), HAFormulariosHerramientas.haCampo("Desde la página", haDesde));
        haOpcionesNumero.disableProperty().bind(haNumerar.selectedProperty().not());
        CheckBox haBuscable = new CheckBox("Texto buscable (si es escaneado, aplicar OCR)");
        haBuscable.setSelected(haP.isTextoBuscable());
        CheckBox haSinMeta = new CheckBox("Sin metadatos (autor, programa, fechas)");
        haSinMeta.setSelected(haP.isSinMetadatos());
        CheckBox haSinClave = new CheckBox("Sin contraseña");
        haSinClave.setSelected(haP.isSinContrasena());
        TextField haMaxPaginas = new TextField(haP.getMaxPaginas() == null ? "" : String.valueOf(haP.getMaxPaginas()));
        haMaxPaginas.setPromptText("Vacío = sin límite");
        TextField haPatronPerfil = new TextField(haP.getPatronNombre());
        haPatronPerfil.setPromptText("Ej.: Tesis - {nombre}");

        Button haEliminar = new Button("Eliminar este perfil", new FontIcon(Feather.TRASH_2));
        haEliminar.getStyleClass().addAll(Styles.DANGER, Styles.FLAT);
        haEliminar.setVisible(haExistente != null && haExistente.getId() != null);
        haEliminar.setManaged(haEliminar.isVisible());
        haEliminar.setOnAction(haE -> haNotificador.haConfirmar("Eliminar perfil", "Se eliminará «" + haP.getNombre() + "».",
                "Eliminar", () -> {
                    haPerfiles.haEliminar(haP);
                    haCombo.setValue(null);
                    haRecargarPerfiles(null);
                    haNotificador.haCerrarPanel();
                }));

        VBox haCuerpo = new VBox(12,
                HAFormulariosHerramientas.haCampo("Nombre", haNombre), HAFormulariosHerramientas.haCampo("Descripción", haDesc),
                HAFormulariosHerramientas.haCampo("Peso máximo (MB)", haPeso), HAFormulariosHerramientas.haCampo("Tamaño de página", haTamano),
                haNumerar, haOpcionesNumero, haBuscable, haSinMeta, haSinClave,
                HAFormulariosHerramientas.haCampo("Máximo de páginas", haMaxPaginas),
                HAFormulariosHerramientas.haCampo("Nombre al guardar (patrón)", haPatronPerfil), haEliminar);
        haPaneles.haAbrir(Feather.PACKAGE, haExistente == null ? "Nuevo perfil" : "Editar perfil",
                "Requisitos que debe cumplir el PDF para este destino.", haCuerpo, "Guardar", () -> {
                    haP.setNombre(haNombre.getText().trim());
                    haP.setDescripcion(haDesc.getText().trim());
                    haP.setPesoMaximoMb(haNumero(haPeso.getText(), "el peso máximo"));
                    haP.setTamano(switch (haTamano.getValue()) {
                        case "A4" -> HATamanoPapel.A4;
                        case "Carta" -> HATamanoPapel.CARTA;
                        default -> null;
                    });
                    haP.setNumerar(haNumerar.isSelected());
                    haP.setFormatoNumero(haFormato.getText());
                    haP.setPosicionNumero(haPosicion.getValue());
                    haP.setDesdeNumero(haDesde.getValue());
                    haP.setTextoBuscable(haBuscable.isSelected());
                    haP.setSinMetadatos(haSinMeta.isSelected());
                    haP.setSinContrasena(haSinClave.isSelected());
                    Double haMax = haNumero(haMaxPaginas.getText(), "el máximo de páginas");
                    haP.setMaxPaginas(haMax == null ? null : haMax.intValue());
                    haP.setPatronNombre(haPatronPerfil.getText().isBlank() ? null : haPatronPerfil.getText().trim());
                    HAPerfilEntrega haGuardado = haPerfiles.haGuardar(haP);
                    haRecargarPerfiles(haGuardado);
                    haNotificador.haExito("Perfil «" + haGuardado.getNombre() + "» guardado.");
                });
    }

    private static Double haNumero(String haTexto, String haQue) {
        if (haTexto == null || haTexto.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(haTexto.trim().replace(',', '.'));
        } catch (NumberFormatException haE) {
            throw new HAPdfListoException("Escribe un número válido para " + haQue + ".");
        }
    }

    private void haRecargarRecetas() {
        haListaRecetas.getChildren().clear();
        List<HAReceta> haLista = haRecetas.haTodas();
        if (haLista.isEmpty()) {
            haListaRecetas.getChildren().add(HAFormulariosHerramientas.haNota("Aún no tienes recetas."));
            return;
        }
        for (HAReceta haR : haLista) {
            Label haNombre = new Label(haR.getNombre());
            haNombre.getStyleClass().add("comprobacion-nombre");
            Label haResumen = new Label(haR.getResumen() + (haR.getUsos() > 0 ? "  ·  usada " + haR.getUsos() + (haR.getUsos() == 1 ? " vez" : " veces") : ""));
            haResumen.getStyleClass().add("texto-tenue");
            haResumen.setWrapText(true);
            VBox haTextos = new VBox(1, haNombre, haResumen);
            HBox.setHgrow(haTextos, Priority.ALWAYS);
            Button haEjecutar = new Button(null, new FontIcon(Feather.PLAY));
            haEjecutar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.ACCENT, Styles.SMALL);
            haEjecutar.setTooltip(new Tooltip("Aplicar al documento abierto"));
            haEjecutar.setOnAction(haE -> haEjecutar(haR));
            Button haBorrar = new Button(null, new FontIcon(Feather.TRASH_2));
            haBorrar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            haBorrar.setOnAction(haE -> haNotificador.haConfirmar("Borrar receta", "Se borrará «" + haR.getNombre() + "».", "Borrar",
                    () -> haRecetas.haEliminar(haR)));
            HBox haFila = new HBox(6, haTextos, haEjecutar, haBorrar);
            haFila.setAlignment(Pos.CENTER_LEFT);
            haFila.getStyleClass().add("fila-dato");
            haListaRecetas.getChildren().add(haFila);
        }
    }

    private void haEjecutar(HAReceta haR) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Abre el PDF al que quieres aplicar la receta.");
            return;
        }
        List<HAPaso> haPasos;
        try {
            List<String> haOtros = haEditor.haSesiones().stream().filter(haO -> haO != haS).map(HASesionEdicion::haNombre).toList();
            haPasos = HASerializadorPasos.haDecodificar(haR.getCodigo(), haOtros);
        } catch (HAPdfListoException haE) {
            haNotificador.haError(haE.getMessage());
            return;
        }
        Runnable haAplicar = () -> {
            haRecetas.haRegistrarUso(haR);
            haPanelPedido.haEjecutarReceta(haR.getNombre(), haPasos);
        };
        if (haPasos.stream().anyMatch(haP -> haP instanceof HAPaso.HALimpiarMetadatos)) {
            haNotificador.haPreguntar("Aplicar receta «" + haR.getNombre() + "»",
                    "Esta receta borra los metadatos (autor, programa, fechas, comentarios). Pasos: " + haR.getResumen() + ".",
                    "Aplicar", haAplicar);
        } else {
            haAplicar.run();
        }
    }

    private void haNuevaReceta() {
        TextField haNombre = new TextField();
        haNombre.setPromptText("Ej.: Para mesa de partes");
        TextField haFrase = new TextField();
        haFrase.setPromptText("Ej.: numera, limpia los metadatos y comprime a menos de 5 MB");
        Label haVista = new Label("Escribe lo que debe hacer la receta.");
        haVista.getStyleClass().add("vista-previa");
        haVista.setWrapText(true);
        haVista.setMaxWidth(Double.MAX_VALUE);
        HAInterpretacion[] haUltima = new HAInterpretacion[1];
        haFrase.textProperty().addListener((haO, haA, haB) -> {
            haUltima[0] = haInterprete.haInterpretar(haB, new HAInterpretePedidos.HAContexto(999, List.of()));
            if (haUltima[0].haPasos().isEmpty()) {
                haVista.setText(haUltima[0].haAvisos().isEmpty() ? "Todavía no entiendo qué hacer." : String.join(" ", haUltima[0].haAvisos()));
            } else {
                StringBuilder haSb = new StringBuilder("Pasos:");
                haUltima[0].haPasos().forEach(haP -> haSb.append("\n• ").append(haP.haDescripcion()));
                if (!haUltima[0].haPreguntas().isEmpty()) {
                    haSb.append("\n\nFalta un dato: ").append(haUltima[0].haPreguntas().get(0).haTexto());
                }
                haVista.setText(haSb.toString());
            }
        });
        VBox haCuerpo = new VBox(12, HAFormulariosHerramientas.haCampo("Nombre", haNombre),
                HAFormulariosHerramientas.haCampo("¿Qué debe hacer?", haFrase), haVista,
                HAFormulariosHerramientas.haNota("Las páginas que nombres (p. ej. «elimina la última») se aplican a cada documento."));
        haPaneles.haAbrir(Feather.BOOKMARK, "Nueva receta", "Se guarda en tu computadora.", haCuerpo, "Guardar", () -> {
            HAInterpretacion haI = haUltima[0];
            if (haI == null || haI.haPasos().isEmpty()) {
                throw new HAPdfListoException("Escribe qué debe hacer la receta (p. ej. «numera y comprime»).");
            }
            if (!haI.haPreguntas().isEmpty()) {
                throw new HAPdfListoException("Falta un dato: " + haI.haPreguntas().get(0).haTexto());
            }
            if (haI.haPasos().stream().anyMatch(haP -> haP instanceof HAPaso.HAUnir)) {
                throw new HAPdfListoException("Una receta no puede unir PDF (depende de qué documentos estén abiertos).");
            }
            String haN = haNombre.getText().isBlank() ? HASerializadorPasos.haResumen(haI.haPasos()) : haNombre.getText();
            haRecetas.haGuardar(haN, HASerializadorPasos.haCodificar(haI.haPasos()), HASerializadorPasos.haResumen(haI.haPasos()));
            haNotificador.haExito("Receta «" + haN.trim() + "» guardada.");
        });
    }

    public String haNombreSugerido(HASesionEdicion haS) {
        HAPerfilEntrega haP = haCombo.getValue();
        String haPatronUsado = haP != null && haP.getPatronNombre() != null && !haP.getPatronNombre().isBlank()
                && haPerfilValidado == haP ? haP.getPatronNombre() : haConfiguracion.haPatronNombre();
        return HAPatronNombre.haAplicar(haPatronUsado, haS.haNombre(), haP == null ? null : haP.getNombre(),
                haS.haVista() == null ? 0 : haS.haVista().haPaginas(), LocalDateTime.now());
    }

    private void haActualizarEjemplo() {
        HASesionEdicion haS = haEditor == null ? null : haEditor.haSesion();
        String haNombre = haS == null ? "contrato.pdf" : haS.haNombre();
        haEjemploNombre.setText("Ejemplo: " + HAPatronNombre.haAplicar(haConfiguracion.haPatronNombre(), haNombre,
                haCombo.getValue() == null ? null : haCombo.getValue().getNombre(), 12, LocalDateTime.now()));
    }

    public void haDespuesDeGuardar(String haCombinacion) {
        haRecetas.haSugerencia(haCombinacion).ifPresent(haCodigo -> {
            List<HAPaso> haPasos;
            try {
                haPasos = HASerializadorPasos.haDecodificar(haCodigo, List.of());
            } catch (HAPdfListoException haE) {
                return;
            }
            String haResumen = HASerializadorPasos.haResumen(haPasos);
            haNotificador.haElegir("¿Guardar como receta?",
                    "Ya preparaste " + HARecetasService.HA_REPETICIONES + " documentos con los mismos pasos: " + haResumen
                            + ". ¿Quieres guardarlos como receta para aplicarlos con un clic la próxima vez?",
                    "No, gracias", () -> {
                        haRecetas.haRechazar(haCodigo);
                        haNotificador.haInfo("Entendido: no volveré a ofrecer esta combinación.");
                    },
                    "Guardar receta", () -> {
                        TextInputDialog haD = new TextInputDialog(haResumen);
                        haD.initOwner(haEditor.haVentana());
                        haD.setTitle("Nueva receta");
                        haD.setHeaderText("Nombre de la receta:");
                        pe.pdflisto.ui.HADialogos.haMostrar(haD).filter(haN -> !haN.isBlank()).ifPresent(haN -> {
                            haRecetas.haGuardar(haN, haCodigo, haResumen);
                            haNotificador.haExito("Receta «" + haN.trim() + "» guardada en la pestaña Entrega.");
                        });
                    });
        });
    }

    private static Label haTitulo(String haTexto) {
        Label haL = new Label(haTexto);
        haL.getStyleClass().add("panel-titulo");
        return haL;
    }
}
