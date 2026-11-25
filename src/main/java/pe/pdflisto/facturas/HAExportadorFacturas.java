package pe.pdflisto.facturas;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HAExportadorFacturas {
    static final String[] HA_TITULOS = {"Archivo", "Pág.", "Tipo", "Serie", "Número", "Fecha", "RUC emisor",
            "Cliente", "IGV", "Total", "Base (Total − IGV)", "IGV esperado 18 %", "Diferencia IGV", "Estado", "Observaciones"};
    private static final int[] HA_ANCHOS = {28, 6, 15, 8, 11, 12, 14, 18, 12, 13, 15, 15, 13, 12, 70};

    public void haExportar(List<HAComprobante> haComprobantes, File haDestino) throws IOException {
        try (XSSFWorkbook haLibro = new XSSFWorkbook()) {
            HALibroExcel.HAEstilos haE = new HALibroExcel.HAEstilos(haLibro);
            Sheet haHoja = haLibro.createSheet("Comprobantes");
            HALibroExcel.haPrepararTabla(haHoja, haE, HA_TITULOS, HA_ANCHOS);
            int haR = 1;
            for (HAComprobante haC : haComprobantes) {
                Row haFila = haHoja.createRow(haR);
                int haN = haR + 1;
                HALibroExcel.haTexto(haFila, 0, haC.haArchivo(), haE.haCelda);
                if (haC.haPagina() > 0) {
                    HALibroExcel.haEntero(haFila, 1, haC.haPagina(), haE.haEntero);
                } else {
                    haFila.createCell(1).setCellStyle(haE.haCelda);
                }
                HADatosQr haD = haC.haDatos();
                if (haD != null) {
                    HALibroExcel.haTexto(haFila, 2, haD.haNombreTipo(), haE.haCelda);
                    HALibroExcel.haTexto(haFila, 3, haD.haSerie(), haE.haCelda);
                    HALibroExcel.haTexto(haFila, 4, haD.haNumero(), haE.haCelda);
                    HALibroExcel.haFecha(haFila, 5, haD.haFecha(), haE.haFecha);
                    HALibroExcel.haTexto(haFila, 6, haD.haRucEmisor(), haE.haCelda);
                    HALibroExcel.haTexto(haFila, 7, haD.haNombreDocCliente(), haE.haCelda);
                    HALibroExcel.haNumero(haFila, 8, haD.haIgv(), haE.haMoneda);
                    HALibroExcel.haNumero(haFila, 9, haD.haTotal(), haE.haMoneda);
                    HALibroExcel.haFormula(haFila, 10, "J" + haN + "-I" + haN, haE.haMoneda);
                    HALibroExcel.haFormula(haFila, 11, "ROUND(K" + haN + "*0.18,2)", haE.haMoneda);
                    HALibroExcel.haFormula(haFila, 12, "I" + haN + "-L" + haN, haE.haMoneda);
                } else {
                    for (int haCol = 2; haCol <= 12; haCol++) {
                        haFila.createCell(haCol).setCellStyle(haE.haCelda);
                    }
                }
                HALibroExcel.haTexto(haFila, 13, haC.haEstado().toString(), haEstiloEstado(haE, haC.haEstado()));
                HALibroExcel.haTexto(haFila, 14, haC.haObservacionesTexto(), haE.haCelda);
                haR++;
            }
            int haUltima = haR;
            if (haR > 1) {
                haHoja.setAutoFilter(new CellRangeAddress(0, haR - 1, 0, HA_TITULOS.length - 1));
            }
            Row haTotales = haHoja.createRow(haR);
            for (int haCol = 0; haCol < HA_TITULOS.length; haCol++) {
                haTotales.createCell(haCol).setCellStyle(haE.haTotal);
            }
            HALibroExcel.haTexto(haTotales, 0, "TOTAL (sin duplicados)", haE.haTotal);
            for (int haCol : new int[]{8, 9, 10}) {
                String haLetra = String.valueOf((char) ('A' + haCol));
                HALibroExcel.haFormula(haTotales, haCol, "SUMIFS(" + haLetra + "2:" + haLetra + haUltima + ",N2:N" + haUltima + ",\"<>Duplicado\")",
                        haE.haTotalMoneda);
            }
            haResumen(haLibro, haE, haUltima);
            try (OutputStream haSalida = new FileOutputStream(haDestino)) {
                haLibro.write(haSalida);
            }
        }
    }

    private static void haResumen(XSSFWorkbook haLibro, HALibroExcel.HAEstilos haE, int haUltima) {
        Sheet haH = haLibro.createSheet("Resumen");
        haH.setColumnWidth(0, 40 * 256);
        haH.setColumnWidth(1, 18 * 256);
        haH.setColumnWidth(2, 18 * 256);
        Row haTitulo = haH.createRow(0);
        HALibroExcel.haTexto(haTitulo, 0, "Resumen de comprobantes", haE.haTitulo);
        HALibroExcel.haTexto(haH.createRow(1), 0, "Generado por PDF Listo el "
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                + " · leído del código QR, sin internet", haE.haSubtitulo);

        String haEst = "Comprobantes!N2:N" + haUltima;
        String haTipo = "Comprobantes!C2:C" + haUltima;
        String[][] haFilas = {
                {"Comprobantes leídos", "COUNTA(Comprobantes!G2:G" + haUltima + ")"},
                {"Cumplen", "COUNTIF(" + haEst + ",\"Cumple\")"},
                {"No se pueden comprobar", "COUNTIF(" + haEst + ",\"No se puede comprobar\")"},
                {"Para revisar", "COUNTIF(" + haEst + ",\"Revisar\")"},
                {"Duplicados (no se suman)", "COUNTIF(" + haEst + ",\"Duplicado\")"},
                {"Archivos o páginas sin QR", "COUNTIF(" + haEst + ",\"Sin QR\")"}};
        int haR = 3;
        HALibroExcel.haCabecera(haH.createRow(haR++), haE, "Concepto", "Cantidad");
        for (String[] haF : haFilas) {
            Row haFila = haH.createRow(haR++);
            HALibroExcel.haTexto(haFila, 0, haF[0], haE.haCelda);
            HALibroExcel.haFormula(haFila, 1, haF[1], haE.haEntero);
        }
        haR++;
        HALibroExcel.haCabecera(haH.createRow(haR++), haE, "Por tipo (sin duplicados)", "Cantidad", "Total S/");
        for (String haT : new String[]{"Factura", "Boleta", "Nota de crédito", "Nota de débito"}) {
            Row haFila = haH.createRow(haR++);
            HALibroExcel.haTexto(haFila, 0, haT, haE.haCelda);
            HALibroExcel.haFormula(haFila, 1, "COUNTIFS(" + haTipo + ",\"" + haT + "\"," + haEst + ",\"<>Duplicado\")", haE.haEntero);
            HALibroExcel.haFormula(haFila, 2, "SUMIFS(Comprobantes!J2:J" + haUltima + "," + haTipo + ",\"" + haT + "\"," + haEst
                    + ",\"<>Duplicado\")", haE.haMoneda);
        }
        haR++;
        Row haIgv = haH.createRow(haR++);
        HALibroExcel.haTexto(haIgv, 0, "IGV total (sin duplicados)", haE.haCeldaNegrita);
        HALibroExcel.haFormula(haIgv, 2, "SUMIFS(Comprobantes!I2:I" + haUltima + "," + haEst + ",\"<>Duplicado\")", haE.haMonedaNegrita);
        Row haTotal = haH.createRow(haR++);
        HALibroExcel.haTexto(haTotal, 0, "Importe total (sin duplicados)", haE.haCeldaNegrita);
        HALibroExcel.haFormula(haTotal, 2, "SUMIFS(Comprobantes!J2:J" + haUltima + "," + haEst + ",\"<>Duplicado\")", haE.haMonedaNegrita);
        haR++;
        HALibroExcel.haTexto(haH.createRow(haR), 0, "Nota: las notas de crédito se muestran aparte; réstalas si corresponde.", haE.haSubtitulo);
    }

    private static XSSFCellStyle haEstiloEstado(HALibroExcel.HAEstilos haE, HAComprobante.HAEstado haEstado) {
        return switch (haEstado) {
            case CUMPLE -> haE.haEstadoEmitida;
            case NO_COMPROBABLE -> haE.haEstadoInfo;
            case REVISAR -> haE.haEstadoAdvertencia;
            default -> haE.haEstadoAnulada;
        };
    }
}
