package pe.pdflisto.herramientas;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import pe.pdflisto.ocr.HAOrientacion;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

public final class HAGenerarEscaneadoGirado {
    private HAGenerarEscaneadoGirado() {
    }

    public static void main(String[] haArgs) throws Exception {
        File haSalida = new File(haArgs.length > 0 ? haArgs[0] : "ejemplos/demo-escaneado-girado.pdf");
        String[][] haHojas = {
                {"Hoja 1 de 4 - derecha", "Solicitud de constancia de estudios",
                        "Alumno: Carlos Ramos Diaz (datos ficticios)", "Codigo de matricula: 2026-0145",
                        "Se solicita la constancia para tramites personales."},
                {"Hoja 2 de 4 - escaneada girada 90 grados", "Informe de practica de laboratorio",
                        "Curso: Quimica general, seccion B", "La muestra se calento durante diez minutos.",
                        "Se observo un cambio de color de azul a verde."},
                {"Hoja 3 de 4 - escaneada de cabeza (180 grados)", "Acta de reunion de delegados",
                        "Fecha: 20 de noviembre de 2026", "Asistieron doce delegados de los distintos ciclos.",
                        "Se acordo organizar la feria de proyectos en diciembre."},
                {"Hoja 4 de 4 - derecha", "Recibo de pago de pension",
                        "Monto pagado: S/ 450.00 por el mes de noviembre", "Medio de pago: deposito en ventanilla.",
                        "Conserve este recibo como comprobante."}};
        int[] haGiro = {0, 90, 180, 0};

        try (PDDocument haDoc = new PDDocument()) {
            for (int haI = 0; haI < haHojas.length; haI++) {
                BufferedImage haImg = HAOrientacion.haGirar(haHoja(haHojas[haI]), haGiro[haI]);
                float haAncho = haImg.getWidth() * 72f / 300f;
                float haAlto = haImg.getHeight() * 72f / 300f;
                PDPage haP = new PDPage(new PDRectangle(haAncho, haAlto));
                haDoc.addPage(haP);
                var haImagen = JPEGFactory.createFromImage(haDoc, haImg, 0.85f);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                    haCs.drawImage(haImagen, 0, 0, haAncho, haAlto);
                }
            }
            haDoc.getDocumentInformation().setTitle("Demo escaneado con paginas giradas (datos ficticios)");
            haDoc.save(haSalida);
        }
        System.out.println("Creado: " + haSalida.getAbsolutePath());
    }

    private static BufferedImage haHoja(String[] haLineas) {
        BufferedImage haImg = new BufferedImage(2480, 3508, BufferedImage.TYPE_INT_RGB);
        Graphics2D haG = haImg.createGraphics();
        haG.setColor(Color.WHITE);
        haG.fillRect(0, 0, haImg.getWidth(), haImg.getHeight());
        haG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        haG.setColor(new Color(25, 25, 25));
        int haY = 450;
        for (int haI = 0; haI < haLineas.length; haI++) {
            haG.setFont(new Font("Arial", haI == 1 ? Font.BOLD : Font.PLAIN, haI == 1 ? 80 : 58));
            haG.drawString(haLineas[haI], 300, haY);
            haY += haI == 0 ? 160 : 120;
        }
        haG.dispose();
        return haImg;
    }
}
