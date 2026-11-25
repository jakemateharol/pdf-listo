package pe.pdflisto.herramientas;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import pe.pdflisto.privacidad.HAValidadorRuc;

import java.io.File;
import java.util.Map;

public final class HAGenerarFacturaExonerada {
    private HAGenerarFacturaExonerada() {
    }

    public static void main(String[] haArgs) throws Exception {
        File haSalida = new File(haArgs.length > 0 ? haArgs[0] : "ejemplos/facturas/F002-00000310 Minimarket (con exonerados).pdf");
        String haRuc = haRucValido("2061234567");
        String haQr = haRuc + "|01|F002|00000310|36.00|336.00|2026-11-12|6|20609998882|";
        String[] haLineas = {
                "MINIMARKET SAN MARTIN S.A.C.  (EJEMPLO FICTICIO)",
                "RUC " + haRuc,
                "FACTURA ELECTRONICA",
                "F002-00000310",
                "Fecha de emision: 12/11/2026",
                "Cliente: RUC 20609998882 - MI EMPRESA DEMO S.A.C.",
                "Cant.  Descripcion                       Importe",
                "10     Arroz superior x 5 kg      S/ 100.00   (exonerado)",
                "20     Detergente x 1 kg          S/ 200.00   (gravado)",
                "Op. gravada:     S/ 200.00",
                "Op. exonerada:   S/ 100.00",
                "IGV (18%):       S/ 36.00",
                "IMPORTE TOTAL:   S/ 336.00"};
        try (PDDocument haDoc = new PDDocument()) {
            PDPage haP = new PDPage(PDRectangle.A4);
            haDoc.addPage(haP);
            var haMatriz = new QRCodeWriter().encode(haQr, BarcodeFormat.QR_CODE, 300, 300, Map.of(EncodeHintType.MARGIN, 2));
            var haImagen = LosslessFactory.createFromImage(haDoc, MatrixToImageWriter.toBufferedImage(haMatriz));
            var haNormal = new PDType1Font(Standard14Fonts.FontName.COURIER);
            var haNegrita = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
            try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                float haY = 780;
                for (int haI = 0; haI < haLineas.length; haI++) {
                    haCs.beginText();
                    haCs.setFont(haI == 0 || haI == 2 || haI == haLineas.length - 1 ? haNegrita : haNormal, 11);
                    haCs.newLineAtOffset(60, haY);
                    haCs.showText(haLineas[haI]);
                    haCs.endText();
                    haY -= haI == 5 || haI == 8 ? 30 : 18;
                }
                haCs.drawImage(haImagen, 60, 80, 110, 110);
            }
            haDoc.getDocumentInformation().setTitle("Factura de ejemplo con montos exonerados (ficticia)");
            haDoc.save(haSalida);
        }
        System.out.println("Creado: " + haSalida.getAbsolutePath() + "  QR: " + haQr);
    }

    private static String haRucValido(String haDiezDigitos) {
        for (int haD = 0; haD <= 9; haD++) {
            if (HAValidadorRuc.haValido(haDiezDigitos + haD)) {
                return haDiezDigitos + haD;
            }
        }
        throw new IllegalStateException("No hay dígito verificador para " + haDiezDigitos);
    }
}
