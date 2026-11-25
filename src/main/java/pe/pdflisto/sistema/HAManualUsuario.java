package pe.pdflisto.sistema;

import pe.pdflisto.config.HACarpetaDatos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HAManualUsuario {
    static final String HA_RECURSO = "/manual/";
    static final String HA_PRINCIPAL = "manual-usuario.html";
    private static final Pattern HA_IMAGEN = Pattern.compile("src=\"([^\":]+\\.(?:png|jpg|svg))\"");

    private HAManualUsuario() {
    }

    public static Optional<Path> haPreparar() throws IOException {
        return haPreparar(HACarpetaDatos.haRuta().resolve("manual"));
    }

    static Optional<Path> haPreparar(Path haDestino) throws IOException {
        byte[] haHtml = haLeer(HA_PRINCIPAL);
        if (haHtml == null) {
            return Optional.empty();
        }
        Files.createDirectories(haDestino);
        for (String haImagen : haImagenes(new String(haHtml, StandardCharsets.UTF_8))) {
            byte[] haDatos = haLeer(haImagen);
            if (haDatos != null) {
                haCopiar(haDatos, haDestino.resolve(haImagen));
            }
        }
        Path haPrincipal = haDestino.resolve(HA_PRINCIPAL);
        haCopiar(haHtml, haPrincipal);
        return Optional.of(haPrincipal);
    }

    static Set<String> haImagenes(String haHtml) {
        Set<String> haRutas = new LinkedHashSet<>();
        Matcher haM = HA_IMAGEN.matcher(haHtml);
        while (haM.find()) {
            String haRuta = haM.group(1);
            if (!haRuta.contains("..") && !haRuta.startsWith("/")) {
                haRutas.add(haRuta);
            }
        }
        return haRutas;
    }

    private static byte[] haLeer(String haRuta) throws IOException {
        try (InputStream haIn = HAManualUsuario.class.getResourceAsStream(HA_RECURSO + haRuta)) {
            return haIn == null ? null : haIn.readAllBytes();
        }
    }

    private static void haCopiar(byte[] haDatos, Path haArchivo) throws IOException {
        Files.createDirectories(haArchivo.getParent());
        if (Files.isRegularFile(haArchivo) && Files.size(haArchivo) == haDatos.length
                && java.util.Arrays.equals(Files.readAllBytes(haArchivo), haDatos)) {
            return;
        }
        Path haTemporal = haArchivo.resolveSibling(haArchivo.getFileName() + ".tmp");
        Files.write(haTemporal, haDatos);
        Files.move(haTemporal, haArchivo, StandardCopyOption.REPLACE_EXISTING);
    }
}
