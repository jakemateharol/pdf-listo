package pe.pdflisto.herramientas;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import pe.pdflisto.HAPdfListoApplication;
import pe.pdflisto.controller.HAMainController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class HACapturasManual extends Application {
    private static Path haSalida;
    private static Path haEjemplos;

    private ConfigurableApplicationContext haContexto;
    private Scene haEscena;
    private HAMainController haControlador;

    public static void main(String[] haArgs) throws Exception {
        haSalida = Path.of(haArgs[0]);
        Path haDatos = Path.of(haArgs[1]);
        haEjemplos = Path.of(haArgs[2]);
        Files.createDirectories(haSalida);
        Files.createDirectories(haDatos.resolve("logs"));
        System.setProperty("app.datos", haDatos.toAbsolutePath().toString().replace('\\', '/'));
        launch(HACapturasManual.class, haArgs);
    }

    @Override
    public void init() {
        haContexto = new SpringApplicationBuilder(HAPdfListoApplication.class).web(WebApplicationType.NONE).run();
    }

    @Override
    public void start(Stage haStage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        FXMLLoader haLoader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        haLoader.setControllerFactory(haContexto::getBean);
        haEscena = new Scene(haLoader.load(), 1680, 960);
        haEscena.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());
        haStage.setScene(haEscena);
        haControlador = haLoader.getController();
        haControlador.haAlMostrarVentana(haStage);
        Thread haGuion = new Thread(Boolean.getBoolean("capturas.barra") ? this::haGuionBarra
                : Boolean.getBoolean("capturas.entrega") ? this::haGuionEntrega : this::haGuion, "capturas");
        haGuion.setDaemon(true);
        haGuion.start();
    }

    private void haGuion() {
        try {
            haEsperar(2500);
            haFoto("02-inicio");
            haFx(() -> haContexto.getBean(pe.pdflisto.controller.HABienvenida.class).haMostrar());
            haEsperar(800);
            haFoto("01-bienvenida");
            haPulsar("Saltar");
            haEsperar(800);

            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-12-paginas.pdf").toFile())));
            haEsperar(4000);
            haFoto("03-documento-abierto");

            haFx(() -> haBuscar(TextArea.class).ifPresent(haT -> haT.setText("comprime a menos de 2 MB y numera")));
            haPulsar("Ver plan");
            haEsperar(1500);
            haFoto("04-plan");
            haPulsar("Aprobar y ejecutar");
            haEsperar(7000);
            haFoto("05-antes-despues");
            haPulsar("Deshacer");
            haEsperar(2500);
            haFx(() -> haBuscar(TextArea.class).ifPresent(haT -> haT.setText("comprime a menos de 2 MB y numera")));
            haPulsar("Ver plan");
            haEsperar(1200);
            haPulsar("Aprobar y ejecutar");
            haEsperar(7000);
            haQuitarAvisos();
            haDesplazarPanelDerecho(0.55);
            haFoto("06-verificacion");

            haPestanaDerecha("Anotar");
            haEsperar(800);
            haFoto("07-anotar");
            haPestanaDerecha("Entrega");
            haEsperar(800);
            haFoto("08-entrega");

            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-datos-sensibles.pdf").toFile())));
            haEsperar(4000);
            haPestanaDerecha("Tachado");
            haEsperar(500);
            haPulsar("Buscar datos sensibles");
            haEsperar(5000);
            haQuitarAvisos();
            haFoto("09-tachado");

            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-formulario.pdf").toFile())));
            haEsperar(4000);
            haPestanaDerecha("Formulario");
            haEsperar(1200);
            haFoto("10-formulario");

            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-escaneado.pdf").toFile())));
            haEsperar(5000);
            haQuitarAvisos();
            haMenu("haMenuHerramientas", "Reconocer texto (OCR)…");
            haEsperar(1500);
            haFoto("11-ocr");
            haPulsar("Cancelar");
            haEsperar(800);

            haMenu("haMenuAyuda", "Comprobar componentes…");
            haEsperar(6000);
            haFoto("12-componentes");
            haPulsar("Cerrar");
            haEsperar(800);

            haFx(() -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet()));
            haPestanaDerecha("Pedido");
            haEsperar(1500);
            haFoto("13-modo-oscuro");
            System.out.println("Capturas listas en " + haSalida.toAbsolutePath());
        } catch (Throwable haE) {
            haE.printStackTrace();
        } finally {
            Platform.exit();
            haContexto.close();
            System.exit(0);
        }
    }

    private void haGuionBarra() {
        try {
            haEsperar(2000);
            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-12-paginas.pdf").toFile(),
                    haEjemplos.resolve("demo-datos-sensibles.pdf").toFile())));
            haEsperar(4000);
            haQuitarAvisos();
            for (int haAncho : new int[] {1680, 1366, 1280, 1200}) {
                haFx(() -> {
                    Parent haRaiz = haEscena.getRoot();
                    haEscena.setRoot(new javafx.scene.layout.Pane());
                    List<String> haEstilos = new ArrayList<>(haEscena.getStylesheets());
                    haEscena = new Scene(haRaiz, haAncho - 16, 700);
                    haEscena.getStylesheets().addAll(haEstilos);
                    return null;
                });
                haEsperar(600);
                haFoto("barra-" + haAncho);
            }
            haFx(() -> {
                Node haN = haEscena.lookup("#haIndicadorTrabajo");
                haN.setVisible(true);
                haN.setManaged(true);
                return null;
            });
            haEsperar(600);
            haFoto("barra-1200-trabajando");
            haFx(() -> {
                Node haN = haEscena.lookup("#haIndicadorTrabajo");
                haN.setVisible(false);
                haN.setManaged(false);
                Parent haRaiz = haEscena.getRoot();
                List<String> haEstilos = new ArrayList<>(haEscena.getStylesheets());
                haEscena.setRoot(new javafx.scene.layout.Pane());
                haEscena = new Scene(haRaiz, 1904, 980);
                haEscena.getStylesheets().addAll(haEstilos);
                return null;
            });
            haFx(() -> haBuscar(TextArea.class).ifPresent(haT -> haT.setText("comprime y numera")));
            haPulsar("Ver plan");
            haEsperar(1500);
            haPulsar("Aprobar y ejecutar");
            haEsperar(7000);
            haQuitarAvisos();
            haDesplazarPanelDerecho(0.6);
            haFoto("ventana-1920-comprime-y-numera");
        } catch (Throwable haE) {
            haE.printStackTrace();
        } finally {
            Platform.exit();
            haContexto.close();
            System.exit(0);
        }
    }

    private void haGuionEntrega() {
        try {
            haEsperar(2500);
            haFoto("e00-arranque");
            haPulsar("Saltar");
            haEsperar(800);
            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-12-paginas.pdf").toFile())));
            haEsperar(4000);
            haQuitarAvisos();
            String[][] haFormularios = {
                    {"haMenuHerramientas", "Unir PDF…", "e01-unir"},
                    {"haMenuHerramientas", "Dividir…", "e02-dividir"},
                    {"haMenuHerramientas", "Comprimir…", "e03-comprimir"},
                    {"haMenuHerramientas", "Numerar páginas…", "e04-numerar"},
                    {"haMenuHerramientas", "Marca de agua…", "e05-marca-agua"},
                    {"haMenuHerramientas", "Leer facturas (QR) a Excel…", "e06-facturas-formulario"},
                    {"haMenuConvertir", "PDF a imágenes…", "e07-convertir"}};
            for (String[] haF : haFormularios) {
                haMenu(haF[0], haF[1]);
                haEsperar(1200);
                haFoto(haF[2]);
                haPulsar("Cancelar");
                haEsperar(700);
            }
            haPestanaDerecha("Entrega");
            haEsperar(1000);
            haFoto("e08-entrega");
            haPestanaDerecha("Historial");
            haEsperar(1000);
            haFoto("e09-historial");
            haPestanaDerecha("Anotar");
            haEsperar(1000);
            haFoto("e10-anotar");

            haFx(() -> haControlador.haAbrirArchivos(List.of(haEjemplos.resolve("demo-datos-sensibles.pdf").toFile())));
            haEsperar(4000);
            haQuitarAvisos();
            haPestanaDerecha("Pedido");
            haEsperar(800);
            haPulsar("Listo para enviar");
            haEsperar(5000);
            haQuitarAvisos();
            haFoto("e12-listo-para-enviar");
            haPulsar("Cancelar");
            haEsperar(800);

            File[] haFacturas = haEjemplos.resolve("facturas").toFile().listFiles((haD, haN) -> haN.endsWith(".pdf"));
            java.util.Arrays.sort(haFacturas);
            var haLista = haContexto.getBean(pe.pdflisto.facturas.HALectorFacturas.class)
                    .haLeer(List.of(haFacturas), pe.pdflisto.operaciones.HAControl.haNinguno());
            javafx.stage.Stage haVentana = haFx(() -> {
                try {
                    Class<?> haC = Class.forName("pe.pdflisto.controller.HAVentanaFacturas");
                    var haM = java.util.Arrays.stream(haC.getDeclaredMethods()).filter(haX -> haX.getName().equals("haCrear"))
                            .findFirst().orElseThrow();
                    haM.setAccessible(true);
                    return (javafx.stage.Stage) haM.invoke(null, null, haLista,
                            haContexto.getBean(pe.pdflisto.facturas.HAExportadorFacturas.class),
                            haContexto.getBean(pe.pdflisto.ui.HAExportador.class),
                            haContexto.getBean(pe.pdflisto.facturas.HAVerificadorExcelFacturas.class));
                } catch (Exception haE) {
                    throw new IllegalStateException(haE);
                }
            });
            Scene haPrincipal = haEscena;
            haEscena = haVentana.getScene();
            haEsperar(1500);
            haFoto("e11-facturas");
            haEscena = haPrincipal;
            System.out.println("Capturas de entrega listas en " + haSalida.toAbsolutePath());
        } catch (Throwable haE) {
            haE.printStackTrace();
        } finally {
            Platform.exit();
            haContexto.close();
            System.exit(0);
        }
    }

    private void haFoto(String haNombre) throws Exception {
        WritableImage haImg = haFx(() -> {
            haEscena.getRoot().applyCss();
            haEscena.getRoot().layout();
            return haEscena.snapshot(null);
        });
        int haW = (int) haImg.getWidth();
        int haH = (int) haImg.getHeight();
        BufferedImage haBi = new BufferedImage(haW, haH, BufferedImage.TYPE_INT_RGB);
        PixelReader haPr = haImg.getPixelReader();
        for (int haY = 0; haY < haH; haY++) {
            for (int haX = 0; haX < haW; haX++) {
                haBi.setRGB(haX, haY, haPr.getArgb(haX, haY));
            }
        }
        File haF = haSalida.resolve(haNombre + ".png").toFile();
        ImageIO.write(haBi, "png", haF);
        System.out.println("captura: " + haF.getName());
    }

    private void haQuitarAvisos() throws Exception {
        haFx(() -> {
            ((javafx.scene.layout.Pane) haEscena.lookup("#haZonaNotificaciones")).getChildren().clear();
            return null;
        });
    }

    private void haPulsar(String haTexto) throws Exception {
        boolean haHecho = haFx(() -> {
            Optional<ButtonBase> haB = haTodos(haEscena.getRoot()).stream()
                    .filter(haN -> haN instanceof ButtonBase haBb && haTexto.equals(haBb.getText()) && !haBb.isDisabled())
                    .map(haN -> (ButtonBase) haN).reduce((haA, haC) -> haC);
            haB.ifPresent(ButtonBase::fire);
            return haB.isPresent();
        });
        if (!haHecho) {
            System.out.println("AVISO: no se encontro el boton «" + haTexto + "»");
        }
    }

    private void haPestanaDerecha(String haTexto) throws Exception {
        haFx(() -> {
            TabPane haPanel = (TabPane) haEscena.lookup("#haPanelDerecho");
            for (Tab haT : haPanel.getTabs()) {
                if (haTexto.equals(haT.getText())) {
                    haPanel.getSelectionModel().select(haT);
                }
            }
            return null;
        });
    }

    private void haDesplazarPanelDerecho(double haV) throws Exception {
        haFx(() -> {
            TabPane haPanel = (TabPane) haEscena.lookup("#haPanelDerecho");
            haTodos(haPanel.getSelectionModel().getSelectedItem().getContent()).stream()
                    .filter(haN -> haN instanceof ScrollPane).map(haN -> (ScrollPane) haN).findFirst()
                    .ifPresent(haS -> haS.setVvalue(haV));
            return null;
        });
    }

    private void haMenu(String haId, String haTexto) throws Exception {
        haFx(() -> {
            MenuButton haM = (MenuButton) haEscena.lookup("#" + haId);
            if (haM.getOnShowing() != null) {
                haM.getOnShowing().handle(null);
            }
            for (MenuItem haI : haM.getItems()) {
                if (haTexto.equals(haI.getText()) && !haI.isDisable()) {
                    haI.fire();
                }
            }
            return null;
        });
    }

    private <T extends Node> Optional<T> haBuscar(Class<T> haTipo) {
        return haTodos(haEscena.getRoot()).stream().filter(haTipo::isInstance).map(haTipo::cast).findFirst();
    }

    private static List<Node> haTodos(Node haRaiz) {
        List<Node> haLista = new ArrayList<>();
        haRecorrer(haRaiz, haLista);
        return haLista;
    }

    private static void haRecorrer(Node haN, List<Node> haLista) {
        if (haN == null || haLista.contains(haN)) {
            return;
        }
        haLista.add(haN);
        if (haN instanceof TabPane haTp) {
            haTp.getTabs().forEach(haT -> haRecorrer(haT.getContent(), haLista));
        }
        if (haN instanceof ScrollPane haSp) {
            haRecorrer(haSp.getContent(), haLista);
        }
        if (haN instanceof Parent haP) {
            haP.getChildrenUnmodifiable().forEach(haC -> haRecorrer(haC, haLista));
        }
    }

    private static void haFx(Runnable haR) throws Exception {
        haFx(() -> {
            haR.run();
            return null;
        });
    }

    private static <T> T haFx(Supplier<T> haS) throws Exception {
        CompletableFuture<T> haF = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                haF.complete(haS.get());
            } catch (Throwable haE) {
                haF.completeExceptionally(haE);
            }
        });
        return haF.get(30, TimeUnit.SECONDS);
    }

    private static void haEsperar(long haMs) throws InterruptedException {
        Thread.sleep(haMs);
    }
}
