package pe.pdflisto.controller;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HAMotorPdf;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.privacidad.HAAnalizadorPrivacidad;
import pe.pdflisto.privacidad.HAAreaTachado;
import pe.pdflisto.privacidad.HADatoSensible;
import pe.pdflisto.privacidad.HADetectorDatos;
import pe.pdflisto.privacidad.HAQuitarContrasena;
import pe.pdflisto.privacidad.HATachar;
import pe.pdflisto.privacidad.HAVerificadorTachado;
import pe.pdflisto.ui.HAExportador;
import pe.pdflisto.ui.HANotificador;
import pe.pdflisto.ui.HAVistaComprobaciones;
import pe.pdflisto.verificacion.HAInformePdf;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAPanelTachado {
    private static final double HA_MARGEN = 1.5;

    private final HADetectorDatos haDetector;
    private final HAVerificadorTachado haVerificadorTachado;
    private final HAAnalizadorPrivacidad haAnalizador;
    private final HAMotorPdf haMotor;
    private final HAFormulariosPrivacidad haFormularios;
    private final HAInformePdf haInformePdf;
    private final HAExportador haExportador;
    private final HANotificador haNotificador;

    private HAEditor haEditor;

    private static final class HAFila {
        final HADatoSensible haDato;
        final HAAreaTachado haManual;
        final CheckBox haCasilla = new CheckBox();

        HAFila(HADatoSensible haDato, HAAreaTachado haManual) {
            this.haDato = haDato;
            this.haManual = haManual;
        }

        int haPagina() {
            return haDato != null ? haDato.haPagina() : haManual.haPagina();
        }

        List<HAAreaTachado> haAreas() {
            if (haManual != null) {
                return List.of(haManual);
            }
            return haDato.haAreas().stream().map(haA -> new HAAreaTachado(haDato.haPagina(), haA)).toList();
        }
    }

    private final List<HAFila> haFilas = new ArrayList<>();
    private final VBox haZonaLista = new VBox(8);
    private final Label haResumen = new Label();
    private final VBox haZonaResultado = new VBox(8);
    private final VBox haZonaPrivacidad = new VBox(8);
    private final ToggleButton haDibujar = new ToggleButton("Tachar un área a mano", new FontIcon(Feather.EDIT_3));
    private final CheckBox haLimpiarMetadatos = new CheckBox("Limpiar también los metadatos");
    private final Button haTachar = new Button("Tachar y verificar", new FontIcon(Feather.SHIELD));
    private HAInformeVerificacion haUltimoInformePrivacidad;

    public void haMontar(VBox haContenedor, HAEditor haEditor) {
        this.haEditor = haEditor;

        Label haTitulo = new Label("Datos sensibles");
        haTitulo.getStyleClass().add("panel-titulo");
        Button haBuscar = new Button("Buscar datos sensibles", new FontIcon(Feather.SEARCH));
        haBuscar.getStyleClass().add(Styles.ACCENT);
        haBuscar.setOnAction(haE -> haBuscar());
        Button haReglas = new Button(null, new FontIcon(Feather.SLIDERS));
        haReglas.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        haReglas.setTooltip(new javafx.scene.control.Tooltip("Reglas de detección (qué se busca)"));
        haReglas.setOnAction(haE -> haFormularios.haReglas(() -> { }));
        HBox haFilaBuscar = new HBox(8, haBuscar, haReglas);
        haFilaBuscar.setAlignment(Pos.CENTER_LEFT);
        Label haExplicacion = HAFormulariosHerramientas.haNota("Busca DNI, RUC (validado), celulares, correos, cuentas, CCI y "
                + "direcciones, y sugiere posibles nombres de personas (sin marcar: decides tú). "
                + "Revisa la lista: lo marcado se verá en rojo en el documento.");

        haDibujar.setMaxWidth(Double.MAX_VALUE);
        haDibujar.selectedProperty().addListener((haO, haA, haActivo) -> {
            if (haEditor.haSesion() == null && haActivo) {
                haNotificador.haAdvertencia("Primero abre un PDF.");
                haDibujar.setSelected(false);
                return;
            }
            haEditor.haModoDibujo(haActivo ? this::haAgregarManual : null);
            if (haActivo) {
                haNotificador.haInfo("Arrastra el mouse sobre la página para marcar el área a tachar.");
            }
        });
        haLimpiarMetadatos.setSelected(true);
        haTachar.getStyleClass().add(Styles.DANGER);
        haTachar.setMaxWidth(Double.MAX_VALUE);
        haTachar.setOnAction(haE -> haConfirmarTachado());

        Label haTituloPrivacidad = new Label("Privacidad del archivo");
        haTituloPrivacidad.getStyleClass().add("panel-titulo");
        Button haInforme = haBoton("Informe de privacidad", Feather.EYE, this::haInformePrivacidad);
        Button haLimpiar = haBoton("Limpiar metadatos…", Feather.FILE_MINUS, this::haAbrirLimpieza);
        Label haTituloClave = new Label("Contraseña");
        haTituloClave.getStyleClass().add("panel-titulo");
        Button haPoner = haBoton("Poner contraseña…", Feather.LOCK, () -> {
            if (haEditor.haSesion() != null) {
                haFormularios.haProteger(haEditor);
            }
        });
        Button haQuitar = haBoton("Quitar contraseña", Feather.UNLOCK, this::haQuitarContrasena);

        haContenedor.getChildren().setAll(haTitulo, haFilaBuscar, haExplicacion, haZonaLista, haDibujar, haLimpiarMetadatos, haTachar,
                haZonaResultado, new Separator(), haTituloPrivacidad, haInforme, haLimpiar, haZonaPrivacidad,
                new Separator(), haTituloClave, haPoner, haQuitar);
        haActualizarBoton();
    }

    public void haDocumentoCambio() {
        haLimpiarLista();
        haZonaResultado.getChildren().clear();
        haZonaPrivacidad.getChildren().clear();
    }

    public void haVersionCambio() {
        haLimpiarLista();
    }

    private void haLimpiarLista() {
        haFilas.clear();
        haZonaLista.getChildren().clear();
        haDibujar.setSelected(false);
        if (haEditor != null) {
            haEditor.haMarcarParaTachar(Map.of());
        }
        haActualizarBoton();
    }

    private void haBuscar() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        File haArchivo = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        haEditor.haEnSegundoPlano("Buscando datos sensibles", haC -> haDetector.haDetectar(haArchivo, haClave, haC), haDatos -> {
                    if (haS != haEditor.haSesion() || haArchivo != haS.haArchivoActual()) {
                        return;
                    }
                    haFilas.removeIf(haF -> haF.haDato != null);
                    haDatos.forEach(haD -> haFilas.add(new HAFila(haD, null)));
                    haDibujarLista();
                    if (haDatos.isEmpty()) {
                        haNotificador.haInfo(haS.haVista().haPaginas() > 0
                                ? "No se encontraron datos sensibles en el texto. Si el PDF es escaneado, primero reconoce el texto "
                                        + "(Herramientas → Reconocer texto) o usa «Tachar un área a mano»."
                                : "No se encontraron datos sensibles.");
                    }
                });
    }

    private void haAgregarManual(int haPagina, HAAreaTexto haArea) {
        haFilas.add(new HAFila(null, new HAAreaTachado(haPagina, haArea)));
        haDibujarLista();
    }

    private void haDibujarLista() {
        haZonaLista.getChildren().clear();
        if (haFilas.isEmpty()) {
            haActualizarMarcas();
            return;
        }
        Map<String, List<HAFila>> haGrupos = new LinkedHashMap<>();
        for (HAFila haF : haFilas) {
            String haGrupo = haF.haDato != null ? haF.haDato.haTipo().toString() : "Áreas marcadas a mano";
            haGrupos.computeIfAbsent(haGrupo, haK -> new ArrayList<>()).add(haF);
        }
        Button haTodo = new Button("Marcar todo");
        haTodo.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        haTodo.setOnAction(haE -> haFilas.forEach(haF -> haF.haCasilla.setSelected(true)));
        Button haNada = new Button("Desmarcar todo");
        haNada.getStyleClass().addAll(Styles.SMALL, Styles.FLAT);
        haNada.setOnAction(haE -> haFilas.forEach(haF -> haF.haCasilla.setSelected(false)));
        haResumen.getStyleClass().add("resumen-tachado");
        haResumen.setWrapText(true);
        haZonaLista.getChildren().addAll(haResumen, new HBox(4, haTodo, haNada));

        for (Map.Entry<String, List<HAFila>> haG : haGrupos.entrySet()) {
            Label haCabecera = new Label(haG.getKey() + " (" + haG.getValue().size() + ")");
            haCabecera.getStyleClass().add("seccion-titulo");
            haZonaLista.getChildren().add(haCabecera);
            for (HAFila haF : haG.getValue()) {
                haZonaLista.getChildren().add(haFilaDato(haF));
            }
        }
        haActualizarMarcas();
    }

    private HBox haFilaDato(HAFila haF) {
        boolean haMarcadoPorDefecto = haF.haDato == null || haF.haDato.haProbable();
        if (haF.haCasilla.getUserData() == null) {
            haF.haCasilla.setSelected(haMarcadoPorDefecto);
            haF.haCasilla.setUserData(Boolean.TRUE);
            haF.haCasilla.selectedProperty().addListener((haO, haA, haB) -> haActualizarMarcas());
        }
        String haTexto = haF.haDato != null ? haF.haDato.haTexto() : "Área manual";
        Label haPrincipal = new Label(haTexto);
        haPrincipal.getStyleClass().add("dato-texto");
        haPrincipal.setWrapText(true);
        Label haPagina = new Label("pág. " + (haF.haPagina() + 1));
        haPagina.getStyleClass().add("etiqueta-pagina");
        haPagina.setMinWidth(Region.USE_PREF_SIZE);
        VBox haTextos = new VBox(1, haPrincipal);
        if (haF.haDato != null && haF.haDato.haNota() != null) {
            Label haNota = new Label("Posible: " + haF.haDato.haNota());
            haNota.getStyleClass().add("texto-tenue");
            haNota.setWrapText(true);
            haTextos.getChildren().add(haNota);
        }
        HBox.setHgrow(haTextos, Priority.ALWAYS);
        Button haVer = new Button(null, new FontIcon(Feather.EYE));
        haVer.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
        haVer.setTooltip(new javafx.scene.control.Tooltip("Ver en el documento"));
        haVer.setOnAction(haE -> haEditor.haMostrarPagina(haF.haPagina()));
        HBox haFila = new HBox(6, haF.haCasilla, haTextos, haPagina, haVer);
        haFila.setAlignment(Pos.CENTER_LEFT);
        haFila.getStyleClass().add("fila-dato");
        if (haF.haManual != null) {
            Button haQuitar = new Button(null, new FontIcon(Feather.X));
            haQuitar.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT, Styles.SMALL);
            haQuitar.setOnAction(haE -> {
                haFilas.remove(haF);
                haDibujarLista();
            });
            haFila.getChildren().add(haQuitar);
        }
        return haFila;
    }

    private void haActualizarMarcas() {
        Map<Integer, List<HAAreaTexto>> haMarcas = new TreeMap<>();
        for (HAFila haF : haFilas) {
            if (haF.haCasilla.isSelected()) {
                haF.haAreas().forEach(haA -> haMarcas.computeIfAbsent(haA.haPagina(), haK -> new ArrayList<>()).add(haA.haArea()));
            }
        }
        haEditor.haMarcarParaTachar(haMarcas);
        haActualizarBoton();
        int haDatos = (int) haFilas.stream().filter(haF -> haF.haDato != null).count();
        int haPaginas = (int) haFilas.stream().filter(haF -> haF.haDato != null).mapToInt(HAFila::haPagina).distinct().count();
        int haManuales = haFilas.size() - haDatos;
        int haMarcados = (int) haFilas.stream().filter(haF -> haF.haCasilla.isSelected()).count();
        haResumen.setText(haTextoResumen(haDatos, haPaginas, haManuales, haMarcados));
    }

    static String haTextoResumen(int haDatos, int haPaginas, int haAreasManuales, int haMarcados) {
        StringBuilder haT = new StringBuilder();
        haT.append(haDatos).append(haDatos == 1 ? " dato encontrado" : " datos encontrados");
        if (haDatos > 0) {
            haT.append(" en ").append(haPaginas).append(haPaginas == 1 ? " página" : " páginas");
        }
        if (haAreasManuales > 0) {
            haT.append(" + ").append(haAreasManuales).append(haAreasManuales == 1 ? " área a mano" : " áreas a mano");
        }
        haT.append(" · ").append(haMarcados).append(haMarcados == 1 ? " marcado" : " marcados").append(" para tachar");
        return haT.toString();
    }

    private void haActualizarBoton() {
        long haN = haFilas.stream().filter(haF -> haF.haCasilla.isSelected()).count();
        haTachar.setDisable(haN == 0);
        haTachar.setText(haN == 0 ? "Tachar y verificar" : "Tachar y verificar (" + haN + ")");
    }

    private void haConfirmarTachado() {
        HASesionEdicion haS = haEditor.haSesion();
        List<HAFila> haElegidas = haFilas.stream().filter(haF -> haF.haCasilla.isSelected()).toList();
        if (haS == null || haElegidas.isEmpty()) {
            return;
        }
        List<HAAreaTachado> haAreas = new ArrayList<>();
        List<HADatoSensible> haDatos = new ArrayList<>();
        for (HAFila haF : haElegidas) {
            haF.haAreas().forEach(haA -> haAreas.add(haA.haAmpliada(HA_MARGEN)));
            if (haF.haDato != null) {
                haDatos.add(haF.haDato);
            }
        }
        long haPaginas = haAreas.stream().map(HAAreaTachado::haPagina).distinct().count();
        boolean haLimpiar = haLimpiarMetadatos.isSelected();
        haNotificador.haConfirmar("Tachar " + haElegidas.size() + (haElegidas.size() == 1 ? " dato" : " datos"),
                "Se borrarán de verdad (no solo se taparán) en " + haPaginas + (haPaginas == 1 ? " página" : " páginas")
                        + ". Esas páginas se convierten en imagen con el texto restante invisible, para que se pueda "
                        + "seguir buscando; pierden sus enlaces y pesan más. Se hace sobre una copia y luego se verifica "
                        + "releyendo el archivo." + (haLimpiar ? " También se limpiarán los metadatos." : ""),
                "Tachar", () -> haEjecutarTachado(haS, haAreas, haDatos, haLimpiar));
    }

    private void haEjecutarTachado(HASesionEdicion haS, List<HAAreaTachado> haAreas, List<HADatoSensible> haDatos, boolean haLimpiar) {
        HATachar haOp = new HATachar(haAreas, haLimpiar);
        haZonaResultado.getChildren().setAll(HAFormulariosHerramientas.haNota("Tachando…"));
        haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC), haR -> {
            haZonaResultado.getChildren().setAll(HAFormulariosHerramientas.haNota("Verificando el resultado…"));
            CompletableFuture.supplyAsync(() -> haVerificadorTachado.haVerificar(haR.haArchivo(), haR.haContrasena(), haAreas, haDatos))
                    .whenComplete((haInforme, haError) -> Platform.runLater(() -> haMostrarVerificacion(haInforme, haError)));
        });
    }

    private void haMostrarVerificacion(HAVerificadorTachado.HAInforme haInforme, Throwable haError) {
        if (haError != null || haInforme == null) {
            log.error("Falló la verificación del tachado", haError);
            haZonaResultado.getChildren().setAll(HAVistaComprobaciones.haVeredicto(
                    "NO se pudo verificar: no lo des por seguro", false, true));
            return;
        }
        if (haInforme.haSeguro()) {
            haZonaResultado.getChildren().setAll(
                    HAVistaComprobaciones.haVeredicto("Seguro: verificado", true, false),
                    HAFormulariosHerramientas.haNota("Se releyó el archivo: no queda texto debajo de los " + haInforme.haAreasRevisadas()
                            + " recuadros y ninguno de los " + haInforme.haDatosRevisados() + " datos tachados aparece en el texto, "
                            + "comentarios, formularios, marcadores, metadatos ni adjuntos. Guarda el resultado con «Guardar copia»."));
            haNotificador.haExito("Tachado verificado: no queda nada recuperable.");
        } else {
            VBox haProblemas = new VBox(4);
            haInforme.haProblemas().forEach(haP -> {
                Label haL = new Label("• " + haP);
                haL.setWrapText(true);
                haProblemas.getChildren().add(haL);
            });
            haZonaResultado.getChildren().setAll(
                    HAVistaComprobaciones.haVeredicto("NO es seguro", false, true),
                    HAFormulariosHerramientas.haNota("Todavía se puede recuperar información. No compartas este archivo:"),
                    haProblemas,
                    HAFormulariosHerramientas.haNota("Qué hacer: vuelve a «Buscar datos sensibles» y tacha también esas apariciones, "
                            + "o limpia los metadatos. Puedes deshacer con Ctrl+Z."));
            haNotificador.haError("El tachado NO es seguro: todavía queda información. Revisa la pestaña Tachado.");
        }
    }

    private void haInformePrivacidad() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        File haArchivo = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        haZonaPrivacidad.getChildren().setAll(HAFormulariosHerramientas.haNota("Analizando…"));
        CompletableFuture.supplyAsync(() -> haAnalizador.haInforme(haArchivo, haClave, haS.haNombre()))
                .whenComplete((haInforme, haError) -> Platform.runLater(() -> {
                    if (haError != null) {
                        haZonaPrivacidad.getChildren().clear();
                        haNotificador.haError("No se pudo analizar el PDF.");
                        return;
                    }
                    haUltimoInformePrivacidad = haInforme;
                    Button haExportar = new Button("Exportar informe PDF", new FontIcon(Feather.DOWNLOAD));
                    haExportar.setMaxWidth(Double.MAX_VALUE);
                    haExportar.setOnAction(haE -> haExportarPrivacidad());
                    haZonaPrivacidad.getChildren().setAll(
                            HAVistaComprobaciones.haVeredicto(haInforme.haCumple() ? "Listo para compartir" : "Revisa antes de compartir",
                                    haInforme.haCumple(), false),
                            HAVistaComprobaciones.haFilas(haInforme.haComprobaciones()), haExportar);
                }));
    }

    private void haExportarPrivacidad() {
        HAInformeVerificacion haInforme = haUltimoInformePrivacidad;
        if (haInforme == null) {
            return;
        }
        haExportador.haGuardar(haEditor.haVentana(), "Guardar informe de privacidad",
                HAArchivos.haNombreSeguro("Informe de privacidad - " + HAArchivos.haSinExtension(haInforme.haDocumento())),
                HAExportador.HAFormato.PDF, haDestino -> haInformePdf.haGenerar("Informe de privacidad", haInforme, haDestino));
    }

    private void haAbrirLimpieza() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            haNotificador.haAdvertencia("Primero abre un PDF.");
            return;
        }
        File haArchivo = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        CompletableFuture.supplyAsync(() -> haAnalizador.haInventario(haArchivo, haClave))
                .whenComplete((haInv, haError) -> Platform.runLater(() -> {
                    if (haError != null) {
                        haNotificador.haError("No se pudo analizar el PDF.");
                        return;
                    }
                    if (!haInv.haHayAlgoQueLimpiar()) {
                        haNotificador.haInfo("Este PDF no tiene metadatos, comentarios, adjuntos ni versiones anteriores que limpiar.");
                        return;
                    }
                    haFormularios.haLimpiarMetadatos(haEditor, haInv, haResumen -> {
                        haNotificador.haExito(haResumen);
                        haInformePrivacidad();
                    });
                }));
    }

    private void haQuitarContrasena() {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            return;
        }
        if (!haS.haVista().haCifrado()) {
            haNotificador.haInfo("Este documento no tiene contraseña.");
            return;
        }
        HAQuitarContrasena haOp = new HAQuitarContrasena();
        haEditor.haAplicar(haOp.haDescripcion(), (haIn, haPw, haOut, haC) -> haMotor.haEjecutar(haIn, haPw, haOp, haOut, haC),
                haR -> haNotificador.haExito("Contraseña quitada en la nueva versión. Guárdala con «Guardar copia»."));
    }

    private static Button haBoton(String haTexto, org.kordamp.ikonli.Ikon haIcono, Runnable haAccion) {
        Button haB = new Button(haTexto, new FontIcon(haIcono));
        haB.setMaxWidth(Double.MAX_VALUE);
        haB.setAlignment(Pos.CENTER_LEFT);
        haB.setOnAction(haE -> haAccion.run());
        return haB;
    }
}
