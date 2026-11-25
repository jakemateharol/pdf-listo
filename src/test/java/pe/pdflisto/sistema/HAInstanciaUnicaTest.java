package pe.pdflisto.sistema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HAInstanciaUnicaTest {
    @TempDir
    Path haCarpeta;

    @Test
    void haLaSegundaAperturaEnviaLosArchivosALaPrimera() throws Exception {
        try (HAInstanciaUnica haPrimera = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000)) {
            assertNotNull(haPrimera);
            assertTrue(haPrimera.haEsPrincipal());
            List<List<File>> haRecibidos = new CopyOnWriteArrayList<>();
            haPrimera.haAlRecibir(haRecibidos::add);

            File haPdf = new File("informe con espacios ñ.pdf");
            assertNull(HAInstanciaUnica.haIniciar(haCarpeta, List.of(haPdf), 2000), "la segunda no abre otra ventana");
            haEsperar(() -> haRecibidos.size() == 1);
            assertEquals(List.of(haPdf.getAbsoluteFile()), haRecibidos.get(0));

            assertNull(HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000));
            haEsperar(() -> haRecibidos.size() == 2);
            assertTrue(haRecibidos.get(1).isEmpty());
        }
    }

    @Test
    void haLoQueLlegaAntesDeQueLaVentanaEsteListaNoSePierde() throws Exception {
        try (HAInstanciaUnica haPrimera = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000)) {
            assertNull(HAInstanciaUnica.haIniciar(haCarpeta, List.of(new File("a.pdf"), new File("b.pdf")), 2000));
            List<List<File>> haRecibidos = new CopyOnWriteArrayList<>();
            haPrimera.haAlRecibir(haRecibidos::add);
            assertEquals(1, haRecibidos.size());
            assertEquals(2, haRecibidos.get(0).size());
        }
    }

    @Test
    void haAlCerrarLaPrimeraLaSiguienteAperturaEsLaPrincipal() {
        HAInstanciaUnica haPrimera = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000);
        haPrimera.close();
        assertFalse(Files.exists(haCarpeta.resolve(HAInstanciaUnica.HA_PUERTO)), "no queda el archivo del puerto");
        try (HAInstanciaUnica haNueva = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000)) {
            assertNotNull(haNueva);
            assertTrue(haNueva.haEsPrincipal());
        }
    }

    @Test
    void haSinLaClaveCorrectaNoSeAceptaNada() throws Exception {
        try (HAInstanciaUnica haPrimera = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000)) {
            List<List<File>> haRecibidos = new CopyOnWriteArrayList<>();
            haPrimera.haAlRecibir(haRecibidos::add);
            int haPuerto = Integer.parseInt(Files.readAllLines(haCarpeta.resolve(HAInstanciaUnica.HA_PUERTO)).get(0));
            try (Socket haS = new Socket(InetAddress.getLoopbackAddress(), haPuerto)) {
                var haOut = new OutputStreamWriter(haS.getOutputStream(), StandardCharsets.UTF_8);
                haOut.write("PDFLISTO 1\nclave-falsa\n1\nC:\\\\otro.pdf\n");
                haOut.flush();
                assertEquals(-1, haS.getInputStream().read(), "cierra la conexión sin responder OK");
            }
            assertNull(HAInstanciaUnica.haIniciar(haCarpeta, List.of(new File("x.pdf")), 2000));
            haEsperar(() -> haRecibidos.size() == 1);
            assertEquals("x.pdf", haRecibidos.get(0).get(0).getName());
        }
    }

    @Test
    void haSiLaOtraNoRespondeSeAbreUnaVentanaIgual() throws Exception {
        try (HAInstanciaUnica haPrimera = HAInstanciaUnica.haIniciar(haCarpeta, List.of(), 2000)) {
            Files.writeString(haCarpeta.resolve(HAInstanciaUnica.HA_PUERTO), "1\nclave\n");
            long haInicio = System.nanoTime();
            HAInstanciaUnica haSegunda = HAInstanciaUnica.haIniciar(haCarpeta, List.of(new File("a.pdf")), 600);
            assertNotNull(haSegunda, "el usuario no se queda sin app");
            assertFalse(haSegunda.haEsPrincipal());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - haInicio) < 5000);
            haSegunda.close();
        }
    }

    private static void haEsperar(java.util.function.BooleanSupplier haCondicion) throws InterruptedException {
        long haLimite = System.currentTimeMillis() + 3000;
        while (!haCondicion.getAsBoolean() && System.currentTimeMillis() < haLimite) {
            Thread.sleep(20);
        }
        assertTrue(haCondicion.getAsBoolean(), "no llegó a tiempo");
    }
}
