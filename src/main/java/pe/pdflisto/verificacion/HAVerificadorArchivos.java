package pe.pdflisto.verificacion;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import pe.pdflisto.conversiones.HAConversor;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class HAVerificadorArchivos {
    public HAInformeVerificacion haVerificar(String haDescripcion, List<File> haArchivos, HAEsperadoArchivos haEsperado) {
        List<HAComprobacion> haLista = new ArrayList<>();
        try {
            if (haArchivos == null || haArchivos.isEmpty()) {
                haLista.add(new HAComprobacion("Archivos creados", HAEstado.REVISAR, "No se creó ningún archivo."));
            } else {
                List<File> haFaltan = haArchivos.stream().filter(haF -> !haF.isFile() || haF.length() == 0).toList();
                haLista.add(haFaltan.isEmpty()
                        ? new HAComprobacion("Archivos creados", HAEstado.CUMPLE, haArchivos.size()
                            + (haArchivos.size() == 1 ? " archivo creado, no está vacío." : " archivos creados, ninguno está vacío."))
                        : new HAComprobacion("Archivos creados", HAEstado.REVISAR, "Faltan o están vacíos: "
                            + String.join(", ", haFaltan.stream().map(File::getName).toList()) + "."));
                if (haFaltan.isEmpty()) {
                    haLista.addAll(haComprobar(haEsperado, haArchivos));
                }
            }
        } catch (IOException | RuntimeException haE) {
            log.warn("No se pudo verificar {}", haDescripcion, haE);
            haLista.add(new HAComprobacion("Verificación", HAEstado.REVISAR, "No se pudo comprobar el resultado: " + haE.getMessage()));
        }
        return new HAInformeVerificacion(haNombreDe(haArchivos), LocalDateTime.now(), null, List.of(haDescripcion), haLista);
    }

    private List<HAComprobacion> haComprobar(HAEsperadoArchivos haE, List<File> haArchivos) throws IOException {
        if (haE instanceof HAEsperadoArchivos.HAPartes haP) {
            return haPartes(haP, haArchivos);
        } else if (haE instanceof HAEsperadoArchivos.HAImagenesAPdf haI) {
            return List.of(haImagenesAPdf(haI, haArchivos.get(0)));
        } else if (haE instanceof HAEsperadoArchivos.HATextoAPdf haT) {
            return List.of(haTextoAPdf(haT, haArchivos.get(0)));
        } else if (haE instanceof HAEsperadoArchivos.HAWordAPdf haW) {
            return haWordAPdf(haArchivos.get(0));
        } else if (haE instanceof HAEsperadoArchivos.HAPdfAImagenes haP) {
            return List.of(haPdfAImagenes(haP, haArchivos));
        } else if (haE instanceof HAEsperadoArchivos.HAPdfATexto haP) {
            return List.of(haPdfATexto(haP, haArchivos.get(0)));
        } else if (haE instanceof HAEsperadoArchivos.HAExtraerImagenes haX) {
            return List.of(haExtraerImagenes(haX, haArchivos));
        }
        throw new IllegalArgumentException("Comprobación desconocida: " + haE);
    }

    private static List<HAComprobacion> haPartes(HAEsperadoArchivos.HAPartes haP, List<File> haArchivos) throws IOException {
        List<HAComprobacion> haLista = new ArrayList<>();
        if (haArchivos.size() != haP.haPartes().size()) {
            haLista.add(new HAComprobacion("Partes", HAEstado.REVISAR, "Se crearon " + haArchivos.size() + " archivos; se esperaban "
                    + haP.haPartes().size() + "."));
            return haLista;
        }
        try (PDDocument haOrigen = Loader.loadPDF(haP.haOrigen(), haP.haClave())) {
            List<byte[]> haHuellas = new ArrayList<>();
            for (PDPage haPagina : haOrigen.getPages()) {
                haHuellas.add(HAVerificadorOperacion.haHuella(haPagina));
            }
            List<String> haMal = new ArrayList<>();
            int haTotalPaginas = 0;
            for (int haK = 0; haK < haArchivos.size(); haK++) {
                List<Integer> haEsperadas = haP.haPartes().get(haK);
                haTotalPaginas += haEsperadas.size();
                try (PDDocument haParte = Loader.loadPDF(haArchivos.get(haK))) {
                    if (haParte.getNumberOfPages() != haEsperadas.size()) {
                        haMal.add("«" + haArchivos.get(haK).getName() + "» tiene " + haParte.getNumberOfPages()
                                + " páginas en vez de " + haEsperadas.size());
                        continue;
                    }
                    for (int haI = 0; haI < haEsperadas.size(); haI++) {
                        int haO = haEsperadas.get(haI);
                        if (haO < 0 || haO >= haHuellas.size()
                                || !Arrays.equals(haHuellas.get(haO), HAVerificadorOperacion.haHuella(haParte.getPage(haI)))) {
                            haMal.add("«" + haArchivos.get(haK).getName() + "», página " + (haI + 1) + " no es la página "
                                    + (haO + 1) + " del original");
                            break;
                        }
                    }
                } catch (IOException haEx) {
                    haMal.add("«" + haArchivos.get(haK).getName() + "» no se puede abrir");
                }
            }
            String haNombre = haArchivos.size() == 1 ? "Páginas extraídas" : "Partes";
            haLista.add(haMal.isEmpty()
                    ? new HAComprobacion(haNombre, HAEstado.CUMPLE, (haArchivos.size() == 1 ? "El archivo tiene " : "Los "
                        + haArchivos.size() + " archivos suman ") + haTotalPaginas + " páginas y cada una es la que corresponde "
                        + "del original (comparando su contenido).")
                    : new HAComprobacion(haNombre, HAEstado.REVISAR, String.join("; ", haMal) + "."));
        }
        return haLista;
    }

    private static HAComprobacion haImagenesAPdf(HAEsperadoArchivos.HAImagenesAPdf haE, File haPdf) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            if (haD.getNumberOfPages() != haE.haImagenes().size()) {
                return new HAComprobacion("Imágenes", HAEstado.REVISAR, "El PDF tiene " + haD.getNumberOfPages()
                        + " páginas; se esperaba una por imagen (" + haE.haImagenes().size() + ").");
            }
            List<Integer> haMal = new ArrayList<>();
            for (int haI = 0; haI < haE.haImagenes().size(); haI++) {
                List<PDImageXObject> haEnPagina = new ArrayList<>();
                haImagenesDe(haD.getPage(haI).getResources(), new IdentityHashMap<>(), haEnPagina, 0);
                int[] haMedida = haMedidaImagen(haE.haImagenes().get(haI));
                boolean haOk = haMedida == null
                        ? !haEnPagina.isEmpty()
                        : haEnPagina.stream().anyMatch(haImg -> haImg.getWidth() == haMedida[0] && haImg.getHeight() == haMedida[1]);
                if (!haOk) {
                    haMal.add(haI + 1);
                }
            }
            return haMal.isEmpty()
                    ? new HAComprobacion("Imágenes", HAEstado.CUMPLE, haE.haImagenes().size() + " páginas, cada una con su imagen completa "
                        + "(mismo tamaño en píxeles que el archivo original) y en el orden elegido.")
                    : new HAComprobacion("Imágenes", HAEstado.REVISAR, "No está la imagen esperada en "
                        + (haMal.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haMal) + ".");
        }
    }

    private static HAComprobacion haTextoAPdf(HAEsperadoArchivos.HATextoAPdf haE, File haPdf) throws IOException {
        String haEsperado = HAConversor.haLeerTexto(haE.haTxt()).replaceAll("\\s", "");
        String haObtenido;
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            haObtenido = new PDFTextStripper().getText(haD).replaceAll("\\s", "");
        }
        if (haEsperado.length() != haObtenido.length()) {
            return new HAComprobacion("Texto", HAEstado.REVISAR, "El PDF tiene " + haObtenido.length() + " letras; el archivo de "
                    + "texto tiene " + haEsperado.length() + ". Puede faltar o sobrar texto.");
        }
        int haReemplazadas = 0;
        for (int haI = 0; haI < haEsperado.length(); haI++) {
            char haA = haEsperado.charAt(haI);
            char haB = haObtenido.charAt(haI);
            if (haA != haB) {
                if (haB != '?') {
                    return new HAComprobacion("Texto", HAEstado.REVISAR, "El texto del PDF no coincide con el archivo (letra "
                            + (haI + 1) + ": «" + haA + "» quedó como «" + haB + "»).");
                }
                haReemplazadas++;
            }
        }
        return new HAComprobacion("Texto", HAEstado.CUMPLE, "Todo el texto del archivo está en el PDF (" + haEsperado.length()
                + " letras, comparadas una por una)" + (haReemplazadas == 0 ? "." : "; " + haReemplazadas
                + " símbolos que la fuente no tiene quedaron como «?»."));
    }

    private static List<HAComprobacion> haWordAPdf(File haPdf) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haPdf)) {
            boolean haConTexto = new PDFTextStripper().getText(haD).replaceAll("\\s", "").length() > 0;
            return List.of(
                    new HAComprobacion("PDF creado", haD.getNumberOfPages() > 0 ? HAEstado.CUMPLE : HAEstado.REVISAR,
                            "Se abre correctamente y tiene " + haD.getNumberOfPages() + " páginas."),
                    new HAComprobacion("Texto", haConTexto ? HAEstado.CUMPLE : HAEstado.REVISAR, haConTexto
                            ? "Tiene texto que se puede buscar y copiar."
                            : "No tiene texto: revisa que el documento se haya convertido bien."),
                    new HAComprobacion("Diseño", HAEstado.INFO,
                            "Que se vea igual que en Word (tablas, fuentes, saltos de página) no se puede comprobar solo: revísalo."));
        }
    }

    private static HAComprobacion haPdfAImagenes(HAEsperadoArchivos.HAPdfAImagenes haE, List<File> haImagenes) throws IOException {
        try (PDDocument haD = Loader.loadPDF(haE.haOrigen(), haE.haClave())) {
            if (haImagenes.size() != haD.getNumberOfPages()) {
                return new HAComprobacion("Imágenes", HAEstado.REVISAR, "Se crearon " + haImagenes.size()
                        + " imágenes; el PDF tiene " + haD.getNumberOfPages() + " páginas.");
            }
            List<String> haMal = new ArrayList<>();
            double haEscala = haE.haDpi() / 72.0;
            for (int haI = 0; haI < haImagenes.size(); haI++) {
                BufferedImage haImg = ImageIO.read(haImagenes.get(haI));
                PDPage haP = haD.getPage(haI);
                PDRectangle haCaja = haP.getCropBox();
                int haGiro = ((haP.getRotation() % 360) + 360) % 360;
                boolean haGirada = haGiro == 90 || haGiro == 270;
                double haAncho = (haGirada ? haCaja.getHeight() : haCaja.getWidth()) * haEscala;
                double haAlto = (haGirada ? haCaja.getWidth() : haCaja.getHeight()) * haEscala;
                if (haImg == null) {
                    haMal.add("«" + haImagenes.get(haI).getName() + "» no se puede abrir");
                } else if (Math.abs(haImg.getWidth() - haAncho) > 2 || Math.abs(haImg.getHeight() - haAlto) > 2) {
                    haMal.add("«" + haImagenes.get(haI).getName() + "» mide " + haImg.getWidth() + "×" + haImg.getHeight()
                            + " en vez de " + Math.round(haAncho) + "×" + Math.round(haAlto));
                }
            }
            return haMal.isEmpty()
                    ? new HAComprobacion("Imágenes", HAEstado.CUMPLE, "Una imagen por página (" + haImagenes.size()
                        + "), todas se abren y miden lo que corresponde a " + haE.haDpi() + " dpi.")
                    : new HAComprobacion("Imágenes", HAEstado.REVISAR, String.join("; ", haMal) + ".");
        }
    }

    private static final Pattern HA_SECCION = Pattern.compile("===== Página (\\d+) =====");

    private static HAComprobacion haPdfATexto(HAEsperadoArchivos.HAPdfATexto haE, File haTxt) throws IOException {
        String haContenido = Files.readString(haTxt.toPath(), StandardCharsets.UTF_8);
        List<String> haSecciones = new ArrayList<>();
        Matcher haM = HA_SECCION.matcher(haContenido);
        int haInicio = -1;
        while (haM.find()) {
            if (haInicio >= 0) {
                haSecciones.add(haContenido.substring(haInicio, haM.start()));
            }
            haInicio = haM.end();
        }
        if (haInicio >= 0) {
            haSecciones.add(haContenido.substring(haInicio));
        }
        try (PDDocument haD = Loader.loadPDF(haE.haOrigen(), haE.haClave())) {
            if (haSecciones.size() != haD.getNumberOfPages()) {
                return new HAComprobacion("Texto", HAEstado.REVISAR, "El archivo tiene " + haSecciones.size()
                        + " secciones de página; el PDF tiene " + haD.getNumberOfPages() + ".");
            }
            PDFTextStripper haLector = new PDFTextStripper();
            haLector.setSortByPosition(true);
            List<Integer> haMal = new ArrayList<>();
            for (int haI = 1; haI <= haD.getNumberOfPages(); haI++) {
                haLector.setStartPage(haI);
                haLector.setEndPage(haI);
                if (!haLector.getText(haD).replaceAll("\\s", "").equals(haSecciones.get(haI - 1).replaceAll("\\s", ""))) {
                    haMal.add(haI);
                }
            }
            return haMal.isEmpty()
                    ? new HAComprobacion("Texto", HAEstado.CUMPLE, "Releído el archivo: cada una de las " + haSecciones.size()
                        + " páginas tiene exactamente el texto del PDF.")
                    : new HAComprobacion("Texto", HAEstado.REVISAR, "No coincide el texto de "
                        + (haMal.size() == 1 ? "la página " : "las páginas ") + HAVerificador.haResumirLista(haMal) + ".");
        }
    }

    private static HAComprobacion haExtraerImagenes(HAEsperadoArchivos.HAExtraerImagenes haE, List<File> haArchivos) throws IOException {
        int haDistintas;
        try (PDDocument haD = Loader.loadPDF(haE.haOrigen(), haE.haClave())) {
            Map<COSBase, Boolean> haVistas = new IdentityHashMap<>();
            List<PDImageXObject> haTodas = new ArrayList<>();
            for (PDPage haP : haD.getPages()) {
                haImagenesDe(haP.getResources(), haVistas, haTodas, 0);
            }
            haDistintas = (int) haTodas.stream().filter(haI -> haI.getWidth() >= 16 && haI.getHeight() >= 16).count();
        }
        List<String> haIlegibles = new ArrayList<>();
        for (File haF : haArchivos) {
            if (!haEsImagen(haF)) {
                haIlegibles.add(haF.getName());
            }
        }
        if (!haIlegibles.isEmpty()) {
            return new HAComprobacion("Imágenes", HAEstado.REVISAR, "No se pueden abrir: " + String.join(", ", haIlegibles) + ".");
        }
        return haArchivos.size() == haDistintas
                ? new HAComprobacion("Imágenes", HAEstado.CUMPLE, haArchivos.size() + (haArchivos.size() == 1 ? " imagen extraída" : " imágenes extraídas")
                    + ": todas las imágenes distintas del PDF (sin repetidas ni diminutas), y todas se abren.")
                : new HAComprobacion("Imágenes", HAEstado.REVISAR, "Se extrajeron " + haArchivos.size() + "; el PDF tiene "
                    + haDistintas + " imágenes distintas.");
    }

    private static void haImagenesDe(PDResources haRecursos, Map<COSBase, Boolean> haVistas, List<PDImageXObject> haSalida, int haNivel) {
        if (haRecursos == null || haNivel > 6) {
            return;
        }
        for (COSName haNombre : haRecursos.getXObjectNames()) {
            PDXObject haX;
            try {
                haX = haRecursos.getXObject(haNombre);
            } catch (IOException haE) {
                continue;
            }
            if (haX instanceof PDImageXObject haImg) {
                if (haVistas.put(haImg.getCOSObject(), Boolean.TRUE) == null) {
                    haSalida.add(haImg);
                }
            } else if (haX instanceof PDFormXObject haForm) {
                haImagenesDe(haForm.getResources(), haVistas, haSalida, haNivel + 1);
            }
        }
    }

    private static int[] haMedidaImagen(File haF) {
        try {
            BufferedImage haImg = ImageIO.read(haF);
            return haImg == null ? null : new int[]{haImg.getWidth(), haImg.getHeight()};
        } catch (IOException | RuntimeException haE) {
            return null;
        }
    }

    private static boolean haEsImagen(File haF) {
        try {
            if (ImageIO.read(haF) != null) {
                return true;
            }
        } catch (IOException | RuntimeException haIgnorado) {
        }
        try (InputStream haIn = Files.newInputStream(haF.toPath())) {
            byte[] haCabecera = haIn.readNBytes(3);
            return haCabecera.length == 3 && (haCabecera[0] & 0xFF) == 0xFF && (haCabecera[1] & 0xFF) == 0xD8;
        } catch (IOException haE) {
            return false;
        }
    }

    private static String haNombreDe(List<File> haArchivos) {
        return haArchivos == null || haArchivos.isEmpty() ? "" : haArchivos.get(0).getName()
                + (haArchivos.size() > 1 ? " y " + (haArchivos.size() - 1) + " más" : "");
    }
}
