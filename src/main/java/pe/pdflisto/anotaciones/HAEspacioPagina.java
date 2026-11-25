package pe.pdflisto.anotaciones;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import pe.pdflisto.pdf.HAAreaTexto;

public final class HAEspacioPagina {
    private final float haX0;
    private final float haY0;
    private final float haW;
    private final float haH;
    private final int haGiro;
    private final float haAncho;
    private final float haAlto;

    private HAEspacioPagina(PDPage haPagina) {
        PDRectangle haCaja = haPagina.getCropBox();
        this.haX0 = haCaja.getLowerLeftX();
        this.haY0 = haCaja.getLowerLeftY();
        this.haW = haCaja.getWidth();
        this.haH = haCaja.getHeight();
        this.haGiro = ((haPagina.getRotation() % 360) + 360) % 360;
        boolean haGirada = haGiro == 90 || haGiro == 270;
        this.haAncho = haGirada ? haH : haW;
        this.haAlto = haGirada ? haW : haH;
    }

    public static HAEspacioPagina haDe(PDPage haPagina) {
        return new HAEspacioPagina(haPagina);
    }

    public float haAncho() { return haAncho; }
    public float haAlto() { return haAlto; }

    public float[] haAUsuario(double haX, double haY) {
        double haDx = haX;
        double haDy = haAlto - haY;
        return switch (haGiro) {
            case 90 -> new float[]{(float) (haX0 + haW - haDy), (float) (haY0 + haDx)};
            case 180 -> new float[]{(float) (haX0 + haW - haDx), (float) (haY0 + haH - haDy)};
            case 270 -> new float[]{(float) (haX0 + haDy), (float) (haY0 + haH - haDx)};
            default -> new float[]{(float) (haX0 + haDx), (float) (haY0 + haDy)};
        };
    }

    public PDRectangle haRectangulo(HAAreaTexto haA, float haMargen) {
        float[][] haEsquinas = haEsquinas(haA);
        float haMinX = Float.MAX_VALUE, haMinY = Float.MAX_VALUE, haMaxX = -Float.MAX_VALUE, haMaxY = -Float.MAX_VALUE;
        for (float[] haP : haEsquinas) {
            haMinX = Math.min(haMinX, haP[0]);
            haMinY = Math.min(haMinY, haP[1]);
            haMaxX = Math.max(haMaxX, haP[0]);
            haMaxY = Math.max(haMaxY, haP[1]);
        }
        return new PDRectangle(haMinX - haMargen, haMinY - haMargen, haMaxX - haMinX + 2 * haMargen, haMaxY - haMinY + 2 * haMargen);
    }

    public float[] haCuadrilatero(HAAreaTexto haA) {
        float[][] haE = haEsquinas(haA);
        return new float[]{haE[0][0], haE[0][1], haE[1][0], haE[1][1], haE[2][0], haE[2][1], haE[3][0], haE[3][1]};
    }

    private float[][] haEsquinas(HAAreaTexto haA) {
        return new float[][]{
                haAUsuario(haA.haX(), haA.haY()),
                haAUsuario(haA.haX() + haA.haAncho(), haA.haY()),
                haAUsuario(haA.haX(), haA.haY() + haA.haAlto()),
                haAUsuario(haA.haX() + haA.haAncho(), haA.haY() + haA.haAlto())};
    }
}
