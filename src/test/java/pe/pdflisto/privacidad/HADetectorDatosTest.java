package pe.pdflisto.privacidad;

import org.junit.jupiter.api.Test;
import pe.pdflisto.model.HAReglaDeteccion;
import pe.pdflisto.pdf.HALectorTexto;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HADetectorDatosTest {
    private final HADetectorDatos haDetector = new HADetectorDatos(null);

    @Test
    void haValidaElDigitoVerificadorDelRuc() {
        assertTrue(HAValidadorRuc.haValido("20131312955"));
        assertTrue(HAValidadorRuc.haValido("20100047218"));
        assertFalse(HAValidadorRuc.haValido("20131312956"));
        assertFalse(HAValidadorRuc.haValido("12345678901"));
        assertFalse(HAValidadorRuc.haValido("2013131295"));
    }

    @Test
    void haRucValidoSiRucInventadoNo() {
        List<HADatoSensible> haD = haDetectar("Proveedor RUC 20131312955. Código interno 20131312956.");
        assertEquals(1, haD.size());
        assertEquals(HATipoDato.RUC, haD.get(0).haTipo());
        assertEquals("20131312955", haD.get(0).haValor());
    }

    @Test
    void haDniConYSinContexto() {
        List<HADatoSensible> haD = haDetectar("Juan Pérez, DNI 45678912, vive aquí. Pedido 87654321. Fecha 20241231.");
        assertEquals(2, haD.size(), haD.toString());
        HADatoSensible haConPalabra = haD.stream().filter(haX -> haX.haValor().equals("45678912")).findFirst().orElseThrow();
        HADatoSensible haSinPalabra = haD.stream().filter(haX -> haX.haValor().equals("87654321")).findFirst().orElseThrow();
        assertTrue(haConPalabra.haProbable());
        assertFalse(haSinPalabra.haProbable(), "sin la palabra DNI cerca debe quedar como 'posible'");
        assertNotNull(haSinPalabra.haNota());
    }

    @Test
    void haCelularesCorreosCuentasYCci() {
        List<HADatoSensible> haD = haDetectar("Llamar al +51 987 654 321 o escribir a ana.lopez@correo.pe\n"
                + "Cuenta de ahorros BCP: 191-12345678-0-12\n"
                + "CCI: 002-191-112345678012-55");
        assertTrue(haTiene(haD, HATipoDato.CELULAR, "987654321"));
        assertTrue(haTiene(haD, HATipoDato.CORREO, "ana.lopez@correo.pe"));
        assertTrue(haTiene(haD, HATipoDato.CUENTA, "19112345678012"));
        assertTrue(haTiene(haD, HATipoDato.CCI, "00219111234567801255"));
        assertTrue(haD.stream().allMatch(HADatoSensible::haProbable));
    }

    @Test
    void haUnNumeroLargoSinContextoNoEsCuenta() {
        assertTrue(haDetectar("Total de unidades vendidas: 1234567890123").stream()
                .noneMatch(haX -> haX.haTipo() == HATipoDato.CUENTA));
    }

    @Test
    void haDirecciones() {
        List<HADatoSensible> haD = haDetectar("Domicilio: Av. Arequipa 1250, Lince\nTeléfono fijo");
        assertTrue(haD.stream().anyMatch(haX -> haX.haTipo() == HATipoDato.DIRECCION && haX.haTexto().startsWith("Av. Arequipa 1250")));
    }

    @Test
    void haReglasDelUsuario() {
        List<HAReglaDeteccion> haReglas = new ArrayList<>(HADetectorDatos.haReglasIncorporadas());
        haReglas.add(HAReglaDeteccion.haDelUsuario("Cliente", HAReglaDeteccion.HAModo.PALABRA, "maria quispe"));
        haReglas.add(HAReglaDeteccion.haDelUsuario("Expediente", HAReglaDeteccion.HAModo.PATRON, "EXP-\\d{4}"));
        List<HADatoSensible> haD = haDetector.haDetectar(List.of(haPagina("Atendida: María Quispe. Expediente EXP-2024.")), haReglas);
        assertTrue(haD.stream().anyMatch(haX -> haX.haTexto().equals("María Quispe")), "sin importar tildes ni mayúsculas");
        assertTrue(haD.stream().anyMatch(haX -> haX.haTexto().equals("EXP-2024")));
    }

    @Test
    void haCadaDigitoPerteneceAUnSoloDato() {
        List<HADatoSensible> haD = haDetectar("RUC 10456789123");
        long haRucs = haD.stream().filter(haX -> haX.haTipo() == HATipoDato.RUC).count();
        long haDnis = haD.stream().filter(haX -> haX.haTipo() == HATipoDato.DNI).count();
        assertEquals(haDnis + haRucs, haD.size());
        assertTrue(haDnis == 0);
    }

    private List<HADatoSensible> haDetectar(String haTexto) {
        return haDetector.haDetectar(List.of(haPagina(haTexto)), HADetectorDatos.haReglasIncorporadas());
    }

    private static boolean haTiene(List<HADatoSensible> haD, HATipoDato haTipo, String haValor) {
        return haD.stream().anyMatch(haX -> haX.haTipo() == haTipo && haX.haValor().equals(haValor));
    }

    private static HALectorTexto.HAPaginaTexto haPagina(String haTexto) {
        List<org.apache.pdfbox.text.TextPosition> haLetras = new ArrayList<>();
        for (int haI = 0; haI < haTexto.length(); haI++) {
            char haC = haTexto.charAt(haI);
            haLetras.add(haC == ' ' || haC == '\n' ? null : haLetra(haI, haC));
        }
        return new HALectorTexto.HAPaginaTexto(0, haTexto, java.util.Collections.unmodifiableList(haLetras), 595, 842);
    }

    private static org.apache.pdfbox.text.TextPosition haLetra(int haI, char haC) {
        return new org.apache.pdfbox.text.TextPosition(0, 595, 842, new org.apache.pdfbox.util.Matrix(10, 0, 0, 10, 72 + haI * 6, 700),
                72 + haI * 6 + 6, 700, 10, 6, 5, String.valueOf(haC), new int[]{haC}, null, 10, 10);
    }
}
