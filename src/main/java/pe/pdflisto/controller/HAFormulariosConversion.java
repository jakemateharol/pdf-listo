package pe.pdflisto.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.conversiones.HAConversor;
import pe.pdflisto.conversiones.HAConversorWord;
import pe.pdflisto.edicion.HASesionEdicion;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.ui.HANotificador;

import java.io.File;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HAFormulariosConversion {
    private final HAConversor haConversor;
    private final HAConversorWord haWord;
    private final HAFormulariosHerramientas haPaneles;
    private final HAConfiguracion haConfiguracion;
    private final HANotificador haNotificador;

    public void haMontar(MenuButton haMenu, HAEditor haEditor) {
        MenuItem haImagenes = haItem("Imágenes a PDF…", Feather.IMAGE, () -> haImagenesAPdf(haEditor));
        MenuItem haTexto = haItem("Texto (.txt) a PDF…", Feather.ALIGN_LEFT, () -> haTextoAPdf(haEditor));
        MenuItem haDocumentoWord = haItem("Word a PDF…", Feather.FILE_TEXT, () -> haWordAPdf(haEditor));
        MenuItem haAImagenes = haItem("PDF a imágenes…", Feather.IMAGE, () -> haPdfAImagenes(haEditor));
        MenuItem haATexto = haItem("PDF a texto (.txt)…", Feather.ALIGN_LEFT, () -> haPdfATexto(haEditor));
        MenuItem haExtraer = haItem("Extraer las imágenes del PDF…", Feather.DOWNLOAD, () -> haExtraerImagenes(haEditor));
        haMenu.getItems().setAll(haImagenes, haTexto, haDocumentoWord, new SeparatorMenuItem(), haAImagenes, haATexto, haExtraer);
        haMenu.setOnShowing(haE -> {
            boolean haHayWord = haWord.haDisponible();
            haDocumentoWord.setDisable(!haHayWord);
            haDocumentoWord.setText(haHayWord ? "Word a PDF…" : "Word a PDF (instala LibreOffice, gratis, para activarlo)");
            boolean haHayDoc = haEditor.haSesion() != null;
            haAImagenes.setDisable(!haHayDoc);
            haATexto.setDisable(!haHayDoc);
            haExtraer.setDisable(!haHayDoc);
        });
    }

    private void haImagenesAPdf(HAEditor haEditor) {
        FileChooser haSelector = haSelector("Imágenes para convertir", new FileChooser.ExtensionFilter("Imágenes", "*.jpg", "*.jpeg", "*.png"));
        List<File> haElegidas = haSelector.showOpenMultipleDialog(haEditor.haVentana());
        if (haElegidas == null || haElegidas.isEmpty()) {
            return;
        }
        haConfiguracion.setUltimaCarpeta(haElegidas.get(0).getParentFile());
        ObservableList<File> haOrden = FXCollections.observableArrayList(haElegidas);
        ListView<File> haLista = new ListView<>(haOrden);
        haLista.setPrefHeight(220);
        haLista.setCellFactory(haL -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(File haF, boolean haVacia) {
                super.updateItem(haF, haVacia);
                setText(haVacia || haF == null ? null : (getIndex() + 1) + ".  " + haF.getName());
            }
        });
        Button haSubir = haBoton(Feather.ARROW_UP, () -> haMover(haLista, -1));
        Button haBajar = haBoton(Feather.ARROW_DOWN, () -> haMover(haLista, 1));
        ComboBox<HAConversor.HATamanoHoja> haTamano = new ComboBox<>(FXCollections.observableArrayList(HAConversor.HATamanoHoja.values()));
        haTamano.getSelectionModel().select(HAConversor.HATamanoHoja.A4);
        VBox haCuerpo = new VBox(12, HAFormulariosHerramientas.haCampo("Orden de las páginas", haLista), new HBox(6, haSubir, haBajar),
                HAFormulariosHerramientas.haCampo("Tamaño de hoja", haTamano),
                HAFormulariosHerramientas.haNota("Útil para juntar fotos de un documento tomadas con el celular. Las fotos JPG "
                        + "se guardan tal cual, sin perder calidad."));
        haPaneles.haAbrir(Feather.IMAGE, "Imágenes a PDF", haOrden.size() + " imágenes.", haCuerpo, "Crear PDF…", () -> {
            File haDestino = haGuardarComo(haEditor, "PDF nuevo", HAArchivos.haSinExtension(haOrden.get(0).getName().replaceFirst("\\.[^.]+$", ".pdf")), ".pdf");
            List<File> haCopia = List.copyOf(haOrden);
            HAConversor.HATamanoHoja haT = haTamano.getValue();
            haEditor.haExportar("Imágenes a PDF", haC -> List.of(haConversor.haImagenesAPdf(haCopia, haT, haDestino, haC)),
                    new pe.pdflisto.verificacion.HAEsperadoArchivos.HAImagenesAPdf(haCopia), haAbrir(haEditor));
        });
    }

    private void haTextoAPdf(HAEditor haEditor) {
        File haTxt = haSelector("Archivo de texto", new FileChooser.ExtensionFilter("Texto", "*.txt")).showOpenDialog(haEditor.haVentana());
        if (haTxt == null) {
            return;
        }
        File haDestino = haGuardarComoSeguro(haEditor, haTxt);
        if (haDestino != null) {
            haEditor.haExportar("Texto a PDF", haC -> List.of(haConversor.haTextoAPdf(haTxt, haDestino, haC)),
                    new pe.pdflisto.verificacion.HAEsperadoArchivos.HATextoAPdf(haTxt), haAbrir(haEditor));
        }
    }

    private void haWordAPdf(HAEditor haEditor) {
        if (!haWord.haDisponible()) {
            haNotificador.haAdvertencia(HAConversorWord.HA_MENSAJE_FALTA);
            return;
        }
        File haDoc = haSelector("Documento de Word", new FileChooser.ExtensionFilter("Documentos", "*.docx", "*.doc", "*.odt", "*.rtf"))
                .showOpenDialog(haEditor.haVentana());
        if (haDoc == null) {
            return;
        }
        File haDestino = haGuardarComoSeguro(haEditor, haDoc);
        if (haDestino != null) {
            haEditor.haExportar("Word a PDF", haC -> List.of(haWord.haConvertir(haDoc, haDestino, haC)),
                    new pe.pdflisto.verificacion.HAEsperadoArchivos.HAWordAPdf(haDoc), haAbrir(haEditor));
        }
    }

    private void haPdfAImagenes(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            return;
        }
        ComboBox<String> haCalidad = new ComboBox<>(FXCollections.observableArrayList(
                "Pantalla (150 dpi, livianas)", "Impresión (300 dpi, nítidas)"));
        haCalidad.getSelectionModel().select(0);
        ComboBox<String> haFormato = new ComboBox<>(FXCollections.observableArrayList("PNG (mejor para texto)", "JPG (más liviano, para fotos)"));
        haFormato.getSelectionModel().select(0);
        VBox haCuerpo = new VBox(12, HAFormulariosHerramientas.haCampo("Calidad", haCalidad), HAFormulariosHerramientas.haCampo("Formato", haFormato),
                HAFormulariosHerramientas.haNota("Se crea una imagen por página en la carpeta que elijas."));
        haPaneles.haAbrir(Feather.IMAGE, "PDF a imágenes", haS.haVista().haPaginas() + " páginas.", haCuerpo, "Elegir carpeta…", () -> {
            File haCarpeta = haCarpeta(haEditor, haS);
            int haDpi = haCalidad.getSelectionModel().getSelectedIndex() == 0 ? 150 : 300;
            boolean haJpg = haFormato.getSelectionModel().getSelectedIndex() == 1;
            File haEntrada = haS.haArchivoActual();
            String haClave = haS.haContrasenaActual();
            String haBase = HAArchivos.haSinExtension(haS.haNombre());
            haEditor.haExportar("PDF a imágenes", haC -> haConversor.haPdfAImagenes(haEntrada, haClave, haBase, haDpi, haJpg, haCarpeta, haC),
                    new pe.pdflisto.verificacion.HAEsperadoArchivos.HAPdfAImagenes(haEntrada, haClave, haDpi));
        });
    }

    private void haPdfATexto(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            return;
        }
        File haDestino = haGuardarComo(haEditor, "Guardar el texto", HAArchivos.haSinExtension(haS.haNombre()), ".txt");
        File haEntrada = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        haEditor.haExportar("PDF a texto", haC -> List.of(haConversor.haPdfATexto(haEntrada, haClave, haDestino, haC)),
                new pe.pdflisto.verificacion.HAEsperadoArchivos.HAPdfATexto(haEntrada, haClave));
    }

    private void haExtraerImagenes(HAEditor haEditor) {
        HASesionEdicion haS = haEditor.haSesion();
        if (haS == null) {
            return;
        }
        File haCarpeta;
        try {
            haCarpeta = haCarpeta(haEditor, haS);
        } catch (HAFormulariosHerramientas.HACancelado haE) {
            return;
        }
        File haEntrada = haS.haArchivoActual();
        String haClave = haS.haContrasenaActual();
        String haBase = HAArchivos.haSinExtension(haS.haNombre());
        haEditor.haExportar("Extraer imágenes", haC -> haConversor.haExtraerImagenes(haEntrada, haClave, haBase, haCarpeta, haC),
                new pe.pdflisto.verificacion.HAEsperadoArchivos.HAExtraerImagenes(haEntrada, haClave));
    }

    private java.util.function.Consumer<List<File>> haAbrir(HAEditor haEditor) {
        return haArchivos -> {
            haNotificador.haExito("Listo: «" + haArchivos.get(0).getName() + "» creado. Se abrió para que lo revises.");
            haEditor.haAbrirPdf(haArchivos);
        };
    }

    private FileChooser haSelector(String haTitulo, FileChooser.ExtensionFilter haFiltro) {
        FileChooser haF = new FileChooser();
        haF.setTitle(haTitulo);
        haF.getExtensionFilters().add(haFiltro);
        haF.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
        return haF;
    }

    private File haGuardarComoSeguro(HAEditor haEditor, File haOrigen) {
        try {
            return haGuardarComo(haEditor, "Guardar el PDF", haOrigen.getName().replaceFirst("\\.[^.]+$", ""), ".pdf");
        } catch (HAFormulariosHerramientas.HACancelado haE) {
            return null;
        }
    }

    private File haGuardarComo(HAEditor haEditor, String haTitulo, String haNombre, String haExtension) {
        FileChooser haF = new FileChooser();
        haF.setTitle(haTitulo);
        haF.getExtensionFilters().add(new FileChooser.ExtensionFilter(haExtension.substring(1).toUpperCase(), "*" + haExtension));
        haF.setInitialDirectory(haConfiguracion.haUltimaCarpeta());
        haF.setInitialFileName(HAArchivos.haNombreSeguro(haNombre) + haExtension);
        File haElegido = haF.showSaveDialog(haEditor.haVentana());
        if (haElegido == null) {
            throw new HAFormulariosHerramientas.HACancelado();
        }
        haConfiguracion.setUltimaCarpeta(haElegido.getParentFile());
        return haElegido.getName().toLowerCase().endsWith(haExtension) ? haElegido : new File(haElegido.getParentFile(), haElegido.getName() + haExtension);
    }

    private File haCarpeta(HAEditor haEditor, HASesionEdicion haS) {
        DirectoryChooser haD = new DirectoryChooser();
        haD.setTitle("¿Dónde guardo las imágenes?");
        File haInicial = haS.haOriginal().getParentFile();
        haD.setInitialDirectory(haInicial != null && haInicial.isDirectory() ? haInicial : haConfiguracion.haUltimaCarpeta());
        File haElegida = haD.showDialog(haEditor.haVentana());
        if (haElegida == null) {
            throw new HAFormulariosHerramientas.HACancelado();
        }
        return haElegida;
    }

    private static MenuItem haItem(String haTexto, Feather haIcono, Runnable haAccion) {
        MenuItem haM = new MenuItem(haTexto, new FontIcon(haIcono));
        haM.setOnAction(haE -> {
            try {
                haAccion.run();
            } catch (HAFormulariosHerramientas.HACancelado haIgnorado) {
            }
        });
        return haM;
    }

    private static Button haBoton(Feather haIcono, Runnable haAccion) {
        Button haB = new Button(null, new FontIcon(haIcono));
        haB.getStyleClass().add(atlantafx.base.theme.Styles.BUTTON_ICON);
        haB.setOnAction(haE -> haAccion.run());
        return haB;
    }

    private static <T> void haMover(ListView<T> haLista, int haPaso) {
        int haI = haLista.getSelectionModel().getSelectedIndex();
        int haJ = haI + haPaso;
        if (haI < 0 || haJ < 0 || haJ >= haLista.getItems().size()) {
            return;
        }
        T haItem = haLista.getItems().remove(haI);
        haLista.getItems().add(haJ, haItem);
        haLista.getSelectionModel().clearAndSelect(haJ);
    }
}
