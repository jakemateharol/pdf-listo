package pe.pdflisto.ocr;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAFuentes;
import pe.pdflisto.operaciones.HALienzo;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.pdf.HATamanoPagina;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public record HAReconocerTexto(HAOpciones haOpciones) implements HAOperacionPdf {
    static final float HA_DPI_OCR = 300f;
    static final float HA_DPI_GUARDADO = 200f;
    static final int HA_MINIMO_LETRAS = 20;

    public record HAOpciones(HADatosOcr.HAIdioma haIdioma, boolean haEnderezar, boolean haLimpiarBordes, boolean haContraste,
                           boolean haCorregirGiro) {
        public HAOpciones(HADatosOcr.HAIdioma haIdioma, boolean haEnderezar, boolean haLimpiarBordes, boolean haContraste) {
            this(haIdioma, haEnderezar, haLimpiarBordes, haContraste, true);
        }

        public static HAOpciones haBasicas() {
            return new HAOpciones(HADatosOcr.HAIdioma.ESPANOL, false, false, false, true);
        }

        boolean haMejora() {
            return haEnderezar || haLimpiarBordes || haContraste;
        }
    }

    @Override
    public String haDescripcion() {
        return "Reconocer texto (OCR)" + (haOpciones.haMejora() ? " con mejora del escaneo" : "");
    }

    @Override
    public PDDocument haAplicar(PDDocument haDoc, HARecursos haRecursos) throws IOException {
        Tesseract haTesseract = haCrearTesseract();
        PDFRenderer haDibujante = new PDFRenderer(haDoc);
        PDFont haFuente = HAFuentes.haCargar(haDoc, false);
        int haTotal = haDoc.getNumberOfPages();
        int haReconocidas = 0;
        int haPalabras = 0;
        double haSumaConfianza = 0;
        int haConTexto = 0;
        List<Integer> haGiradas = new ArrayList<>();
        for (int haI = 0; haI < haTotal; haI++) {
            haRecursos.haControl().haAvance(haI, haTotal, "Reconociendo texto: página " + (haI + 1) + " de " + haTotal);
            if (haTieneTexto(haDoc, haI)) {
                haConTexto++;
                continue;
            }
            BufferedImage haImagen = HAMejoraEscaneo.haAGris(haDibujante.renderImageWithDPI(haI, HA_DPI_OCR, ImageType.GRAY));
            if (haOpciones.haCorregirGiro()) {
                int haGiro = HAOrientacion.haCorreccion(haTesseract, haImagen);
                if (haGiro != 0) {
                    PDPage haPagina = haDoc.getPage(haI);
                    haPagina.setRotation((haPagina.getRotation() + haGiro) % 360);
                    haImagen = HAOrientacion.haGirar(haImagen, haGiro);
                    haGiradas.add(haI + 1);
                }
            }
            if (haOpciones.haMejora()) {
                haImagen = haMejorar(haImagen);
                haReemplazarPorImagen(haDoc, haDoc.getPage(haI), haImagen);
            }
            haRecursos.haControl().haComprobar();
            List<Word> haReconocidasPagina = haReconocer(haTesseract, haImagen);
            List<HALienzo.HAPalabraInvisible> haCapa = new ArrayList<>();
            double haEscala = 72.0 / HA_DPI_OCR;
            for (Word haW : haReconocidasPagina) {
                String haTexto = haW.getText() == null ? "" : haW.getText().trim();
                if (haTexto.isEmpty() || haW.getConfidence() < 15) {
                    continue;
                }
                Rectangle haR = haW.getBoundingBox();
                double haAlto = haR.height * haEscala;
                haCapa.add(new HALienzo.HAPalabraInvisible(haTexto, haR.x * haEscala, (haR.y + haR.height) * haEscala,
                        haR.width * haEscala, haAlto / 0.75));
                haSumaConfianza += haW.getConfidence();
                haPalabras++;
            }
            try (HALienzo haLienzo = new HALienzo(haDoc, haDoc.getPage(haI))) {
                haLienzo.haTextoInvisible(haCapa, haFuente);
            }
            haReconocidas++;
        }
        if (haReconocidas == 0) {
            throw new HAPdfListoException(haConTexto == haTotal
                    ? "Todas las páginas ya tienen texto: no hace falta el OCR."
                    : "No se encontraron páginas para reconocer.");
        }
        if (!haGiradas.isEmpty()) {
            haRecursos.haNota(haGiradas.size() == 1
                    ? "La página " + haGiradas.get(0) + " estaba girada y se puso derecha."
                    : "Las páginas " + haUnir(haGiradas) + " estaban giradas y se pusieron derechas.");
        }
        double haConfianza = haPalabras == 0 ? 0 : haSumaConfianza / haPalabras;
        log.info("OCR: {} paginas, {} palabras, confianza media {}", haReconocidas, haPalabras, Math.round(haConfianza));
        if (haPalabras == 0) {
            throw new HAPdfListoException("No se reconoció ninguna palabra. Puede que las páginas estén en blanco, sean "
                    + "fotos sin texto o el escaneo sea demasiado borroso. Prueba con «Mejorar contraste».");
        }
        return haDoc;
    }

    private static String haUnir(List<Integer> haPaginas) {
        List<String> haT = haPaginas.stream().map(String::valueOf).toList();
        return haT.size() == 1 ? haT.get(0)
                : String.join(", ", haT.subList(0, haT.size() - 1)) + " y " + haT.get(haT.size() - 1);
    }

    private Tesseract haCrearTesseract() {
        try {
            Tesseract haT = new Tesseract();
            haT.setDatapath(HADatosOcr.haCarpeta().toString());
            haT.setLanguage(haOpciones.haIdioma().haCodigo());
            haT.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
            haT.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
            haT.setVariable("user_defined_dpi", String.valueOf((int) HA_DPI_OCR));
            return haT;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError haE) {
            throw new HAPdfListoException("No se pudo iniciar el motor de OCR. Normalmente se soluciona instalando "
                    + "«Microsoft Visual C++ Redistributable» (gratuito, de Microsoft).", haE);
        }
    }

    private static List<Word> haReconocer(Tesseract haT, BufferedImage haImagen) {
        try {
            return haT.getWords(haImagen, ITessAPI.TessPageIteratorLevel.RIL_WORD);
        } catch (UnsatisfiedLinkError | NoClassDefFoundError haE) {
            throw new HAPdfListoException("No se pudo iniciar el motor de OCR. Normalmente se soluciona instalando "
                    + "«Microsoft Visual C++ Redistributable» (gratuito, de Microsoft).", haE);
        } catch (RuntimeException haE) {
            throw new HAPdfListoException("El OCR no pudo leer una página: " + haE.getMessage(), haE);
        }
    }

    private BufferedImage haMejorar(BufferedImage haImagen) {
        BufferedImage haR = haImagen;
        if (haOpciones.haContraste()) {
            haR = HAMejoraEscaneo.haMejorarContraste(haR);
        }
        if (haOpciones.haLimpiarBordes()) {
            haR = HAMejoraEscaneo.haLimpiarBordes(haR);
        }
        if (haOpciones.haEnderezar()) {
            haR = HAMejoraEscaneo.haEnderezar(haR);
        }
        return haR;
    }

    static boolean haTieneTexto(PDDocument haDoc, int haIndice) throws IOException {
        HALectorTexto.HAPaginaTexto haT = HALectorTexto.haLeerPagina(haDoc, haIndice);
        return haT.haLetras().stream().filter(haL -> haL != null).count() >= HA_MINIMO_LETRAS;
    }

    private static void haReemplazarPorImagen(PDDocument haDoc, PDPage haPagina, BufferedImage haImagen) throws IOException {
        HATamanoPagina haT = HATamanoPagina.haDe(haPagina);
        float haAncho = (float) haT.haAncho();
        float haAlto = (float) haT.haAlto();
        int haW = Math.max(1, Math.round(haImagen.getWidth() * HA_DPI_GUARDADO / HA_DPI_OCR));
        int haH = Math.max(1, Math.round(haImagen.getHeight() * HA_DPI_GUARDADO / HA_DPI_OCR));
        BufferedImage haGuardada = new BufferedImage(haW, haH, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D haG = haGuardada.createGraphics();
        haG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        haG.drawImage(haImagen, 0, 0, haW, haH, null);
        haG.dispose();

        COSDictionary haDic = haPagina.getCOSObject();
        for (String haClave : new String[]{"Thumb", "PieceInfo", "BleedBox", "TrimBox", "ArtBox"}) {
            haDic.removeItem(COSName.getPDFName(haClave));
        }
        PDRectangle haCaja = new PDRectangle(0, 0, haAncho, haAlto);
        haPagina.setMediaBox(haCaja);
        haPagina.setCropBox(haCaja);
        haPagina.setRotation(0);
        haPagina.setResources(new PDResources());
        PDImageXObject haFoto = JPEGFactory.createFromImage(haDoc, haGuardada, 0.85f);
        try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina, PDPageContentStream.AppendMode.OVERWRITE, true)) {
            haCs.drawImage(haFoto, 0, 0, haAncho, haAlto);
        }
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HATextoBuscable());
    }
}
