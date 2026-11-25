package pe.pdflisto.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Component
@Slf4j
public class HAConfiguracion {
    private static final String HA_TEMA = "tema";
    private static final String HA_ULTIMA_CARPETA = "ultimaCarpeta";

    private final Path haArchivo;
    private final Properties haValores = new Properties();

    public HAConfiguracion() {
        this(HACarpetaDatos.haRuta().resolve("config.properties"));
    }

    HAConfiguracion(Path haArchivo) {
        this.haArchivo = haArchivo;
        if (Files.isRegularFile(haArchivo)) {
            try (Reader haR = Files.newBufferedReader(haArchivo, StandardCharsets.UTF_8)) {
                haValores.load(haR);
            } catch (IOException haE) {
                log.warn("No se pudo leer la configuracion; se usan valores por defecto", haE);
            }
        }
    }

    public boolean haTemaOscuro() {
        return "oscuro".equals(haValores.getProperty(HA_TEMA));
    }

    public void setTemaOscuro(boolean haOscuro) {
        haGuardar(HA_TEMA, haOscuro ? "oscuro" : "claro");
    }

    public File haUltimaCarpeta() {
        String haRuta = haValores.getProperty(HA_ULTIMA_CARPETA);
        File haCarpeta = haRuta == null ? null : new File(haRuta);
        return haCarpeta != null && haCarpeta.isDirectory() ? haCarpeta : new File(System.getProperty("user.home"));
    }

    public void setUltimaCarpeta(File haCarpeta) {
        if (haCarpeta != null && haCarpeta.isDirectory()) {
            haGuardar(HA_ULTIMA_CARPETA, haCarpeta.getAbsolutePath());
        }
    }

    public boolean haMostrarBienvenida() {
        return !"no".equals(haValores.getProperty("bienvenida"));
    }

    public void setMostrarBienvenida(boolean haMostrar) {
        haGuardar("bienvenida", haMostrar ? "si" : "no");
    }

    public String haPatronNombre() {
        return haValores.getProperty("patronNombre", "{nombre} (editado)");
    }

    public void setPatronNombre(String haPatron) {
        haGuardar("patronNombre", haPatron == null ? "" : haPatron);
    }

    public boolean haRecetaRechazada(String haCombinacion) {
        return java.util.Arrays.asList(haValores.getProperty("recetasRechazadas", "").split(",")).contains(haHuella(haCombinacion));
    }

    public void haRechazarReceta(String haCombinacion) {
        String haActual = haValores.getProperty("recetasRechazadas", "");
        haGuardar("recetasRechazadas", (haActual.isBlank() ? "" : haActual + ",") + haHuella(haCombinacion));
    }

    private static String haHuella(String haTexto) {
        return Integer.toHexString(haTexto == null ? 0 : haTexto.hashCode());
    }

    private synchronized void haGuardar(String haClave, String haValor) {
        haValores.setProperty(haClave, haValor);
        try (Writer haW = Files.newBufferedWriter(haArchivo, StandardCharsets.UTF_8)) {
            haValores.store(haW, "Configuracion de PDF Listo");
        } catch (IOException haE) {
            log.warn("No se pudo guardar la configuracion", haE);
        }
    }
}
