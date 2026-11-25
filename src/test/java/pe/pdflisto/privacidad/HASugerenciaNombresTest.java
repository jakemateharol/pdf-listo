package pe.pdflisto.privacidad;

import org.junit.jupiter.api.Test;
import pe.pdflisto.pdf.HALectorTexto;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HASugerenciaNombresTest {
    private final HADetectorDatos haDetector = new HADetectorDatos(null);

    @Test
    void haDespuesDeEtiquetas() {
        assertEquals(List.of("María Quispe Huamán"), haNombres("Cliente: María Quispe Huamán\nDNI: 45678912"));
        assertEquals(List.of("José Luis Ramírez Torres"), haNombres("Nombres y apellidos: José Luis Ramírez Torres"));
        assertEquals(List.of("QUISPE MAMANI"), haNombres("APELLIDOS: QUISPE MAMANI"));
        assertEquals(List.of("María de los Ángeles Pérez"), haNombres("Alumna: María de los Ángeles Pérez"));
    }

    @Test
    void haDespuesDeTratamientos() {
        assertEquals(List.of("Quispe"), haNombres("Estimada Sra. Quispe: le escribimos por su trámite."));
        assertEquals(List.of("Carlos Mendoza"), haNombres("Atención: Dr. Carlos Mendoza, jefe de área"));
        assertEquals(List.of("Rosa Elena Vargas"), haNombres("Firma la Señora Rosa Elena Vargas en señal de conformidad"));
    }

    @Test
    void haJuntoAUnDni() {
        assertEquals(List.of("Juan Pérez Quispe"),
                haNombres("El arrendatario Juan Pérez Quispe, identificado con DNI 45678912, declara…"));
        assertEquals(List.of("Ana Torres"), haNombres("Se entregó a Ana Torres (DNI 12345678) el original."));
        assertEquals(List.of("Luis Paredes"), haNombres("DNI 45678912 - Luis Paredes"));
    }

    @Test
    void haSinPistasNoSeInventaNada() {
        assertTrue(haNombres("Contrato de Servicios Profesionales entre las partes").isEmpty());
        assertTrue(haNombres("Juan firmó el acta en Lima.").isEmpty(), "sin pista no se adivina");
        assertTrue(haNombres("Nombre: ________________").isEmpty());
        assertTrue(haNombres("Nombre: juan").isEmpty(), "en minúsculas no se reconoce");
        assertTrue(haNombres("Cliente: Servicios").isEmpty(), "tras una etiqueta se piden nombre y apellido");
        assertTrue(haNombres("Fecha: 12 de mayo. Universidad Nacional de Lima, DNI del alumno").isEmpty());
    }

    @Test
    void haSonSugerenciasSinMarcarYNoPisanOtrosDatos() {
        List<HADatoSensible> haD = haDetector.haDetectar(List.of(haPagina(
                "Cliente: María Quispe Huamán\nDNI: 45678912\nDomicilio: Av. Los Álamos 345, San Borja")),
                HADetectorDatos.haReglasIncorporadas(), true);
        HADatoSensible haNombre = haD.stream().filter(haX -> haX.haTipo() == HATipoDato.NOMBRE).findFirst().orElseThrow();
        assertEquals("María Quispe Huamán", haNombre.haTexto());
        assertFalse(haNombre.haProbable(), "nunca se marca solo");
        assertTrue(haNombre.haNota().startsWith("nombre de persona (después de «Cliente:»)"), haNombre.haNota());
        assertEquals(1, haD.stream().filter(haX -> haX.haTipo() == HATipoDato.NOMBRE).count(), "«Los Álamos» es parte de la dirección");
        assertTrue(haD.stream().anyMatch(haX -> haX.haTipo() == HATipoDato.DNI && haX.haProbable()));

        assertTrue(haDetector.haDetectar(List.of(haPagina("Cliente: María Quispe Huamán")), HADetectorDatos.haReglasIncorporadas())
                .stream().noneMatch(haX -> haX.haTipo() == HATipoDato.NOMBRE));
    }

    @Test
    void haEnElPdfDeEjemplo() {
        List<HADatoSensible> haD = haDetector.haDetectar(new File("ejemplos/demo-datos-sensibles.pdf"), null);
        List<String> haSugeridos = haD.stream().filter(haX -> haX.haTipo() == HATipoDato.NOMBRE).map(HADatoSensible::haTexto).toList();
        assertEquals(List.of("María Quispe Huamán"), haSugeridos);
        assertTrue(haD.stream().filter(haX -> haX.haTipo() == HATipoDato.NOMBRE).noneMatch(HADatoSensible::haProbable));
    }

    private static List<String> haNombres(String haTexto) {
        List<String> haR = new ArrayList<>();
        for (HASugerenciaNombres.HAHallazgo haH : HASugerenciaNombres.haBuscar(haTexto)) {
            haR.add(haTexto.substring(haH.haInicio(), haH.haFin()));
        }
        return haR;
    }

    private static HALectorTexto.HAPaginaTexto haPagina(String haTexto) {
        List<org.apache.pdfbox.text.TextPosition> haLetras = new ArrayList<>();
        for (int haI = 0; haI < haTexto.length(); haI++) {
            char haC = haTexto.charAt(haI);
            haLetras.add(haC == ' ' || haC == '\n' ? null : new org.apache.pdfbox.text.TextPosition(0, 595, 842,
                    new org.apache.pdfbox.util.Matrix(10, 0, 0, 10, 72 + haI * 6, 700), 72 + haI * 6 + 6, 700, 10, 6, 5,
                    String.valueOf(haC), new int[]{haC}, null, 10, 10));
        }
        return new HALectorTexto.HAPaginaTexto(0, haTexto, java.util.Collections.unmodifiableList(haLetras), 595, 842);
    }
}
