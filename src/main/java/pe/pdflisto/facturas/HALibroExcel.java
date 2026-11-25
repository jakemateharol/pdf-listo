package pe.pdflisto.facturas;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

final class HALibroExcel {
    private HALibroExcel() {
    }

    static void haPrepararTabla(Sheet haHoja, HAEstilos haE, String[] haTitulos, int[] haAnchos) {
        haCabecera(haHoja.createRow(0), haE, haTitulos);
        for (int haI = 0; haI < haAnchos.length; haI++) {
            haHoja.setColumnWidth(haI, haAnchos[haI] * 256);
        }
        haHoja.createFreezePane(0, 1);
    }

    static void haCerrarTabla(Sheet haHoja, HAEstilos haE, int haColumnas, int haSiguienteFila, int[] haEnteros, int[] haImportes) {
        if (haSiguienteFila > 1) {
            haHoja.setAutoFilter(new CellRangeAddress(0, haSiguienteFila - 1, 0, haColumnas - 1));
        }
        Row haFila = haHoja.createRow(haSiguienteFila);
        for (int haC = 0; haC < haColumnas; haC++) {
            haFila.createCell(haC).setCellStyle(haE.haTotal);
        }
        haFilaTotales(haFila, haE, 1, haSiguienteFila - 1, 0, haEnteros, haImportes);
    }

    static void haFilaTotales(Row haFila, HAEstilos haE, int haDesde, int haHasta, int haColEtiqueta, int[] haEnteros, int[] haImportes) {
        haTexto(haFila, haColEtiqueta, "TOTAL", haE.haTotal);
        for (int haC : haEnteros) {
            haFormula(haFila, haC, haSubtotal(haC, haDesde, haHasta), haE.haTotalEntero);
        }
        for (int haC : haImportes) {
            haFormula(haFila, haC, haSubtotal(haC, haDesde, haHasta), haE.haTotalMoneda);
        }
    }

    static String haSubtotal(int haColumna, int haDesde, int haHasta) {
        if (haHasta < haDesde) {
            return "0";
        }
        String haCol = CellReference.convertNumToColString(haColumna);
        return "SUBTOTAL(109," + haCol + (haDesde + 1) + ":" + haCol + (haHasta + 1) + ")";
    }

    static void haCabecera(Row haFila, HAEstilos haE, String... haTitulos) {
        haFila.setHeightInPoints(22);
        for (int haI = 0; haI < haTitulos.length; haI++) {
            haTexto(haFila, haI, haTitulos[haI], haE.haCabecera);
        }
    }

    static void haTexto(Row haFila, int haCol, String haValor, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellValue(haValor == null ? "" : haValor);
        haC.setCellStyle(haEstilo);
    }

    static void haNumero(Row haFila, int haCol, BigDecimal haValor, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellValue(haValor == null ? 0 : haValor.doubleValue());
        haC.setCellStyle(haEstilo);
    }

    static void haEntero(Row haFila, int haCol, long haValor, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellValue(haValor);
        haC.setCellStyle(haEstilo);
    }

    static void haFecha(Row haFila, int haCol, LocalDate haValor, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellValue(Date.from(haValor.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        haC.setCellStyle(haEstilo);
    }

    static void haFechaHora(Row haFila, int haCol, LocalDateTime haValor, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellValue(haValor);
        haC.setCellStyle(haEstilo);
    }

    static void haFormula(Row haFila, int haCol, String haFormula, CellStyle haEstilo) {
        Cell haC = haFila.createCell(haCol);
        haC.setCellFormula(haFormula);
        haC.setCellStyle(haEstilo);
    }

    static final class HAEstilos {
        final XSSFCellStyle haTitulo, haSubtitulo, haSeccion, haCabecera, haCelda, haCeldaNegrita, haAnulada;
        final XSSFCellStyle haEntero, haMoneda, haMonedaNegrita, haPorcentaje, haFecha, haMes, haFechaHora;
        final XSSFCellStyle haEstadoEmitida, haEstadoAnulada, haEstadoAdvertencia, haEstadoInfo, haTotal, haTotalEntero, haTotalMoneda;

        HAEstilos(XSSFWorkbook haLibro) {
            XSSFColor haAcento = haColor(42, 120, 214);
            XSSFColor haBordeColor = haColor(216, 222, 228);
            XSSFColor haFondoTotal = haColor(234, 242, 252);
            short haFmtMoneda = haLibro.createDataFormat().getFormat("\"S/\" #,##0.00");
            short haFmtEntero = haLibro.createDataFormat().getFormat("#,##0");

            haTitulo = haEstilo(haLibro, haFuente(haLibro, 18, true, haColor(31, 35, 40)), null, null);
            haSubtitulo = haEstilo(haLibro, haFuente(haLibro, 10, false, haColor(101, 109, 118)), null, null);
            haSeccion = haEstilo(haLibro, haFuente(haLibro, 12, true, haAcento), null, null);

            haCabecera = haEstilo(haLibro, haFuente(haLibro, 10, true, haColor(255, 255, 255)), haAcento, null);
            haCabecera.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFFont haNormal = haFuente(haLibro, 10, false, haColor(31, 35, 40));
            XSSFFont haNegrita = haFuente(haLibro, 10, true, haColor(31, 35, 40));
            haCelda = haEstilo(haLibro, haNormal, null, haBordeColor);
            haCeldaNegrita = haEstilo(haLibro, haNegrita, null, haBordeColor);

            XSSFFont haTachada = haFuente(haLibro, 10, false, haColor(140, 149, 159));
            haTachada.setStrikeout(true);
            haAnulada = haEstilo(haLibro, haTachada, null, haBordeColor);

            haEntero = haEstilo(haLibro, haNormal, null, haBordeColor);
            haEntero.setDataFormat(haFmtEntero);
            haEntero.setAlignment(HorizontalAlignment.CENTER);
            haMoneda = haEstilo(haLibro, haNormal, null, haBordeColor);
            haMoneda.setDataFormat(haFmtMoneda);
            haMonedaNegrita = haEstilo(haLibro, haNegrita, null, haBordeColor);
            haMonedaNegrita.setDataFormat(haFmtMoneda);
            haPorcentaje = haEstilo(haLibro, haNormal, null, haBordeColor);
            haPorcentaje.setDataFormat(haLibro.createDataFormat().getFormat("0.0%"));
            haFecha = haEstilo(haLibro, haNormal, null, haBordeColor);
            haFecha.setDataFormat(haLibro.createDataFormat().getFormat("dd/mm/yyyy"));
            haMes = haEstilo(haLibro, haNormal, null, haBordeColor);
            haMes.setDataFormat(haLibro.createDataFormat().getFormat("mmm yyyy"));
            haFechaHora = haEstilo(haLibro, haNormal, null, haBordeColor);
            haFechaHora.setDataFormat(haLibro.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

            haEstadoEmitida = haEstilo(haLibro, haFuente(haLibro, 9, true, haColor(26, 127, 55)), haColor(218, 251, 225), haBordeColor);
            haEstadoEmitida.setAlignment(HorizontalAlignment.CENTER);
            haEstadoAnulada = haEstilo(haLibro, haFuente(haLibro, 9, true, haColor(207, 34, 46)), haColor(255, 235, 233), haBordeColor);
            haEstadoAnulada.setAlignment(HorizontalAlignment.CENTER);
            haEstadoAdvertencia = haEstilo(haLibro, haFuente(haLibro, 9, true, haColor(154, 103, 0)), haColor(255, 248, 197), haBordeColor);
            haEstadoAdvertencia.setAlignment(HorizontalAlignment.CENTER);
            haEstadoInfo = haEstilo(haLibro, haFuente(haLibro, 9, true, haColor(9, 105, 218)), haColor(221, 244, 255), haBordeColor);
            haEstadoInfo.setAlignment(HorizontalAlignment.CENTER);

            XSSFFont haFuenteTotal = haFuente(haLibro, 10, true, haAcento);
            haTotal = haEstilo(haLibro, haFuenteTotal, haFondoTotal, null);
            haTotal.setBorderTop(BorderStyle.MEDIUM);
            haTotal.setTopBorderColor(haAcento);
            haTotalEntero = haLibro.createCellStyle();
            haTotalEntero.cloneStyleFrom(haTotal);
            haTotalEntero.setDataFormat(haFmtEntero);
            haTotalEntero.setAlignment(HorizontalAlignment.CENTER);
            haTotalMoneda = haLibro.createCellStyle();
            haTotalMoneda.cloneStyleFrom(haTotal);
            haTotalMoneda.setDataFormat(haFmtMoneda);
        }

        private static XSSFCellStyle haEstilo(XSSFWorkbook haLibro, XSSFFont haFuente, XSSFColor haFondo, XSSFColor haBorde) {
            XSSFCellStyle haS = haLibro.createCellStyle();
            haS.setFont(haFuente);
            haS.setVerticalAlignment(VerticalAlignment.CENTER);
            if (haFondo != null) {
                haS.setFillForegroundColor(haFondo);
                haS.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (haBorde != null) {
                haS.setBorderBottom(BorderStyle.THIN);
                haS.setBottomBorderColor(haBorde);
            }
            return haS;
        }

        private static XSSFFont haFuente(XSSFWorkbook haLibro, int haPuntos, boolean haNegrita, XSSFColor haColor) {
            XSSFFont haF = haLibro.createFont();
            haF.setFontName("Calibri");
            haF.setFontHeightInPoints((short) haPuntos);
            haF.setBold(haNegrita);
            haF.setColor(haColor);
            return haF;
        }

        private static XSSFColor haColor(int haR, int haG, int haB) {
            return new XSSFColor(new byte[]{(byte) haR, (byte) haG, (byte) haB}, null);
        }
    }
}
