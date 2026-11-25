package pe.pdflisto.anotaciones;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.pdflisto.config.HACarpetaDatos;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.pdf.HAAreaTexto;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

@Service
@Slf4j
public class HABibliotecaFirmas {
    public record HAPosicion(double haX, double haY, double haAncho, double haAlto) {
        public HAAreaTexto haEn(double haAnchoPagina, double haAltoPagina) {
            return new HAAreaTexto(haX * haAnchoPagina, haY * haAltoPagina, haAncho * haAnchoPagina, haAlto * haAltoPagina);
        }
    }

    public record HAFirma(String haNombre, File haArchivo, HAPosicion haPosicion) {
        @Override
        public String toString() {
            return haNombre;
        }
    }

    private static final String HA_POSICIONES = "posiciones.properties";
    private final Path haCarpeta;

    public HABibliotecaFirmas() {
        this(HACarpetaDatos.haRuta().resolve("firmas"));
    }

    HABibliotecaFirmas(Path haCarpeta) {
        this.haCarpeta = haCarpeta;
    }

    public List<HAFirma> haListar() {
        List<HAFirma> haFirmas = new ArrayList<>();
        File[] haArchivos = haCarpeta.toFile().listFiles((haD, haN) -> haN.toLowerCase().endsWith(".png"));
        if (haArchivos == null) {
            return haFirmas;
        }
        Properties haPos = haPosiciones();
        for (File haF : haArchivos) {
            String haNombre = haF.getName().replaceFirst("(?i)\\.png$", "");
            haFirmas.add(new HAFirma(haNombre, haF, haLeer(haPos.getProperty(haNombre))));
        }
        haFirmas.sort((haA, haB) -> haA.haNombre().compareToIgnoreCase(haB.haNombre()));
        return haFirmas;
    }

    public HAFirma haGuardar(String haNombre, BufferedImage haImagen) {
        if (haNombre == null || haNombre.isBlank()) {
            throw new HAPdfListoException("Ponle un nombre a la firma (por ejemplo, tu nombre).");
        }
        BufferedImage haRecortada = haRecortar(haImagen);
        if (haRecortada == null) {
            throw new HAPdfListoException("La firma está vacía: dibuja, escribe o elige una imagen primero.");
        }
        try {
            Files.createDirectories(haCarpeta);
            File haDestino = HAArchivos.haLibre(haCarpeta.toFile(), haNombre.trim(), ".png");
            ImageIO.write(haRecortada, "png", haDestino);
            return new HAFirma(haDestino.getName().replaceFirst("(?i)\\.png$", ""), haDestino, null);
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo guardar la firma: " + haE.getMessage(), haE);
        }
    }

    public void haEliminar(HAFirma haFirma) {
        try {
            Files.deleteIfExists(haFirma.haArchivo().toPath());
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo borrar la firma: " + haE.getMessage(), haE);
        }
    }

    public void haRecordarPosicion(String haNombre, HAAreaTexto haArea, double haAnchoPagina, double haAltoPagina) {
        Properties haPos = haPosiciones();
        haPos.setProperty(haNombre, String.format(java.util.Locale.ROOT, "%.5f;%.5f;%.5f;%.5f",
                haArea.haX() / haAnchoPagina, haArea.haY() / haAltoPagina, haArea.haAncho() / haAnchoPagina, haArea.haAlto() / haAltoPagina));
        try {
            Files.createDirectories(haCarpeta);
            try (Writer haW = Files.newBufferedWriter(haCarpeta.resolve(HA_POSICIONES), StandardCharsets.UTF_8)) {
                haPos.store(haW, "Ultima posicion de cada firma (fraccion de la pagina)");
            }
        } catch (IOException haE) {
            log.warn("No se pudo guardar la posición de la firma", haE);
        }
    }

    private Properties haPosiciones() {
        Properties haP = new Properties();
        Path haArchivo = haCarpeta.resolve(HA_POSICIONES);
        if (Files.isRegularFile(haArchivo)) {
            try (Reader haR = Files.newBufferedReader(haArchivo, StandardCharsets.UTF_8)) {
                haP.load(haR);
            } catch (IOException haE) {
                log.warn("No se pudieron leer las posiciones de firmas", haE);
            }
        }
        return haP;
    }

    private static HAPosicion haLeer(String haTexto) {
        if (haTexto == null) {
            return null;
        }
        try {
            String[] haV = haTexto.split(";");
            return new HAPosicion(Double.parseDouble(haV[0]), Double.parseDouble(haV[1]),
                    Double.parseDouble(haV[2]), Double.parseDouble(haV[3]));
        } catch (RuntimeException haE) {
            return null;
        }
    }

    public static BufferedImage haRecortar(BufferedImage haImg) {
        int haMinX = haImg.getWidth(), haMinY = haImg.getHeight(), haMaxX = -1, haMaxY = -1;
        for (int haY = 0; haY < haImg.getHeight(); haY++) {
            for (int haX = 0; haX < haImg.getWidth(); haX++) {
                if ((haImg.getRGB(haX, haY) >>> 24) > 16) {
                    haMinX = Math.min(haMinX, haX);
                    haMinY = Math.min(haMinY, haY);
                    haMaxX = Math.max(haMaxX, haX);
                    haMaxY = Math.max(haMaxY, haY);
                }
            }
        }
        if (haMaxX < 0) {
            return null;
        }
        int haM = 4;
        haMinX = Math.max(0, haMinX - haM);
        haMinY = Math.max(0, haMinY - haM);
        haMaxX = Math.min(haImg.getWidth() - 1, haMaxX + haM);
        haMaxY = Math.min(haImg.getHeight() - 1, haMaxY + haM);
        BufferedImage haSalida = new BufferedImage(haMaxX - haMinX + 1, haMaxY - haMinY + 1, BufferedImage.TYPE_INT_ARGB);
        haSalida.getGraphics().drawImage(haImg.getSubimage(haMinX, haMinY, haSalida.getWidth(), haSalida.getHeight()), 0, 0, null);
        return haSalida;
    }

    public static BufferedImage haQuitarFondoBlanco(BufferedImage haImg) {
        BufferedImage haSalida = new BufferedImage(haImg.getWidth(), haImg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int haY = 0; haY < haImg.getHeight(); haY++) {
            for (int haX = 0; haX < haImg.getWidth(); haX++) {
                int haRgb = haImg.getRGB(haX, haY);
                int haR = (haRgb >> 16) & 0xFF;
                int haG = (haRgb >> 8) & 0xFF;
                int haB = haRgb & 0xFF;
                int haClaridad = (haR + haG + haB) / 3;
                int haAlfa = haClaridad > 225 ? 0 : haClaridad > 170 ? (225 - haClaridad) * 255 / 55 : 255;
                haSalida.setRGB(haX, haY, (Math.min(haAlfa, (haRgb >>> 24)) << 24) | (haRgb & 0xFFFFFF));
            }
        }
        return haSalida;
    }
}
