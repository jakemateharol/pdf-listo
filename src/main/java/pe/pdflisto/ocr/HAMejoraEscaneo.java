package pe.pdflisto.ocr;

import com.recognition.software.jdeskew.ImageDeskew;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

public final class HAMejoraEscaneo {
    private HAMejoraEscaneo() {
    }

    public static double haInclinacion(BufferedImage haGris) {
        return new ImageDeskew(haGris).getSkewAngle();
    }

    public static BufferedImage haEnderezar(BufferedImage haGris) {
        double haAngulo = haInclinacion(haGris);
        if (Math.abs(haAngulo) < 0.3 || Math.abs(haAngulo) > 15) {
            return haGris;
        }
        BufferedImage haSalida = new BufferedImage(haGris.getWidth(), haGris.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D haG = haSalida.createGraphics();
        int haFondo = haColorDeFondo(haGris);
        haG.setColor(new Color(haFondo, haFondo, haFondo));
        haG.fillRect(0, 0, haSalida.getWidth(), haSalida.getHeight());
        haG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        haG.rotate(Math.toRadians(-haAngulo), haGris.getWidth() / 2.0, haGris.getHeight() / 2.0);
        haG.drawImage(haGris, 0, 0, null);
        haG.dispose();
        return haSalida;
    }

    public static BufferedImage haLimpiarBordes(BufferedImage haGris) {
        BufferedImage haSalida = haCopiaGris(haGris);
        WritableRaster haR = haSalida.getRaster();
        int haW = haSalida.getWidth();
        int haH = haSalida.getHeight();
        int haMaxX = (int) (haW * 0.12);
        int haMaxY = (int) (haH * 0.12);
        for (int haX = 0; haX < haMaxX && haColumnaOscura(haR, haX, haH); haX++) {
            haPintarColumna(haR, haX, haH);
        }
        for (int haX = haW - 1; haX >= haW - haMaxX && haColumnaOscura(haR, haX, haH); haX--) {
            haPintarColumna(haR, haX, haH);
        }
        for (int haY = 0; haY < haMaxY && haFilaOscura(haR, haY, haW); haY++) {
            haPintarFila(haR, haY, haW);
        }
        for (int haY = haH - 1; haY >= haH - haMaxY && haFilaOscura(haR, haY, haW); haY--) {
            haPintarFila(haR, haY, haW);
        }
        return haSalida;
    }

    public static BufferedImage haMejorarContraste(BufferedImage haGris) {
        BufferedImage haSalida = haCopiaGris(haGris);
        WritableRaster haR = haSalida.getRaster();
        int haW = haSalida.getWidth();
        int haH = haSalida.getHeight();
        int[] haHistograma = new int[256];
        for (int haY = 0; haY < haH; haY++) {
            for (int haX = 0; haX < haW; haX++) {
                haHistograma[haR.getSample(haX, haY, 0)]++;
            }
        }
        long haTotal = (long) haW * haH;
        int haBajo = haPercentil(haHistograma, haTotal, 0.01);
        int haAlto = haPercentil(haHistograma, haTotal, 0.99);
        if (haAlto - haBajo < 10) {
            return haSalida;
        }
        int[] haTabla = new int[256];
        for (int haV = 0; haV < 256; haV++) {
            haTabla[haV] = Math.max(0, Math.min(255, (haV - haBajo) * 255 / (haAlto - haBajo)));
        }
        for (int haY = 0; haY < haH; haY++) {
            for (int haX = 0; haX < haW; haX++) {
                haR.setSample(haX, haY, 0, haTabla[haR.getSample(haX, haY, 0)]);
            }
        }
        return haSalida;
    }

    static int haColorDeFondo(BufferedImage haGris) {
        int[] haHistograma = new int[256];
        var haR = haGris.getRaster();
        for (int haY = 0; haY < haGris.getHeight(); haY += 2) {
            for (int haX = 0; haX < haGris.getWidth(); haX += 2) {
                haHistograma[haR.getSample(haX, haY, 0)]++;
            }
        }
        int haMejor = 255;
        for (int haV = 129; haV < 256; haV++) {
            if (haHistograma[haV] > haHistograma[haMejor]) {
                haMejor = haV;
            }
        }
        return haMejor;
    }

    public static BufferedImage haAGris(BufferedImage haImg) {
        if (haImg.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return haImg;
        }
        BufferedImage haGris = new BufferedImage(haImg.getWidth(), haImg.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D haG = haGris.createGraphics();
        haG.drawImage(haImg, 0, 0, null);
        haG.dispose();
        return haGris;
    }

    private static BufferedImage haCopiaGris(BufferedImage haImg) {
        BufferedImage haCopia = new BufferedImage(haImg.getWidth(), haImg.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D haG = haCopia.createGraphics();
        haG.drawImage(haImg, 0, 0, null);
        haG.dispose();
        return haCopia;
    }

    private static int haPercentil(int[] haHistograma, long haTotal, double haFraccion) {
        long haAcumulado = 0;
        for (int haV = 0; haV < 256; haV++) {
            haAcumulado += haHistograma[haV];
            if (haAcumulado >= haTotal * haFraccion) {
                return haV;
            }
        }
        return 255;
    }

    private static boolean haColumnaOscura(WritableRaster haR, int haX, int haH) {
        int haOscuros = 0;
        for (int haY = 0; haY < haH; haY++) {
            if (haR.getSample(haX, haY, 0) < 80) {
                haOscuros++;
            }
        }
        return haOscuros > haH * 0.6;
    }

    private static boolean haFilaOscura(WritableRaster haR, int haY, int haW) {
        int haOscuros = 0;
        for (int haX = 0; haX < haW; haX++) {
            if (haR.getSample(haX, haY, 0) < 80) {
                haOscuros++;
            }
        }
        return haOscuros > haW * 0.6;
    }

    private static void haPintarColumna(WritableRaster haR, int haX, int haH) {
        for (int haY = 0; haY < haH; haY++) {
            haR.setSample(haX, haY, 0, 255);
        }
    }

    private static void haPintarFila(WritableRaster haR, int haY, int haW) {
        for (int haX = 0; haX < haW; haX++) {
            haR.setSample(haX, haY, 0, 255);
        }
    }
}
