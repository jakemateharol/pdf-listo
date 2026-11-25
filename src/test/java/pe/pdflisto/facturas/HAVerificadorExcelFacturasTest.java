package pe.pdflisto.facturas;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pe.pdflisto.operaciones.HAControl;
import pe.pdflisto.verificacion.HAComprobacion;
import pe.pdflisto.verificacion.HAComprobacion.HAEstado;
import pe.pdflisto.verificacion.HAInformeVerificacion;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class HAVerificadorExcelFacturasTest {
    private final HAVerificadorExcelFacturas haVerificador = new HAVerificadorExcelFacturas();

    @TempDir
    Path haCarpeta;

    private static List<HAComprobante> haLista;
    private File haExcel;

    @BeforeEach
    void haExportarEjemplos() throws IOException {
        if (haLista == null) {
            File[] haPdfs = new File("ejemplos/facturas").listFiles((haD, haN) -> haN.toLowerCase().endsWith(".pdf"));
            assertNotNull(haPdfs, "faltan los PDF de ejemplo de facturas");
            Arrays.sort(haPdfs, Comparator.comparing(File::getName));
            haLista = new HALectorFacturas().haLeer(List.of(haPdfs), HAControl.haNinguno());
        }
        assertTrue(haLista.stream().anyMatch(haC -> haC.haEstado() == HAComprobante.HAEstado.DUPLICADO), "los ejemplos traen un duplicado");
        haExcel = haCarpeta.resolve("comprobantes.xlsx").toFile();
        new HAExportadorFacturas().haExportar(haLista, haExcel);
    }

    @Test
    void haElExcelExportadoCumple() {
        HAInformeVerificacion haI = haVerificador.haVerificar(haExcel, haLista);
        assertTrue(haI.haCumple(), () -> haTexto(haI));
        for (String haNombre : List.of("Archivo Excel", "Filas", "Fórmulas", "Cálculo del IGV", "Totales y resumen")) {
            assertEquals(HAEstado.CUMPLE, haFila(haI, haNombre).haEstado(), () -> haTexto(haI));
        }
    }

    @Test
    void haUnTotalCambiadoDaRevisar() throws Exception {
        haModificar(haH -> haH.getRow(1).getCell(9).setCellValue(99999));
        HAInformeVerificacion haI = haVerificador.haVerificar(haExcel, haLista);
        assertEquals(HAEstado.REVISAR, haFila(haI, "Filas").haEstado(), () -> haTexto(haI));
        assertEquals(HAEstado.REVISAR, haFila(haI, "Totales y resumen").haEstado(), () -> haTexto(haI));
    }

    @Test
    void haUnaFormulaRotaDaRevisar() throws Exception {
        haModificar(haH -> haH.getRow(1).getCell(11).setCellFormula("1/0"));
        HAInformeVerificacion haI = haVerificador.haVerificar(haExcel, haLista);
        assertEquals(HAEstado.REVISAR, haFila(haI, "Fórmulas").haEstado(), () -> haTexto(haI));
        assertEquals(HAEstado.REVISAR, haFila(haI, "Cálculo del IGV").haEstado(), () -> haTexto(haI));
    }

    @Test
    void haUnaFilaQueFaltaDaRevisar() throws Exception {
        haModificar(haH -> haH.shiftRows(2, haH.getLastRowNum(), -1));
        HAInformeVerificacion haI = haVerificador.haVerificar(haExcel, haLista);
        assertEquals(HAEstado.REVISAR, haFila(haI, "Filas").haEstado(), () -> haTexto(haI));
    }

    @Test
    void haUnArchivoQueNoEsExcelDaRevisarSinError() throws Exception {
        File haRoto = haCarpeta.resolve("roto.xlsx").toFile();
        Files.writeString(haRoto.toPath(), "esto no es un Excel");
        HAInformeVerificacion haI = haVerificador.haVerificar(haRoto, haLista);
        assertFalse(haI.haCumple());
        assertEquals(HAEstado.REVISAR, haFila(haI, "Archivo Excel").haEstado());
    }

    private void haModificar(Consumer<Sheet> haCambio) throws IOException {
        try (XSSFWorkbook haLibro = new XSSFWorkbook(new FileInputStream(haExcel))) {
            haCambio.accept(haLibro.getSheet("Comprobantes"));
            try (FileOutputStream haOut = new FileOutputStream(haExcel)) {
                haLibro.write(haOut);
            }
        }
    }

    private static HAComprobacion haFila(HAInformeVerificacion haI, String haNombre) {
        return haI.haComprobaciones().stream().filter(haC -> haC.haNombre().equals(haNombre)).findFirst()
                .orElseThrow(() -> new AssertionError("No hay fila «" + haNombre + "»:\n" + haTexto(haI)));
    }

    private static String haTexto(HAInformeVerificacion haI) {
        StringBuilder haSb = new StringBuilder();
        haI.haComprobaciones().forEach(haC -> haSb.append(haC.haNombre()).append(" · ").append(haC.haEstado()).append(": ")
                .append(haC.haDetalle()).append('\n'));
        return haSb.toString();
    }
}
