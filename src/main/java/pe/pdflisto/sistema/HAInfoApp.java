package pe.pdflisto.sistema;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class HAInfoApp {
    private final String haVersion;

    public HAInfoApp(@Value("${app.version:desarrollo}") String haVersion) {
        this.haVersion = haVersion.startsWith("@") ? "desarrollo" : haVersion;
    }

    public String haVersion() {
        return haVersion;
    }

    public String haLicencias() {
        try (InputStream haIn = HAInfoApp.class.getResourceAsStream("/licencias/TERCEROS.txt")) {
            return haIn == null ? "No se encontró la lista de licencias." : new String(haIn.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException haE) {
            return "No se pudo leer la lista de licencias: " + haE.getMessage();
        }
    }
}
