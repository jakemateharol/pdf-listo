package pe.pdflisto.ui;

import javafx.application.HostServices;
import javafx.concurrent.Task;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
@RequiredArgsConstructor
@Slf4j
public class HAExportador {
    @FunctionalInterface
    public interface HAGenerador {
        void haGenerar(File haDestino) throws Exception;
    }

    public enum HAFormato {
        PDF("Documento PDF", "*.pdf", ".pdf"),
        EXCEL("Libro de Excel", "*.xlsx", ".xlsx");

        final String haDescripcion;
        final String haPatron;
        final String haExtension;

        HAFormato(String haDescripcion, String haPatron, String haExtension) {
            this.haDescripcion = haDescripcion;
            this.haPatron = haPatron;
            this.haExtension = haExtension;
        }
    }

    private final HANotificador haNotificador;
    private HostServices haHostServices;
    private File haUltimaCarpeta = new File(System.getProperty("user.home"));

    public void setHostServices(HostServices haHostServices) {
        this.haHostServices = haHostServices;
    }

    public void haGuardar(Window haVentana, String haTitulo, String haNombreSugerido, HAFormato haFormato, HAGenerador haGenerador) {
        File haDestino = haElegirDestino(haVentana, haTitulo, haNombreSugerido, haFormato);
        if (haDestino == null) {
            return;
        }
        haEjecutar(haDestino, haGenerador, () -> {
            haNotificador.haExito("Archivo guardado: " + haDestino.getName());
            haAbrir(haDestino);
        });
    }

    public void haGuardarVerificado(Window haVentana, String haTitulo, String haNombreSugerido, HAFormato haFormato, HAGenerador haGenerador,
                                  java.util.function.Function<File, pe.pdflisto.verificacion.HAInformeVerificacion> haVerificar,
                                  java.util.function.Consumer<pe.pdflisto.verificacion.HAInformeVerificacion> haAlVerificar) {
        File haDestino = haElegirDestino(haVentana, haTitulo, haNombreSugerido, haFormato);
        if (haDestino == null) {
            return;
        }
        Task<pe.pdflisto.verificacion.HAInformeVerificacion> haTarea = new Task<>() {
            @Override
            protected pe.pdflisto.verificacion.HAInformeVerificacion call() throws Exception {
                haGenerador.haGenerar(haDestino);
                return haVerificar.apply(haDestino);
            }
        };
        haTarea.setOnSucceeded(haE -> {
            pe.pdflisto.verificacion.HAInformeVerificacion haInforme = haTarea.getValue();
            if (haInforme.haCumple()) {
                haNotificador.haExito("Archivo guardado y verificado: " + haDestino.getName());
            } else {
                haNotificador.haAdvertencia("Archivo guardado, pero la verificación encontró algo para revisar: " + haDestino.getName());
            }
            haAlVerificar.accept(haInforme);
            haAbrir(haDestino);
        });
        haTarea.setOnFailed(haE -> {
            Throwable haCausa = haTarea.getException();
            log.error("No se pudo generar {}", haDestino, haCausa);
            haNotificador.haError("No se pudo generar el archivo: " + haCausa.getMessage());
        });
        Thread haHilo = new Thread(haTarea, "exportador");
        haHilo.setDaemon(true);
        haHilo.start();
    }

    private File haElegirDestino(Window haVentana, String haTitulo, String haNombreSugerido, HAFormato haFormato) {
        FileChooser haSelector = new FileChooser();
        haSelector.setTitle(haTitulo);
        haSelector.setInitialFileName(haNombreSugerido + haFormato.haExtension);
        haSelector.getExtensionFilters().add(new FileChooser.ExtensionFilter(haFormato.haDescripcion, haFormato.haPatron));
        if (haUltimaCarpeta.isDirectory()) {
            haSelector.setInitialDirectory(haUltimaCarpeta);
        }
        File haElegido = haSelector.showSaveDialog(haVentana);
        if (haElegido == null) {
            return null;
        }
        File haDestino = haElegido.getName().toLowerCase().endsWith(haFormato.haExtension)
                ? haElegido
                : new File(haElegido.getParentFile(), haElegido.getName() + haFormato.haExtension);
        haUltimaCarpeta = haDestino.getParentFile();
        return haDestino;
    }

    public void haAbrirTemporal(String haNombre, HAFormato haFormato, HAGenerador haGenerador) {
        File haDestino;
        try {
            File haCarpeta = Files.createTempDirectory("pdflisto").toFile();
            haCarpeta.deleteOnExit();
            haDestino = new File(haCarpeta, haNombre + haFormato.haExtension);
            haDestino.deleteOnExit();
        } catch (IOException haE) {
            haNotificador.haError("No se pudo crear el archivo temporal: " + haE.getMessage());
            return;
        }
        haEjecutar(haDestino, haGenerador, () -> {
            haNotificador.haInfo("Abriendo " + haDestino.getName() + "…");
            haAbrir(haDestino);
        });
    }

    private void haEjecutar(File haDestino, HAGenerador haGenerador, Runnable haAlTerminar) {
        Task<Void> haTarea = new Task<>() {
            @Override
            protected Void call() throws Exception {
                haGenerador.haGenerar(haDestino);
                return null;
            }
        };
        haTarea.setOnSucceeded(haE -> haAlTerminar.run());
        haTarea.setOnFailed(haE -> {
            Throwable haCausa = haTarea.getException();
            log.error("No se pudo generar {}", haDestino, haCausa);
            haNotificador.haError("No se pudo generar el archivo: " + haCausa.getMessage());
        });
        Thread haHilo = new Thread(haTarea, "exportador");
        haHilo.setDaemon(true);
        haHilo.start();
    }

    public void haAbrirEnSistema(File haArchivo) {
        haAbrir(haArchivo);
    }

    private void haAbrir(File haArchivo) {
        if (haHostServices != null) {
            haHostServices.showDocument(haArchivo.toURI().toString());
        }
    }
}
