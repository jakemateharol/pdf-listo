package pe.pdflisto.pdf;

import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

public record HATamanoPagina(double haAncho, double haAlto, int haRotacion) {
    public static HATamanoPagina haDe(PDPage haPagina) {
        PDRectangle haCaja = haPagina.getCropBox();
        int haRotacion = ((haPagina.getRotation() % 360) + 360) % 360;
        boolean haGirada = haRotacion == 90 || haRotacion == 270;
        return new HATamanoPagina(
                haGirada ? haCaja.getHeight() : haCaja.getWidth(),
                haGirada ? haCaja.getWidth() : haCaja.getHeight(),
                haRotacion);
    }
}
