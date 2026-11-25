package pe.pdflisto.ui;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import pe.pdflisto.pdf.HADocumentoPdf;
import pe.pdflisto.pdf.HATamanoPagina;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class HACeldaMiniatura extends ListCell<Integer> {
    private static final double HA_ANCHO = 118;
    private static final DataFormat HA_PAGINAS = new DataFormat("application/x-pdflisto-paginas");
    private static final PseudoClass HA_ANTES = PseudoClass.getPseudoClass("soltar-antes");
    private static final PseudoClass HA_DESPUES = PseudoClass.getPseudoClass("soltar-despues");

    private final Supplier<HADocumentoPdf> haDocumentoActual;
    private final HAMiniaturas haMiniaturas;
    private final BiConsumer<List<Integer>, Integer> haAlMover;
    private final ImageView haImagen = new ImageView();
    private final StackPane haHoja = new StackPane(haImagen);
    private final Label haNumero = new Label();
    private final VBox haCaja = new VBox(6, haHoja, haNumero);

    public HACeldaMiniatura(Supplier<HADocumentoPdf> haDocumentoActual, HAMiniaturas haMiniaturas,
                          BiConsumer<List<Integer>, Integer> haAlMover) {
        this.haDocumentoActual = haDocumentoActual;
        this.haMiniaturas = haMiniaturas;
        this.haAlMover = haAlMover;
        getStyleClass().add("celda-miniatura");
        haHoja.getStyleClass().add("miniatura-hoja");
        haNumero.getStyleClass().add("miniatura-numero");
        haCaja.setAlignment(Pos.CENTER);
        haImagen.setSmooth(true);
        haInstalarArrastre();
    }

    @Override
    protected void updateItem(Integer haPagina, boolean haVacia) {
        super.updateItem(haPagina, haVacia);
        HADocumentoPdf haDocumento = haDocumentoActual.get();
        if (haVacia || haPagina == null || haDocumento == null || haPagina >= haDocumento.haPaginas()) {
            setGraphic(null);
            setText(null);
            return;
        }
        HATamanoPagina haT = haDocumento.haTamano(haPagina);
        double haAlto = HA_ANCHO * haT.haAlto() / haT.haAncho();
        haImagen.setFitWidth(HA_ANCHO);
        haImagen.setFitHeight(haAlto);
        haHoja.setPrefSize(HA_ANCHO, haAlto);
        haHoja.setMaxSize(HA_ANCHO, haAlto);
        haNumero.setText(String.valueOf(haPagina + 1));

        haImagen.setImage(haMiniaturas.haEnMemoria(haDocumento, haPagina));
        if (haImagen.getImage() == null) {
            haMiniaturas.haObtener(haDocumento, haPagina, HA_ANCHO * 2).thenAccept(haImg -> Platform.runLater(() -> {
                if (Objects.equals(getItem(), haPagina) && haDocumentoActual.get() == haDocumento) {
                    haImagen.setImage(haImg);
                }
            }));
        }
        setText(null);
        setGraphic(haCaja);
    }

    private void haInstalarArrastre() {
        setOnDragDetected(haE -> {
            if (getItem() == null) {
                return;
            }
            var haSeleccion = getListView().getSelectionModel();
            if (!haSeleccion.getSelectedItems().contains(getItem())) {
                haSeleccion.clearAndSelect(getIndex());
            }
            Dragboard haDb = startDragAndDrop(TransferMode.MOVE);
            ClipboardContent haContenido = new ClipboardContent();
            haContenido.put(HA_PAGINAS, new ArrayList<>(haSeleccion.getSelectedItems()));
            haDb.setContent(haContenido);
            if (haImagen.getImage() != null) {
                haDb.setDragView(haHoja.snapshot(null, null));
            }
            haE.consume();
        });
        setOnDragOver(haE -> {
            if (haE.getGestureSource() instanceof HACeldaMiniatura && haE.getDragboard().hasContent(HA_PAGINAS)) {
                haE.acceptTransferModes(TransferMode.MOVE);
                boolean haDespues = isEmpty() || haE.getY() > getHeight() / 2;
                pseudoClassStateChanged(HA_ANTES, !haDespues && !isEmpty());
                pseudoClassStateChanged(HA_DESPUES, haDespues && !isEmpty());
                haE.consume();
            }
        });
        setOnDragExited(haE -> haLimpiarMarcas());
        setOnDragDropped(this::haAlSoltar);
    }

    private void haAlSoltar(DragEvent haE) {
        haLimpiarMarcas();
        Object haContenido = haE.getDragboard().getContent(HA_PAGINAS);
        if (!(haContenido instanceof List<?> haLista)) {
            haE.setDropCompleted(false);
            return;
        }
        List<Integer> haPaginas = haLista.stream().map(haO -> (Integer) haO).toList();
        int haDestino = isEmpty() || getItem() == null
                ? getListView().getItems().size()
                : getItem() + (haE.getY() > getHeight() / 2 ? 1 : 0);
        haE.setDropCompleted(true);
        haE.consume();
        Platform.runLater(() -> haAlMover.accept(haPaginas, haDestino));
    }

    private void haLimpiarMarcas() {
        pseudoClassStateChanged(HA_ANTES, false);
        pseudoClassStateChanged(HA_DESPUES, false);
    }
}
