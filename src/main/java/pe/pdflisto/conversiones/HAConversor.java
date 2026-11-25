package pe.pdflisto.conversiones;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import pe.pdflisto.exception.HAPdfListoException;
import pe.pdflisto.operaciones.HAArchivos;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.operaciones.HAFuentes;
import pe.pdflisto.pdf.HACargaPdf;
import pe.pdflisto.pdf.HAEscrituraSegura;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class HAConversor {
    public enum HATamanoHoja {
        IMAGEN("Del tamaño de cada imagen"), A4("A4 (la imagen se ajusta con márgenes)");

        private final String haTexto;

        HATamanoHoja(String haTexto) {
            this.haTexto = haTexto;
        }

        @Override
        public String toString() {
            return haTexto;
        }
    }

    public File haImagenesAPdf(List<File> haImagenes, HATamanoHoja haTamano, File haDestino, HAControl haControl) {
        if (haImagenes.isEmpty()) {
            throw new HAPdfListoException("Elige al menos una imagen.");
        }
        try (PDDocument haDoc = new PDDocument()) {
            for (int haI = 0; haI < haImagenes.size(); haI++) {
                File haF = haImagenes.get(haI);
                haControl.haAvance(haI, haImagenes.size(), "Agregando " + haF.getName() + " (" + (haI + 1) + " de " + haImagenes.size() + ")");
                PDImageXObject haImg;
                try {
                    haImg = PDImageXObject.createFromFileByContent(haF, haDoc);
                } catch (IOException | IllegalArgumentException haE) {
                    throw new HAPdfListoException("«" + haF.getName() + "» no es una imagen que se pueda leer (usa JPG o PNG).");
                }
                float haAnchoImg = haImg.getWidth() * 72f / 150f;
                float haAltoImg = haImg.getHeight() * 72f / 150f;
                PDRectangle haHoja = haTamano == HATamanoHoja.A4
                        ? (haImg.getWidth() > haImg.getHeight() ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4)
                        : new PDRectangle(haAnchoImg, haAltoImg);
                PDPage haPagina = new PDPage(haHoja);
                haDoc.addPage(haPagina);
                float haMargen = haTamano == HATamanoHoja.A4 ? 28 : 0;
                float haEscala = Math.min((haHoja.getWidth() - 2 * haMargen) / haAnchoImg, (haHoja.getHeight() - 2 * haMargen) / haAltoImg);
                haEscala = haTamano == HATamanoHoja.A4 ? Math.min(haEscala, 1f) : haEscala;
                float haW = haAnchoImg * haEscala;
                float haH = haAltoImg * haEscala;
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                    haCs.drawImage(haImg, (haHoja.getWidth() - haW) / 2, (haHoja.getHeight() - haH) / 2, haW, haH);
                }
            }
            haControl.haMensaje("Guardando y verificando…");
            HAEscrituraSegura.haGuardar(haDoc, haDestino);
            return haDestino;
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo crear el PDF: " + haE.getMessage(), haE);
        }
    }

    public List<File> haPdfAImagenes(File haPdf, String haClave, int haDpi, boolean haJpg, File haCarpeta, HAControl haControl) {
        return haPdfAImagenes(haPdf, haClave, HAArchivos.haSinExtension(haPdf.getName()), haDpi, haJpg, haCarpeta, haControl);
    }

    public List<File> haPdfAImagenes(File haPdf, String haClave, String haBase, int haDpi, boolean haJpg, File haCarpeta, HAControl haControl) {
        List<File> haCreadas = new ArrayList<>();
        try (PDDocument haDoc = HACargaPdf.haAbrir(haPdf, haClave)) {
            PDFRenderer haDibujante = new PDFRenderer(haDoc);
            int haTotal = haDoc.getNumberOfPages();
            int haDigitos = String.valueOf(haTotal).length();
            for (int haI = 0; haI < haTotal; haI++) {
                haControl.haAvance(haI, haTotal, "Página " + (haI + 1) + " de " + haTotal);
                BufferedImage haImg = haDibujante.renderImageWithDPI(haI, haDpi, ImageType.RGB);
                File haF = HAArchivos.haLibre(haCarpeta, haBase + " - página " + String.format("%0" + Math.max(2, haDigitos) + "d", haI + 1),
                        haJpg ? ".jpg" : ".png");
                ImageIO.write(haImg, haJpg ? "jpg" : "png", haF);
                haCreadas.add(haF);
            }
            return haCreadas;
        } catch (IOException haE) {
            haCreadas.forEach(File::delete);
            throw new HAPdfListoException("No se pudo convertir a imágenes: " + haE.getMessage(), haE);
        } catch (RuntimeException haE) {
            haCreadas.forEach(File::delete);
            throw haE;
        }
    }

    public File haPdfATexto(File haPdf, String haClave, File haDestino, HAControl haControl) {
        try (PDDocument haDoc = HACargaPdf.haAbrir(haPdf, haClave)) {
            PDFTextStripper haLector = new PDFTextStripper();
            haLector.setSortByPosition(true);
            StringBuilder haSb = new StringBuilder();
            int haTotal = haDoc.getNumberOfPages();
            int haConTexto = 0;
            for (int haI = 1; haI <= haTotal; haI++) {
                haControl.haAvance(haI - 1, haTotal, "Leyendo página " + haI + " de " + haTotal);
                haLector.setStartPage(haI);
                haLector.setEndPage(haI);
                String haT = haLector.getText(haDoc);
                if (haT.replaceAll("\\s", "").length() > 0) {
                    haConTexto++;
                }
                haSb.append("===== Página ").append(haI).append(" =====").append(System.lineSeparator()).append(haT.strip())
                        .append(System.lineSeparator()).append(System.lineSeparator());
            }
            if (haConTexto == 0) {
                throw new HAPdfListoException("Este PDF no tiene texto (parece escaneado): primero usa Herramientas → "
                        + "Reconocer texto (OCR) y luego conviértelo.");
            }
            Files.writeString(haDestino.toPath(), haSb.toString(), StandardCharsets.UTF_8);
            return haDestino;
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo extraer el texto: " + haE.getMessage(), haE);
        }
    }

    public File haTextoAPdf(File haTxt, File haDestino, HAControl haControl) {
        String haContenido = haLeerTexto(haTxt);
        float haTam = 11f;
        float haMargen = 56f;
        float haInterlineado = haTam * 1.35f;
        try (PDDocument haDoc = new PDDocument()) {
            PDFont haFuente = HAFuentes.haCargar(haDoc, false);
            float haAncho = PDRectangle.A4.getWidth() - 2 * haMargen;
            List<String> haLineas = new ArrayList<>();
            for (String haParrafo : haContenido.split("\\R", -1)) {
                haLineas.addAll(haPartir(haLimpiar(haParrafo, haFuente), haFuente, haTam, haAncho));
            }
            int haPorPagina = (int) ((PDRectangle.A4.getHeight() - 2 * haMargen) / haInterlineado);
            for (int haInicio = 0; haInicio < Math.max(1, haLineas.size()); haInicio += haPorPagina) {
                haControl.haAvance(haInicio, Math.max(1, haLineas.size()), "Escribiendo página " + (haInicio / haPorPagina + 1));
                PDPage haPagina = new PDPage(PDRectangle.A4);
                haDoc.addPage(haPagina);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haPagina)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, haTam);
                    haCs.newLineAtOffset(haMargen, PDRectangle.A4.getHeight() - haMargen - haTam);
                    for (int haI = haInicio; haI < Math.min(haLineas.size(), haInicio + haPorPagina); haI++) {
                        haCs.showText(haLineas.get(haI));
                        haCs.newLineAtOffset(0, -haInterlineado);
                    }
                    haCs.endText();
                }
            }
            HAEscrituraSegura.haGuardar(haDoc, haDestino);
            return haDestino;
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo crear el PDF: " + haE.getMessage(), haE);
        }
    }

    public static String haLeerTexto(File haTxt) {
        try {
            byte[] haBytes = Files.readAllBytes(haTxt.toPath());
            String haUtf8 = new String(haBytes, StandardCharsets.UTF_8);
            String haTexto = haUtf8.contains("�") ? new String(haBytes, Charset.forName("windows-1252")) : haUtf8;
            return haTexto.startsWith("﻿") ? haTexto.substring(1) : haTexto;
        } catch (IOException haE) {
            throw new HAPdfListoException("No se pudo leer «" + haTxt.getName() + "»: " + haE.getMessage(), haE);
        }
    }

    private static String haLimpiar(String haLinea, PDFont haFuente) {
        StringBuilder haSb = new StringBuilder();
        for (char haC : haLinea.replace("\t", "    ").toCharArray()) {
            haSb.append(HAFuentes.haPuedeEscribir(haFuente, haC) ? haC : '?');
        }
        return haSb.toString();
    }

    private static List<String> haPartir(String haParrafo, PDFont haFuente, float haTam, float haAncho) {
        List<String> haLineas = new ArrayList<>();
        if (haParrafo.isEmpty()) {
            haLineas.add("");
            return haLineas;
        }
        StringBuilder haActual = new StringBuilder();
        for (String haPalabra : haParrafo.split(" ")) {
            String haPrueba = haActual.isEmpty() ? haPalabra : haActual + " " + haPalabra;
            if (HAFuentes.haAncho(haFuente, haPrueba, haTam) <= haAncho) {
                haActual.setLength(0);
                haActual.append(haPrueba);
            } else {
                if (!haActual.isEmpty()) {
                    haLineas.add(haActual.toString());
                }
                haActual.setLength(0);
                haActual.append(haPalabra);
            }
        }
        haLineas.add(haActual.toString());
        return haLineas;
    }

    public List<File> haExtraerImagenes(File haPdf, String haClave, File haCarpeta, HAControl haControl) {
        return haExtraerImagenes(haPdf, haClave, HAArchivos.haSinExtension(haPdf.getName()), haCarpeta, haControl);
    }

    public List<File> haExtraerImagenes(File haPdf, String haClave, String haBase, File haCarpeta, HAControl haControl) {
        List<File> haCreadas = new ArrayList<>();
        try (PDDocument haDoc = HACargaPdf.haAbrir(haPdf, haClave)) {
            Map<COSBase, Boolean> haVistas = new IdentityHashMap<>();
            int haTotal = haDoc.getNumberOfPages();
            for (int haI = 0; haI < haTotal; haI++) {
                haControl.haAvance(haI, haTotal, "Buscando imágenes: página " + (haI + 1) + " de " + haTotal);
                haExtraer(haDoc.getPage(haI).getResources(), haVistas, haCarpeta, haBase + " - pág " + (haI + 1), haCreadas, 0);
            }
            if (haCreadas.isEmpty()) {
                throw new HAPdfListoException("Este PDF no contiene imágenes (sus gráficos pueden ser dibujos vectoriales). "
                        + "Si quieres cada página como imagen, usa «PDF a imágenes».");
            }
            return haCreadas;
        } catch (IOException haE) {
            haCreadas.forEach(File::delete);
            throw new HAPdfListoException("No se pudieron extraer las imágenes: " + haE.getMessage(), haE);
        }
    }

    private static void haExtraer(PDResources haRecursos, Map<COSBase, Boolean> haVistas, File haCarpeta, String haPrefijo,
                                List<File> haCreadas, int haNivel) throws IOException {
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
                if (haVistas.put(haImg.getCOSObject(), Boolean.TRUE) != null || haImg.getWidth() < 16 || haImg.getHeight() < 16) {
                    continue;
                }
                String haN = haPrefijo + " - imagen " + (haCreadas.size() + 1);
                boolean haEsJpeg = "jpg".equals(haImg.getSuffix());
                File haF = HAArchivos.haLibre(haCarpeta, haN, haEsJpeg ? ".jpg" : ".png");
                if (haEsJpeg && haImg.getSoftMask() == null) {
                    try (InputStream haIn = haImg.createInputStream(List.of(COSName.DCT_DECODE.getName()))) {
                        Files.copy(haIn, haF.toPath());
                    }
                } else {
                    ImageIO.write(haImg.getImage(), "png", haF);
                }
                haCreadas.add(haF);
            } else if (haX instanceof PDFormXObject haForm) {
                haExtraer(haForm.getResources(), haVistas, haCarpeta, haPrefijo, haCreadas, haNivel + 1);
            }
        }
    }
}
