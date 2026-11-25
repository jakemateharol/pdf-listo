package pe.pdflisto.ocr;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.OSDResult;
import net.sourceforge.tess4j.Tesseract;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

@Slf4j
public final class HAOrientacion {
    static final float HA_CONFIANZA_MINIMA = 2.0f;

    private HAOrientacion() {
    }

    public static int haCorreccion(Tesseract haTesseract, BufferedImage haImagen) {
        if (!HADatosOcr.haHayOrientacion()) {
            return 0;
        }
        try {
            OSDResult haR = haTesseract.getOSD(haImagen);
            if (haR == null) {
                return 0;
            }
            int haGrados = haCorreccionDesde(haR.getOrientDeg(), haR.getOrientConf());
            log.debug("OSD: orientacion {}°, confianza {} → girar {}°", haR.getOrientDeg(), haR.getOrientConf(), haGrados);
            return haGrados;
        } catch (RuntimeException haE) {
            log.debug("OSD no pudo decidir la orientacion: {}", haE.getMessage());
            return 0;
        }
    }

    static int haCorreccionDesde(int haOrientacionDetectada, float haConfianza) {
        int haG = ((haOrientacionDetectada % 360) + 360) % 360;
        if (haConfianza < HA_CONFIANZA_MINIMA || haG % 90 != 0) {
            return 0;
        }
        return (360 - haG) % 360;
    }

    public static BufferedImage haGirar(BufferedImage haOrigen, int haGrados) {
        int haG = ((haGrados % 360) + 360) % 360;
        if (haG == 0) {
            return haOrigen;
        }
        int haW = haOrigen.getWidth();
        int haH = haOrigen.getHeight();
        boolean haCruza = haG % 180 != 0;
        int haTipo = haOrigen.getType() == BufferedImage.TYPE_CUSTOM ? BufferedImage.TYPE_INT_RGB : haOrigen.getType();
        BufferedImage haR = new BufferedImage(haCruza ? haH : haW, haCruza ? haW : haH, haTipo);
        Graphics2D haGr = haR.createGraphics();
        haGr.translate(haR.getWidth() / 2.0, haR.getHeight() / 2.0);
        haGr.rotate(Math.toRadians(haG));
        haGr.translate(-haW / 2.0, -haH / 2.0);
        haGr.drawImage(haOrigen, 0, 0, null);
        haGr.dispose();
        return haR;
    }
}
