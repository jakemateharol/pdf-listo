package pe.pdflisto;

import javafx.application.Application;
import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.conversiones.HAConversorWord;
import pe.pdflisto.sistema.HAComprobadorComponentes;
import pe.pdflisto.sistema.HAEstadoComponente;
import pe.pdflisto.sistema.HAInstanciaUnica;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class HALauncher {
    public static void main(String[] haArgs) throws IOException {
        if (haArgs.length == 2 && "--comprobar".equals(haArgs[0])) {
            haComprobar(Path.of(haArgs[1]));
            return;
        }
        List<File> haArchivos = Arrays.stream(haArgs).filter(haA -> !haA.startsWith("--")).map(File::new).toList();
        if (HAInstanciaUnica.haIniciar(HACarpetaDatos.haRuta(), haArchivos, 8000) == null) {
            System.exit(0);
        }
        Application.launch(HAJavaFxApplication.class, haArgs);
    }

    private static void haComprobar(Path haSalida) throws IOException {
        List<HAEstadoComponente> haEstados = new HAComprobadorComponentes(new HAConversorWord()).haComprobar();
        StringBuilder haTexto = new StringBuilder();
        for (HAEstadoComponente haE : haEstados) {
            haTexto.append(haE.haNivel()).append(" | ").append(haE.haNombre()).append(" | ").append(haE.haDetalle()).append('\n');
        }
        Files.writeString(haSalida, haTexto, StandardCharsets.UTF_8);
        System.exit(haEstados.stream().anyMatch(haE -> haE.haNivel() == HAEstadoComponente.HANivel.FALTA) ? 1 : 0);
    }
}
