package pe.pdflisto.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

public final class HAImagenes {
    private HAImagenes() {
    }

    public static Image haAFx(BufferedImage haOrigen) {
        int haAncho = haOrigen.getWidth();
        int haAlto = haOrigen.getHeight();
        int[] haPixeles = haOrigen.getRGB(0, 0, haAncho, haAlto, null, 0, haAncho);
        WritableImage haDestino = new WritableImage(haAncho, haAlto);
        haDestino.getPixelWriter().setPixels(0, 0, haAncho, haAlto, PixelFormat.getIntArgbInstance(), haPixeles, 0, haAncho);
        return haDestino;
    }

    public static BufferedImage haABuffered(Image haOrigen) {
        int haAncho = (int) haOrigen.getWidth();
        int haAlto = (int) haOrigen.getHeight();
        int[] haPixeles = new int[haAncho * haAlto];
        haOrigen.getPixelReader().getPixels(0, 0, haAncho, haAlto, PixelFormat.getIntArgbInstance(), haPixeles, 0, haAncho);
        BufferedImage haDestino = new BufferedImage(haAncho, haAlto, BufferedImage.TYPE_INT_ARGB);
        haDestino.setRGB(0, 0, haAncho, haAlto, haPixeles, 0, haAncho);
        return haDestino;
    }

    public static String haTamanoLegible(long haBytes) {
        return pe.pdflisto.operaciones.HAArchivos.haTamanoLegible(haBytes);
    }
}
