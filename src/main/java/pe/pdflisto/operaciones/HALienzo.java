package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.Closeable;
import java.io.IOException;

public final class HALienzo implements Closeable {
    private final PDPageContentStream haTrazo;
    private final float haAncho;
    private final float haAlto;

    public HALienzo(PDDocument haDocumento, PDPage haPagina) throws IOException {
        this.haTrazo = new PDPageContentStream(haDocumento, haPagina, PDPageContentStream.AppendMode.APPEND, true, true);
        PDRectangle haCaja = haPagina.getCropBox();
        float haX0 = haCaja.getLowerLeftX();
        float haY0 = haCaja.getLowerLeftY();
        float haW = haCaja.getWidth();
        float haH = haCaja.getHeight();
        int haGiro = ((haPagina.getRotation() % 360) + 360) % 360;
        Matrix haAPantalla = switch (haGiro) {
            case 90 -> new Matrix(0, 1, -1, 0, haX0 + haW, haY0);
            case 180 -> new Matrix(-1, 0, 0, -1, haX0 + haW, haY0 + haH);
            case 270 -> new Matrix(0, -1, 1, 0, haX0, haY0 + haH);
            default -> new Matrix(1, 0, 0, 1, haX0, haY0);
        };
        boolean haGirada = haGiro == 90 || haGiro == 270;
        this.haAncho = haGirada ? haH : haW;
        this.haAlto = haGirada ? haW : haH;
        haTrazo.saveGraphicsState();
        haTrazo.transform(haAPantalla);
    }

    public float haAncho() { return haAncho; }
    public float haAlto() { return haAlto; }

    public void haTexto(String haTexto, PDFont haFuente, float haTamano, float haX, float haY, Color haColor, float haOpacidad, double haGrados)
            throws IOException {
        haTrazo.saveGraphicsState();
        haTransparencia(haOpacidad);
        haTrazo.setNonStrokingColor(haColor);
        haTrazo.beginText();
        haTrazo.setFont(haFuente, haTamano);
        haTrazo.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(haGrados), haX, haY));
        haTrazo.showText(haTexto);
        haTrazo.endText();
        haTrazo.restoreGraphicsState();
    }

    public void haImagen(PDImageXObject haImagen, float haX, float haY, float haW, float haH, float haOpacidad) throws IOException {
        haTrazo.saveGraphicsState();
        haTransparencia(haOpacidad);
        haTrazo.drawImage(haImagen, haX, haY, haW, haH);
        haTrazo.restoreGraphicsState();
    }

    public record HAPalabraInvisible(String haTexto, double haX, double haYBase, double haAncho, double haTamano) {
    }

    public void haTextoInvisible(java.util.List<HAPalabraInvisible> haPalabras, PDFont haFuente) throws IOException {
        if (haPalabras.isEmpty()) {
            return;
        }
        java.util.Map<Character, Boolean> haEscribibles = new java.util.HashMap<>();
        haTrazo.saveGraphicsState();
        haTrazo.beginText();
        haTrazo.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER);
        for (HAPalabraInvisible haP : haPalabras) {
            StringBuilder haSb = new StringBuilder();
            for (char haC : haP.haTexto().toCharArray()) {
                if (haEscribibles.computeIfAbsent(haC, haX -> HAFuentes.haPuedeEscribir(haFuente, haX))) {
                    haSb.append(haC);
                }
            }
            String haS = haSb.toString();
            float haTam = (float) Math.max(1, haP.haTamano());
            float haNatural;
            try {
                haNatural = haS.isEmpty() ? 0 : haFuente.getStringWidth(haS) / 1000f * haTam;
            } catch (IllegalArgumentException | IllegalStateException haE) {
                continue;
            }
            if (haNatural <= 0) {
                continue;
            }
            haTrazo.setFont(haFuente, haTam);
            haTrazo.setHorizontalScaling((float) Math.max(10, Math.min(400, 100 * haP.haAncho() / haNatural)));
            haTrazo.setTextMatrix(Matrix.getTranslateInstance((float) haP.haX(), (float) (haAlto - haP.haYBase())));
            haTrazo.showText(haS);
        }
        haTrazo.endText();
        haTrazo.restoreGraphicsState();
    }

    public void haRectangulo(float haX, float haY, float haW, float haH, Color haColor, float haGrosor) throws IOException {
        haTrazo.saveGraphicsState();
        haTrazo.setStrokingColor(haColor);
        haTrazo.setLineWidth(haGrosor);
        haTrazo.addRect(haX + haGrosor / 2, haY + haGrosor / 2, haW - haGrosor, haH - haGrosor);
        haTrazo.stroke();
        haTrazo.restoreGraphicsState();
    }

    private void haTransparencia(float haOpacidad) throws IOException {
        if (haOpacidad < 1f) {
            PDExtendedGraphicsState haEstado = new PDExtendedGraphicsState();
            haEstado.setNonStrokingAlphaConstant(haOpacidad);
            haEstado.setStrokingAlphaConstant(haOpacidad);
            haTrazo.setGraphicsStateParameters(haEstado);
        }
    }

    @Override
    public void close() throws IOException {
        haTrazo.restoreGraphicsState();
        haTrazo.close();
    }
}
