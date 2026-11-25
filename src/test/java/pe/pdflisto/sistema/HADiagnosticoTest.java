package pe.pdflisto.sistema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class HADiagnosticoTest {
    @TempDir
    Path haCarpeta;

    private final HADiagnostico haDiagnostico = new HADiagnostico("mrodriguez", "C:\\Users\\mrodriguez");

    @Test
    void haQuitaRutasNombresUsuarioCorreosYNumeros() {
        String haLinea = "ERROR No se pudo generar C:\\Users\\mrodriguez\\Documents\\Contrato de Juan Pérez.pdf: disco lleno";
        String haLimpia = haDiagnostico.haLimpiar(haLinea);
        assertEquals("ERROR No se pudo generar [archivo .pdf]: disco lleno", haLimpia);

        String haVarias = haDiagnostico.haLimpiar("Abriendo D:/clientes/ana lopez/informe final.docx y 'notas.txt' de mrodriguez");
        assertFalse(haVarias.contains("ana") || haVarias.contains("informe") || haVarias.contains("notas")
                || haVarias.contains("mrodriguez"), haVarias);
        assertTrue(haVarias.contains("[archivo .docx]") && haVarias.contains("[usuario]"), haVarias);

        String haCarpetaSuelta = haDiagnostico.haLimpiar("No se pudo borrar la carpeta E:\\Tesis\\Maria Quispe\\borradores");
        assertEquals("No se pudo borrar la carpeta [carpeta]", haCarpetaSuelta);

        String haDatos = haDiagnostico.haLimpiar("DNI 45678912, RUC 20601112222, cel 987 654 321, correo ana.lopez@gmail.com");
        assertEquals("DNI [número], RUC [número], cel [número], correo [correo]", haDatos);

        String haRed = haDiagnostico.haLimpiar("Archivo en \\\\servidor\\compartida\\Planilla 2026.xlsx");
        assertEquals("Archivo en [archivo .xlsx]", haRed);
    }

    @Test
    void haConservaLoQueSirveParaSoporte() {
        String haTraza = "2026-11-25T07:14:26.385-05:00 ERROR 21972 --- [pdf-listo] [exportador] p.p.ui.Exportador : "
                + "No se pudo generar";
        assertEquals(haTraza, haDiagnostico.haLimpiar(haTraza), "fecha, hora, proceso y clase quedan intactos");
        String haPila = "\tat pe.pdflisto.ocr.HAReconocerTexto.ejecutar(ReconocerTexto.java:142)";
        assertEquals(haPila, haDiagnostico.haLimpiar(haPila));
        assertEquals("OK | OCR | Idiomas en %USERPROFILE%\\AppData\\Roaming\\PDFListo\\tessdata",
                haDiagnostico.haLimpiar("OK | OCR | Idiomas en C:\\Users\\mrodriguez\\AppData\\Roaming\\PDFListo\\tessdata"));
        assertEquals("LibreOffice en C:\\Program Files\\LibreOffice\\program\\soffice.exe",
                haDiagnostico.haLimpiar("LibreOffice en C:\\Program Files\\LibreOffice\\program\\soffice.exe"));
    }

    @Test
    void haElZipTraeSistemaComponentesYRegistroLimpio() throws Exception {
        Path haDatos = Files.createDirectories(haCarpeta.resolve("PDFListo"));
        Path haLogs = Files.createDirectories(haDatos.resolve("logs"));
        Files.writeString(haLogs.resolve("pdflisto.log"),
                "07:10 INFO abierto C:\\Users\\mrodriguez\\Desktop\\Planilla Sueldos.pdf\n07:11 ERROR algo falló\n",
                StandardCharsets.UTF_8);
        try (OutputStream haO = new GZIPOutputStream(Files.newOutputStream(haLogs.resolve("pdflisto.log.2026-11-24.0.gz")))) {
            haO.write("06:00 INFO linea de ayer, cliente ana@empresa.pe\n".getBytes(StandardCharsets.UTF_8));
        }
        Files.setLastModifiedTime(haLogs.resolve("pdflisto.log.2026-11-24.0.gz"),
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - 86_400_000L));
        Files.writeString(haDatos.resolve("pdflisto.db"), "historial secreto");
        Files.writeString(haDatos.resolve("config.properties"), "ultimaCarpeta=D:/clientes/ana");

        Path haZip = haCarpeta.resolve("diagnostico.zip");
        var haR = haDiagnostico.haCrear(haZip, "1.0.1", List.of(new HAEstadoComponente("OCR", HAEstadoComponente.HANivel.OK,
                "Tesseract en C:\\Users\\mrodriguez\\AppData\\Roaming\\PDFListo\\tessdata")), haLogs, haDatos);
        assertEquals(3, haR.haLineasRegistro());

        Map<String, String> haContenido = haLeerZip(haZip);
        assertEquals(java.util.Set.of("LEEME.txt", "sistema.txt", "registro.txt"), haContenido.keySet());
        String haSistema = haContenido.get("sistema.txt");
        assertTrue(haSistema.contains("PDF Listo 1.0.1") && haSistema.contains("Java:") && haSistema.contains("OK | OCR"), haSistema);
        String haRegistro = haContenido.get("registro.txt");
        assertTrue(haRegistro.indexOf("linea de ayer") < haRegistro.indexOf("algo falló"), "de lo más viejo a lo más nuevo");
        String haTodo = String.join("\n", haContenido.values());
        for (String haProhibido : List.of("mrodriguez", "Planilla", "Sueldos", "ana@empresa.pe", "historial secreto",
                "ultimaCarpeta", "clientes")) {
            assertFalse(haTodo.contains(haProhibido), "el diagnóstico no debe contener «" + haProhibido + "»");
        }
        assertTrue(haRegistro.contains("[archivo .pdf]"));
        assertFalse(Files.list(haCarpeta).anyMatch(haP -> haP.getFileName().toString().endsWith(".tmp")), "no quedan temporales");
    }

    @Test
    void haUnRegistroEnormeSeRecortaALoMasReciente() throws Exception {
        Path haLogs = Files.createDirectories(haCarpeta.resolve("logs"));
        StringBuilder haSb = new StringBuilder();
        for (int haI = 0; haI < 120_000; haI++) {
            haSb.append("linea ").append(haI).append(" con texto de relleno para que el registro pese bastante\n");
        }
        Files.writeString(haLogs.resolve("pdflisto.log"), haSb);
        List<String> haLineas = haDiagnostico.haRegistro(haLogs);
        long haTamano = haLineas.stream().mapToLong(haL -> haL.length() + 1).sum();
        assertTrue(haTamano <= HADiagnostico.HA_MAX_REGISTRO + 200, "tamaño " + haTamano);
        assertEquals("linea 119999 con texto de relleno para que el registro pese bastante", haLineas.get(haLineas.size() - 1));
        assertTrue(haLineas.get(0).contains("se omitieron"));
    }

    private static Map<String, String> haLeerZip(Path haZip) throws Exception {
        Map<String, String> haM = new HashMap<>();
        try (ZipInputStream haIn = new ZipInputStream(Files.newInputStream(haZip))) {
            ZipEntry haE;
            while ((haE = haIn.getNextEntry()) != null) {
                haM.put(haE.getName(), new String(haIn.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return haM;
    }
}
