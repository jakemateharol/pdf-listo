package pe.pdflisto.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import pe.pdflisto.pdf.HAAreaTexto;
import pe.pdflisto.pdf.HADocumentoPdf;
import pe.pdflisto.pdf.HATamanoPagina;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class HAVisorPagina extends StackPane {
    public interface HAHerramienta {
        void haPresionar(int haPagina, double haX, double haY);

        void haArrastrar(int haPagina, double haX, double haY);

        void haSoltar(int haPagina, double haX, double haY);
    }

    public interface HAPintor {
        List<javafx.scene.Node> haPintar(int haPagina, double haFactor);
    }

    public static final double HA_PIXELES_POR_PUNTO = 96.0 / 72.0;
    private static final float HA_DPI_MAXIMO = 300;

    private final ImageView haImagen = new ImageView();
    private final Pane haCapaResaltados = new Pane();
    private final javafx.scene.control.Label haAvisoDanada =
            new javafx.scene.control.Label("No se pudo mostrar esta página.\nPuede estar dañada; las demás siguen disponibles.");
    private final Pane haCapaExtra = new Pane();
    private final StackPane haHoja = new StackPane(haImagen, haAvisoDanada, haCapaResaltados, haCapaExtra);
    private HAHerramienta haHerramienta;
    private HAPintor haPintor;
    private final DoubleProperty haZoom = new SimpleDoubleProperty(1.0);
    private final PauseTransition haEsperaRedibujo = new PauseTransition(Duration.millis(140));

    private HADocumentoPdf haDocumento;
    private int haPagina;
    private long haTurno;
    private List<HAAreaTexto> haResaltados = List.of();
    private List<HAAreaTexto> haActivos = List.of();
    private Consumer<Throwable> haAlFallar = haE -> { };

    private Map<Integer, List<HAAreaTexto>> haParaTachar = Map.of();
    private BiConsumer<Integer, HAAreaTexto> haAlDibujar;
    private Rectangle haBorrador;
    private double haOrigenX;
    private double haOrigenY;

    public HAVisorPagina() {
        getStyleClass().add("visor-fondo");
        haHoja.getStyleClass().add("hoja-pdf");
        haHoja.setMaxSize(USE_PREF_SIZE, USE_PREF_SIZE);
        haAvisoDanada.getStyleClass().add("aviso-pagina-danada");
        haAvisoDanada.setWrapText(true);
        haAvisoDanada.setVisible(false);
        haCapaResaltados.setMouseTransparent(true);
        haCapaExtra.setMouseTransparent(true);
        haImagen.setSmooth(true);
        setPadding(new Insets(24));
        getChildren().add(haHoja);

        haZoom.addListener((haObs, haAntes, haAhora) -> {
            haAjustarTamano();
            haEsperaRedibujo.playFromStart();
        });
        haEsperaRedibujo.setOnFinished(haE -> haDibujar());
        haInstalarDibujoDeAreas();
    }

    public void setParaTachar(Map<Integer, List<HAAreaTexto>> haMarcas) {
        this.haParaTachar = haMarcas == null ? Map.of() : haMarcas;
        haPintarResaltados();
    }

    public void setModoDibujo(BiConsumer<Integer, HAAreaTexto> haAlDibujar) {
        this.haAlDibujar = haAlDibujar;
        haHoja.setCursor(haAlDibujar != null ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public void setHerramienta(HAHerramienta haH) {
        this.haHerramienta = haH;
        haHoja.setCursor(haH != null || haAlDibujar != null ? Cursor.CROSSHAIR : Cursor.DEFAULT);
    }

    public void setPintor(HAPintor haP) {
        this.haPintor = haP;
        haRepintarExtra();
    }

    public void haRepintarExtra() {
        haCapaExtra.getChildren().clear();
        if (haPintor != null && haDocumento != null) {
            haCapaExtra.getChildren().addAll(haPintor.haPintar(haPagina, haFactor()));
        }
    }

    public int haPagina() {
        return haPagina;
    }

    private boolean haAHerramienta(javafx.scene.input.MouseEvent haE, int haFase) {
        if (haHerramienta == null || haDocumento == null) {
            return false;
        }
        double haF = haFactor();
        double haX = haLimitar(haE.getX(), haHoja.getWidth()) / haF;
        double haY = haLimitar(haE.getY(), haHoja.getHeight()) / haF;
        switch (haFase) {
            case 0 -> haHerramienta.haPresionar(haPagina, haX, haY);
            case 1 -> haHerramienta.haArrastrar(haPagina, haX, haY);
            default -> haHerramienta.haSoltar(haPagina, haX, haY);
        }
        haE.consume();
        return true;
    }

    private void haInstalarDibujoDeAreas() {
        haHoja.setOnMousePressed(haE -> {
            if (haAHerramienta(haE, 0)) {
                return;
            }
            if (haAlDibujar == null || haDocumento == null) {
                return;
            }
            haOrigenX = haLimitar(haE.getX(), haHoja.getWidth());
            haOrigenY = haLimitar(haE.getY(), haHoja.getHeight());
            haBorrador = new Rectangle(haOrigenX, haOrigenY, 0, 0);
            haBorrador.getStyleClass().add("marca-tachado");
            haCapaResaltados.getChildren().add(haBorrador);
            haE.consume();
        });
        haHoja.setOnMouseDragged(haE -> {
            if (haAHerramienta(haE, 1)) {
                return;
            }
            if (haBorrador == null) {
                return;
            }
            double haX = haLimitar(haE.getX(), haHoja.getWidth());
            double haY = haLimitar(haE.getY(), haHoja.getHeight());
            haBorrador.setX(Math.min(haOrigenX, haX));
            haBorrador.setY(Math.min(haOrigenY, haY));
            haBorrador.setWidth(Math.abs(haX - haOrigenX));
            haBorrador.setHeight(Math.abs(haY - haOrigenY));
            haE.consume();
        });
        haHoja.setOnMouseReleased(haE -> {
            if (haAHerramienta(haE, 2)) {
                return;
            }
            if (haBorrador == null) {
                return;
            }
            Rectangle haR = haBorrador;
            haBorrador = null;
            haCapaResaltados.getChildren().remove(haR);
            double haF = haFactor();
            if (haR.getWidth() > 4 && haR.getHeight() > 4 && haAlDibujar != null) {
                haAlDibujar.accept(haPagina, new HAAreaTexto(haR.getX() / haF, haR.getY() / haF, haR.getWidth() / haF, haR.getHeight() / haF));
            }
            haE.consume();
        });
    }

    private static double haLimitar(double haV, double haMax) {
        return Math.max(0, Math.min(haV, haMax));
    }

    public DoubleProperty haZoomProperty() {
        return haZoom;
    }

    public void setAlFallar(Consumer<Throwable> haAlFallar) {
        this.haAlFallar = haAlFallar;
    }

    public void haMostrar(HADocumentoPdf haDocumento, int haPagina) {
        boolean haCambia = this.haDocumento != haDocumento || this.haPagina != haPagina;
        this.haDocumento = haDocumento;
        this.haPagina = haPagina;
        if (haCambia) {
            haAvisoDanada.setVisible(false);
            haImagen.setImage(null);
            haResaltados = List.of();
            haActivos = List.of();
        }
        haAjustarTamano();
        haDibujar();
    }

    public void haLimpiar() {
        haDocumento = null;
        haTurno++;
        haImagen.setImage(null);
        haCapaResaltados.getChildren().clear();
    }

    public void setResaltados(List<HAAreaTexto> haTodos, List<HAAreaTexto> haActuales) {
        this.haResaltados = haTodos;
        this.haActivos = haActuales;
        haPintarResaltados();
    }

    public Bounds haUbicacion(HAAreaTexto haArea) {
        double haF = haFactor();
        Bounds haH = haHoja.getBoundsInParent();
        return new BoundingBox(haH.getMinX() + haArea.haX() * haF, haH.getMinY() + haArea.haY() * haF,
                haArea.haAncho() * haF, haArea.haAlto() * haF);
    }

    private double haFactor() {
        return HA_PIXELES_POR_PUNTO * haZoom.get();
    }

    private void haAjustarTamano() {
        if (haDocumento == null) {
            return;
        }
        HATamanoPagina haT = haDocumento.haTamano(haPagina);
        double haAncho = haT.haAncho() * haFactor();
        double haAlto = haT.haAlto() * haFactor();
        haImagen.setFitWidth(haAncho);
        haImagen.setFitHeight(haAlto);
        haHoja.setPrefSize(haAncho, haAlto);
        haHoja.setMinSize(haAncho, haAlto);
        haPintarResaltados();
    }

    private void haDibujar() {
        if (haDocumento == null) {
            return;
        }
        long haMiTurno = ++haTurno;
        HADocumentoPdf haDoc = haDocumento;
        int haPag = haPagina;
        double haEscalaPantalla = getScene() != null && getScene().getWindow() != null
                ? getScene().getWindow().getOutputScaleX() : 1.0;
        float haDpi = (float) Math.min(HA_DPI_MAXIMO, 72 * haFactor() * haEscalaPantalla);
        haDoc.haRenderizar(haPag, haDpi, HADocumentoPdf.HAPrioridad.ALTA)
                .thenApplyAsync(HAImagenes::haAFx)
                .whenComplete((haImg, haError) -> Platform.runLater(() -> {
                    if (haMiTurno != haTurno) {
                        return;
                    }
                    if (haError != null) {
                        Throwable haC = haError.getCause() != null ? haError.getCause() : haError;
                        if (!(haC instanceof java.util.concurrent.CancellationException)) {
                            haAvisoDanada.setVisible(true);
                        }
                        haAlFallar.accept(haError);
                    } else {
                        haAvisoDanada.setVisible(false);
                        haImagen.setImage(haImg);
                    }
                }));
    }

    private void haPintarResaltados() {
        haCapaResaltados.getChildren().clear();
        double haF = haFactor();
        for (HAAreaTexto haA : haResaltados) {
            haCapaResaltados.getChildren().add(haRectangulo(haA, haF, haActivos.contains(haA) ? "resaltado-activo" : "resaltado"));
        }
        for (HAAreaTexto haA : haParaTachar.getOrDefault(haPagina, List.of())) {
            haCapaResaltados.getChildren().add(haRectangulo(haA, haF, "marca-tachado"));
        }
        haRepintarExtra();
    }

    private static Rectangle haRectangulo(HAAreaTexto haA, double haF, String haEstilo) {
        Rectangle haR = new Rectangle(haA.haX() * haF, haA.haY() * haF, haA.haAncho() * haF, haA.haAlto() * haF);
        haR.getStyleClass().add(haEstilo);
        haR.setArcWidth(4);
        haR.setArcHeight(4);
        return haR;
    }
}
