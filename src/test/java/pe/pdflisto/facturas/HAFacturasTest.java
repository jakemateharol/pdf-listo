package pe.pdflisto.facturas;

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
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.operaciones.HAControl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HAFacturasTest {
    private static final String HA_RUC_EMISOR = "20100047218";
    private static final String HA_RUC_CLIENTE = "20131312955";

    @TempDir
    Path haCarpeta;

    @Test
    void haEntiendeElQrDeSunatYSusVariantes() {
        HADatosQr haF = HAParserQrSunat.haParsear(HA_RUC_EMISOR + "|01|F001|00012345|180.00|1180.00|2026-11-10|6|" + HA_RUC_CLIENTE + "|abc=|").orElseThrow();
        assertEquals("Factura", haF.haNombreTipo());
        assertEquals("F001-00012345", haF.haSerieNumero());
        assertEquals(new BigDecimal("180.00"), haF.haIgv());
        assertEquals(new BigDecimal("1180.00"), haF.haTotal());
        assertEquals(LocalDate.of(2026, 11, 10), haF.haFecha());
        assertEquals("RUC " + HA_RUC_CLIENTE, haF.haNombreDocCliente());

        HADatosQr haBoleta = HAParserQrSunat.haParsear(HA_RUC_EMISOR + " | 3 | B002 | 789 | 9,00 | 59,00 | 10/11/2026").orElseThrow();
        assertEquals("03", haBoleta.haTipo());
        assertEquals("Boleta", haBoleta.haNombreTipo());
        assertEquals(new BigDecimal("59.00"), haBoleta.haTotal());
        assertEquals("", haBoleta.haNombreDocCliente(), "boleta sin datos del cliente");

        assertEquals(new BigDecimal("1500.00"), HAParserQrSunat.haMonto("1,500.00"));
        assertTrue(HAParserQrSunat.haParsear("https://e-consulta.sunat.gob.pe/abc").isEmpty(), "un enlace no es un comprobante");
        assertTrue(HAParserQrSunat.haParsear("123|01|F001|1|1|2|2026-01-01").isEmpty(), "RUC de 3 dígitos");
    }

    @Test
    void haValidaIgvRucFechaYDuplicados() {
        List<HAComprobante> haLista = new ArrayList<>(List.of(
                haComp("ok.pdf", HA_RUC_EMISOR + "|01|F001|1|180.00|1180.00|2026-11-10|6|" + HA_RUC_CLIENTE),
                haComp("mype.pdf", HA_RUC_EMISOR + "|03|B001|2|10.00|110.00|2026-11-10|1|45678912"),
                haComp("exonerado.pdf", HA_RUC_EMISOR + "|01|F001|3|0.00|500.00|2026-11-10|6|" + HA_RUC_CLIENTE),
                haComp("no-cuadra.pdf", HA_RUC_EMISOR + "|01|F001|4|50.00|1000.00|2026-11-10|6|" + HA_RUC_CLIENTE),
                haComp("ruc-malo.pdf", "20100047219|01|F001|5|180.00|1180.00|2026-11-10|6|" + HA_RUC_CLIENTE),
                haComp("futura.pdf", HA_RUC_EMISOR + "|01|F001|6|180.00|1180.00|2031-01-01|6|" + HA_RUC_CLIENTE),
                haComp("copia.pdf", HA_RUC_EMISOR + "|01|F001|0001|180.00|1180.00|2026-11-10|6|" + HA_RUC_CLIENTE)));
        HAValidadorComprobantes.haValidar(haLista, LocalDate.of(2026, 11, 14));
        Map<String, HAComprobante> haPorArchivo = new java.util.HashMap<>();
        haLista.forEach(haC -> haPorArchivo.put(haC.haArchivo(), haC));

        assertEquals(HAComprobante.HAEstado.CUMPLE, haPorArchivo.get("ok.pdf").haEstado());
        assertEquals(HAComprobante.HAEstado.CUMPLE, haPorArchivo.get("mype.pdf").haEstado());
        assertTrue(haPorArchivo.get("mype.pdf").haObservacionesTexto().contains("10 %"));
        assertEquals(HAComprobante.HAEstado.NO_COMPROBABLE, haPorArchivo.get("exonerado.pdf").haEstado());
        assertTrue(haPorArchivo.get("exonerado.pdf").haObservacionesTexto().contains("Sin IGV"));
        assertEquals(HAComprobante.HAEstado.NO_COMPROBABLE, haPorArchivo.get("no-cuadra.pdf").haEstado());
        assertTrue(haPorArchivo.get("no-cuadra.pdf").haObservacionesTexto().contains("No se puede comprobar"));
        assertEquals(HAComprobante.HAEstado.REVISAR, haPorArchivo.get("ruc-malo.pdf").haEstado());
        assertEquals(HAComprobante.HAEstado.REVISAR, haPorArchivo.get("futura.pdf").haEstado());
        assertEquals(HAComprobante.HAEstado.DUPLICADO, haPorArchivo.get("copia.pdf").haEstado(), "F001-0001 = F001-1");
        assertTrue(haPorArchivo.get("copia.pdf").haObservacionesTexto().contains("ok.pdf"));
    }

    @Test
    void haTresEstadosDelIgv() {
        String haBase = HA_RUC_EMISOR + "|01|F001|";
        String haCola = "|2026-11-10|6|" + HA_RUC_CLIENTE;
        List<HAComprobante> haLista = new ArrayList<>(List.of(
                haComp("mayor.pdf", haBase + "1|300.00|1180.00" + haCola),
                haConTexto("menor-sin-motivo.pdf", haBase + "2|150.00|1350.00" + haCola,
                        "FACTURA F001-2 Mantenimiento S/ 1,200.00 IGV (18%) S/ 150.00 IMPORTE TOTAL S/ 1,350.00"),
                haConTexto("con-exonerado.pdf", haBase + "3|90.00|690.00" + haCola,
                        "FACTURA F001-3 Op. gravada S/ 500.00 Op. exonerada S/ 100.00 IGV S/ 90.00 TOTAL S/ 690.00"),
                haConTexto("bolsa.pdf", haBase + "4|18.00|123.00" + haCola,
                        "BOLETA Productos S/ 100.00 IGV S/ 18.00 ICBPER S/ 5.00 TOTAL S/ 123.00"),
                haConTexto("cumple.pdf", haBase + "5|18.00|118.00" + haCola, "FACTURA F001-5 IGV S/ 18.00 TOTAL S/ 118.00")));
        HAValidadorComprobantes.haValidar(haLista, LocalDate.of(2026, 11, 14));
        Map<String, HAComprobante> haE = new java.util.HashMap<>();
        haLista.forEach(haC -> haE.put(haC.haArchivo(), haC));

        assertEquals(HAComprobante.HAEstado.REVISAR, haE.get("mayor.pdf").haEstado());
        assertTrue(haE.get("mayor.pdf").haObservacionesTexto().contains("MAYOR"));
        assertEquals(HAComprobante.HAEstado.REVISAR, haE.get("menor-sin-motivo.pdf").haEstado());
        assertTrue(haE.get("menor-sin-motivo.pdf").haObservacionesTexto().contains("no menciona"));
        assertEquals(HAComprobante.HAEstado.NO_COMPROBABLE, haE.get("con-exonerado.pdf").haEstado());
        assertEquals(HAComprobante.HAEstado.NO_COMPROBABLE, haE.get("bolsa.pdf").haEstado());
        assertEquals(HAComprobante.HAEstado.CUMPLE, haE.get("cumple.pdf").haEstado());
        assertEquals("No se puede comprobar", HAComprobante.HAEstado.NO_COMPROBABLE.toString());
    }

    @Test
    void haUnErrorSeguroPesaMasQueNoSePuedeComprobar() {
        HAComprobante haC = haComp("exonerado-futuro.pdf", HA_RUC_EMISOR + "|01|F001|9|0.00|500.00|2031-01-01|6|" + HA_RUC_CLIENTE);
        HAValidadorComprobantes.haValidar(new ArrayList<>(List.of(haC)), LocalDate.of(2026, 11, 14));
        assertEquals(HAComprobante.HAEstado.REVISAR, haC.haEstado());
        assertTrue(haC.haObservacionesTexto().contains("Sin IGV") && haC.haObservacionesTexto().contains("futura"));
    }

    @Test
    void haElOriginalEsElArchivoMasAntiguoNoElPrimeroAlfabeticamente() throws Exception {
        String haQr = HA_RUC_EMISOR + "|01|F001|00000101|180.00|1180.00|2026-11-03|6|" + HA_RUC_CLIENTE + "|";
        String[] haTexto = {"FACTURA F001-00000101 TOTAL S/ 1,180.00"};
        File haAntigua = haFactura("zz-original.pdf", new String[]{haQr}, haTexto);
        File haNueva = haFactura("aa-copia.pdf", new String[]{haQr}, haTexto);
        assertTrue(haAntigua.setLastModified(System.currentTimeMillis() - 86_400_000L));
        List<HAComprobante> haLista = new HALectorFacturas().haLeer(List.of(haNueva, haAntigua), HAControl.haNinguno());
        assertEquals(HAComprobante.HAEstado.DUPLICADO, haLista.get(0).haEstado());
        assertEquals(HAComprobante.HAEstado.CUMPLE, haLista.get(1).haEstado());
        assertTrue(haLista.get(0).haObservacionesTexto().contains("zz-original.pdf"));
    }

    @Test
    void haLeeVariosPdfYExportaUnExcelConFormulas() throws Exception {
        File haLote = haFactura("lote.pdf",
                new String[]{HA_RUC_EMISOR + "|01|F001|00000101|180.00|1180.00|2026-11-03|6|" + HA_RUC_CLIENTE + "|",
                        HA_RUC_EMISOR + "|03|B001|00000202|9.00|59.00|2026-11-04|1|45678912|"},
                new String[]{"FACTURA ELECTRONICA F001-00000101   TOTAL S/ 1,180.00", "BOLETA ELECTRONICA B001-00000202 TOTAL: 59.00"});
        File haRepetida = haFactura("repetida.pdf",
                new String[]{HA_RUC_EMISOR + "|01|F001|101|180.00|1180.00|2026-11-03|6|" + HA_RUC_CLIENTE + "|"},
                new String[]{"Copia de la misma factura. TOTAL S/ 1,180.00"});
        File haImpresoDistinto = haFactura("total-distinto.pdf",
                new String[]{HA_RUC_EMISOR + "|01|F002|7|36.00|236.00|2026-11-05|6|" + HA_RUC_CLIENTE + "|"},
                new String[]{"FACTURA F002-7 cuyo total impreso es S/ 263.00 (error de digitación)"});
        File haSinQr = haCarpeta.resolve("sin-qr.pdf").toFile();
        try (PDDocument haD = new PDDocument()) {
            haD.addPage(new PDPage());
            haD.save(haSinQr);
        }

        List<HAComprobante> haLista = new HALectorFacturas().haLeer(List.of(haLote, haRepetida, haImpresoDistinto, haSinQr), HAControl.haNinguno());
        assertEquals(5, haLista.size(), haLista.toString());
        assertEquals(List.of(HAComprobante.HAEstado.CUMPLE, HAComprobante.HAEstado.CUMPLE, HAComprobante.HAEstado.DUPLICADO,
                HAComprobante.HAEstado.REVISAR, HAComprobante.HAEstado.SIN_QR), haLista.stream().map(HAComprobante::haEstado).toList());
        assertTrue(haLista.get(3).haObservacionesTexto().contains("no aparece impreso"));
        assertEquals(2, haLista.get(1).haPagina());

        File haExcel = haCarpeta.resolve("facturas.xlsx").toFile();
        new HAExportadorFacturas().haExportar(haLista, haExcel);
        try (XSSFWorkbook haLibro = new XSSFWorkbook(new FileInputStream(haExcel))) {
            Sheet haHoja = haLibro.getSheet("Comprobantes");
            assertEquals(6, haHoja.getLastRowNum(), "cabecera + 5 filas + totales");
            assertEquals(CellType.FORMULA, haHoja.getRow(1).getCell(11).getCellType(), "el IGV esperado es una fórmula");
            FormulaEvaluator haEv = haLibro.getCreationHelper().createFormulaEvaluator();
            haEv.evaluateAll();
            assertEquals(1000.00, haEv.evaluate(haHoja.getRow(1).getCell(10)).getNumberValue(), 0.001, "base = total − IGV");
            assertEquals(1475.00, haEv.evaluate(haHoja.getRow(6).getCell(9)).getNumberValue(), 0.001);
            Sheet haResumen = haLibro.getSheet("Resumen");
            assertEquals(4, (int) haEv.evaluate(haResumen.getRow(4).getCell(1)).getNumberValue(), "comprobantes leídos");
            assertEquals(1, (int) haEv.evaluate(haResumen.getRow(7).getCell(1)).getNumberValue(), "duplicados");
        }
    }

    private static HAComprobante haComp(String haArchivo, String haQr) {
        return haConTexto(haArchivo, haQr, null);
    }

    private static HAComprobante haConTexto(String haArchivo, String haQr, String haTextoPagina) {
        return new HAComprobante(haArchivo, 1, HAParserQrSunat.haParsear(haQr).orElseThrow(), haQr, haTextoPagina);
    }

    private File haFactura(String haNombre, String[] haQrs, String[] haTextos) throws Exception {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            for (int haI = 0; haI < haQrs.length; haI++) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haDoc.addPage(haP);
                var haMatriz = new QRCodeWriter().encode(haQrs[haI], BarcodeFormat.QR_CODE, 300, 300,
                        Map.of(EncodeHintType.MARGIN, 2));
                var haImagen = LosslessFactory.createFromImage(haDoc, MatrixToImageWriter.toBufferedImage(haMatriz));
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                    haCs.beginText();
                    haCs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    haCs.newLineAtOffset(60, 760);
                    haCs.showText(haTextos[haI]);
                    haCs.endText();
                    haCs.drawImage(haImagen, 60, 80, 110, 110);
                }
            }
            haDoc.save(haArchivo);
        }
        assertTrue(Files.size(haArchivo.toPath()) > 0);
        return haArchivo;
    }
}
