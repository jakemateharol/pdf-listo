package pe.pdflisto.operaciones;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HACancelacionYProgresoTest {
    private final HAMotorPdf haMotor = new HAMotorPdf();

    @TempDir
    Path haCarpeta;

    @Test
    void haCancelarALaMitadNoDejaNingunArchivo() throws Exception {
        File haEntrada = haPdf("largo.pdf", 20);
        File haSalida = haCarpeta.resolve("salida.pdf").toFile();
        AtomicInteger haAvisos = new AtomicInteger();
        HAControl[] haControl = new HAControl[1];
        haControl[0] = new HAControl((haFraccion, haMensaje) -> {
            if (haFraccion > 0.3) {
                haControl[0].haCancelar();
            }
            haAvisos.incrementAndGet();
        });
        assertThrows(HACanceladoException.class, () -> haMotor.haEjecutar(haEntrada, null,
                new HANumerar("{n}", HAPosicion.ABAJO_CENTRO, 1, 1, 11), haSalida, haControl[0]));
        assertFalse(haSalida.exists(), "no debe quedar el resultado");
        try (var haArchivos = Files.list(haCarpeta)) {
            assertEquals(List.of("largo.pdf"), haArchivos.map(haP -> haP.getFileName().toString()).toList(),
                    "no deben quedar temporales");
        }
        assertTrue(haAvisos.get() > 3);
    }

    @Test
    void haElAvanceDeUnTrabajoPorPartesSubeHastaElFinal() throws Exception {
        File haEntrada = haPdf("partes.pdf", 6);
        List<Double> haFracciones = new ArrayList<>();
        HAControl haControl = new HAControl((haF, haM) -> {
            if (haF >= 0) {
                haFracciones.add(haF);
            }
        });
        List<File> haPartes = new HADivisor(haMotor).haDividir(haEntrada, null, HAPlanDivision.haCadaN(2, 6, "partes"),
                haCarpeta.resolve("salida").toFile(), haControl);
        assertEquals(3, haPartes.size());
        for (int haI = 1; haI < haFracciones.size(); haI++) {
            assertTrue(haFracciones.get(haI) >= haFracciones.get(haI - 1) - 1e-9, "el avance nunca retrocede: " + haFracciones);
        }
        assertTrue(haFracciones.get(haFracciones.size() - 1) > 0.6);
    }

    @Test
    void haCancelarUnaDivisionBorraLasPartesYaCreadas() throws Exception {
        File haEntrada = haPdf("dividir.pdf", 6);
        File haDestino = haCarpeta.resolve("partes").toFile();
        HAControl[] haControl = new HAControl[1];
        haControl[0] = new HAControl((haF, haM) -> {
            if (haF > 0.5) {
                haControl[0].haCancelar();
            }
        });
        assertThrows(HACanceladoException.class, () -> new HADivisor(haMotor).haDividir(haEntrada, null,
                HAPlanDivision.haCadaN(2, 6, "dividir"), haDestino, haControl[0]));
        String[] haQuedan = haDestino.list();
        assertTrue(haQuedan == null || haQuedan.length == 0, "no debe quedar un juego incompleto de partes");
    }

    @Test
    void haTramosRepartenLaBarra() {
        List<Double> haF = new ArrayList<>();
        HAControl haC = new HAControl((haX, haM) -> haF.add(haX));
        haC.haTramo(1, 4).haAvance(1, 2, null);
        assertEquals(0.375, haF.get(0), 1e-9);
        haC.haCancelar();
        assertThrows(HACanceladoException.class, () -> haC.haTramo(2, 4).haComprobar(), "cancelar afecta a todos los tramos");
    }

    private File haPdf(String haNombre, int haPaginas) throws IOException {
        File haArchivo = haCarpeta.resolve(haNombre).toFile();
        try (PDDocument haDoc = new PDDocument()) {
            PDType1Font haFuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int haI = 1; haI <= haPaginas; haI++) {
                PDPage haP = new PDPage(PDRectangle.A4);
                haDoc.addPage(haP);
                try (PDPageContentStream haCs = new PDPageContentStream(haDoc, haP)) {
                    haCs.beginText();
                    haCs.setFont(haFuente, 12);
                    haCs.newLineAtOffset(72, 700);
                    haCs.showText("Pagina " + haI);
                    haCs.endText();
                }
            }
            haDoc.save(haArchivo);
        }
        return haArchivo;
    }
}
