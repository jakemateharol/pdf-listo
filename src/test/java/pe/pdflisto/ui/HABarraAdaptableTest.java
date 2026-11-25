package pe.pdflisto.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HABarraAdaptableTest {
    private static boolean haHayJavaFx;

    @BeforeAll
    static void haIniciarJavaFx() throws Exception {
        CountDownLatch haListo = new CountDownLatch(1);
        try {
            Platform.startup(haListo::countDown);
            haHayJavaFx = haListo.await(10, TimeUnit.SECONDS);
        } catch (IllegalStateException haYaIniciado) {
            haHayJavaFx = true;
        } catch (Throwable haSinPantalla) {
            haHayJavaFx = false;
        }
    }

    @Test
    void haEligeElPrimerNivelQueEntra() {
        double[] haNecesario = {1400, 1050, 900};
        assertEquals(HABarraAdaptable.HANivel.COMPLETO, HABarraAdaptable.haElegirNivel(1600, haNecesario));
        assertEquals(HABarraAdaptable.HANivel.COMPACTO, HABarraAdaptable.haElegirNivel(1366, haNecesario));
        assertEquals(HABarraAdaptable.HANivel.MINIMO, HABarraAdaptable.haElegirNivel(1000, haNecesario));
        assertEquals(HABarraAdaptable.HANivel.MINIMO, HABarraAdaptable.haElegirNivel(500, haNecesario));
    }

    @Test
    void haNingunBotonSeCortaEnPantallasAngostas() throws Exception {
        assumeTrue(haHayJavaFx, "JavaFX no disponible en este equipo");
        for (int haAncho : new int[] {1366, 1280, 1200, 1120}) {
            for (boolean haTrabajando : new boolean[] {false, true}) {
                List<String> haCortados = haEnFx(() -> haRevisar(haAncho, haTrabajando));
                assertTrue(haCortados.isEmpty(),
                        "A " + haAncho + " px" + (haTrabajando ? " (trabajando)" : "") + " se cortan: " + haCortados);
            }
        }
    }

    @Test
    void haEnPantallaGrandeSeVenLosTextos() throws Exception {
        assumeTrue(haHayJavaFx, "JavaFX no disponible en este equipo");
        HABarraAdaptable.HANivel haNivel = haEnFx(() -> haArmar(1920, false).haAdaptable.haNivel());
        assertEquals(HABarraAdaptable.HANivel.COMPLETO, haNivel);
    }

    @Test
    void haMedirNoProvocaOtraMedicionSinFin() throws Exception {
        assumeTrue(haHayJavaFx, "JavaFX no disponible en este equipo");
        HAArmado haA = haEnFx(() -> haArmar(1366, false));
        int[] haAntes = new int[1];
        haEnFx(() -> {
            haA.haAdaptable.haPedirMedicion();
            return null;
        });
        for (int haI = 0; haI < 10; haI++) {
            haEnFx(() -> null);
        }
        haAntes[0] = haEnFx(haA.haAdaptable::haMediciones);
        for (int haI = 0; haI < 20; haI++) {
            haEnFx(() -> null);
        }
        assertEquals(haAntes[0], (int) haEnFx(haA.haAdaptable::haMediciones), "la barra se sigue midiendo sola");
        haEnFx(() -> {
            Node haIndicador = haA.haBarra.getChildren().get(4);
            haIndicador.setVisible(true);
            haIndicador.setManaged(true);
            return null;
        });
        for (int haI = 0; haI < 10; haI++) {
            haEnFx(() -> null);
        }
        int haDespues = haEnFx(haA.haAdaptable::haMediciones);
        assertTrue(haDespues > haAntes[0] && haDespues <= haAntes[0] + 2, "mediciones: " + haAntes[0] + " → " + haDespues);
    }

    private record HAArmado(HBox haBarra, HABarraAdaptable haAdaptable) { }

    private static List<String> haRevisar(int haAncho, boolean haTrabajando) {
        HAArmado haA = haArmar(haAncho, haTrabajando);
        List<String> haCortados = new ArrayList<>();
        for (Node haN : haA.haBarra.getChildren()) {
            if (haN.isManaged() && haN instanceof Labeled haL && haN instanceof Region haR
                    && haR.getWidth() + 0.5 < haR.prefWidth(-1)) {
                haCortados.add(haL.getText() + " (" + Math.round(haR.getWidth()) + " de " + Math.round(haR.prefWidth(-1)) + " px)");
            }
        }
        double haUsado = haA.haBarra.getChildren().stream().filter(Node::isManaged)
                .mapToDouble(haN -> haN.getBoundsInParent().getMaxX()).max().orElse(0);
        if (haUsado > haAncho + 0.5) {
            haCortados.add("la barra se sale de la ventana (" + Math.round(haUsado) + " px)");
        }
        return haCortados;
    }

    private static HAArmado haArmar(int haAncho, boolean haTrabajando) {
        HBox haBarra = new HBox(12);
        haBarra.setStyle("-fx-padding: 10 16 10 16; -fx-font-size: 13px;");
        Label haMarca = new Label("PDF Listo");
        haMarca.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        VBox haNombre = new VBox(new Label("informe-final-de-tesis-version-corregida.pdf"), new Label("12 páginas · 2,4 MB"));
        haNombre.setMinWidth(0);
        Button haCerrar = haIcono("fth-x");
        haCerrar.setMinWidth(Region.USE_PREF_SIZE);
        HBox haArchivo = new HBox(4, haNombre, haCerrar);
        haArchivo.setMinWidth(150);
        HBox.setHgrow(haArchivo, Priority.ALWAYS);
        javafx.scene.control.Separator haSeparador = new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL);
        Label haLblTrabajo = new Label("Comprimiendo página 3 de 12…");
        HBox haIndicador = new HBox(8, haLblTrabajo, new Button("Cancelar"));
        haIndicador.setVisible(haTrabajando);
        haIndicador.setManaged(haTrabajando);
        MenuButton haHerramientas = new MenuButton("Herramientas", new FontIcon("fth-tool"));
        MenuButton haConvertir = new MenuButton("Convertir", new FontIcon("fth-repeat"));
        ComboBox<String> haDocumentos = new ComboBox<>();
        haDocumentos.setPrefWidth(220);
        MenuButton haRecientes = new MenuButton("Recientes", new FontIcon("fth-clock"));
        Button haAbrir = new Button("Abrir PDF", new FontIcon("fth-folder"));
        Button haGuardar = new Button("Guardar copia", new FontIcon("fth-save"));
        haBarra.getChildren().addAll(new Region() {{ setPrefWidth(32); }}, haMarca, haSeparador,
                haArchivo, haIndicador, haIcono("fth-corner-up-left"), haIcono("fth-corner-up-right"), haHerramientas, haConvertir,
                haDocumentos, haRecientes, haAbrir, haGuardar, haIcono("fth-moon"),
                new MenuButton(null, new FontIcon("fth-help-circle")));

        HABarraAdaptable haAdaptable = new HABarraAdaptable(haBarra, haArchivo)
                .haSoloIconoDesde(haHerramientas, HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haConvertir, HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haRecientes, HABarraAdaptable.HANivel.COMPACTO)
                .haSoloIconoDesde(haGuardar, HABarraAdaptable.HANivel.COMPACTO)
                .haTextoCorto(haAbrir, "Abrir")
                .haOcultarDesde(haMarca, HABarraAdaptable.HANivel.COMPACTO)
                .haOcultarDesde(haSeparador, HABarraAdaptable.HANivel.MINIMO)
                .haAlCambiarNivel(haN -> {
                    boolean haMinimo = haN == HABarraAdaptable.HANivel.MINIMO;
                    haDocumentos.setPrefWidth(haN == HABarraAdaptable.HANivel.COMPLETO ? 220 : haMinimo ? 140 : 170);
                    haLblTrabajo.setPrefWidth(haMinimo ? 140 : 260);
                });
        Scene haEscena = new Scene(haBarra, haAncho - 16, 60);
        haEscena.getStylesheets().add(new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        haAdaptable.haInstalar();
        haBarra.resize(haAncho - 16, 60);
        haAdaptable.haMedirAhora();
        for (int haI = 0; haI < 3; haI++) {
            haBarra.applyCss();
            haBarra.resize(haAncho - 16, 60);
            haBarra.layout();
        }
        return new HAArmado(haBarra, haAdaptable);
    }

    private static Button haIcono(String haLiteral) {
        Button haB = new Button(null, new FontIcon(haLiteral));
        haB.getStyleClass().addAll("button-icon", "flat");
        return haB;
    }

    private static <T> T haEnFx(java.util.concurrent.Callable<T> haTarea) throws Exception {
        AtomicReference<T> haResultado = new AtomicReference<>();
        AtomicReference<Throwable> haError = new AtomicReference<>();
        CountDownLatch haListo = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                haResultado.set(haTarea.call());
            } catch (Throwable haT) {
                haError.set(haT);
            } finally {
                haListo.countDown();
            }
        });
        assertTrue(haListo.await(20, TimeUnit.SECONDS), "JavaFX no respondió");
        if (haError.get() != null) {
            throw new AssertionError(haError.get());
        }
        return haResultado.get();
    }
}
