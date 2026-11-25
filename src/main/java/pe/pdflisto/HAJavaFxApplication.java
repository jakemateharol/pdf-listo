package pe.pdflisto;

import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.config.HAConfiguracion;
import pe.pdflisto.controller.HAMainController;
import pe.pdflisto.sistema.HAInstanciaUnica;
import pe.pdflisto.ui.HAExportador;
import pe.pdflisto.ui.HANotificador;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.util.List;
import java.util.Objects;

@Slf4j
public class HAJavaFxApplication extends Application {
    private ConfigurableApplicationContext haContext;
    private Throwable haErrorInicio;

    @Override
    public void init() {
        try {
            HACarpetaDatos.haRuta();
            haContext = new SpringApplicationBuilder(HAPdfListoApplication.class)
                    .web(WebApplicationType.NONE)
                    .run();
        } catch (Throwable haE) {
            haErrorInicio = haE;
        }
    }

    @Override
    public void start(Stage haStage) throws Exception {
        if (haErrorInicio != null) {
            haMostrarErrorInicio(haErrorInicio);
            return;
        }
        HAConfiguracion haConfiguracion = haContext.getBean(HAConfiguracion.class);
        Application.setUserAgentStylesheet(haConfiguracion.haTemaOscuro()
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        haContext.getBean(HAExportador.class).setHostServices(getHostServices());

        FXMLLoader haLoader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));
        haLoader.setControllerFactory(haContext::getBean);
        Scene haScene = new Scene(haLoader.load());
        haScene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/css/app.css")).toExternalForm());

        haInstalarRedDeSeguridad(haContext.getBean(HANotificador.class));

        haStage.setTitle("PDF Listo");
        for (int haTamano : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            var haIcono = getClass().getResource("/icono/pdflisto-" + haTamano + ".png");
            if (haIcono != null) {
                haStage.getIcons().add(new javafx.scene.image.Image(haIcono.toExternalForm()));
            }
        }
        haStage.setScene(haScene);
        haStage.setMinWidth(1200);
        haStage.setMinHeight(640);
        haStage.setFullScreenExitHint("Pulsa Esc o F11 para salir de pantalla completa");

        HAMainController haControlador = haLoader.getController();
        haControlador.haAlMostrarVentana(haStage);
        haStage.show();

        List<File> haArchivos = getParameters().getRaw().stream()
                .map(File::new).filter(File::isFile).toList();
        if (!haArchivos.isEmpty()) {
            Platform.runLater(() -> haControlador.haAbrirArchivos(haArchivos));
        }

        HAInstanciaUnica haInstancia = HAInstanciaUnica.haActual();
        if (haInstancia != null) {
            haInstancia.haAlRecibir(haRecibidos -> Platform.runLater(() -> {
                haTraerAlFrente(haStage);
                List<File> haValidos = haRecibidos.stream().filter(File::isFile).toList();
                if (!haValidos.isEmpty()) {
                    haControlador.haAbrirArchivos(haValidos);
                }
            }));
        }
    }

    private static void haTraerAlFrente(Stage haStage) {
        if (haStage.isIconified()) {
            haStage.setIconified(false);
        }
        haStage.show();
        haStage.toFront();
        haStage.setAlwaysOnTop(true);
        haStage.setAlwaysOnTop(false);
        haStage.requestFocus();
    }

    private void haInstalarRedDeSeguridad(HANotificador haNotificador) {
        Thread.UncaughtExceptionHandler haManejador = (haHilo, haError) -> {
            log.error("Error no controlado en el hilo {}", haHilo.getName(), haError);
            Platform.runLater(() -> haNotificador.haError(
                    "Ocurrió un error inesperado. Quedó anotado en el registro y puedes seguir trabajando."));
        };
        Thread.setDefaultUncaughtExceptionHandler(haManejador);
        Thread.currentThread().setUncaughtExceptionHandler(haManejador);
    }

    private void haMostrarErrorInicio(Throwable haE) {
        log.error("No se pudo iniciar PDF Listo", haE);
        Alert haAlerta = new Alert(Alert.AlertType.ERROR);
        haAlerta.setTitle("PDF Listo");
        haAlerta.setHeaderText("No se pudo iniciar PDF Listo");
        haAlerta.setContentText("Es posible que la aplicación ya esté abierta en otra ventana.\n\nDetalle: "
                + haRaiz(haE).getMessage());
        haAlerta.showAndWait();
        Platform.exit();
    }

    private static Throwable haRaiz(Throwable haE) {
        Throwable haT = haE;
        while (haT.getCause() != null && haT.getCause() != haT) {
            haT = haT.getCause();
        }
        return haT;
    }

    @Override
    public void stop() {
        if (haContext != null) {
            haContext.getBean(HAMainController.class).haCerrarTodo();
            haContext.close();
        }
        HAInstanciaUnica haInstancia = HAInstanciaUnica.haActual();
        if (haInstancia != null) {
            haInstancia.close();
        }
        Platform.exit();
    }
}
