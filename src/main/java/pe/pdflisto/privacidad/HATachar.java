package pe.pdflisto.privacidad;

import org.apache.pdfbox.cos.COSBase;
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
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAFuentes;
import pe.pdflisto.operaciones.HAOperacionPdf;
import pe.pdflisto.operaciones.HARecursos;
import pe.pdflisto.pdf.HALectorTexto;
import pe.pdflisto.pdf.HATamanoPagina;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public record HATachar(List<HAAreaTachado> haAreas, boolean haLimpiarMetadatos) implements HAOperacionPdf {
    static final float HA_DPI = 200f;

    @Override
    public String haDescripcion() {
        long haPaginas = haAreas.stream().map(HAAreaTachado::haPagina).distinct().count();
        return "Tachado seguro (" + haAreas.size() + (haAreas.size() == 1 ? " área" : " áreas") + " en "
                + haPaginas + (haPaginas == 1 ? " página)" : " páginas)");
    }

    @Override
    public PDDocument haAplicar(PDDocument haDocumento, HARecursos haRecursos) throws IOException {
        if (haAreas.isEmpty()) {
            throw new HAPdfListoException("No hay nada marcado para tachar.");
        }
        Map<Integer, List<HAAreaTachado>> haPorPagina = new TreeMap<>();
        for (HAAreaTachado haA : haAreas) {
            if (haA.haPagina() < 0 || haA.haPagina() >= haDocumento.getNumberOfPages()) {
                throw new HAPdfListoException("Un área marcada está en una página que ya no existe.");
            }
            haPorPagina.computeIfAbsent(haA.haPagina(), haK -> new ArrayList<>()).add(haA);
        }
        PDFRenderer haDibujante = new PDFRenderer(haDocumento);
        haDibujante.setAnnotationsFilter(haA -> !"Text".equals(haA.getSubtype()) && !"Popup".equals(haA.getSubtype()));
        PDFont haFuente = HAFuentes.haCargar(haDocumento, false);
        Set<COSBase> haWidgetsQuitados = new HashSet<>();
        Map<Character, Boolean> haEscribibles = new HashMap<>();

        int haHechas = 0;
        for (Map.Entry<Integer, List<HAAreaTachado>> haE : haPorPagina.entrySet()) {
            haRecursos.haControl().haAvance(haHechas++, haPorPagina.size(),
                    "Tachando página " + (haE.getKey() + 1) + " (" + haHechas + " de " + haPorPagina.size() + ")");
            int haIndice = haE.getKey();
            List<HAAreaTachado> haEnPagina = haE.getValue();
            HALectorTexto.HAPaginaTexto haTexto = HALectorTexto.haLeerPagina(haDocumento, haIndice);
            BufferedImage haImagen = haDibujante.renderImageWithDPI(haIndice, HA_DPI, ImageType.RGB);
            haPintarNegro(haImagen, haEnPagina);

            PDPage haPagina = haDocumento.getPage(haIndice);
            for (PDAnnotation haA : haPagina.getAnnotations()) {
                if (haA instanceof PDAnnotationWidget) {
                    haWidgetsQuitados.add(haA.getCOSObject());
                }
            }
            haReconstruir(haDocumento, haPagina, haImagen, haTexto, haEnPagina, haFuente, haEscribibles);
        }
        haQuitarCamposDeFormulario(haDocumento, haWidgetsQuitados);
        if (haLimpiarMetadatos) {
            new HALimpiarMetadatos(HALimpiarMetadatos.HAOpciones.haTodo()).haAplicar(haDocumento, haRecursos);
        }
        return haDocumento;
    }

    private static void haPintarNegro(BufferedImage haImagen, List<HAAreaTachado> haAreas) {
        double haEscala = HA_DPI / 72.0;
        Graphics2D haG = haImagen.createGraphics();
        haG.setColor(Color.BLACK);
        for (HAAreaTachado haA : haAreas) {
            int haX = (int) Math.floor(haA.haArea().haX() * haEscala) - 1;
            int haY = (int) Math.floor(haA.haArea().haY() * haEscala) - 1;
            int haW = (int) Math.ceil(haA.haArea().haAncho() * haEscala) + 2;
            int haH = (int) Math.ceil(haA.haArea().haAlto() * haEscala) + 2;
            haG.fillRect(haX, haY, haW, haH);
        }
        haG.dispose();
    }

    private static void haReconstruir(PDDocument haDoc, PDPage haPagina, BufferedImage haImagen, HALectorTexto.HAPaginaTexto haTexto,
                                    List<HAAreaTachado> haAreas, PDFont haFuente, Map<Character, Boolean> haEscribibles)
            throws IOException {
        HATamanoPagina haT = HATamanoPagina.haDe(haPagina);
        float haAncho = (float) haT.haAncho();
        float haAlto = (float) haT.haAlto();

        COSDictionary haDic = haPagina.getCOSObject();
        for (String haClave : new String[]{"Annots", "Thumb", "Metadata", "PieceInfo", "B", "Group", "AA",
                "BleedBox", "TrimBox", "ArtBox", "SeparationInfo", "VP"}) {
            haDic.removeItem(COSName.getPDFName(haClave));
        }
        PDRectangle haCaja = new PDRectangle(0, 0, haAncho, haAlto);
        haPagina.setMediaBox(haCaja);
        haPagina.setCropBox(haCaja);
        haPagina.setRotation(0);
        haPagina.setResources(new PDResources());

        PDImageXObject haFoto = JPEGFactory.createFromImage(haDoc, haImagen, 0.9f);
        try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina, PDPageContentStream.AppendMode.OVERWRITE, true)) {
            haCs.drawImage(haFoto, 0, 0, haAncho, haAlto);
            haEscribirTextoInvisible(haCs, haTexto, haAreas, haFuente, haAlto, haEscribibles);
        }
    }

    private static void haEscribirTextoInvisible(PDPageContentStream haCs, HALectorTexto.HAPaginaTexto haTexto,
                                               List<HAAreaTachado> haAreas, PDFont haFuente, float haAlto,
                                               Map<Character, Boolean> haEscribibles) throws IOException {
        List<List<Integer>> haPalabras = new ArrayList<>();
        List<Integer> haActual = new ArrayList<>();
        for (int haI = 0; haI < haTexto.haLetras().size(); haI++) {
            TextPosition haL = haTexto.haLetras().get(haI);
            boolean haSirve = haL != null && !haDentroDeAlguna(haL, haAreas)
                    && haEscribibles.computeIfAbsent(haTexto.haTexto().charAt(haI), haC -> HAFuentes.haPuedeEscribir(haFuente, haC));
            boolean haMismaLinea = !haActual.isEmpty() && haL != null
                    && Math.abs(haL.getY() - haTexto.haLetras().get(haActual.get(0)).getY()) < 1.5;
            if (haSirve && (haActual.isEmpty() || haMismaLinea)) {
                haActual.add(haI);
            } else {
                if (!haActual.isEmpty()) {
                    haPalabras.add(haActual);
                }
                haActual = new ArrayList<>();
                if (haSirve) {
                    haActual.add(haI);
                }
            }
        }
        if (!haActual.isEmpty()) {
            haPalabras.add(haActual);
        }
        if (haPalabras.isEmpty()) {
            return;
        }
        haCs.beginText();
        haCs.setRenderingMode(RenderingMode.NEITHER);
        for (List<Integer> haPalabra : haPalabras) {
            StringBuilder haSb = new StringBuilder();
            haPalabra.forEach(haI -> haSb.append(haTexto.haTexto().charAt(haI)));
            String haS = haFiltrar(haSb.toString(), haFuente, haEscribibles);
            if (haS.isEmpty()) {
                continue;
            }
            TextPosition haPrimera = haTexto.haLetras().get(haPalabra.get(0));
            TextPosition haUltima = haTexto.haLetras().get(haPalabra.get(haPalabra.size() - 1));
            float haTam = haPrimera.getFontSizeInPt() > 0 ? haPrimera.getFontSizeInPt() : Math.max(haPrimera.getHeight(), 6);
            float haAnchoDeseado = haUltima.getX() + haUltima.getWidth() - haPrimera.getX();
            float haAnchoNatural;
            try {
                haAnchoNatural = haFuente.getStringWidth(haS) / 1000f * haTam;
            } catch (IllegalArgumentException | IllegalStateException haE) {
                continue;
            }
            haCs.setFont(haFuente, haTam);
            if (haAnchoNatural > 0 && haAnchoDeseado > 0) {
                haCs.setHorizontalScaling(Math.max(10f, Math.min(400f, 100f * haAnchoDeseado / haAnchoNatural)));
            }
            haCs.setTextMatrix(Matrix.getTranslateInstance(haPrimera.getX(), haAlto - haPrimera.getY()));
            haCs.showText(haS);
        }
        haCs.endText();
    }

    private static String haFiltrar(String haS, PDFont haFuente, Map<Character, Boolean> haEscribibles) {
        StringBuilder haSb = new StringBuilder();
        for (char haC : haS.toCharArray()) {
            if (haEscribibles.computeIfAbsent(haC, haX -> HAFuentes.haPuedeEscribir(haFuente, haX))) {
                haSb.append(haC);
            }
        }
        return haSb.toString();
    }

    static boolean haDentroDeAlguna(TextPosition haL, List<HAAreaTachado> haAreas) {
        var haA = HALectorTexto.haAreaDe(haL);
        double haCx = haA.haX() + haA.haAncho() / 2;
        double haCy = haA.haY() + haA.haAlto() / 2;
        for (HAAreaTachado haT : haAreas) {
            if (haT.haContiene(haCx, haCy)) {
                return true;
            }
        }
        return false;
    }

    private static void haQuitarCamposDeFormulario(PDDocument haDoc, Set<COSBase> haWidgets) {
        if (haWidgets.isEmpty()) {
            return;
        }
        PDAcroForm haFormulario = haDoc.getDocumentCatalog().getAcroForm(null);
        if (haFormulario == null) {
            return;
        }
        List<PDField> haQuedan = haFiltrar(haFormulario.getFields(), haWidgets);
        haFormulario.setFields(haQuedan);
        if (haQuedan.isEmpty()) {
            haDoc.getDocumentCatalog().getCOSObject().removeItem(COSName.ACRO_FORM);
        }
    }

    private static List<PDField> haFiltrar(List<PDField> haCampos, Set<COSBase> haWidgets) {
        List<PDField> haQuedan = new ArrayList<>();
        for (PDField haCampo : haCampos) {
            if (haCampo instanceof PDNonTerminalField haGrupo) {
                List<PDField> haHijos = haFiltrar(haGrupo.getChildren(), haWidgets);
                haGrupo.setChildren(haHijos);
                if (!haHijos.isEmpty()) {
                    haQuedan.add(haGrupo);
                }
            } else if (haCampo instanceof PDTerminalField haTerminal) {
                boolean haTocado = haTerminal.getWidgets().stream().anyMatch(haW -> haWidgets.contains(haW.getCOSObject()));
                if (!haTocado) {
                    haQuedan.add(haCampo);
                }
            }
        }
        return haQuedan;
    }

    @Override
    public java.util.List<pe.pdflisto.verificacion.HAEsperado> haEsperado() {
        java.util.List<Integer> haPaginas = haAreas.stream().map(HAAreaTachado::haPagina).distinct().sorted().toList();
        return java.util.List.of(new pe.pdflisto.verificacion.HAEsperado.HAMismasPaginas(), new pe.pdflisto.verificacion.HAEsperado.HAContenidoCambiado("Páginas tachadas", haPaginas),
                new pe.pdflisto.verificacion.HAEsperado.HATachado(haPaginas));
    }
}
