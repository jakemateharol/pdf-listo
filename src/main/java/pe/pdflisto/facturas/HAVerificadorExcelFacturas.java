package pe.pdflisto.facturas;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class HAVerificadorExcelFacturas {
    private static final BigDecimal HA_TOLERANCIA = new BigDecimal("0.005");

    public HAInformeVerificacion haVerificar(File haExcel, List<HAComprobante> haComprobantes) {
        List<HAComprobacion> haLista = new ArrayList<>();
        try (Workbook haLibro = WorkbookFactory.create(haExcel, null, true)) {
            Sheet haHoja = haLibro.getSheet("Comprobantes");
            Sheet haResumen = haLibro.getSheet("Resumen");
            if (haHoja == null || haResumen == null) {
                haLista.add(new HAComprobacion("Archivo Excel", HAEstado.REVISAR, "Faltan las hojas «Comprobantes» o «Resumen»."));
                return haInforme(haExcel, haLista);
            }
            haLista.add(new HAComprobacion("Archivo Excel", HAEstado.CUMPLE, "Se abre correctamente y tiene las hojas "
                    + "«Comprobantes» y «Resumen»."));
            FormulaEvaluator haCalculo = haLibro.getCreationHelper().createFormulaEvaluator();
            haLista.add(haFilas(haHoja, haComprobantes));
            haLista.add(haFormulas(haLibro, haCalculo));
            haLista.add(haIgvPorFila(haHoja, haComprobantes, haCalculo));
            haLista.add(haTotales(haHoja, haResumen, haComprobantes, haCalculo));
        } catch (Exception haE) {
            log.warn("No se pudo verificar el Excel {}", haExcel, haE);
            haLista.add(new HAComprobacion("Archivo Excel", HAEstado.REVISAR, "No se pudo volver a abrir el Excel: " + haE.getMessage()));
        }
        return haInforme(haExcel, haLista);
    }

    private static HAInformeVerificacion haInforme(File haExcel, List<HAComprobacion> haLista) {
        return new HAInformeVerificacion(haExcel.getName(), LocalDateTime.now(), null, List.of("Exportar facturas a Excel"), haLista);
    }

    private static HAComprobacion haFilas(Sheet haHoja, List<HAComprobante> haComprobantes) {
        List<String> haMal = new ArrayList<>();
        for (int haI = 0; haI < haComprobantes.size(); haI++) {
            HAComprobante haC = haComprobantes.get(haI);
            Row haFila = haHoja.getRow(haI + 1);
            int haN = haI + 2;
            if (haFila == null) {
                haMal.add("falta la fila " + haN);
                continue;
            }
            List<String> haCampos = new ArrayList<>();
            if (!Objects.equals(haTexto(haFila, 0), haC.haArchivo())) haCampos.add("archivo");
            if (!Objects.equals(haTexto(haFila, 13), haC.haEstado().toString())) haCampos.add("estado");
            HADatosQr haD = haC.haDatos();
            if (haD != null) {
                if (!Objects.equals(haTexto(haFila, 2), haD.haNombreTipo())) haCampos.add("tipo");
                if (!Objects.equals(haTexto(haFila, 3), haD.haSerie())) haCampos.add("serie");
                if (!Objects.equals(haTexto(haFila, 4), haD.haNumero())) haCampos.add("número");
                if (!Objects.equals(haTexto(haFila, 6), haD.haRucEmisor())) haCampos.add("RUC");
                if (haD.haFecha() != null && !haD.haFecha().equals(haFecha(haFila, 5))) haCampos.add("fecha");
                if (!haIgual(haNumero(haFila, 8), haV(haD.haIgv()))) haCampos.add("IGV");
                if (!haIgual(haNumero(haFila, 9), haV(haD.haTotal()))) haCampos.add("total");
            }
            if (!haCampos.isEmpty()) {
                haMal.add("fila " + haN + " (" + String.join(", ", haCampos) + ")");
            }
        }
        Row haSiguiente = haHoja.getRow(haComprobantes.size() + 1);
        if (haSiguiente == null || !"TOTAL (sin duplicados)".equals(haTexto(haSiguiente, 0))) {
            haMal.add("la fila de totales no está donde debe (¿filas de más o de menos?)");
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Filas", HAEstado.CUMPLE, haComprobantes.size() + " filas, una por comprobante, con sus datos "
                    + "iguales a los leídos del QR (archivo, tipo, serie, número, fecha, RUC, IGV, total y estado).")
                : new HAComprobacion("Filas", HAEstado.REVISAR, "No coinciden: " + haResumir(haMal) + ".");
    }

    private static HAComprobacion haFormulas(Workbook haLibro, FormulaEvaluator haCalculo) {
        int haTotal = 0;
        List<String> haMal = new ArrayList<>();
        for (Sheet haHoja : haLibro) {
            for (Row haFila : haHoja) {
                for (Cell haCelda : haFila) {
                    if (haCelda.getCellType() != CellType.FORMULA) {
                        continue;
                    }
                    haTotal++;
                    try {
                        CellValue haV = haCalculo.evaluate(haCelda);
                        if (haV == null || haV.getCellType() == CellType.ERROR) {
                            haMal.add(haHoja.getSheetName() + "!" + haCelda.getAddress());
                        }
                    } catch (RuntimeException haE) {
                        haMal.add(haHoja.getSheetName() + "!" + haCelda.getAddress() + " (" + haE.getMessage() + ")");
                    }
                }
            }
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Fórmulas", HAEstado.CUMPLE, haTotal + " fórmulas calculadas sin errores.")
                : new HAComprobacion("Fórmulas", HAEstado.REVISAR, "Dan error o no se pueden calcular: " + haResumir(haMal) + ".");
    }

    private static HAComprobacion haIgvPorFila(Sheet haHoja, List<HAComprobante> haComprobantes, FormulaEvaluator haCalculo) {
        List<Integer> haMal = new ArrayList<>();
        int haRevisadas = 0;
        for (int haI = 0; haI < haComprobantes.size(); haI++) {
            HADatosQr haD = haComprobantes.get(haI).haDatos();
            Row haFila = haHoja.getRow(haI + 1);
            if (haD == null || haFila == null) {
                continue;
            }
            haRevisadas++;
            BigDecimal haBase = haV(haD.haTotal()).subtract(haV(haD.haIgv()));
            BigDecimal haEsperado = haBase.multiply(new BigDecimal("0.18")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal haDiferencia = haV(haD.haIgv()).subtract(haEsperado);
            if (!haIgual(haCalculado(haFila, 10, haCalculo), haBase) || !haIgual(haCalculado(haFila, 11, haCalculo), haEsperado)
                    || !haIgual(haCalculado(haFila, 12, haCalculo), haDiferencia)) {
                haMal.add(haI + 2);
            }
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Cálculo del IGV", HAEstado.CUMPLE, "En las " + haRevisadas + " filas con datos, las fórmulas de "
                    + "base, IGV esperado y diferencia dan lo mismo que el cálculo de la app.")
                : new HAComprobacion("Cálculo del IGV", HAEstado.REVISAR, "Las fórmulas no dan lo esperado en las filas "
                    + haMal.stream().map(String::valueOf).reduce((haA, haB) -> haA + ", " + haB).orElse("") + ".");
    }

    private static HAComprobacion haTotales(Sheet haHoja, Sheet haResumen, List<HAComprobante> haComprobantes, FormulaEvaluator haCalculo) {
        List<HAComprobante> haSinDuplicados = haComprobantes.stream()
                .filter(haC -> haC.haDatos() != null && haC.haEstado() != HAComprobante.HAEstado.DUPLICADO).toList();
        BigDecimal haIgv = haSinDuplicados.stream().map(haC -> haV(haC.haDatos().haIgv())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal haImporte = haSinDuplicados.stream().map(haC -> haV(haC.haDatos().haTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> haMal = new ArrayList<>();
        Row haFilaTotal = haHoja.getRow(haComprobantes.size() + 1);
        if (haFilaTotal == null || !haIgual(haCalculado(haFilaTotal, 8, haCalculo), haIgv)) haMal.add("IGV total de la hoja Comprobantes");
        if (haFilaTotal == null || !haIgual(haCalculado(haFilaTotal, 9, haCalculo), haImporte)) haMal.add("total de la hoja Comprobantes");

        Map<String, Object[]> haEsperados = new LinkedHashMap<>();
        haEsperados.put("Comprobantes leídos", new Object[]{1, haCuenta(haComprobantes, null)});
        haEsperados.put("Cumplen", new Object[]{1, haCuenta(haComprobantes, HAComprobante.HAEstado.CUMPLE)});
        haEsperados.put("No se pueden comprobar", new Object[]{1, haCuenta(haComprobantes, HAComprobante.HAEstado.NO_COMPROBABLE)});
        haEsperados.put("Para revisar", new Object[]{1, haCuenta(haComprobantes, HAComprobante.HAEstado.REVISAR)});
        haEsperados.put("Duplicados (no se suman)", new Object[]{1, haCuenta(haComprobantes, HAComprobante.HAEstado.DUPLICADO)});
        haEsperados.put("Archivos o páginas sin QR", new Object[]{1, haCuenta(haComprobantes, HAComprobante.HAEstado.SIN_QR)});
        for (String haTipo : new String[]{"Factura", "Boleta", "Nota de crédito", "Nota de débito"}) {
            List<HAComprobante> haDeTipo = haSinDuplicados.stream().filter(haC -> haTipo.equals(haC.haDatos().haNombreTipo())).toList();
            haEsperados.put(haTipo + "|cantidad", new Object[]{1, BigDecimal.valueOf(haDeTipo.size())});
            haEsperados.put(haTipo + "|total", new Object[]{2, haDeTipo.stream().map(haC -> haV(haC.haDatos().haTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add)});
        }
        haEsperados.put("IGV total (sin duplicados)", new Object[]{2, haIgv});
        haEsperados.put("Importe total (sin duplicados)", new Object[]{2, haImporte});
        for (Map.Entry<String, Object[]> haE : haEsperados.entrySet()) {
            String haEtiqueta = haE.getKey().split("\\|")[0];
            Row haFila = haBuscar(haResumen, haEtiqueta);
            int haCol = (Integer) haE.getValue()[0];
            if (haFila == null || !haIgual(haCalculado(haFila, haCol, haCalculo), (BigDecimal) haE.getValue()[1])) {
                haMal.add("Resumen: «" + haE.getKey().replace("|", " – ") + "»");
            }
        }
        return haMal.isEmpty()
                ? new HAComprobacion("Totales y resumen", HAEstado.CUMPLE, "Calculadas las fórmulas: total S/ "
                    + haImporte.setScale(2, RoundingMode.HALF_UP) + " e IGV S/ " + haIgv.setScale(2, RoundingMode.HALF_UP)
                    + " sin duplicados, y los conteos del Resumen coinciden con lo leído.")
                : new HAComprobacion("Totales y resumen", HAEstado.REVISAR, "No coinciden: " + haResumir(haMal) + ".");
    }

    private static BigDecimal haV(BigDecimal haX) {
        return haX == null ? BigDecimal.ZERO : haX;
    }

    private static BigDecimal haCuenta(List<HAComprobante> haLista, HAComprobante.HAEstado haEstado) {
        return BigDecimal.valueOf(haLista.stream().filter(haC -> haEstado == null ? haC.haDatos() != null : haC.haEstado() == haEstado).count());
    }

    private static Row haBuscar(Sheet haHoja, String haEtiqueta) {
        for (Row haFila : haHoja) {
            if (haEtiqueta.equals(haTexto(haFila, 0))) {
                return haFila;
            }
        }
        return null;
    }

    private static String haTexto(Row haFila, int haCol) {
        Cell haC = haFila.getCell(haCol);
        if (haC == null || haC.getCellType() == CellType.BLANK) {
            return null;
        }
        return haC.getCellType() == CellType.STRING ? haC.getStringCellValue() : new DataFormatter().formatCellValue(haC);
    }

    private static BigDecimal haNumero(Row haFila, int haCol) {
        Cell haC = haFila.getCell(haCol);
        return haC == null || haC.getCellType() != CellType.NUMERIC ? null : BigDecimal.valueOf(haC.getNumericCellValue());
    }

    private static LocalDate haFecha(Row haFila, int haCol) {
        Cell haC = haFila.getCell(haCol);
        if (haC == null || haC.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(haC)) {
            return null;
        }
        return haC.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static BigDecimal haCalculado(Row haFila, int haCol, FormulaEvaluator haCalculo) {
        Cell haC = haFila.getCell(haCol);
        if (haC == null) {
            return null;
        }
        try {
            CellValue haV = haCalculo.evaluate(haC);
            return haV != null && haV.getCellType() == CellType.NUMERIC ? BigDecimal.valueOf(haV.getNumberValue()) : null;
        } catch (RuntimeException haE) {
            return null;
        }
    }

    private static boolean haIgual(BigDecimal haA, BigDecimal haB) {
        if (haA == null || haB == null) {
            return haA == haB;
        }
        return haA.subtract(haB).abs().compareTo(HA_TOLERANCIA) <= 0;
    }

    private static String haResumir(List<String> haLista) {
        return haLista.size() <= 6 ? String.join("; ", haLista)
                : String.join("; ", haLista.subList(0, 6)) + "… (" + haLista.size() + " en total)";
    }
}
