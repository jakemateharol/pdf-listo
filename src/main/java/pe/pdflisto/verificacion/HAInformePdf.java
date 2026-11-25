package pe.pdflisto.verificacion;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class HAInformePdf {
    private static final Color HA_TEXTO = new Color(31, 35, 40);
    private static final Color HA_SUAVE = new Color(101, 109, 118);
    private static final Color HA_BORDE = new Color(216, 222, 228);
    private static final Color HA_VERDE = new Color(26, 127, 55);
    private static final Color HA_VERDE_FONDO = new Color(218, 251, 225);
    private static final Color HA_AMBAR = new Color(154, 103, 0);
    private static final Color HA_AMBAR_FONDO = new Color(255, 248, 197);
    private static final Color HA_GRIS_FONDO = new Color(246, 248, 250);
    private static final DateTimeFormatter HA_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public void haGenerar(HAInformeVerificacion haInforme, File haDestino) throws IOException {
        haGenerar("Informe de verificación", haInforme, haDestino);
    }

    public void haGenerar(String haTitulo, HAInformeVerificacion haInforme, File haDestino) throws IOException {
        try (OutputStream haSalida = new FileOutputStream(haDestino)) {
            Document haDoc = new Document(PageSize.A4, 48, 48, 48, 48);
            PdfWriter.getInstance(haDoc, haSalida);
            haDoc.addTitle(haTitulo);
            haDoc.addCreator("PDF Listo");
            haDoc.open();

            haDoc.add(new Paragraph(haTitulo, haFuente(18, Font.BOLD, HA_TEXTO)));
            haDoc.add(new Paragraph("Generado por PDF Listo, sin internet", haFuente(9, Font.NORMAL, HA_SUAVE)));
            haDoc.add(haEspacio(10));

            PdfPTable haDatos = new PdfPTable(new float[]{1.2f, 4f});
            haDatos.setWidthPercentage(100);
            haFila(haDatos, "Documento", haInforme.haDocumento());
            haFila(haDatos, "Fecha", haInforme.haFecha().format(HA_FECHA));
            if (haInforme.haPedido() != null) {
                haFila(haDatos, "Pedido", "«" + haInforme.haPedido() + "»");
            }
            haDoc.add(haDatos);
            haDoc.add(haEspacio(10));

            boolean haOk = haInforme.haCumple();
            PdfPTable haVeredicto = new PdfPTable(1);
            haVeredicto.setWidthPercentage(100);
            PdfPCell haCelda = new PdfPCell(new Phrase(haOk ? "CUMPLE" : "REVISAR", haFuente(16, Font.BOLD, haOk ? HA_VERDE : HA_AMBAR)));
            haCelda.setBackgroundColor(haOk ? HA_VERDE_FONDO : HA_AMBAR_FONDO);
            haCelda.setBorder(Rectangle.NO_BORDER);
            haCelda.setPadding(10);
            haCelda.setHorizontalAlignment(Element.ALIGN_CENTER);
            haVeredicto.addCell(haCelda);
            haDoc.add(haVeredicto);
            haDoc.add(haEspacio(10));

            if (!haInforme.haPasos().isEmpty()) {
                haDoc.add(new Paragraph("Pasos ejecutados (sobre una copia; el original no se modificó)", haFuente(11, Font.BOLD, HA_TEXTO)));
                int haN = 1;
                for (String haPaso : haInforme.haPasos()) {
                    haDoc.add(new Paragraph(haN++ + ". " + haPaso, haFuente(10, Font.NORMAL, HA_TEXTO)));
                }
                haDoc.add(haEspacio(10));
            }

            haDoc.add(new Paragraph("Comprobaciones", haFuente(11, Font.BOLD, HA_TEXTO)));
            haDoc.add(haEspacio(4));
            PdfPTable haTabla = new PdfPTable(new float[]{1.6f, 1f, 4.4f});
            haTabla.setWidthPercentage(100);
            haTabla.setHeaderRows(1);
            for (String haT : new String[]{"Comprobación", "Estado", "Detalle"}) {
                PdfPCell haC = new PdfPCell(new Phrase(haT, haFuente(9, Font.BOLD, HA_TEXTO)));
                haC.setBackgroundColor(HA_GRIS_FONDO);
                haC.setBorderColor(HA_BORDE);
                haC.setPadding(6);
                haTabla.addCell(haC);
            }
            for (HAComprobacion haC : haInforme.haComprobaciones()) {
                if (haC.haEstado() == HAComprobacion.HAEstado.NO_APLICA) {
                    continue;
                }
                haTabla.addCell(haCelda(haC.haNombre(), haFuente(9, Font.BOLD, HA_TEXTO), null));
                Color haFondo = switch (haC.haEstado()) {
                    case CUMPLE -> HA_VERDE_FONDO;
                    case REVISAR -> HA_AMBAR_FONDO;
                    default -> null;
                };
                Color haColor = switch (haC.haEstado()) {
                    case CUMPLE -> HA_VERDE;
                    case REVISAR -> HA_AMBAR;
                    default -> HA_SUAVE;
                };
                haTabla.addCell(haCelda(haC.haEstado().toString(), haFuente(9, Font.BOLD, haColor), haFondo));
                haTabla.addCell(haCelda(haC.haDetalle(), haFuente(9, Font.NORMAL, HA_TEXTO), null));
            }
            haDoc.add(haTabla);
            haDoc.close();
        }
    }

    private static void haFila(PdfPTable haT, String haEtiqueta, String haValor) {
        PdfPCell haA = new PdfPCell(new Phrase(haEtiqueta, haFuente(9, Font.BOLD, HA_SUAVE)));
        PdfPCell haB = new PdfPCell(new Phrase(haValor, haFuente(10, Font.NORMAL, HA_TEXTO)));
        for (PdfPCell haC : new PdfPCell[]{haA, haB}) {
            haC.setBorder(Rectangle.NO_BORDER);
            haC.setPaddingBottom(4);
            haT.addCell(haC);
        }
    }

    private static PdfPCell haCelda(String haTexto, Font haFuente, Color haFondo) {
        PdfPCell haC = new PdfPCell(new Phrase(haTexto, haFuente));
        haC.setBorderColor(HA_BORDE);
        haC.setPadding(6);
        if (haFondo != null) {
            haC.setBackgroundColor(haFondo);
        }
        return haC;
    }

    private static Paragraph haEspacio(float haAlto) {
        Paragraph haP = new Paragraph(" ");
        haP.setLeading(haAlto);
        return haP;
    }

    private static Font haFuente(float haTamano, int haEstilo, Color haColor) {
        return new Font(Font.HELVETICA, haTamano, haEstilo, haColor);
    }
}
